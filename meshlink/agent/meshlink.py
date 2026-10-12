#!/usr/bin/env python3
"""MeshLink agent: joins this device to a MeshLink network and provides RDP helpers.

Requires Python 3.9+ and the official WireGuard tools (wg / wg-quick, or WireGuard for Windows).
Uses only the standard library; private keys are generated and kept on this device.

  meshlink up --server https://192.168.1.10:8443,https://203.0.113.7:8443 \
              --fingerprint AB12... --authkey mlk-... [--rdp]
  meshlink daemon            # keep config in sync (run as a service)
  meshlink set-server URL[,URL]   # e.g. after the office public IP changed
  meshlink status
  meshlink rdp <device> [--user NAME] [--fullscreen]
  meshlink enable-rdp        # Windows: allow RDP only from the mesh
  meshlink down | logout
"""
from __future__ import annotations

import argparse
import base64
import ctypes
import hashlib
import http.client
import json
import os
import platform
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.parse
from pathlib import Path

IFACE = "meshlink0"
POLL_SECONDS = 30
SYSTEM = platform.system()  # 'Linux', 'Windows', 'Darwin'
HOSTS_BEGIN, HOSTS_END = "# BEGIN MeshLink", "# END MeshLink"


# --------------------------------------------------------------------------- paths & state

def state_dir() -> Path:
    if os.environ.get("MESHLINK_STATE_DIR"):
        d = Path(os.environ["MESHLINK_STATE_DIR"])
    elif SYSTEM == "Windows":
        d = Path(os.environ.get("ProgramData", r"C:\ProgramData")) / "MeshLink"
    elif SYSTEM == "Darwin":
        d = Path("/usr/local/var/meshlink")
    else:
        d = Path("/var/lib/meshlink")
    d.mkdir(parents=True, exist_ok=True)
    return d


def state_file() -> Path:
    return state_dir() / "state.json"


def load_state() -> dict:
    try:
        return json.loads(state_file().read_text())
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def save_state(st: dict) -> None:
    p = state_file()
    p.write_text(json.dumps(st, indent=2))
    restrict(p)


def restrict(path: Path) -> None:
    """Make a secret-bearing file readable by its owner only."""
    if SYSTEM == "Windows":
        subprocess.run(["icacls", str(path), "/inheritance:r", "/grant:r", "SYSTEM:F",
                        "/grant:r", "Administrators:F"], capture_output=True)
    else:
        os.chmod(path, 0o600)


def conf_path() -> Path:
    return state_dir() / f"{IFACE}.conf"


def is_admin() -> bool:
    if SYSTEM == "Windows":
        try:
            return bool(ctypes.windll.shell32.IsUserAnAdmin())
        except Exception:
            return False
    return os.geteuid() == 0


def die(msg: str, code: int = 1) -> None:
    print(f"error: {msg}", file=sys.stderr)
    sys.exit(code)


# --------------------------------------------------------------------------- keys

def generate_keypair() -> tuple[str, str]:
    """WireGuard (Curve25519) keypair. Prefers `wg genkey`; falls back to `cryptography`."""
    wg = find_wg()
    if wg:
        priv = subprocess.run([wg, "genkey"], capture_output=True, text=True, check=True).stdout.strip()
        pub = subprocess.run([wg, "pubkey"], input=priv, capture_output=True, text=True,
                             check=True).stdout.strip()
        return priv, pub
    try:
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
    except ImportError:
        die("WireGuard tools not found. Install WireGuard first (https://www.wireguard.com/install/).")
    k = X25519PrivateKey.generate()
    raw = k.private_bytes(serialization.Encoding.Raw, serialization.PrivateFormat.Raw,
                          serialization.NoEncryption())
    pub = k.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
    return base64.b64encode(raw).decode(), base64.b64encode(pub).decode()


# --------------------------------------------------------------------------- HTTP

class ServerUnreachable(RuntimeError):
    pass


def server_candidates(st: dict) -> list[str]:
    """Addresses to try, in order: the last one that worked, the ones the user gave, and the
    LAN / public addresses the server last reported (this is how a new public IP is picked up)."""
    learned = st.get("learned_servers", {})
    urls = [st.get("last_good")] + st.get("servers", []) + [learned.get("lan"), learned.get("public")]
    return list(dict.fromkeys(u.rstrip("/") for u in urls if u))


def _request(base: str, fingerprint: str, method: str, path: str, data: bytes | None,
             headers: dict) -> tuple[int, bytes]:
    """HTTPS request that only proceeds if the server certificate matches the pinned SHA-256
    fingerprint; nothing (including the device token) is sent before that check."""
    u = urllib.parse.urlsplit(base)
    if u.scheme != "https":
        raise RuntimeError("server URL must start with https://")
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE  # trust is established by the fingerprint check below
    conn = http.client.HTTPSConnection(u.hostname, u.port or 443, timeout=6, context=ctx)
    try:
        try:
            conn.connect()
        except OSError as e:
            raise ServerUnreachable(f"{base}: {e}") from None
        der = conn.sock.getpeercert(binary_form=True)
        actual = hashlib.sha256(der).hexdigest().upper()
        if actual != fingerprint.replace(":", "").upper():
            raise RuntimeError(f"certificate fingerprint mismatch for {base} (got {actual}). "
                               "Possible interception; not sending anything.")
        conn.timeout = 20
        conn.sock.settimeout(20)
        conn.request(method, path, body=data, headers=headers)
        r = conn.getresponse()
        return r.status, r.read()
    finally:
        conn.close()


def api(st: dict, method: str, path: str, body: dict | None = None, token: str | None = None) -> dict:
    if not st.get("fingerprint"):
        die("server certificate fingerprint unknown; pass --fingerprint (shown on the admin dashboard).")
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    errors = []
    for base in server_candidates(st):
        try:
            status, raw = _request(base, st["fingerprint"], method, path, data, headers)
        except ServerUnreachable as e:
            errors.append(str(e))
            continue
        if st.get("last_good") != base:
            st["last_good"] = base
        if status >= 400:
            try:
                detail = json.loads(raw).get("detail", raw.decode(errors="replace"))
            except Exception:
                detail = raw.decode(errors="replace")
            raise RuntimeError(f"server returned {status}: {detail}")
        return json.loads(raw)
    raise RuntimeError("server unreachable on all known addresses: " + "; ".join(errors)
                       + "\nIf the office public IP changed, run: meshlink set-server https://NEW-IP:8443")


# --------------------------------------------------------------------------- WireGuard control

def find_wg() -> str | None:
    if SYSTEM == "Windows":
        p = Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "WireGuard" / "wg.exe"
        return str(p) if p.exists() else shutil.which("wg")
    return shutil.which("wg")


def wireguard_exe() -> str:
    p = Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "WireGuard" / "wireguard.exe"
    if not p.exists():
        die("WireGuard for Windows is not installed (https://www.wireguard.com/install/).")
    return str(p)


def interface_up() -> bool:
    wg = find_wg()
    return bool(wg) and subprocess.run([wg, "show", IFACE], capture_output=True).returncode == 0


def strip_conf(text: str) -> str:
    """Equivalent of `wg-quick strip`: drop wg-quick-only keys so `wg syncconf` accepts it."""
    skip = ("address", "dns", "mtu", "table", "preup", "postup", "predown", "postdown", "saveconfig")
    return "\n".join(l for l in text.splitlines() if l.split("=", 1)[0].strip().lower() not in skip) + "\n"


def apply_config(wg_conf: str, private_key: str) -> bool:
    """Write the config and bring the tunnel up or hot-reload it. Returns True if it changed."""
    text = wg_conf.replace("__PRIVATE_KEY__", private_key)
    path = conf_path()
    changed = not path.exists() or path.read_text() != text
    if changed:
        path.write_text(text)
        restrict(path)

    if not interface_up():
        if SYSTEM == "Windows":
            subprocess.run([wireguard_exe(), "/installtunnelservice", str(path)], check=True)
        else:
            if not shutil.which("wg-quick"):
                die("wg-quick not found. Install wireguard-tools.")
            subprocess.run(["wg-quick", "up", str(path)], check=True)
    elif changed:
        # Peers changed: hot-reload without dropping sessions. No route changes are needed:
        # the hub's range already routes the whole mesh into the interface and WireGuard
        # picks the most specific peer (direct /32 vs. hub) internally.
        with tempfile.NamedTemporaryFile("w", delete=False, suffix=".conf") as tmp:
            tmp.write(strip_conf(text))
        try:
            subprocess.run([find_wg(), "syncconf", IFACE, tmp.name], check=True)
        finally:
            os.unlink(tmp.name)
    return changed


def bring_down() -> None:
    if SYSTEM == "Windows":
        subprocess.run([wireguard_exe(), "/uninstalltunnelservice", IFACE])
    elif conf_path().exists():
        subprocess.run(["wg-quick", "down", str(conf_path())])


# --------------------------------------------------------------------------- name resolution

def hosts_path() -> Path:
    if SYSTEM == "Windows":
        return Path(os.environ.get("SystemRoot", r"C:\Windows")) / "System32" / "drivers" / "etc" / "hosts"
    return Path("/etc/hosts")


def update_hosts(entries: list[dict], domain: str) -> None:
    """Maintain a managed block in the hosts file so devices resolve as name and name.<domain>.
    An empty list removes the block."""
    path = hosts_path()
    try:
        current = path.read_text()
    except OSError:
        return
    block = [HOSTS_BEGIN] + [f"{p['ip']}\t{p['name']}.{domain} {p['name']}" for p in entries] + [HOSTS_END]
    if not entries:
        block = []
    lines, inside = [], False
    for line in current.splitlines():
        if line.strip() == HOSTS_BEGIN:
            inside = True
        elif line.strip() == HOSTS_END:
            inside = False
        elif not inside:
            lines.append(line)
    new = "\n".join(lines).rstrip("\n") + "\n" + "".join(l + "\n" for l in block)
    if new != current:
        try:
            path.write_text(new)
        except OSError as e:
            print(f"warning: could not update {path}: {e}", file=sys.stderr)


# --------------------------------------------------------------------------- sync

def sync(st: dict, quiet: bool = False) -> dict:
    cfg = api(st, "GET", "/api/v1/config", token=st["node_token"])
    changed = apply_config(cfg["wireguard"], st["private_key"])
    update_hosts([cfg["node"], {"ip": cfg["network"]["hub_ip"], "name": "hub"}] + cfg["peers"],
                 cfg["network"]["domain"])
    st["learned_servers"] = cfg.get("server_urls", {})
    if SYSTEM == "Windows" and st.get("rdp_host"):
        apply_rdp_firewall(cfg.get("rdp_allowed_from", []), st)
    st["last_config"] = cfg
    save_state(st)
    if changed and not quiet:
        print(f"[{time.strftime('%H:%M:%S')}] configuration updated ({len(cfg['peers'])} peer(s))")
    return cfg


# --------------------------------------------------------------------------- commands

def cmd_up(a) -> None:
    if not is_admin():
        die("run as Administrator / root (configuring network interfaces requires it).")
    st = load_state()
    if a.server:
        st["servers"] = parse_servers(a.server)
    if a.fingerprint:
        st["fingerprint"] = a.fingerprint.replace(":", "").upper()
    if not st.get("servers"):
        die("--server is required the first time.")
    if not st.get("private_key"):
        st["private_key"], st["public_key"] = generate_keypair()
        save_state(st)

    if not st.get("node_token") or a.authkey:
        if not a.authkey:
            die("--authkey is required to join the network (create one in the admin dashboard).")
        resp = api(st, "POST", "/api/v1/register", {
            "authkey": a.authkey, "pubkey": st["public_key"], "hostname": a.hostname or socket.gethostname(),
            "os": f"{SYSTEM} {platform.release()}", "endpoint": a.endpoint, "listen_port": a.listen_port,
            "rdp_enabled": bool(a.rdp)})
        st["node_token"] = resp.pop("node_token")
        save_state(st)
        print(f"Joined as {resp['node']['name']} ({resp['node']['ip']})")
    elif a.endpoint is not None or a.rdp:
        body = {"endpoint": a.endpoint or None} if a.endpoint is not None else {}
        if a.rdp:
            body["rdp_enabled"] = True
        api(st, "PATCH", "/api/v1/self", body, token=st["node_token"])

    cfg = sync(st)
    if a.rdp and SYSTEM == "Windows":
        cmd_enable_rdp(a)
    print(f"MeshLink is up. This device: {cfg['node']['name']} {cfg['node']['ip']}")
    if a.daemon:
        cmd_daemon(a)


def cmd_daemon(_a) -> None:
    st = load_state()
    if not st.get("node_token"):
        die("not logged in; run `meshlink up` first.")
    print(f"MeshLink daemon running; polling every {POLL_SECONDS}s")
    while True:
        try:
            sync(load_state())  # reload so `meshlink set-server` takes effect without a restart
        except Exception as e:  # keep running through transient network errors
            print(f"[{time.strftime('%H:%M:%S')}] sync failed: {e}", file=sys.stderr)
        time.sleep(POLL_SECONDS)


def cmd_status(_a) -> None:
    st = load_state()
    if not st.get("node_token"):
        die("not logged in.")
    try:
        cfg = api(st, "GET", "/api/v1/config", token=st["node_token"])
    except Exception as e:
        print(f"(server unreachable: {e}; showing cached data)")
        cfg = st.get("last_config") or die("no cached configuration")
    me = cfg["node"]
    print(f"This device : {me['name']}  {me['ip']}  tags={','.join(me['tags']) or '-'}")
    print(f"Tunnel      : {'UP' if interface_up() else 'DOWN'}   server={st.get('last_good', '-')}")
    print(f"Hub         : {cfg['network']['hub_ip']} (hub.{cfg['network']['domain']})\n")
    print(f"{'DEVICE':<24}{'MESH IP':<16}{'STATE':<10}{'PATH':<8}RDP")
    for p in cfg["peers"]:
        print(f"{p['name']:<24}{p['ip']:<16}{'online' if p['online'] else 'offline':<10}"
              f"{'direct' if p['direct'] else 'relay':<8}{'yes' if p['rdp_enabled'] else '-'}")


def resolve_peer(target: str) -> dict | None:
    cfg = load_state().get("last_config") or {}
    if cfg and target in ("hub", f"hub.{cfg['network']['domain']}"):
        return {"name": "hub", "ip": cfg["network"]["hub_ip"], "online": True, "rdp_enabled": True}
    for p in cfg.get("peers", []):
        if target in (p["name"], p["ip"], p["fqdn"]):
            return p
    return None


def cmd_rdp(a) -> None:
    peer = resolve_peer(a.target)
    host = peer["ip"] if peer else a.target
    if peer and not peer["online"]:
        print(f"warning: {peer['name']} appears offline", file=sys.stderr)
    if peer and not peer["rdp_enabled"]:
        print(f"note: {peer['name']} has not advertised RDP; connection may be refused", file=sys.stderr)
    address = f"{host}:{a.port}" if a.port != 3389 else host

    lines = [f"full address:s:{address}", f"screen mode id:i:{2 if a.fullscreen else 1}",
             "prompt for credentials:i:1", "authentication level:i:2", "enablecredsspsupport:i:1",
             "redirectclipboard:i:1", "audiomode:i:0", "autoreconnection enabled:i:1"]
    if a.user:
        lines.append(f"username:s:{a.user}")
    rdp_file = Path(tempfile.gettempdir()) / f"meshlink-{(peer or {}).get('name', host).replace(':', '_')}.rdp"
    rdp_file.write_text("\r\n".join(lines) + "\r\n")

    if SYSTEM == "Windows":
        subprocess.Popen(["mstsc", str(rdp_file)])
    elif SYSTEM == "Darwin":
        subprocess.Popen(["open", str(rdp_file)])  # Windows App / Microsoft Remote Desktop
    else:
        client = next((c for c in ("xfreerdp3", "xfreerdp", "wlfreerdp", "remmina") if shutil.which(c)), None)
        if client is None:
            die(f"no RDP client found; install freerdp or remmina. RDP file saved to {rdp_file}")
        if client == "remmina":
            subprocess.Popen(["remmina", "-c", f"rdp://{(a.user + '@') if a.user else ''}{address}"])
        else:
            args = [client, f"/v:{address}", "/cert:tofu", "+clipboard", "/dynamic-resolution"]
            if a.user:
                args.append(f"/u:{a.user}")
            if a.fullscreen:
                args.append("/f")
            subprocess.Popen(args)
    print(f"Opening RDP session to {address}")


def cmd_enable_rdp(_a) -> None:
    """Turn on Remote Desktop with NLA and allow TCP/UDP 3389 only from devices the ACL permits."""
    st = load_state()
    cidr = (st.get("last_config") or {}).get("network", {}).get("cidr", "100.96.0.0/16")
    if SYSTEM != "Windows":
        print("Remote Desktop hosting is a Windows feature. On Linux, install xrdp and run e.g.:\n"
              f"  sudo ufw allow from {cidr} to any port 3389 proto tcp")
        return
    if not is_admin():
        die("run as Administrator.")
    ts = r"HKLM\SYSTEM\CurrentControlSet\Control\Terminal Server"
    cmds = [
        ["reg", "add", ts, "/v", "fDenyTSConnections", "/t", "REG_DWORD", "/d", "0", "/f"],
        ["reg", "add", ts + r"\WinStations\RDP-Tcp", "/v", "UserAuthentication", "/t", "REG_DWORD",
         "/d", "1", "/f"],
    ]
    for c in cmds:
        subprocess.run(c, capture_output=True)
    st["rdp_host"] = True
    st.pop("rdp_firewall_applied", None)
    cfg = st.get("last_config") or {}
    apply_rdp_firewall(cfg.get("rdp_allowed_from", []), st)
    save_state(st)
    if st.get("node_token"):
        api(st, "PATCH", "/api/v1/self", {"rdp_enabled": True}, token=st["node_token"])
    print("Remote Desktop enabled (NLA required). Port 3389 is opened only to the MeshLink devices\n"
          "that the access policy allows; the list is kept up to date by the agent.\n"
          "Recommended: disable the built-in 'Remote Desktop' firewall rules so RDP is not reachable\n"
          "from the office LAN or internet directly.")


def apply_rdp_firewall(allowed_ips: list[str], st: dict | None = None) -> None:
    """Windows Firewall rule allowing RDP only from the devices the server's ACL permits."""
    st = st if st is not None else load_state()
    wanted = sorted(set(allowed_ips))
    if st.get("rdp_firewall_applied") == wanted:
        return
    subprocess.run(["netsh", "advfirewall", "firewall", "delete", "rule", "name=MeshLink RDP"],
                   capture_output=True)
    if wanted:
        for proto in ("TCP", "UDP"):
            subprocess.run(["netsh", "advfirewall", "firewall", "add", "rule", "name=MeshLink RDP", "dir=in",
                            "action=allow", f"protocol={proto}", "localport=3389",
                            "remoteip=" + ",".join(wanted)], capture_output=True)
    st["rdp_firewall_applied"] = wanted


def parse_servers(value: str) -> list[str]:
    urls = [u.strip().rstrip("/") for u in value.split(",") if u.strip()]
    for u in urls:
        if not u.startswith("https://"):
            die(f"server URL must start with https:// ({u})")
    return urls


def cmd_set_server(a) -> None:
    """Point this device at new server address(es), e.g. after the office public IP changed."""
    st = load_state()
    st["servers"] = parse_servers(a.urls)
    st.pop("last_good", None)
    if a.fingerprint:
        st["fingerprint"] = a.fingerprint.replace(":", "").upper()
    save_state(st)
    if st.get("node_token") and is_admin():
        cfg = sync(st)
        print(f"Connected via {st['last_good']}; tunnel endpoint updated ({cfg['node']['ip']}).")
    else:
        print("Saved. The running agent will use the new address on its next sync.")


def cmd_down(_a) -> None:
    if not is_admin():
        die("run as Administrator / root.")
    bring_down()
    print("MeshLink is down.")


def cmd_logout(_a) -> None:
    if not is_admin():
        die("run as Administrator / root.")
    st = load_state()
    if st.get("node_token"):
        try:
            api(st, "POST", "/api/v1/logout", {}, token=st["node_token"])
        except Exception as e:
            print(f"warning: server logout failed: {e}", file=sys.stderr)
    bring_down()
    update_hosts([], "")
    for f in (state_file(), conf_path()):
        f.unlink(missing_ok=True)
    print("Logged out; keys and configuration removed from this device.")


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(prog="meshlink", description="Self-hosted WireGuard mesh VPN with RDP helpers")
    sub = ap.add_subparsers(dest="cmd", required=True)

    up = sub.add_parser("up", help="join the network / bring the tunnel up")
    up.add_argument("--server", help="server URL(s), comma-separated, e.g. "
                                     "https://192.168.1.10:8443,https://203.0.113.7:8443 (office LAN first)")
    up.add_argument("--fingerprint", help="server certificate SHA-256 fingerprint (from the admin dashboard)")
    up.add_argument("--authkey", help="auth key from the admin dashboard")
    up.add_argument("--hostname", help="device name (default: system hostname)")
    up.add_argument("--endpoint", help="public IP:port where this device accepts WireGuard (enables direct paths)")
    up.add_argument("--listen-port", type=int, help="local WireGuard UDP port (use with --endpoint)")
    up.add_argument("--rdp", action="store_true", help="advertise (and on Windows, enable) Remote Desktop")
    up.add_argument("--daemon", action="store_true", help="stay running and keep config in sync")
    up.set_defaults(fn=cmd_up)

    sub.add_parser("daemon", help="keep configuration in sync (run as a service)").set_defaults(fn=cmd_daemon)
    ss = sub.add_parser("set-server", help="change the server address(es), e.g. after a public IP change")
    ss.add_argument("urls", help="comma-separated https:// URLs")
    ss.add_argument("--fingerprint", help="only needed if the server certificate was regenerated")
    ss.set_defaults(fn=cmd_set_server)
    sub.add_parser("status", help="show devices on the network").set_defaults(fn=cmd_status)

    rdp = sub.add_parser("rdp", help="open a Remote Desktop session to a device")
    rdp.add_argument("target", help="device name, name.<domain> or mesh IP")
    rdp.add_argument("--user", help="username")
    rdp.add_argument("--port", type=int, default=3389)
    rdp.add_argument("--fullscreen", action="store_true")
    rdp.set_defaults(fn=cmd_rdp)

    sub.add_parser("enable-rdp", help="Windows: enable RDP restricted to the mesh").set_defaults(fn=cmd_enable_rdp)
    sub.add_parser("down", help="bring the tunnel down").set_defaults(fn=cmd_down)
    sub.add_parser("logout", help="leave the network and delete local keys").set_defaults(fn=cmd_logout)

    a = ap.parse_args(argv)
    try:
        a.fn(a)
    except RuntimeError as e:
        die(str(e))
    except subprocess.CalledProcessError as e:
        die(f"command failed: {' '.join(map(str, e.cmd))}")


if __name__ == "__main__":
    main()
