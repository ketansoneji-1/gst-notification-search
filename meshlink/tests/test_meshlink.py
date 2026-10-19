import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "agent"))

from server import core  # noqa: E402
from server.main import Settings, create_app  # noqa: E402
import meshlink as agent  # noqa: E402

ADMIN = {"Authorization": "Bearer admin-token-0123456789"}


@pytest.fixture
def client(tmp_path):
    s = Settings(data_dir=str(tmp_path), admin_token=ADMIN["Authorization"][7:],
                 public_endpoint="vpn.example.com:51820", hub_apply=False, watch_public_ip=False)
    return TestClient(create_app(s))


@pytest.fixture
def auto_client(tmp_path):
    """Default on-premises mode: endpoint follows the address the device used."""
    s = Settings(data_dir=str(tmp_path), admin_token=ADMIN["Authorization"][7:],
                 hub_apply=False, watch_public_ip=False, tls_fingerprint="AB" * 32)
    return TestClient(create_app(s), base_url="https://192.168.1.10:8443")


def authkey(c, **kw):
    r = c.post("/api/v1/admin/authkeys", json=kw, headers=ADMIN)
    assert r.status_code == 200
    return r.json()["authkey"]


def join(c, key, name, **kw):
    _, pub = core.generate_keypair()
    r = c.post("/api/v1/register", json={"authkey": key, "pubkey": pub, "hostname": name, **kw})
    assert r.status_code == 200, r.text
    return r.json()


def test_admin_requires_token(client):
    assert client.get("/api/v1/admin/nodes").status_code == 401
    assert client.get("/api/v1/admin/nodes", headers={"Authorization": "Bearer wrong"}).status_code == 401


def test_register_and_peers(client):
    key = authkey(client, reusable=True)
    a = join(client, key, "Office PC")
    b = join(client, key, "office pc")
    assert a["node"]["name"] == "office-pc" and b["node"]["name"] == "office-pc-2"
    assert a["node"]["ip"] != b["node"]["ip"] != "100.96.0.1"
    cfg = client.get("/api/v1/config", headers={"Authorization": f"Bearer {a['node_token']}"}).json()
    assert [p["name"] for p in cfg["peers"]] == ["office-pc-2"]
    assert "__PRIVATE_KEY__" in cfg["wireguard"] and "Endpoint = vpn.example.com:51820" in cfg["wireguard"]
    assert "AllowedIPs = 100.96.0.0/16" in cfg["wireguard"]


def test_single_use_and_revoked_keys(client):
    key = authkey(client)
    join(client, key, "a")
    _, pub = core.generate_keypair()
    r = client.post("/api/v1/register", json={"authkey": key, "pubkey": pub, "hostname": "b"})
    assert r.status_code == 403
    assert client.post("/api/v1/register", json={"authkey": "bogus", "pubkey": pub, "hostname": "b"}).status_code == 403


def test_invalid_pubkey_rejected(client):
    r = client.post("/api/v1/register", json={"authkey": authkey(client), "pubkey": "nope", "hostname": "x"})
    assert r.status_code == 422


def test_direct_peer_when_endpoint_known(client):
    key = authkey(client, reusable=True)
    a = join(client, key, "server", endpoint="203.0.113.5:51821")
    b = join(client, key, "laptop")
    cfg_b = client.get("/api/v1/config", headers={"Authorization": f"Bearer {b['node_token']}"}).json()
    assert "Endpoint = 203.0.113.5:51821" in cfg_b["wireguard"]
    cfg_a = client.get("/api/v1/config", headers={"Authorization": f"Bearer {a['node_token']}"}).json()
    assert "ListenPort = 51821" in cfg_a["wireguard"]
    assert f"AllowedIPs = {b['node']['ip']}/32" in cfg_a["wireguard"]


def test_acl_limits_visibility(client):
    staff = authkey(client, tags=["staff"], reusable=True)
    srv = authkey(client, tags=["servers"], reusable=True)
    s1 = join(client, staff, "alice")
    join(client, staff, "bob")
    join(client, srv, "rdp-host", rdp_enabled=True)
    acl = {"rules": [{"src": ["tag:staff"], "dst": ["tag:servers"], "ports": ["rdp"]}]}
    assert client.put("/api/v1/admin/acl", json=acl, headers=ADMIN).status_code == 200
    cfg = client.get("/api/v1/config", headers={"Authorization": f"Bearer {s1['node_token']}"}).json()
    assert [p["name"] for p in cfg["peers"]] == ["rdp-host"]
    assert cfg["peers"][0]["rdp_enabled"] is True
    bad = {"rules": [{"src": [], "dst": ["*"]}]}
    assert client.put("/api/v1/admin/acl", json=bad, headers=ADMIN).status_code == 422


def test_firewall_rules():
    a = core.Node(1, "a", "k", "100.96.0.2", ["staff"])
    b = core.Node(2, "b", "k", "100.96.0.3", ["servers"])
    rules = core.render_firewall({"rules": [{"src": ["tag:staff"], "dst": ["tag:servers"],
                                             "ports": ["rdp", "udp:3389"]}]}, [a, b], "meshlink0")
    assert "-s 100.96.0.2/32 -d 100.96.0.3/32 -p tcp --dport 3389 -j ACCEPT" in rules
    assert "-p udp --dport 3389" in rules
    assert "-s 100.96.0.3/32 -d 100.96.0.2/32" not in rules
    assert rules.strip().endswith("COMMIT")


def test_delete_and_logout(client):
    key = authkey(client, reusable=True)
    a = join(client, key, "a")
    h = {"Authorization": f"Bearer {a['node_token']}"}
    assert client.post("/api/v1/logout", headers=h).status_code == 200
    assert client.get("/api/v1/config", headers=h).status_code == 401


def test_ip_allocation_skips_hub():
    assert core.hub_ip("10.0.0.0/30") == "10.0.0.1"
    assert core.allocate_ip("10.0.0.0/30", set()) == "10.0.0.2"
    with pytest.raises(RuntimeError):
        core.allocate_ip("10.0.0.0/30", {"10.0.0.2"})


def test_agent_hosts_block(tmp_path, monkeypatch):
    hosts = tmp_path / "hosts"
    hosts.write_text("127.0.0.1 localhost\n")
    monkeypatch.setattr(agent, "hosts_path", lambda: hosts)
    agent.update_hosts([{"ip": "100.96.0.2", "name": "pc"}], "mesh")
    agent.update_hosts([{"ip": "100.96.0.3", "name": "pc"}], "mesh")
    text = hosts.read_text()
    assert text.count(agent.HOSTS_BEGIN) == 1 and "100.96.0.3\tpc.mesh pc" in text
    agent.update_hosts([], "")
    assert hosts.read_text() == "127.0.0.1 localhost\n"


def test_agent_strip_conf():
    out = agent.strip_conf("[Interface]\nPrivateKey = x\nAddress = 1.2.3.4/32\nDNS = 1.1.1.1\n")
    assert "Address" not in out and "DNS" not in out and "PrivateKey = x" in out


def test_auto_endpoint_follows_request_host(auto_client):
    a = join(auto_client, authkey(auto_client), "office-pc")
    assert "Endpoint = 192.168.1.10:51820" in a["wireguard"]
    h = {"Authorization": f"Bearer {a['node_token']}"}
    cfg = auto_client.get("https://203.0.113.7:8443/api/v1/config", headers=h).json()
    assert "Endpoint = 203.0.113.7:51820" in cfg["wireguard"]


def test_manual_public_ip_is_advertised(auto_client):
    r = auto_client.put("/api/v1/admin/server-info", json={"public_ip": "203.0.113.9"}, headers=ADMIN)
    assert r.status_code == 200
    info = auto_client.get("/api/v1/admin/server-info", headers=ADMIN).json()
    assert info["public_ip"] == "203.0.113.9" and info["tls_fingerprint"] == "AB" * 32
    a = join(auto_client, authkey(auto_client), "pc")
    assert a["server_urls"]["public"] == "https://203.0.113.9:8443"
    bad = auto_client.put("/api/v1/admin/server-info", json={"public_ip": "not-an-ip"}, headers=ADMIN)
    assert bad.status_code == 422


def test_rdp_allowed_from_follows_acl(client):
    staff = authkey(client, tags=["staff"], reusable=True)
    srv = authkey(client, tags=["servers"], reusable=True)
    alice = join(client, staff, "alice")
    host = join(client, srv, "rdp-host", rdp_enabled=True)
    acl = {"rules": [{"src": ["tag:staff"], "dst": ["tag:servers"], "ports": ["rdp"]}]}
    client.put("/api/v1/admin/acl", json=acl, headers=ADMIN)
    cfg = client.get("/api/v1/config", headers={"Authorization": f"Bearer {host['node_token']}"}).json()
    assert cfg["rdp_allowed_from"] == [alice["node"]["ip"]]
    acl = {"rules": [{"src": ["tag:staff"], "dst": ["tag:servers"], "ports": ["ssh"]}]}
    client.put("/api/v1/admin/acl", json=acl, headers=ADMIN)
    cfg = client.get("/api/v1/config", headers={"Authorization": f"Bearer {host['node_token']}"}).json()
    assert cfg["rdp_allowed_from"] == []


def test_public_ip_classification():
    from server import upnp
    assert upnp.classify_public_ip("100.70.1.2").startswith("The ISP uses carrier-grade NAT")
    assert "double NAT" in upnp.classify_public_ip("192.168.0.5")
    assert upnp.classify_public_ip("49.36.10.20") is None


def test_self_signed_cert_is_stable(tmp_path):
    from server.tls import ensure_cert
    _, _, fp1 = ensure_cert(tmp_path)
    _, _, fp2 = ensure_cert(tmp_path)
    assert fp1 == fp2 and len(fp1) == 64


def test_agent_server_candidates_order():
    st = {"last_good": "https://203.0.113.7:8443", "servers": ["https://192.168.1.10:8443/"],
          "learned_servers": {"lan": "https://192.168.1.10:8443", "public": "https://198.51.100.4:8443"}}
    assert agent.server_candidates(st) == ["https://203.0.113.7:8443", "https://192.168.1.10:8443",
                                           "https://198.51.100.4:8443"]


def test_upnp_against_fake_router(monkeypatch):
    import threading
    from http.server import BaseHTTPRequestHandler, HTTPServer
    from server import upnp

    calls = []
    desc = b"""<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><device><deviceList>
      <device><serviceList><service>
        <serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
        <controlURL>/ctl/IPConn</controlURL></service></serviceList></device>
    </deviceList></device></root>"""

    class H(BaseHTTPRequestHandler):
        def log_message(self, *a):
            pass

        def do_GET(self):
            self.send_response(200); self.end_headers(); self.wfile.write(desc)

        def do_POST(self):
            body = self.rfile.read(int(self.headers["Content-Length"])).decode()
            calls.append((self.headers["SOAPAction"], body))
            self.send_response(200); self.end_headers()
            self.wfile.write(b'<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>'
                             b'<u:R xmlns:u="x"><NewExternalIPAddress>49.36.10.20</NewExternalIPAddress>'
                             b'</u:R></s:Body></s:Envelope>')

    srv = HTTPServer(("127.0.0.1", 0), H)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    monkeypatch.setattr(upnp, "_discover", lambda: [f"http://127.0.0.1:{srv.server_port}/desc.xml"])
    gw = upnp.Gateway.find()
    assert gw.control_url.endswith("/ctl/IPConn")
    assert gw.external_ip() == "49.36.10.20"
    gw.add_port_mapping(51820, "udp", "MeshLink WireGuard")
    assert "AddPortMapping" in calls[-1][0] and "<NewExternalPort>51820</NewExternalPort>" in calls[-1][1]
    assert "<NewProtocol>UDP</NewProtocol>" in calls[-1][1]
    srv.shutdown()


def test_upnp_ignores_public_gateway():
    from server import upnp
    with pytest.raises(upnp.UPnPError):
        upnp._local_only("http://8.8.8.8:5000/desc.xml")
