#!/usr/bin/env bash
# Installs the MeshLink coordination server + WireGuard hub on Ubuntu/Debian.
# Usage: sudo PUBLIC_HOST=vpn.example.com ./install-server.sh
set -euo pipefail
: "${PUBLIC_HOST:?Set PUBLIC_HOST to the public DNS name or IP}"
WG_PORT="${WG_PORT:-51820}"
APP_DIR=/opt/meshlink

apt-get update
apt-get install -y wireguard-tools iptables python3-venv
mkdir -p "$APP_DIR" /var/lib/meshlink /etc/meshlink
cp -r "$(dirname "$0")/../server" "$(dirname "$0")/../requirements.txt" "$APP_DIR/"
python3 -m venv "$APP_DIR/.venv"
"$APP_DIR/.venv/bin/pip" install -q -r "$APP_DIR/requirements.txt"

# Let the hub relay traffic between devices.
echo "net.ipv4.ip_forward=1" > /etc/sysctl.d/90-meshlink.conf
sysctl -p /etc/sysctl.d/90-meshlink.conf

if [ ! -f /etc/meshlink/server.env ]; then
  ADMIN_TOKEN="$(head -c 32 /dev/urandom | base64 | tr -d '=+/')"
  cat > /etc/meshlink/server.env <<ENV
MESHLINK_ADMIN_TOKEN=${ADMIN_TOKEN}
MESHLINK_PUBLIC_ENDPOINT=${PUBLIC_HOST}:${WG_PORT}
MESHLINK_WG_PORT=${WG_PORT}
MESHLINK_CIDR=100.96.0.0/16
MESHLINK_DOMAIN=mesh
MESHLINK_DB=/var/lib/meshlink/meshlink.db
MESHLINK_HUB_APPLY=1
ENV
  chmod 600 /etc/meshlink/server.env
fi

cp "$(dirname "$0")/meshlink-server.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now meshlink-server
echo
echo "MeshLink server is running on 127.0.0.1:8443 (put it behind HTTPS, see README)."
echo "Open UDP ${WG_PORT} in your firewall / cloud security group."
grep MESHLINK_ADMIN_TOKEN /etc/meshlink/server.env
