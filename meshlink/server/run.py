"""Start the MeshLink server over HTTPS with its own self-signed certificate.

    python -m server.run            (settings come from environment variables / server.env)
"""
from __future__ import annotations

import logging
import os
from pathlib import Path

import uvicorn

from .main import Settings, _default_data_dir, create_app
from .tls import ensure_cert


def load_env_file(path: Path) -> None:
    """Read KEY=VALUE lines (server.env) into the environment without overriding real env vars."""
    if path.exists():
        for line in path.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                os.environ.setdefault(k.strip(), v.strip())


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    data_dir = os.environ.get("MESHLINK_DATA_DIR", _default_data_dir())
    load_env_file(Path(os.environ.get("MESHLINK_ENV_FILE", Path(data_dir) / "server.env")))
    settings = Settings()
    cert, key, fp = ensure_cert(Path(settings.data_dir) / "tls")
    settings.tls_fingerprint = fp
    logging.info("TLS certificate fingerprint (SHA-256): %s", fp)
    uvicorn.run(create_app(settings), host=os.environ.get("MESHLINK_LISTEN", "0.0.0.0"),
                port=settings.api_port, ssl_certfile=str(cert), ssl_keyfile=str(key))


if __name__ == "__main__":
    main()
