"""MeshLink coordination server.

Run:  MESHLINK_ADMIN_TOKEN=... MESHLINK_PUBLIC_ENDPOINT=vpn.example.com:51820 \
      uvicorn --factory server.main:create_app --host 0.0.0.0 --port 8443
"""
from __future__ import annotations

import json
import logging
import os
import secrets
import shutil
import subprocess
import tempfile
import time
from dataclasses import dataclass, field
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import HTMLResponse, PlainTextResponse
from pydantic import BaseModel, Field

from . import core
from .db import Database, sha256

log = logging.getLogger("meshlink")
ONLINE_WINDOW = 90          # seconds since last poll for a node to count as online
EPHEMERAL_TTL = 600         # ephemeral nodes are removed after this much silence


@dataclass
class Settings:
    db_path: str = field(default_factory=lambda: os.environ.get("MESHLINK_DB", "meshlink.db"))
    admin_token: str = field(default_factory=lambda: os.environ.get("MESHLINK_ADMIN_TOKEN", ""))
    cidr: str = field(default_factory=lambda: os.environ.get("MESHLINK_CIDR", "100.96.0.0/16"))
    public_endpoint: str = field(default_factory=lambda: os.environ.get("MESHLINK_PUBLIC_ENDPOINT", ""))
    wg_port: int = field(default_factory=lambda: int(os.environ.get("MESHLINK_WG_PORT", "51820")))
    domain: str = field(default_factory=lambda: os.environ.get("MESHLINK_DOMAIN", "mesh"))
    hub_apply: bool = field(default_factory=lambda: os.environ.get("MESHLINK_HUB_APPLY", "0") == "1")
    hub_iface: str = field(default_factory=lambda: os.environ.get("MESHLINK_HUB_IFACE", "meshlink0"))
    wg_dir: str = field(default_factory=lambda: os.environ.get("MESHLINK_WG_DIR", "/etc/wireguard"))


# ------------------------------------------------------------------ request models

class RegisterReq(BaseModel):
    authkey: str
    pubkey: str
    hostname: str = Field(max_length=255)
    os: str = Field("", max_length=64)
    endpoint: str | None = Field(None, max_length=255)
    listen_port: int | None = Field(None, ge=1, le=65535)
    rdp_enabled: bool = False


class SelfUpdateReq(BaseModel):
    endpoint: str | None = Field(None, max_length=255)
    rdp_enabled: bool | None = None


class AuthKeyReq(BaseModel):
    description: str = ""
    reusable: bool = False
    ephemeral: bool = False
    tags: list[str] = []
    ttl_hours: float | None = 24


class NodePatchReq(BaseModel):
    name: str | None = None
    tags: list[str] | None = None


# ------------------------------------------------------------------ app factory

def create_app(settings: Settings | None = None) -> FastAPI:
    s = settings or Settings()
    if len(s.admin_token) < 16:
        raise RuntimeError("MESHLINK_ADMIN_TOKEN must be set (at least 16 characters)")
    db = Database(s.db_path)

    if not db.get_setting("hub_privkey"):
        priv, pub = core.generate_keypair()
        db.set_setting("hub_privkey", priv)
        db.set_setting("hub_pubkey", pub)
    if db.get_setting("acl") is None:
        db.set_setting("acl", core.DEFAULT_ACL)

    app = FastAPI(title="MeshLink", version="1.0.0")
    app.state.db, app.state.settings = db, s

    # -------------------------------------------------------------- helpers
    def to_node(row) -> core.Node:
        return core.Node(id=row["id"], name=row["name"], pubkey=row["pubkey"], ip=row["ip"],
                         tags=json.loads(row["tags"]), endpoint=row["endpoint"],
                         rdp_enabled=bool(row["rdp_enabled"]),
                         online=bool(row["last_seen"] and time.time() - row["last_seen"] < ONLINE_WINDOW))

    def all_nodes() -> list[core.Node]:
        db.exec("DELETE FROM nodes WHERE ephemeral=1 AND COALESCE(last_seen, created_at) < ?",
                (time.time() - EPHEMERAL_TTL,))
        return [to_node(r) for r in db.q("SELECT * FROM nodes ORDER BY name")]

    def hub_endpoint() -> str:
        return s.public_endpoint or f"127.0.0.1:{s.wg_port}"

    def sync_hub() -> None:
        """Push the current peer list and ACL firewall to the hub's WireGuard interface."""
        if not s.hub_apply:
            return
        nodes = all_nodes()
        conf = core.render_hub_config(db.get_setting("hub_privkey"), s.cidr, s.wg_port, nodes)
        path = Path(s.wg_dir) / f"{s.hub_iface}.conf"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(conf)
        path.chmod(0o600)
        try:
            if subprocess.run(["wg", "show", s.hub_iface], capture_output=True).returncode != 0:
                subprocess.run(["wg-quick", "up", str(path)], check=True)
            else:
                stripped = subprocess.run(["wg-quick", "strip", str(path)], check=True,
                                          capture_output=True, text=True).stdout
                with tempfile.NamedTemporaryFile("w", delete=False) as tmp:
                    tmp.write(stripped)
                subprocess.run(["wg", "syncconf", s.hub_iface, tmp.name], check=True)
                os.unlink(tmp.name)
            if shutil.which("iptables-restore"):
                rules = core.render_firewall(db.get_setting("acl"), nodes, s.hub_iface)
                subprocess.run(["iptables-restore", "--noflush"], input=rules, text=True, check=True)
                jump = ["FORWARD", "-i", s.hub_iface, "-j", "MESHLINK-FWD"]
                if subprocess.run(["iptables", "-C", *jump], capture_output=True).returncode != 0:
                    subprocess.run(["iptables", "-I", *jump], check=True)
        except (subprocess.CalledProcessError, FileNotFoundError) as exc:
            log.error("Failed to apply hub configuration: %s", exc)

    def require_admin(authorization: str = Header("")) -> None:
        if not secrets.compare_digest(authorization, f"Bearer {s.admin_token}"):
            raise HTTPException(401, "Invalid admin token")

    def require_node(authorization: str = Header("")):
        token = authorization.removeprefix("Bearer ").strip()
        row = db.one("SELECT * FROM nodes WHERE token_hash=?", (sha256(token),)) if token else None
        if row is None:
            raise HTTPException(401, "Unknown or revoked node")
        db.exec("UPDATE nodes SET last_seen=? WHERE id=?", (time.time(), row["id"]))
        return row

    def node_config(row) -> dict:
        me = to_node(row)
        peers = core.visible_peers(db.get_setting("acl"), me, all_nodes())
        return {
            "node": {"id": me.id, "name": me.name, "ip": me.ip, "tags": me.tags},
            "network": {"cidr": s.cidr, "domain": s.domain, "hub_ip": core.hub_ip(s.cidr)},
            "wireguard": core.render_client_config(me, s.cidr, db.get_setting("hub_pubkey"),
                                                   hub_endpoint(), peers, row["listen_port"]),
            "peers": [{"name": p.name, "ip": p.ip, "fqdn": f"{p.name}.{s.domain}", "online": p.online,
                       "rdp_enabled": p.rdp_enabled, "direct": bool(p.endpoint or me.endpoint),
                       "tags": p.tags} for p in peers],
        }

    def validate_endpoint(ep: str | None) -> None:
        if ep and (":" not in ep or not ep.rsplit(":", 1)[1].isdigit()):
            raise HTTPException(422, "endpoint must be host:port")

    # -------------------------------------------------------------- node API
    @app.post("/api/v1/register")
    def register(req: RegisterReq):
        if not core.is_valid_wg_key(req.pubkey):
            raise HTTPException(422, "pubkey is not a valid WireGuard public key")
        validate_endpoint(req.endpoint)
        # A reachable device must listen on a fixed port; default to the advertised endpoint port.
        listen_port = req.listen_port or (int(req.endpoint.rsplit(":", 1)[1]) if req.endpoint else None)
        key = db.consume_auth_key(req.authkey)
        if key is None:
            raise HTTPException(403, "Auth key is invalid, expired, revoked or already used")

        token = secrets.token_urlsafe(32)
        with db.lock:
            existing = db.one("SELECT * FROM nodes WHERE pubkey=?", (req.pubkey,))
            if existing:  # re-registration of the same device keeps its IP and name
                db.exec("UPDATE nodes SET token_hash=?, endpoint=?, listen_port=?, rdp_enabled=?, os=?, tags=?"
                        " WHERE id=?", (sha256(token), req.endpoint, listen_port, int(req.rdp_enabled), req.os,
                                        key["tags"], existing["id"]))
                node_id = existing["id"]
            else:
                taken = {r["name"] for r in db.q("SELECT name FROM nodes")}
                used_ips = {r["ip"] for r in db.q("SELECT ip FROM nodes")}
                node_id = db.exec(
                    "INSERT INTO nodes(name,pubkey,ip,token_hash,tags,os,endpoint,listen_port,rdp_enabled,"
                    "ephemeral,last_seen,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    (core.unique_name(core.sanitize_name(req.hostname), taken), req.pubkey,
                     core.allocate_ip(s.cidr, used_ips), sha256(token), key["tags"], req.os,
                     req.endpoint, listen_port, int(req.rdp_enabled), key["ephemeral"], time.time(),
                     time.time()))
        sync_hub()
        row = db.one("SELECT * FROM nodes WHERE id=?", (node_id,))
        return {"node_token": token, **node_config(row)}

    @app.get("/api/v1/config")
    def get_config(row=Depends(require_node)):
        return node_config(row)

    @app.patch("/api/v1/self")
    def update_self(req: SelfUpdateReq, row=Depends(require_node)):
        validate_endpoint(req.endpoint)
        if "endpoint" in req.model_fields_set:
            port = int(req.endpoint.rsplit(":", 1)[1]) if req.endpoint else None
            db.exec("UPDATE nodes SET endpoint=?, listen_port=? WHERE id=?", (req.endpoint, port, row["id"]))
        if req.rdp_enabled is not None:
            db.exec("UPDATE nodes SET rdp_enabled=? WHERE id=?", (int(req.rdp_enabled), row["id"]))
        return node_config(db.one("SELECT * FROM nodes WHERE id=?", (row["id"],)))

    @app.post("/api/v1/logout")
    def logout(row=Depends(require_node)):
        db.exec("DELETE FROM nodes WHERE id=?", (row["id"],))
        sync_hub()
        return {"ok": True}

    # -------------------------------------------------------------- admin API
    admin = [Depends(require_admin)]

    @app.post("/api/v1/admin/authkeys", dependencies=admin)
    def create_authkey(req: AuthKeyReq):
        key = "mlk-" + secrets.token_urlsafe(24)
        kid = db.create_auth_key(key, req.description, req.reusable, req.ephemeral, req.tags, req.ttl_hours)
        return {"id": kid, "authkey": key, "note": "Shown once only. Store it securely."}

    @app.get("/api/v1/admin/authkeys", dependencies=admin)
    def list_authkeys():
        return [{k: (json.loads(r[k]) if k == "tags" else r[k]) for k in r.keys() if k != "key_hash"}
                for r in db.q("SELECT * FROM auth_keys ORDER BY id DESC")]

    @app.delete("/api/v1/admin/authkeys/{kid}", dependencies=admin)
    def revoke_authkey(kid: int):
        db.exec("UPDATE auth_keys SET revoked=1 WHERE id=?", (kid,))
        return {"ok": True}

    @app.get("/api/v1/admin/nodes", dependencies=admin)
    def list_nodes():
        rows = {r["id"]: r for r in db.q("SELECT * FROM nodes")}
        return [{"id": n.id, "name": n.name, "fqdn": f"{n.name}.{s.domain}", "ip": n.ip, "tags": n.tags,
                 "os": rows[n.id]["os"], "endpoint": n.endpoint, "rdp_enabled": n.rdp_enabled,
                 "online": n.online, "last_seen": rows[n.id]["last_seen"],
                 "ephemeral": bool(rows[n.id]["ephemeral"])} for n in all_nodes() if n.id in rows]

    @app.patch("/api/v1/admin/nodes/{nid}", dependencies=admin)
    def patch_node(nid: int, req: NodePatchReq):
        if not db.one("SELECT id FROM nodes WHERE id=?", (nid,)):
            raise HTTPException(404, "No such node")
        if req.name is not None:
            name = core.sanitize_name(req.name)
            if db.one("SELECT id FROM nodes WHERE name=? AND id<>?", (name, nid)):
                raise HTTPException(409, "Name already in use")
            db.exec("UPDATE nodes SET name=? WHERE id=?", (name, nid))
        if req.tags is not None:
            db.exec("UPDATE nodes SET tags=? WHERE id=?", (json.dumps(req.tags), nid))
        sync_hub()
        return {"ok": True}

    @app.delete("/api/v1/admin/nodes/{nid}", dependencies=admin)
    def delete_node(nid: int):
        db.exec("DELETE FROM nodes WHERE id=?", (nid,))
        sync_hub()
        return {"ok": True}

    @app.get("/api/v1/admin/acl", dependencies=admin)
    def get_acl():
        return db.get_setting("acl")

    @app.put("/api/v1/admin/acl", dependencies=admin)
    def put_acl(policy: dict):
        try:
            core.validate_acl(policy)
        except ValueError as exc:
            raise HTTPException(422, str(exc))
        db.set_setting("acl", policy)
        sync_hub()
        return {"ok": True}

    @app.get("/api/v1/admin/hub-config", dependencies=admin, response_class=PlainTextResponse)
    def hub_config():
        """Hub WireGuard config + firewall, for operators who apply it manually."""
        nodes = all_nodes()
        conf = core.render_hub_config("<hub private key in database>", s.cidr, s.wg_port, nodes)
        return conf + "\n# iptables-restore --noflush\n" + core.render_firewall(
            db.get_setting("acl"), nodes, s.hub_iface)

    # -------------------------------------------------------------- misc
    @app.get("/healthz")
    def health():
        return {"ok": True}

    @app.get("/", response_class=HTMLResponse)
    def dashboard():
        return (Path(__file__).parent / "dashboard.html").read_text()

    sync_hub()
    return app
