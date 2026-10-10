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
    s = Settings(db_path=str(tmp_path / "t.db"), admin_token=ADMIN["Authorization"][7:],
                 public_endpoint="vpn.example.com:51820", hub_apply=False)
    return TestClient(create_app(s))


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
