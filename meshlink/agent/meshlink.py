#!/usr/bin/env python3
"""MeshLink agent: joins this device to a MeshLink network and provides RDP helpers.

Requires Python 3.9+ and the official WireGuard tools (wg / wg-quick, or WireGuard for Windows).
Uses only the standard library; private keys are generated and kept on this device.

  meshlink up --server https://vpn.example.com --authkey mlk-... [--rdp] [--endpoint IP:PORT]
  meshlink daemon            # keep config in sync (run as a service)
  meshlink status
  meshlink rdp <device> [--user NAME] [--fullscreen]
  meshlink enable-rdp        # Windows: allow RDP only from the mesh
  meshlink down | logout
"""
from __future__ import annotations

import argparse
import base64
import ctypes
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
import urllib.error
import urllib.request
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

def api(st: dict, method: str, path: str, body: dict | None = None, token: str | None = None) -> dict:
    url = st["server"].rstrip("/") + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    ctx = ssl.create_default_context()
    if os.environ.get("MESHLINK_CA_FILE"):
        ctx.load_verify_locations(os.environ["MESHLINK_CA_FILE"])
    try:
        with urllib.request.urlopen(req, timeout=20, context=ctx) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        try:
            detail = json.loads(e.read()).get("detail", e.reason)
        except Exception:
            detail = e.reason
        raise RuntimeError(f"server returned {e.code}: {detail}") from None


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
    update_hosts([cfg["node"]] + cfg["peers"], cfg["network"]["domain"])
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
        st["server"] = a.server
    if not st.get("server"):
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

    if a.rdp and SYSTEM == "Windows":
        cmd_enable_rdp(a)
    cfg = sync(st)
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
            sync(st)
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
    print(f"Tunnel      : {'UP' if interface_up() else 'DOWN'}   server={st['server']}\n")
    print(f"{'DEVICE':<24}{'MESH IP':<16}{'STATE':<10}{'PATH':<8}RDP")
    for p in cfg["peers"]:
        print(f"{p['name']:<24}{p['ip']:<16}{'online' if p['online'] else 'offline':<10}"
              f"{'direct' if p['direct'] else 'relay':<8}{'yes' if p['rdp_enabled'] else '-'}")


def resolve_peer(target: str) -> dict | None:
    cfg = load_state().get("last_config") or {}
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
    """Turn on Remote Desktop with NLA and allow TCP/UDP 3389 only from the mesh range."""
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
        ["netsh", "advfirewall", "firewall", "delete", "rule", "name=MeshLink RDP"],
        ["netsh", "advfirewall", "firewall", "add", "rule", "name=MeshLink RDP", "dir=in", "action=allow",
         "protocol=TCP", "localport=3389", f"remoteip={cidr}"],
        ["netsh", "advfirewall", "firewall", "add", "rule", "name=MeshLink RDP", "dir=in", "action=allow",
         "protocol=UDP", "localport=3389", f"remoteip={cidr}"],
    ]
    for c in cmds:
        subprocess.run(c, capture_output=True)
    if st.get("node_token"):
        api(st, "PATCH", "/api/v1/self", {"rdp_enabled": True}, token=st["node_token"])
    print(f"Remote Desktop enabled (NLA required); firewall allows 3389 from {cidr} only.\n"
          "Tip: disable the built-in 'Remote Desktop' firewall rules if you do not want RDP on other networks.")


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
    up.add_argument("--server", help="coordination server URL, e.g. https://vpn.example.com")
    up.add_argument("--authkey", help="auth key from the admin dashboard")
    up.add_argument("--hostname", help="device name (default: system hostname)")
    up.add_argument("--endpoint", help="public IP:port where this device accepts WireGuard (enables direct paths)")
    up.add_argument("--listen-port", type=int, help="local WireGuard UDP port (use with --endpoint)")
    up.add_argument("--rdp", action="store_true", help="advertise (and on Windows, enable) Remote Desktop")
    up.add_argument("--daemon", action="store_true", help="stay running and keep config in sync")
    up.set_defaults(fn=cmd_up)

    sub.add_parser("daemon", help="keep configuration in sync (run as a service)").set_defaults(fn=cmd_daemon)
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
