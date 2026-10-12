"""Self-signed TLS certificate for the coordination API. No external certificate authority is used;
devices pin the certificate's SHA-256 fingerprint instead."""
from __future__ import annotations

import datetime
import hashlib
import ssl
from pathlib import Path


def fingerprint(cert_path: Path) -> str:
    der = ssl.PEM_cert_to_DER_cert(cert_path.read_text())
    return hashlib.sha256(der).hexdigest().upper()


def ensure_cert(directory: str | Path) -> tuple[Path, Path, str]:
    """Create (once) and return (cert_path, key_path, sha256_fingerprint)."""
    d = Path(directory)
    d.mkdir(parents=True, exist_ok=True)
    cert, key = d / "server.crt", d / "server.key"
    if not (cert.exists() and key.exists()):
        from cryptography import x509
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
        from cryptography.x509.oid import NameOID

        pk = ec.generate_private_key(ec.SECP256R1())
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "MeshLink server")])
        now = datetime.datetime.now(datetime.timezone.utc)
        crt = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(pk.public_key())
               .serial_number(x509.random_serial_number())
               .not_valid_before(now - datetime.timedelta(days=1))
               .not_valid_after(now + datetime.timedelta(days=3650))
               .add_extension(x509.SubjectAlternativeName([x509.DNSName("meshlink")]), critical=False)
               .sign(pk, hashes.SHA256()))
        key.write_bytes(pk.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                         serialization.NoEncryption()))
        cert.write_bytes(crt.public_bytes(serialization.Encoding.PEM))
        try:
            key.chmod(0o600)
        except OSError:
            pass
    return cert, key, fingerprint(cert)
