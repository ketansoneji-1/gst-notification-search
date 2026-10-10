# MeshLink — self-hosted VPN mesh with Remote Desktop

MeshLink is a self-hosted alternative to Tailscale for connecting office and home computers into a private network and opening Remote Desktop (RDP) sessions between them. All infrastructure runs on a server you control; no third-party account is required.

## How it works

- **Data plane:** standard [WireGuard](https://www.wireguard.com/) (kernel module on Linux, official clients on Windows/macOS). MeshLink does no custom cryptography.
- **Coordination server** (`server/`): hands out mesh IPs (default `100.96.0.0/16`), distributes public keys, enforces access rules, and serves an admin dashboard.
- **Hub / relay:** the same server runs a WireGuard interface that relays traffic for devices behind NAT, with the ACL enforced through `iptables`.
- **Direct paths:** a device registered with a reachable `--endpoint` (public IP or port-forward) is reached directly by peers; everything else falls back to the hub.
- **Agent** (`agent/meshlink.py`): one Python file (standard library only) that generates keys locally, joins the network, keeps peers in sync, maintains `name` / `name.mesh` host entries, and launches RDP.

Private keys never leave the device. Auth keys and device tokens are stored only as SHA-256 hashes.

```
 Laptop (NAT) ──WireGuard──┐                 ┌──WireGuard── Office PC (NAT, RDP host)
                           ├── MeshLink hub ─┤
 Home PC (NAT) ─WireGuard──┘ (VPS, public IP)└──WireGuard── Server (public IP) ◄── direct path
```

## Comparison with Tailscale

| Capability | Tailscale | MeshLink |
|---|---|---|
| WireGuard encryption | Yes | Yes |
| Self-hosted control plane | Headscale only | Yes (built in) |
| NAT traversal | Automatic hole punching + DERP relays | Hub relay; direct when one side is reachable |
| Access control (ACLs) | Yes | Yes (tags, names, IPs, ports) |
| Name resolution | MagicDNS | Hosts-file entries (`pc`, `pc.mesh`) |
| RDP helper | No | `meshlink rdp`, `meshlink enable-rdp` |
| SSO login | Yes | Auth keys (pre-shared) |

## 1. Install the server (Ubuntu/Debian VPS with a public IP)

```bash
git clone <this repo> && cd <repo>/meshlink
sudo PUBLIC_HOST=vpn.example.com ./deploy/install-server.sh
```

The script installs WireGuard, enables IP forwarding, creates `/etc/meshlink/server.env` with a random admin token, and starts the `meshlink-server` systemd service on `127.0.0.1:8443`.

Then:
1. Open **UDP 51820** in the firewall / cloud security group.
2. Put the API behind HTTPS, e.g. with Caddy: copy `deploy/Caddyfile`, set your domain, `sudo systemctl reload caddy`.
3. Open `https://vpn.example.com/`, paste the admin token (`sudo cat /etc/meshlink/server.env`).

Docker alternative: `docker build -t meshlink . && docker run -d --network host --cap-add NET_ADMIN --env-file server.env -v meshlink:/var/lib/meshlink meshlink` (the host still needs `net.ipv4.ip_forward=1`).

### Server settings (`server.env`)

| Variable | Default | Purpose |
|---|---|---|
| `MESHLINK_ADMIN_TOKEN` | — (required, ≥16 chars) | Dashboard / admin API token |
| `MESHLINK_PUBLIC_ENDPOINT` | — | `host:port` devices use to reach the hub |
| `MESHLINK_CIDR` | `100.96.0.0/16` | Mesh address range |
| `MESHLINK_WG_PORT` | `51820` | Hub WireGuard UDP port |
| `MESHLINK_DOMAIN` | `mesh` | Suffix for device names |
| `MESHLINK_HUB_APPLY` | `0` | `1` = configure the hub's WireGuard interface and firewall automatically |
| `MESHLINK_DB` | `meshlink.db` | SQLite database path |

## 2. Add devices

Create an **auth key** in the dashboard (single-use by default; tick *Reusable* for bulk roll-out; add tags such as `staff` or `servers`).

**Windows** (elevated PowerShell; installs WireGuard and Python via winget if missing):
```powershell
.\deploy\install-agent-windows.ps1 -Server https://vpn.example.com -AuthKey mlk-XXXX -EnableRdp
```

**Linux** (`sudo apt install wireguard-tools`):
```bash
sudo install -D agent/meshlink.py /opt/meshlink/meshlink.py && sudo ln -sf /opt/meshlink/meshlink.py /usr/local/bin/meshlink
sudo python3 /opt/meshlink/meshlink.py up --server https://vpn.example.com --authkey mlk-XXXX
sudo cp deploy/meshlink-agent.service /etc/systemd/system/ && sudo systemctl enable --now meshlink-agent
```

**macOS** (`brew install wireguard-tools`): same as Linux using `sudo python3 meshlink.py up ...`; run `meshlink.py daemon` via launchd to keep peers in sync.

Optional flags for `up`: `--hostname NAME`, `--rdp` (advertise RDP), `--endpoint 203.0.113.5:51820` (device is directly reachable; enables direct paths).

## 3. Remote Desktop

On the PC you want to connect **to** (Windows Pro/Enterprise/Server):
```powershell
python "C:\Program Files\MeshLink\meshlink.py" enable-rdp
```
This enables RDP with Network Level Authentication and adds a firewall rule allowing TCP/UDP 3389 **only from the mesh range**. Consider disabling Windows' built-in "Remote Desktop" firewall rules so RDP is not exposed on other networks.

From any other device:
```bash
meshlink status                          # list devices, online state, direct/relay path, RDP
meshlink rdp office-pc --user ACME\\ca1  # opens mstsc (Windows), Windows App (macOS) or FreeRDP/Remmina (Linux)
```
You can also type `office-pc` or `office-pc.mesh` directly into any RDP client.

## 4. Access control

Default policy allows every device to reach every other device. Example: staff may only RDP/SSH into servers, admins may reach everything:

```json
{
  "rules": [
    {"src": ["tag:staff"],  "dst": ["tag:servers"], "ports": ["rdp", "ssh"]},
    {"src": ["tag:admins"], "dst": ["*"],           "ports": ["*"]}
  ]
}
```

- Selectors: `*`, `tag:<name>`, device name, IP, or CIDR.
- Ports: `*`, `rdp`, `ssh`, `vnc`, `http`, `https`, `3389`, `udp:53`, `1000-2000` (TCP unless prefixed).
- Devices only learn about peers they are allowed to talk to; the hub firewall enforces ports for relayed traffic. Direct paths are filtered by visibility only, so use host firewalls (as `enable-rdp` does) for port-level control on directly reachable devices.

## Admin API

All admin calls take `Authorization: Bearer <admin token>`.

| Method & path | Purpose |
|---|---|
| `POST /api/v1/admin/authkeys` | Create auth key `{description, reusable, ephemeral, tags, ttl_hours}` |
| `GET /api/v1/admin/authkeys` · `DELETE …/{id}` | List / revoke keys |
| `GET /api/v1/admin/nodes` | List devices |
| `PATCH /api/v1/admin/nodes/{id}` · `DELETE …/{id}` | Rename / retag / remove a device |
| `GET` · `PUT /api/v1/admin/acl` | Read / replace the access policy |
| `GET /api/v1/admin/hub-config` | Rendered hub WireGuard config + firewall (for manual setups) |

## Security notes

- Serve the coordination API over HTTPS only; auth keys and device tokens travel in requests.
- Use short-lived, single-use auth keys; revoke unused keys; remove lost devices from the dashboard immediately.
- Back up the database (`/var/lib/meshlink/meshlink.db`): it holds the hub private key and device registry.
- Keep NLA enabled and use strong Windows passwords or account lockout for RDP hosts.

## Development

```bash
pip install -r requirements-dev.txt
python -m pytest -q tests
MESHLINK_ADMIN_TOKEN=dev-token-123456789 uvicorn --factory server.main:create_app --reload --port 8443
```

## Limitations

- No UDP hole punching yet: two devices both behind NAT communicate via the hub (adds latency; consumes VPS bandwidth).
- No SSO/OIDC login; enrolment uses auth keys.
- Name resolution uses the hosts file rather than a DNS server.
