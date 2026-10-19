"""SQLite persistence for MeshLink. Secrets (auth keys, node tokens) are stored as SHA-256 hashes."""
from __future__ import annotations

import hashlib
import json
import sqlite3
import threading
import time

SCHEMA = """
CREATE TABLE IF NOT EXISTS settings (k TEXT PRIMARY KEY, v TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS auth_keys (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    key_hash TEXT UNIQUE NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    reusable INTEGER NOT NULL DEFAULT 0,
    ephemeral INTEGER NOT NULL DEFAULT 0,
    tags TEXT NOT NULL DEFAULT '[]',
    expires_at REAL,
    used_count INTEGER NOT NULL DEFAULT 0,
    revoked INTEGER NOT NULL DEFAULT 0,
    created_at REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS nodes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    pubkey TEXT UNIQUE NOT NULL,
    ip TEXT UNIQUE NOT NULL,
    token_hash TEXT UNIQUE NOT NULL,
    tags TEXT NOT NULL DEFAULT '[]',
    os TEXT NOT NULL DEFAULT '',
    endpoint TEXT,
    listen_port INTEGER,
    rdp_enabled INTEGER NOT NULL DEFAULT 0,
    ephemeral INTEGER NOT NULL DEFAULT 0,
    last_seen REAL,
    created_at REAL NOT NULL
);
"""


def sha256(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


class Database:
    def __init__(self, path: str):
        self.conn = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute("PRAGMA journal_mode=WAL")
        self.conn.executescript(SCHEMA)
        self.lock = threading.RLock()

    # ---------------------------------------------------------------- helpers
    def q(self, sql: str, args: tuple = ()) -> list[sqlite3.Row]:
        with self.lock:
            return self.conn.execute(sql, args).fetchall()

    def one(self, sql: str, args: tuple = ()) -> sqlite3.Row | None:
        rows = self.q(sql, args)
        return rows[0] if rows else None

    def exec(self, sql: str, args: tuple = ()) -> int:
        with self.lock:
            return self.conn.execute(sql, args).lastrowid

    # ---------------------------------------------------------------- settings
    def get_setting(self, k: str, default=None):
        row = self.one("SELECT v FROM settings WHERE k=?", (k,))
        return json.loads(row["v"]) if row else default

    def set_setting(self, k: str, v) -> None:
        self.exec("INSERT INTO settings(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v",
                  (k, json.dumps(v)))

    # ---------------------------------------------------------------- auth keys
    def create_auth_key(self, key: str, description: str, reusable: bool, ephemeral: bool,
                        tags: list[str], ttl_hours: float | None) -> int:
        expires = time.time() + ttl_hours * 3600 if ttl_hours else None
        return self.exec(
            "INSERT INTO auth_keys(key_hash,description,reusable,ephemeral,tags,expires_at,created_at)"
            " VALUES(?,?,?,?,?,?,?)",
            (sha256(key), description, int(reusable), int(ephemeral), json.dumps(tags), expires, time.time()))

    def consume_auth_key(self, key: str) -> sqlite3.Row | None:
        """Validate an auth key and mark it used. Returns the row, or None if invalid."""
        with self.lock:
            row = self.one("SELECT * FROM auth_keys WHERE key_hash=?", (sha256(key),))
            if (row is None or row["revoked"]
                    or (row["expires_at"] and row["expires_at"] < time.time())
                    or (not row["reusable"] and row["used_count"] > 0)):
                return None
            self.exec("UPDATE auth_keys SET used_count=used_count+1 WHERE id=?", (row["id"],))
            return row
