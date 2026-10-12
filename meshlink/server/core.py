"""Pure logic for MeshLink: IP allocation, ACL evaluation and WireGuard config rendering.

Nothing in this module touches the database or the network, so it is easy to test.
"""
from __future__ import annotations

import base64
import ipaddress
import os
import platform
import re
import shutil
from pathlib import Path
from dataclasses import dataclass, field

# Ports used by remote-desktop style services; handy aliases in ACL rules.
PORT_ALIASES = {"rdp": "3389", "ssh": "22", "vnc": "5900", "http": "80", "https": "443"}


@dataclass
class Node:
    id: int
    name: str
    pubkey: str
    ip: str
    tags: list[str] = field(default_factory=list)
    endpoint: str | None = None
    rdp_enabled: bool = False
    online: bool = False


# --------------------------------------------------------------------------- keys

def generate_keypair() -> tuple[str, str]:
    """Return (private, public) WireGuard keys as base64 strings."""
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey

    priv = X25519PrivateKey.generate()
    raw_priv = priv.private_bytes(
        serialization.Encoding.Raw, serialization.PrivateFormat.Raw, serialization.NoEncryption()
    )
    raw_pub = priv.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
    return base64.b64encode(raw_priv).decode(), base64.b64encode(raw_pub).decode()


def is_valid_wg_key(key: str) -> bool:
    try:
        return len(base64.b64decode(key, validate=True)) == 32
    except Exception:
        return False


# --------------------------------------------------------------------------- names

def sanitize_name(raw: str) -> str:
    """Turn an arbitrary hostname into a DNS label (lowercase, a-z0-9-, max 63 chars)."""
    name = re.sub(r"[^a-z0-9-]+", "-", raw.lower()).strip("-")[:63]
    return name or "node"


def unique_name(base: str, taken: set[str]) -> str:
    if base not in taken:
        return base
    i = 2
    while f"{base}-{i}" in taken:
        i += 1
    return f"{base}-{i}"


# --------------------------------------------------------------------------- IPAM

def hub_ip(cidr: str) -> str:
    """The coordination server (hub) always takes the first usable address."""
    return str(next(ipaddress.ip_network(cidr).hosts()))


def allocate_ip(cidr: str, used: set[str]) -> str:
    net = ipaddress.ip_network(cidr)
    reserved = used | {hub_ip(cidr)}
    for host in net.hosts():
        if str(host) not in reserved:
            return str(host)
    raise RuntimeError(f"Address pool {cidr} exhausted")


# --------------------------------------------------------------------------- ACL

def _matches(selector: str, node: Node) -> bool:
    if selector == "*":
        return True
    if selector.startswith("tag:"):
        return selector[4:] in node.tags
    if "/" in selector:
        return ipaddress.ip_address(node.ip) in ipaddress.ip_network(selector, strict=False)
    return selector in (node.name, node.ip)


def _parse_port(spec: str) -> tuple[str, int, int] | None:
    """'3389' -> ('tcp', 3389, 3389); 'udp:53' -> ('udp', 53, 53); 'rdp'; '1000-2000'; '*' -> None."""
    spec = spec.strip().lower()
    if spec == "*":
        return None
    proto = "tcp"
    if ":" in spec:
        proto, spec = spec.split(":", 1)
        if proto not in ("tcp", "udp"):
            raise ValueError(f"Unsupported protocol '{proto}'")
    spec = PORT_ALIASES.get(spec, spec)
    lo, _, hi = spec.partition("-")
    lo_i, hi_i = int(lo), int(hi or lo)
    if not (0 < lo_i <= hi_i <= 65535):
        raise ValueError(f"Invalid port range '{spec}'")
    return proto, lo_i, hi_i


def validate_acl(policy: dict) -> None:
    """Raise ValueError if the policy is malformed."""
    if not isinstance(policy, dict) or not isinstance(policy.get("rules"), list):
        raise ValueError("ACL must be an object with a 'rules' list")
    for i, rule in enumerate(policy["rules"]):
        for key in ("src", "dst"):
            if not isinstance(rule.get(key), list) or not rule[key]:
                raise ValueError(f"rule {i}: '{key}' must be a non-empty list")
            for sel in rule[key]:
                if "/" in sel:
                    ipaddress.ip_network(sel, strict=False)
        for p in rule.get("ports", ["*"]):
            _parse_port(p)


DEFAULT_ACL = {"rules": [{"src": ["*"], "dst": ["*"], "ports": ["*"]}]}


def allowed(policy: dict, src: Node, dst: Node) -> bool:
    """True if `src` may open any connection to `dst`."""
    return any(
        any(_matches(s, src) for s in r["src"]) and any(_matches(d, dst) for d in r["dst"])
        for r in policy["rules"]
    )


def allowed_port(policy: dict, src: Node, dst: Node, proto: str, port: int) -> bool:
    """True if `src` may connect to `dst` on proto/port."""
    for r in policy["rules"]:
        if not (any(_matches(s, src) for s in r["src"]) and any(_matches(d, dst) for d in r["dst"])):
            continue
        for spec in r.get("ports", ["*"]):
            p = _parse_port(spec)
            if p is None or (p[0] == proto and p[1] <= port <= p[2]):
                return True
    return False


def visible_peers(policy: dict, me: Node, nodes: list[Node]) -> list[Node]:
    """Nodes `me` needs to know about: anything it can reach or that can reach it."""
    return [n for n in nodes if n.id != me.id and (allowed(policy, me, n) or allowed(policy, n, me))]


def render_firewall(policy: dict, nodes: list[Node], iface: str, chain: str = "MESHLINK-FWD") -> str:
    """iptables-restore fragment enforcing the ACL for traffic forwarded through the hub."""
    lines = ["*filter", f":{chain} - [0:0]",
             f"-A {chain} -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT"]
    for rule in policy["rules"]:
        srcs = [n for n in nodes if any(_matches(s, n) for s in rule["src"])]
        dsts = [n for n in nodes if any(_matches(d, n) for d in rule["dst"])]
        ports = [_parse_port(p) for p in rule.get("ports", ["*"])]
        for s in srcs:
            for d in dsts:
                if s.id == d.id:
                    continue
                for p in ports:
                    base = f"-A {chain} -i {iface} -o {iface} -s {s.ip}/32 -d {d.ip}/32"
                    if p is None:
                        lines.append(f"{base} -j ACCEPT")
                    else:
                        proto, lo, hi = p
                        rng = str(lo) if lo == hi else f"{lo}:{hi}"
                        lines.append(f"{base} -p {proto} --dport {rng} -j ACCEPT")
    lines += [f"-A {chain} -i {iface} -o {iface} -j DROP", "COMMIT", ""]
    return "\n".join(lines)


# --------------------------------------------------------------------------- WireGuard

def render_client_config(node: Node, cidr: str, hub_pubkey: str, hub_endpoint: str,
                         peers: list[Node], listen_port: int | None = None) -> str:
    """wg-quick config for a node. PrivateKey is a placeholder the agent fills in locally,
    so private keys never leave the device."""
    out = ["[Interface]", "PrivateKey = __PRIVATE_KEY__", f"Address = {node.ip}/32"]
    if listen_port:
        out.append(f"ListenPort = {listen_port}")
    out += ["", "# MeshLink hub (relay for every node)", "[Peer]", f"PublicKey = {hub_pubkey}",
            f"Endpoint = {hub_endpoint}", f"AllowedIPs = {cidr}", "PersistentKeepalive = 25"]
    # A pair talks directly when at least one side has a reachable endpoint (the other side
    # learns the roaming endpoint from the handshake). The /32 route wins over the hub's range.
    # When neither side is reachable, traffic is relayed through the hub.
    for p in peers:
        if not (p.endpoint or node.endpoint):
            continue
        out += ["", f"# {p.name} (direct)", "[Peer]", f"PublicKey = {p.pubkey}"]
        if p.endpoint:
            out += [f"Endpoint = {p.endpoint}"]
        out += [f"AllowedIPs = {p.ip}/32", "PersistentKeepalive = 25"]
    return "\n".join(out) + "\n"


def render_hub_config(hub_privkey: str, cidr: str, listen_port: int, nodes: list[Node]) -> str:
    prefix = ipaddress.ip_network(cidr).prefixlen
    out = ["[Interface]", f"PrivateKey = {hub_privkey}", f"Address = {hub_ip(cidr)}/{prefix}",
           f"ListenPort = {listen_port}"]
    for n in nodes:
        out += ["", f"# {n.name}", "[Peer]", f"PublicKey = {n.pubkey}", f"AllowedIPs = {n.ip}/32"]
    return "\n".join(out) + "\n"


def strip_conf(text: str) -> str:
    """Equivalent of `wg-quick strip`: drop wg-quick-only keys so `wg syncconf` accepts the file."""
    skip = ("address", "dns", "mtu", "table", "preup", "postup", "predown", "postdown", "saveconfig")
    return "\n".join(l for l in text.splitlines() if l.split("=", 1)[0].strip().lower() not in skip) + "\n"


def _wireguard_dir() -> Path:
    return Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "WireGuard"


def find_wg() -> str | None:
    if platform.system() == "Windows" and (_wireguard_dir() / "wg.exe").exists():
        return str(_wireguard_dir() / "wg.exe")
    return shutil.which("wg")


def wireguard_exe() -> str:
    exe = _wireguard_dir() / "wireguard.exe"
    if not exe.exists():
        raise FileNotFoundError("WireGuard for Windows is not installed")
    return str(exe)
