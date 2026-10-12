"""Minimal UPnP-IGD client (standard library only).

Talks only to the router on the local network: discovers it with SSDP, reads the public
(WAN) IP address and creates port forwards. No external "what is my IP" service is used.
"""
from __future__ import annotations

import ipaddress
import re
import socket
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

SSDP_ADDR = ("239.255.255.250", 1900)
SERVICE_TYPES = ("urn:schemas-upnp-org:service:WANIPConnection:2",
                 "urn:schemas-upnp-org:service:WANIPConnection:1",
                 "urn:schemas-upnp-org:service:WANPPPConnection:1")


class UPnPError(RuntimeError):
    pass


def _discover(timeout: float = 3.0) -> list[str]:
    """Return LOCATION URLs of Internet Gateway Devices answering on the LAN."""
    msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n"
           "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n").encode()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
    sock.settimeout(timeout)
    locations = []
    try:
        sock.sendto(msg, SSDP_ADDR)
        while True:
            data, _ = sock.recvfrom(4096)
            m = re.search(rb"(?im)^location:\s*(\S+)", data)
            if m and m.group(1).decode() not in locations:
                locations.append(m.group(1).decode())
    except socket.timeout:
        pass
    finally:
        sock.close()
    return locations


def _local_only(url: str) -> None:
    """Refuse to talk to anything that is not on a private network (defence against spoofed SSDP)."""
    host = urllib.parse.urlparse(url).hostname or ""
    try:
        if not ipaddress.ip_address(host).is_private:
            raise UPnPError(f"Ignoring non-local gateway {host}")
    except ValueError:
        raise UPnPError(f"Ignoring gateway with non-IP host {host}") from None


class Gateway:
    def __init__(self, control_url: str, service_type: str):
        self.control_url, self.service_type = control_url, service_type

    @classmethod
    def find(cls) -> "Gateway":
        for loc in _discover():
            try:
                _local_only(loc)
                with urllib.request.urlopen(loc, timeout=5) as r:
                    root = ET.fromstring(r.read())
            except Exception:
                continue
            for svc in root.iter():
                if not svc.tag.endswith("service"):
                    continue
                fields = {c.tag.split("}")[-1]: (c.text or "").strip() for c in svc}
                if fields.get("serviceType") in SERVICE_TYPES and fields.get("controlURL"):
                    return cls(urllib.parse.urljoin(loc, fields["controlURL"]), fields["serviceType"])
        raise UPnPError("No UPnP router found. Enable UPnP on the router or forward the ports manually.")

    def _soap(self, action: str, args: dict[str, str]) -> ET.Element:
        body = "".join(f"<{k}>{v}</{k}>" for k, v in args.items())
        envelope = ('<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" '
                    's:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>'
                    f'<u:{action} xmlns:u="{self.service_type}">{body}</u:{action}></s:Body></s:Envelope>')
        req = urllib.request.Request(self.control_url, data=envelope.encode(), method="POST", headers={
            "Content-Type": 'text/xml; charset="utf-8"', "SOAPAction": f'"{self.service_type}#{action}"'})
        try:
            with urllib.request.urlopen(req, timeout=5) as r:
                return ET.fromstring(r.read())
        except Exception as exc:
            raise UPnPError(f"Router rejected {action}: {exc}") from None

    def external_ip(self) -> str:
        root = self._soap("GetExternalIPAddress", {})
        for el in root.iter():
            if el.tag.endswith("NewExternalIPAddress") and el.text:
                return el.text.strip()
        raise UPnPError("Router did not report a public IP")

    def add_port_mapping(self, port: int, proto: str, description: str) -> None:
        self._soap("AddPortMapping", {
            "NewRemoteHost": "", "NewExternalPort": str(port), "NewProtocol": proto.upper(),
            "NewInternalPort": str(port), "NewInternalClient": local_ip(self.control_url),
            "NewEnabled": "1", "NewPortMappingDescription": description, "NewLeaseDuration": "0"})


def local_ip(towards_url: str) -> str:
    """This machine's LAN address on the interface facing the router."""
    host = urllib.parse.urlparse(towards_url).hostname
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect((host, 1900))
        return s.getsockname()[0]
    finally:
        s.close()


def default_lan_ip() -> str | None:
    """LAN address of the default-route interface (no packet is sent)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("192.0.2.1", 9))  # TEST-NET address: only selects a route
        return s.getsockname()[0]
    except OSError:
        return None
    finally:
        s.close()


def classify_public_ip(ip: str) -> str | None:
    """Explain why an address reported by the router cannot receive inbound connections."""
    addr = ipaddress.ip_address(ip)
    if addr in ipaddress.ip_network("100.64.0.0/10"):
        return "The ISP uses carrier-grade NAT (CGNAT); ask the ISP for a public IP."
    if not addr.is_global:
        return "The router is behind another router (double NAT); forward the ports on the upstream router too."
    return None
