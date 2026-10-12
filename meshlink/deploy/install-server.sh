#!/usr/bin/env bash
# Installs the MeshLink server + WireGuard hub on an on-premises Ubuntu/Debian machine.
# Usage: sudo ./install-server.sh            (the Windows equivalent is install-server-windows.ps1)
set -euo pipefail
WG_PORT="${WG_PORT:-51820}"
APP_DIR=/opt/meshlink

apt-get update
apt-get install -y wireguard-tools iptables python3-venv
mkdir -p "$APP_DIR" /var/lib/meshlink /etc/meshlink
cp -r "$(dirname "$0")/../server" "$(dirname "$0")/../requirements.txt" "$APP_DIR/"
API_PORT="${API_PORT:-8443}"
python3 -m venv "$APP_DIR/.venv"
"$APP_DIR/.venv/bin/pip" install -q -r "$APP_DIR/requirements.txt"

# Let the hub relay traffic between devices.
echo "net.ipv4.ip_forward=1" > /etc/sysctl.d/90-meshlink.conf
sysctl -p /etc/sysctl.d/90-meshlink.conf

if [ ! -f /etc/meshlink/server.env ]; then
  ADMIN_TOKEN="$(head -c 32 /dev/urandom | base64 | tr -d '=+/')"
  cat > /etc/meshlink/server.env <<ENV
MESHLINK_ADMIN_TOKEN=${ADMIN_TOKEN}
MESHLINK_PUBLIC_ENDPOINT=auto
MESHLINK_WG_PORT=${WG_PORT}
MESHLINK_API_PORT=${API_PORT}
MESHLINK_UPNP_MAP=${UPNP:-0}
MESHLINK_CIDR=100.96.0.0/16
MESHLINK_DOMAIN=mesh
MESHLINK_DATA_DIR=/var/lib/meshlink
MESHLINK_HUB_APPLY=1
ENV
  chmod 600 /etc/meshlink/server.env
fi

# Create the self-signed HTTPS certificate before the service starts.
FP=$(cd "$APP_DIR" && MESHLINK_DATA_DIR=/var/lib/meshlink "$APP_DIR/.venv/bin/python" -c \
  "from server.tls import ensure_cert; print(ensure_cert('/var/lib/meshlink/tls')[2])")

cp "$(dirname "$0")/meshlink-server.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now meshlink-server
echo
echo "MeshLink server is running: https://<this-machine>:${API_PORT}/"
grep MESHLINK_ADMIN_TOKEN /etc/meshlink/server.env
echo "Certificate fingerprint: ${FP}"
echo "Forward UDP ${WG_PORT} and TCP ${API_PORT} on the office router to this machine (or set UPNP=1)."
