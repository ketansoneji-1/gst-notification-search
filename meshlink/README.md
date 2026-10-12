# MeshLink — on-premises VPN mesh with Remote Desktop

MeshLink is a fully self-hosted alternative to Tailscale. The server runs **on your own office machine** (Windows or Linux). It connects office PCs, home PCs and laptops into one private network and lets you open Remote Desktop (RDP) sessions between them.

It does not depend on any outside service:
- **No cloud account and no rented VPS:** the coordination server and relay run on your server.
- **No public certificate authority:** the server creates its own HTTPS certificate, and every device is locked to its fingerprint.
- **No dynamic-DNS or "what is my IP" website:** the server asks your office router (UPnP, on the LAN) for the current public IP.
- **No telemetry:** devices talk only to your server and to each other.

## How it works

```
 Home PC ─────┐   internet   ┌──── office router ─── Office server (MeshLink hub, 100.96.0.1)
 Laptop  ─────┘ (WireGuard)  │      (forwards UDP 51820, TCP 8443)        │
                                                                           ├── Office PC-1 (RDP host)
                                                                           └── Office PC-2
```

- **Encryption:** standard [WireGuard](https://www.wireguard.com/). MeshLink adds no custom cryptography, and private keys never leave the device that created them.
- **Server / hub** (`server/`): assigns mesh IPs (`100.96.x.x`), enrols devices with auth keys, applies access rules, and runs the admin dashboard. It also relays traffic between devices that are both behind routers.
- **Agent** (`agent/meshlink.py`, standard library only): joins a device to the network and keeps peers in sync. It maintains device names (`office-pc`, `office-pc.mesh`, `hub`), launches RDP, and keeps the Windows Firewall RDP rule in line with the access policy.

## Dynamic public IP: how it is handled

1. **Detection:** every 5 minutes the server asks the office router (UPnP) for the current public IP and shows it on the dashboard. With `-UPnP`, it also creates the port forwards on the router itself.
2. **Two addresses per device:** devices are given both addresses, the office LAN URL and the public URL. Inside the office they connect over the LAN, so no router "hairpin" support is needed; outside, they use the public IP.
3. **Tunnel follows automatically:** the WireGuard endpoint each device uses is the same address it reached the server on. When a device's address changes, the tunnel follows without any further step.
4. **Automatic pickup of a new IP:** any device that syncs after the IP changed (for example, a laptop that comes back into the office) learns the new public IP on its own.
5. **One manual case:** a device that was outside the office when the IP changed cannot find the server on its own, because without an outside service there is nothing to tell it. Read the new IP from the dashboard and run on that device:
   `meshlink set-server https://<office-LAN-IP>:8443,https://<NEW-PUBLIC-IP>:8443`
6. **Recommendation:** if remote use is frequent, a static IP from your ISP removes step 5 entirely. If the dashboard shows a **CGNAT** warning, the ISP is not giving you a public IP at all, and inbound connections cannot work until they do.

## 1. Install the server on your Windows machine

**Requirements**
- Windows Server 2016+ or Windows 10/11 Pro, kept always on.
- A fixed LAN IP: set a DHCP reservation on the router.

**Steps**
1. Copy the `meshlink` folder to the server.
2. Open **PowerShell as Administrator** and run:
   ```powershell
   cd meshlink\deploy
   Set-ExecutionPolicy -Scope Process Bypass
   .\install-server-windows.ps1 -UPnP -AllowRdpFromMesh
   ```
   - `-UPnP`: the server opens the router ports itself. Omit it if you forward ports by hand.
   - `-AllowRdpFromMesh`: lets MeshLink devices RDP into the server itself, at mesh IP `100.96.0.1` / name `hub`.
3. Note the **Admin token** and **Certificate fingerprint** the script prints.
4. If you are not using `-UPnP`, forward these ports on the office router to the server's LAN IP:
   - **UDP 51820** (WireGuard)
   - **TCP 8443** (API)
5. Open `https://localhost:8443/` on the server and accept the self-signed certificate warning. Paste the admin token, then check the **Server** panel:
   - The public IP is shown.
   - There is no CGNAT or double-NAT warning.

**What the installer sets up**
- WireGuard for Windows and machine-wide Python.
- The MeshLink server, as a startup task running under the SYSTEM account.
- Windows Firewall rules for the two ports.
- IP forwarding, so the server can relay traffic between devices.

**Fully offline installation (optional)**
- Put the WireGuard `.msi` and the Python `.exe` installer in `deploy\offline\`.
- Put a `wheels\` folder in the same place. Build it once on any internet-connected PC with the `pip download` command shown at the top of `install-server-windows.ps1`.
- The installer then downloads nothing.

A Linux server is also supported: `sudo UPNP=1 ./deploy/install-server.sh`.

## 2. Add devices

1. In the dashboard, create an **auth key**:
   - Single-use by default; tick *Reusable* for rolling out to many PCs.
   - Add tags such as `staff` or `servers`.
2. The dashboard then shows the exact join command.
3. On each Windows PC, open **PowerShell as Administrator** in `meshlink\deploy` and paste the command:

```powershell
.\install-agent-windows.ps1 -Server "https://192.168.1.10:8443,https://49.36.10.20:8443" `
    -Fingerprint 05E6B8... -AuthKey mlk-... -EnableRdp
```

- Use `-EnableRdp` on PCs that others should be able to RDP **into**.
- The agent runs at startup and adds a `meshlink` command.

On Linux or macOS, install `wireguard-tools`, then run:
`sudo python3 agent/meshlink.py up --server "<urls>" --fingerprint <fp> --authkey <key>`

## 3. Remote Desktop

```text
meshlink status                        # devices, online/offline, direct or relayed, RDP
meshlink rdp office-pc --user ABC\ca1  # opens mstsc (Windows), Windows App (macOS) or FreeRDP (Linux)
meshlink rdp hub                       # RDP into the server itself
```

You can also type `office-pc` or `office-pc.mesh` directly into the Windows Remote Desktop client.

What `-EnableRdp` / `meshlink enable-rdp` does on the target PC:
- Turns on RDP with Network Level Authentication.
- Opens port 3389 in Windows Firewall **only to the MeshLink devices the access policy allows**. The agent updates this list whenever the policy or the devices change.
- Recommended: also disable Windows' built-in "Remote Desktop" firewall rules, so RDP cannot be reached from the LAN or internet directly.

## 4. Access control

The default policy lets every device reach every other device. A typical office policy:

```json
{
  "rules": [
    {"src": ["tag:staff"],  "dst": ["tag:office"], "ports": ["rdp"]},
    {"src": ["tag:admins"], "dst": ["*"],          "ports": ["*"]}
  ]
}
```

**How rules are written**
- Selectors: `*`, `tag:<name>`, device name, IP or CIDR.
- Ports: `*`, `rdp`, `ssh`, `vnc`, `http`, `https`, `3389`, `udp:53`, `1000-2000`. A port is TCP unless prefixed with `udp:`.

**How rules are enforced**
- Devices only learn about the peers they are allowed to reach.
- RDP permissions are enforced by each PC's Windows Firewall, which the agent keeps up to date.
- On a Linux hub, relayed traffic is also filtered per port with `iptables`.
- A Windows hub relays without per-port filtering, because Windows Firewall does not filter routed traffic. On a Windows hub, the PC-level firewall rules above are therefore the enforcement point.

## Security notes

- Keep the admin token secret. Use single-use, short-lived auth keys, and remove lost devices from the dashboard at once.
- Back up `C:\ProgramData\MeshLink\server\`. It holds the database, the hub private key and the TLS certificate. If the certificate is lost, every device must be given the new fingerprint with `meshlink set-server <urls> --fingerprint <new>`.
- Only SYSTEM and Administrators can read the data folder.
- Use strong Windows passwords and an account-lockout policy on PCs that accept RDP.

## Admin API

All calls require `Authorization: Bearer <admin token>`.

| Method & path | Purpose |
|---|---|
| `GET /api/v1/admin/server-info[?refresh=true]` | Public/LAN IP, CGNAT warning, UPnP status, certificate fingerprint |
| `PUT /api/v1/admin/server-info` | Set the public IP by hand `{public_ip}`, for routers without UPnP |
| `POST /api/v1/admin/authkeys` | Create an auth key `{description, reusable, ephemeral, tags, ttl_hours}` |
| `GET /api/v1/admin/authkeys` · `DELETE …/{id}` | List / revoke auth keys |
| `GET /api/v1/admin/nodes` · `PATCH`/`DELETE …/{id}` | List, rename, retag or remove devices |
| `GET` · `PUT /api/v1/admin/acl` | Read / replace the access policy |

## Development

```bash
pip install -r requirements-dev.txt
python -m pytest -q tests
MESHLINK_ADMIN_TOKEN=dev-token-123456789 MESHLINK_DATA_DIR=./data python -m server.run
```

## Limitations

- Two devices that are both outside the office talk through the office server. This uses the office upload bandwidth; RDP typically needs 0.5–2 Mbps per session.
- There is no UDP hole punching, and no SSO login: enrolment uses auth keys.
- Name resolution uses the hosts file, not a DNS server.
- The Windows hub has not yet been tested end to end on real Windows hardware. Pilot it with two or three PCs before rolling it out.
