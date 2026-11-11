#!/usr/bin/env python3
"""Office Attendance Server.

Runs on one always-on office PC. Needs only Python 3.9+ (no extra packages).

    python server.py            # listens on port 8080
    python server.py --port 9000

Two independent records are kept for every staff member and day:
  1. AUTO   - captured by agent.ps1 on the staff PC (first / last keyboard-mouse
              activity). Time is always stamped by THIS server's clock.
  2. MANUAL - the time the staff member declares on the web page, entered
              without being shown the AUTO record.
The admin dashboard compares the two.
"""
import argparse
import calendar
import csv
import hashlib
import hmac
import io
import json
import os
import re
import secrets
import sqlite3
import time
from datetime import date, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

BASE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.environ.get("ATTENDANCE_DB", os.path.join(BASE, "attendance.db"))
WEB = os.path.join(BASE, "web")
BEAT_MINUTES = 5          # agent sends one "active" beat per 5 minutes of use
SESSION_HOURS = 12
DEFAULTS = {
    "office_start": "10:00",   # default reporting time
    "grace_min": "10",         # minutes allowed after reporting time
    "tolerance_min": "10",     # allowed gap between declared and PC time
    "weekly_off": "6",         # comma list, Monday=0 ... Sunday=6
    "holidays": "",            # one YYYY-MM-DD per line
    "firm_name": "Office Attendance",
}

# Type of work -> words looked for in the program name / window title (admin can edit these).
WORK_TYPES = {
    "GST Returns": "gst, gstr, gstr1, gstr3b, gstr2a, gstr2b, 3b, goods and services tax, e-way bill, eway",
    "GST Annual Return / Audit": "gstr-9, gstr9, gstr-9c, 9c",
    "Income Tax Return": "income tax, incometax, e-filing, itr, 26as, ais, computation",
    "TDS / TCS": "tds, tcs, traces, 26q, 24q, 27q",
    "Tax Audit": "tax audit, 3cd, 3ca, 3cb",
    "Statutory Audit": "statutory audit, audit report, caro",
    "Accounting / Bookkeeping": "tally, tallyprime, busy, ledger, bank reconciliation",
    "ROC / Company Law": "mca, roc, aoc-4, mgt-7, dir-3",
    "Notices / Assessments / Appeals": "notice, drc, asmt, appeal, scn, assessment",
    "Consultancy / Advisory": "",
    "Certification": "certificate, 15cb, 15ca",
    "Office / Administration": "outlook, gmail, whatsapp, teams",
}
NO_CLIENT, NO_TYPE = "(Client not identified)", "(Unclassified)"

SCHEMA = """
CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE IF NOT EXISTS staff(
  id INTEGER PRIMARY KEY, name TEXT NOT NULL, win_user TEXT NOT NULL DEFAULT '',
  pin_hash TEXT NOT NULL, shift_start TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1,
  joined TEXT NOT NULL DEFAULT '');   -- attendance is expected only from this date
CREATE TABLE IF NOT EXISTS auto_day(
  win_user TEXT, day TEXT, first_seen TEXT, last_seen TEXT, host TEXT, ip TEXT,
  beats INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(win_user, day));
CREATE TABLE IF NOT EXISTS manual(
  staff_id INTEGER, day TEXT, in_time TEXT, in_entered TEXT, in_note TEXT,
  out_time TEXT, out_entered TEXT, out_note TEXT,
  adm_in TEXT, adm_out TEXT, adm_remark TEXT, PRIMARY KEY(staff_id, day));
CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY, kind TEXT, staff_id INTEGER, expires REAL);
CREATE TABLE IF NOT EXISTS audit(ts TEXT, who TEXT, action TEXT);
CREATE TABLE IF NOT EXISTS clients(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE,
  keywords TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS work_types(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE,
  keywords TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS activity(
  win_user TEXT, day TEXT, app TEXT, title TEXT, seconds INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(win_user, day, app, title));
"""


def db():
    con = sqlite3.connect(DB_PATH, timeout=10)
    con.row_factory = sqlite3.Row
    return con


def init_db():
    con = db()
    con.executescript(SCHEMA)
    con.execute("PRAGMA journal_mode=WAL")
    for k, v in DEFAULTS.items():
        con.execute("INSERT OR IGNORE INTO settings VALUES(?,?)", (k, v))
    con.execute("INSERT OR IGNORE INTO settings VALUES('agent_key',?)", (secrets.token_hex(12),))
    if not con.execute("SELECT 1 FROM settings WHERE key='masters_seeded'").fetchone():
        con.executemany("INSERT OR IGNORE INTO work_types(name,keywords) VALUES(?,?)", list(WORK_TYPES.items()))
        con.execute("INSERT INTO settings VALUES('masters_seeded','1')")
    con.commit()
    con.close()


def get_settings(con):
    return {r["key"]: r["value"] for r in con.execute("SELECT * FROM settings")}


def hash_secret(secret, salt=None):
    salt = salt or secrets.token_hex(8)
    h = hashlib.pbkdf2_hmac("sha256", secret.encode(), salt.encode(), 100_000).hex()
    return f"{salt}${h}"


def check_secret(secret, stored):
    try:
        salt = stored.split("$")[0]
    except Exception:
        return False
    return hmac.compare_digest(hash_secret(secret, salt), stored)


def now():
    return datetime.now()


def hhmm(ts):
    """'YYYY-MM-DD HH:MM:SS' -> 'HH:MM'"""
    return ts[11:16] if ts else ""


def mins(t):
    return int(t[:2]) * 60 + int(t[3:5]) if t else None


def valid_time(t):
    try:
        datetime.strptime(t, "%H:%M")
        return len(t) == 5
    except (ValueError, TypeError):
        return False


def valid_day(d):
    try:
        datetime.strptime(d, "%Y-%m-%d")
        return True
    except (ValueError, TypeError):
        return False


def is_working_day(day, s):
    d = date.fromisoformat(day)
    offs = {int(x) for x in s["weekly_off"].split(",") if x.strip().isdigit()}
    hols = {x.strip() for x in s["holidays"].splitlines()}
    return d.weekday() not in offs and day not in hols


def compute(staff, day, auto, man, s, today):
    """Build one staff-day row. `auto` and `man` are sqlite rows or None."""
    start = staff["shift_start"] or s["office_start"]
    limit = mins(start) + int(s["grace_min"])
    tol = int(s["tolerance_min"])
    a_in, a_out = (hhmm(auto["first_seen"]), hhmm(auto["last_seen"])) if auto else ("", "")
    m_in = (man["in_time"] or "") if man else ""
    m_out = (man["out_time"] or "") if man else ""
    adm_in = (man["adm_in"] or "") if man else ""
    adm_out = (man["adm_out"] or "") if man else ""
    eff_in = adm_in or a_in or m_in       # PC record prevails unless admin corrected
    eff_out = adm_out or a_out or m_out
    if day == today and not adm_out:
        eff_out_for_hours = ""            # day still running
    else:
        eff_out_for_hours = eff_out
    working = is_working_day(day, s) and day >= staff["joined"]
    flags = []
    if eff_in:
        status = "Late" if mins(eff_in) > limit else "On time"
        late_by = max(0, mins(eff_in) - mins(start))
    else:
        late_by = 0
        status = ("Absent" if day < today else "Not in yet") if working else "Off"
    in_gap = out_gap = None
    if a_in and m_in:
        in_gap = mins(a_in) - mins(m_in)       # +ve: declared EARLIER than PC shows
        if in_gap > tol:
            flags.append(f"Declared arrival {in_gap} min earlier than PC")
    if a_out and m_out and day < today:
        out_gap = mins(m_out) - mins(a_out)    # +ve: declared leaving LATER than PC shows
        if out_gap > tol:
            flags.append(f"Declared leaving {out_gap} min later than PC")
    if a_in and not m_in:
        flags.append("Arrival not declared")
    if m_in and not a_in:
        flags.append("No PC record")
    if eff_in and not m_out and day < today:
        flags.append("Leaving not declared")
    hours = None
    if eff_in and eff_out_for_hours and mins(eff_out_for_hours) >= mins(eff_in):
        hours = round((mins(eff_out_for_hours) - mins(eff_in)) / 60, 2)
    return {
        "staff_id": staff["id"], "name": staff["name"], "day": day, "shift_start": start,
        "auto_in": a_in, "auto_out": a_out, "manual_in": m_in, "manual_out": m_out,
        "adm_in": adm_in, "adm_out": adm_out, "adm_remark": (man["adm_remark"] or "") if man else "",
        "in_note": (man["in_note"] or "") if man else "", "out_note": (man["out_note"] or "") if man else "",
        "in_entered": hhmm(man["in_entered"]) if man and man["in_entered"] else "",
        "eff_in": eff_in, "eff_out": eff_out, "status": status, "late_by": late_by,
        "in_gap": in_gap, "out_gap": out_gap, "flags": flags, "hours": hours,
        "active_min": (auto["beats"] * BEAT_MINUTES) if auto else 0,
        "host": auto["host"] if auto else "", "working": working,
    }


def rows_for(con, days, staff_rows=None):
    s = get_settings(con)
    today = now().strftime("%Y-%m-%d")
    staff_rows = staff_rows or con.execute("SELECT * FROM staff WHERE active=1 ORDER BY name").fetchall()
    lo, hi = min(days), max(days)
    autos = {(r["win_user"], r["day"]): r for r in
             con.execute("SELECT * FROM auto_day WHERE day BETWEEN ? AND ?", (lo, hi))}
    mans = {(r["staff_id"], r["day"]): r for r in
            con.execute("SELECT * FROM manual WHERE day BETWEEN ? AND ?", (lo, hi))}
    out = []
    for st in staff_rows:
        for d in days:
            out.append(compute(st, d, autos.get((st["win_user"], d)) if st["win_user"] else None,
                               mans.get((st["id"], d)), s, today))
    return out


def norm(text):
    """Lower-case and reduce every run of punctuation to one space, padded for whole-word matching."""
    return " " + re.sub(r"[^a-z0-9]+", " ", (text or "").lower()).strip() + " "


def matchers(con, table, use_name):
    """[(normalised keyword, name)] longest keyword first, so the most specific match wins."""
    out = []
    for r in con.execute(f"SELECT name,keywords FROM {table}"):
        for kw in ([r["name"]] if use_name else []) + r["keywords"].split(","):
            k = norm(kw)
            if len(k.strip()) >= 2:
                out.append((k, r["name"]))
    return sorted(out, key=lambda x: -len(x[0]))


def classify(text, rules, default):
    return next((name for kw, name in rules if kw in text), default)


def month_days(month):
    y, m = int(month[:4]), int(month[5:7])
    today = now().date()
    return [date(y, m, d).isoformat() for d in range(1, calendar.monthrange(y, m)[1] + 1)
            if date(y, m, d) <= today]


def month_summary(con, month):
    days = month_days(month)
    if not days:
        return {"month": month, "summary": [], "rows": []}
    rows = rows_for(con, days)
    summary = {}
    for r in rows:
        a = summary.setdefault(r["staff_id"], {
            "staff_id": r["staff_id"], "name": r["name"], "present": 0, "on_time": 0, "late": 0,
            "absent": 0, "mismatch": 0, "not_declared": 0, "no_pc": 0, "late_min": 0,
            "_arr": [], "_hrs": []})
        if r["eff_in"]:
            a["present"] += 1
            a["_arr"].append(mins(r["eff_in"]))
            if r["status"] == "Late":
                a["late"] += 1
                a["late_min"] += r["late_by"]
            else:
                a["on_time"] += 1
        elif r["status"] == "Absent":
            a["absent"] += 1
        if r["hours"] is not None:
            a["_hrs"].append(r["hours"])
        for f in r["flags"]:
            if f.startswith("Declared"):
                a["mismatch"] += 1
            elif f == "Arrival not declared":
                a["not_declared"] += 1
            elif f == "No PC record":
                a["no_pc"] += 1
    out = []
    for a in summary.values():
        arr, hrs = a.pop("_arr"), a.pop("_hrs")
        avg = round(sum(arr) / len(arr)) if arr else None
        a["avg_arrival"] = f"{avg // 60:02d}:{avg % 60:02d}" if avg is not None else ""
        a["avg_hours"] = round(sum(hrs) / len(hrs), 1) if hrs else None
        a["punctuality"] = round(100 * a["on_time"] / a["present"]) if a["present"] else None
        out.append(a)
    out.sort(key=lambda x: (-(x["punctuality"] if x["punctuality"] is not None else -1), x["name"]))
    return {"month": month, "summary": out, "rows": rows}


def lan_ip():
    """Best guess of this PC's address on the office network (no traffic is sent)."""
    import socket
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sk:
            sk.connect(("10.255.255.255", 1))
            return sk.getsockname()[0]
    except OSError:
        return "127.0.0.1"


PIN_FAILS = {}   # staff_id or 'admin' -> (count, locked_until)


def locked(key):
    c, until = PIN_FAILS.get(key, (0, 0))
    return until > time.time()


def note_fail(key):
    c, _ = PIN_FAILS.get(key, (0, 0))
    c += 1
    PIN_FAILS[key] = (0, time.time() + 600) if c >= 5 else (c, 0)


class ApiError(Exception):
    def __init__(self, msg, code=400):
        self.msg, self.code = msg, code


class Handler(BaseHTTPRequestHandler):
    server_version = "OfficeAttendance/1.0"

    def log_message(self, fmt, *args):
        pass

    # ---------- plumbing ----------
    def send(self, body, code=200, ctype="application/json", headers=None):
        if isinstance(body, (dict, list)):
            body = json.dumps(body)
        if isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype + ("; charset=utf-8" if "text" in ctype or "json" in ctype else ""))
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def cookie(self, name):
        for part in (self.headers.get("Cookie") or "").split(";"):
            k, _, v = part.strip().partition("=")
            if k == name:
                return v
        return ""

    def session(self, con, kind):
        tok = self.cookie("sid_" + kind)
        if not tok:
            return None
        r = con.execute("SELECT * FROM sessions WHERE token=? AND kind=? AND expires>?",
                        (tok, kind, time.time())).fetchone()
        return r

    def new_session(self, con, kind, staff_id=None):
        tok = secrets.token_urlsafe(24)
        con.execute("DELETE FROM sessions WHERE expires<?", (time.time(),))
        con.execute("INSERT INTO sessions VALUES(?,?,?,?)",
                    (tok, kind, staff_id, time.time() + SESSION_HOURS * 3600))
        return {"Set-Cookie": f"sid_{kind}={tok}; Path=/; HttpOnly; SameSite=Strict; Max-Age={SESSION_HOURS * 3600}"}

    def need_staff(self, con):
        s = self.session(con, "staff")
        if not s:
            raise ApiError("Please sign in again.", 401)
        st = con.execute("SELECT * FROM staff WHERE id=? AND active=1", (s["staff_id"],)).fetchone()
        if not st:
            raise ApiError("Please sign in again.", 401)
        return st

    def need_admin(self, con):
        if not self.session(con, "admin"):
            raise ApiError("Admin sign-in required.", 401)

    def audit(self, con, who, action):
        con.execute("INSERT INTO audit VALUES(?,?,?)", (now().strftime("%Y-%m-%d %H:%M:%S"), who, action))

    # ---------- routing ----------
    def do_GET(self):
        self.route("GET")

    def do_POST(self):
        self.route("POST")

    def route(self, method):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        path = u.path.rstrip("/") or "/"
        if method == "GET" and path in ("/", "/admin"):
            name = "staff.html" if path == "/" else "admin.html"
            with open(os.path.join(WEB, name), "rb") as f:
                return self.send(f.read(), ctype="text/html")
        if not path.startswith("/api/"):
            return self.send({"error": "Not found"}, 404)
        body = {}
        if method == "POST":
            try:
                n = int(self.headers.get("Content-Length") or 0)
                body = json.loads(self.rfile.read(min(n, 100_000)) or b"{}")
                if not isinstance(body, dict):
                    raise ValueError
            except ValueError:
                return self.send({"error": "Bad request"}, 400)
        fn = getattr(self, f"{method.lower()}_{path[5:].replace('/', '_').replace('-', '_')}", None)
        if not fn:
            return self.send({"error": "Not found"}, 404)
        con = db()
        try:
            res = fn(con, q, body)
            con.commit()
            if isinstance(res, tuple):
                self.send(res[0], headers=res[1]) if isinstance(res[1], dict) else self.send(*res)
            else:
                self.send(res if res is not None else {"ok": True})
        except ApiError as e:
            con.commit()   # keep audit / lockout side effects
            self.send({"error": e.msg}, e.code)
        except Exception as e:  # noqa
            self.send({"error": f"Server error: {e}"}, 500)
        finally:
            con.close()

    # ---------- agent ----------
    def post_agent(self, con, q, b):
        s = get_settings(con)
        if not hmac.compare_digest(str(b.get("key", "")), s["agent_key"]):
            raise ApiError("Bad agent key", 403)
        user = str(b.get("user", "")).strip().lower()[:64]
        if not user:
            raise ApiError("user missing")
        host = str(b.get("host", ""))[:64]
        n = now()
        ts, day = n.strftime("%Y-%m-%d %H:%M:%S"), n.strftime("%Y-%m-%d")
        con.execute(
            "INSERT INTO auto_day(win_user,day,first_seen,last_seen,host,ip,beats) VALUES(?,?,?,?,?,?,1) "
            "ON CONFLICT(win_user,day) DO UPDATE SET last_seen=excluded.last_seen, host=excluded.host, "
            "ip=excluded.ip, beats=beats+1",
            (user, day, ts, ts, host, self.client_address[0]))
        samples = b.get("samples")
        for smp in (samples if isinstance(samples, list) else [])[:400]:
            try:
                sec = max(0, min(int(smp.get("seconds", 0)), 4 * 3600))
                app, title = str(smp.get("app", ""))[:60].lower(), str(smp.get("title", ""))[:200]
            except (AttributeError, TypeError, ValueError):
                continue
            if sec:
                con.execute("INSERT INTO activity VALUES(?,?,?,?,?) ON CONFLICT(win_user,day,app,title) "
                            "DO UPDATE SET seconds=seconds+excluded.seconds", (user, day, app, title, sec))
        return {"ok": True, "server_time": ts}

    # ---------- staff ----------
    def get_staff_list(self, con, q, b):
        rows = con.execute("SELECT id,name,win_user FROM staff WHERE active=1 ORDER BY name").fetchall()
        u = q.get("u", "").strip().lower()
        pre = next((r["id"] for r in rows if u and r["win_user"] == u), None)
        return {"staff": [{"id": r["id"], "name": r["name"]} for r in rows], "preselect": pre,
                "firm": get_settings(con)["firm_name"]}

    def post_login(self, con, q, b):
        sid = b.get("staff_id")
        st = con.execute("SELECT * FROM staff WHERE id=? AND active=1", (sid,)).fetchone()
        if not st:
            raise ApiError("Select your name.")
        if locked(st["id"]):
            raise ApiError("Too many wrong attempts. Try again after 10 minutes.", 429)
        if not check_secret(str(b.get("pin", "")), st["pin_hash"]):
            note_fail(st["id"])
            raise ApiError("Wrong PIN.", 401)
        PIN_FAILS.pop(st["id"], None)
        return {"ok": True}, self.new_session(con, "staff", st["id"])

    def post_logout(self, con, q, b):
        for kind in ("staff", "admin"):
            con.execute("DELETE FROM sessions WHERE token=?", (self.cookie("sid_" + kind),))
        return {"ok": True}

    def pending_out(self, con, st, today):
        """Most recent earlier day (within 7 days) with a declared arrival but no declared leaving."""
        lo = (now().date() - timedelta(days=7)).isoformat()
        return con.execute(
            "SELECT day,in_time FROM manual WHERE staff_id=? AND day<? AND day>=? AND in_time IS NOT NULL "
            "AND out_time IS NULL ORDER BY day DESC LIMIT 1", (st["id"], today, lo)).fetchone()

    def get_me(self, con, q, b):
        st = self.need_staff(con)
        n = now()
        today = n.strftime("%Y-%m-%d")
        t = con.execute("SELECT * FROM manual WHERE staff_id=? AND day=?", (st["id"], today)).fetchone()
        p = self.pending_out(con, st, today)
        hist = con.execute(
            "SELECT day,in_time,out_time FROM manual WHERE staff_id=? AND day>=? ORDER BY day DESC",
            (st["id"], (n.date() - timedelta(days=31)).isoformat())).fetchall()
        s = get_settings(con)
        return {
            "name": st["name"], "today": today, "now": n.strftime("%H:%M"),
            "shift_start": st["shift_start"] or s["office_start"], "firm": s["firm_name"],
            "in_time": (t["in_time"] or "") if t else "", "out_time": (t["out_time"] or "") if t else "",
            "pending_out": {"day": p["day"], "in_time": p["in_time"]} if p else None,
            "history": [dict(h) for h in hist],
        }

    def post_mark(self, con, q, b):
        st = self.need_staff(con)
        n = now()
        today, cur = n.strftime("%Y-%m-%d"), n.strftime("%H:%M")
        stamp = n.strftime("%Y-%m-%d %H:%M:%S")
        kind, t = b.get("type"), str(b.get("time", ""))
        note = str(b.get("note", "")).strip()[:200]
        if not valid_time(t):
            raise ApiError("Enter a valid time.")
        if kind == "in":
            if mins(t) > mins(cur) + 1:
                raise ApiError("Arrival time cannot be later than the current time.")
            r = con.execute("SELECT in_time FROM manual WHERE staff_id=? AND day=?", (st["id"], today)).fetchone()
            if r and r["in_time"]:
                raise ApiError("Arrival is already recorded for today. Ask the admin for any correction.")
            con.execute(
                "INSERT INTO manual(staff_id,day,in_time,in_entered,in_note) VALUES(?,?,?,?,?) "
                "ON CONFLICT(staff_id,day) DO UPDATE SET in_time=excluded.in_time, "
                "in_entered=excluded.in_entered, in_note=excluded.in_note",
                (st["id"], today, t, stamp, note))
        elif kind == "out":
            day = b.get("day") or today
            if day != today:
                p = self.pending_out(con, st, today)
                if not p or p["day"] != day:
                    raise ApiError("That day is not open for entry. Ask the admin for any correction.")
            elif mins(t) > mins(cur) + 1:
                raise ApiError("Leaving time cannot be later than the current time.")
            r = con.execute("SELECT * FROM manual WHERE staff_id=? AND day=?", (st["id"], day)).fetchone()
            if not r or not r["in_time"]:
                raise ApiError("Record your arrival time first.")
            if r["out_time"]:
                raise ApiError("Leaving time is already recorded. Ask the admin for any correction.")
            if mins(t) < mins(r["in_time"]):
                raise ApiError("Leaving time cannot be before arrival time.")
            con.execute("UPDATE manual SET out_time=?, out_entered=?, out_note=? WHERE staff_id=? AND day=?",
                        (t, stamp, note, st["id"], day))
        else:
            raise ApiError("Bad request")
        return {"ok": True}

    def post_change_pin(self, con, q, b):
        st = self.need_staff(con)
        new = str(b.get("new", ""))
        if not check_secret(str(b.get("old", "")), st["pin_hash"]):
            raise ApiError("Current PIN is wrong.")
        if not (new.isdigit() and 4 <= len(new) <= 8):
            raise ApiError("New PIN must be 4 to 8 digits.")
        con.execute("UPDATE staff SET pin_hash=? WHERE id=?", (hash_secret(new), st["id"]))
        self.audit(con, st["name"], "changed own PIN")

    # ---------- work log: built automatically from the window titles the agent reports ----------
    def work_range(self, q):
        today = now().date()
        f, t = q.get("from") or today.replace(day=1).isoformat(), q.get("to") or today.isoformat()
        if not (valid_day(f) and valid_day(t)) or f > t:
            raise ApiError("Bad date range")
        if (date.fromisoformat(t) - date.fromisoformat(f)).days > 400:
            raise ApiError("Choose a period of up to 400 days.")
        return f, t

    def work_entries(self, con, f, t):
        crules, trules = matchers(con, "clients", True), matchers(con, "work_types", False)
        out = []
        for r in con.execute(
                "SELECT a.day,s.id staff_id,s.name staff,a.app,a.title,a.seconds FROM activity a "
                "JOIN staff s ON s.win_user=a.win_user AND s.win_user<>'' "
                "WHERE a.day BETWEEN ? AND ? ORDER BY a.day DESC,a.seconds DESC", (f, t)):
            e = dict(r)
            title = norm(e["title"])
            e["client"] = classify(title, crules, NO_CLIENT)
            e["type"] = classify(norm(e["app"]) + title, trules, NO_TYPE)
            out.append(e)
        return out

    def get_admin_work(self, con, q, b):
        self.need_admin(con)
        f, t = self.work_range(q)
        today = now().date()
        days, d = [], date.fromisoformat(f)
        while d <= min(date.fromisoformat(t), today):
            days.append(d.isoformat())
            d += timedelta(days=1)
        attended = {}
        if days:
            staff = con.execute("SELECT * FROM staff ORDER BY name").fetchall()
            for r in rows_for(con, days, staff):
                if r["hours"] is not None:
                    attended[r["staff_id"]] = round(attended.get(r["staff_id"], 0) + r["hours"], 2)
        return {"from": f, "to": t, "entries": self.work_entries(con, f, t), "attended_hours": attended,
                "no_client": NO_CLIENT, "no_type": NO_TYPE}

    def get_admin_work_export(self, con, q, b):
        self.need_admin(con)
        f, t = self.work_range(q)
        buf = io.StringIO()
        w = csv.writer(buf)
        w.writerow(["Date", "Staff", "Client", "Type of work", "Hours", "Minutes", "Program", "Window title"])
        for e in self.work_entries(con, f, t):
            w.writerow([e["day"], e["staff"], e["client"], e["type"], round(e["seconds"] / 3600, 2),
                        round(e["seconds"] / 60, 1), e["app"], e["title"]])
        return ("\ufeff" + buf.getvalue(), 200, "text/csv",
                {"Content-Disposition": f'attachment; filename="work-log-{f}-to-{t}.csv"'})

    def get_admin_masters(self, con, q, b):
        self.need_admin(con)
        return {"clients": [dict(r) for r in con.execute("SELECT * FROM clients ORDER BY active DESC,name")],
                "types": [dict(r) for r in con.execute("SELECT * FROM work_types ORDER BY active DESC,name")]}

    def post_admin_masters(self, con, q, b):
        """Add names (one per line) to clients / work types, or rename / (de)activate one."""
        self.need_admin(con)
        table = {"client": "clients", "type": "work_types"}.get(b.get("kind"))
        if not table:
            raise ApiError("Bad request")
        if b.get("id"):
            name = str(b.get("name", "")).strip()[:120]
            if not name:
                raise ApiError("Name is required.")
            if con.execute(f"SELECT 1 FROM {table} WHERE name=? AND id<>?", (name, b["id"])).fetchone():
                raise ApiError("That name already exists.")
            kws = ", ".join(k.strip() for k in str(b.get("keywords", "")).split(",") if k.strip())[:500]
            con.execute(f"UPDATE {table} SET name=?, keywords=?, active=? WHERE id=?",
                        (name, kws, 1 if b.get("active", True) else 0, b["id"]))
            return {"ok": True}
        names = [n.strip()[:120] for n in str(b.get("names", "")).splitlines() if n.strip()]
        if not names:
            raise ApiError("Enter at least one name.")
        added = 0
        for n in names:
            added += con.execute(f"INSERT OR IGNORE INTO {table}(name) VALUES(?)", (n,)).rowcount
        self.audit(con, "admin", f"added {added} {b['kind']}(s)")
        return {"ok": True, "added": added, "skipped": len(names) - added}

    # ---------- admin ----------
    def get_admin_state(self, con, q, b):
        s = get_settings(con)
        return {"setup_needed": "admin_hash" not in s, "logged_in": bool(self.session(con, "admin")),
                "firm": s["firm_name"]}

    def post_admin_setup(self, con, q, b):
        if "admin_hash" in get_settings(con):
            raise ApiError("Admin password is already set.", 403)
        pw = str(b.get("password", ""))
        if len(pw) < 6:
            raise ApiError("Password must be at least 6 characters.")
        con.execute("INSERT INTO settings VALUES('admin_hash',?)", (hash_secret(pw),))
        return {"ok": True}, self.new_session(con, "admin")

    def post_admin_login(self, con, q, b):
        s = get_settings(con)
        if locked("admin"):
            raise ApiError("Too many wrong attempts. Try again after 10 minutes.", 429)
        if "admin_hash" not in s or not check_secret(str(b.get("password", "")), s["admin_hash"]):
            note_fail("admin")
            raise ApiError("Wrong password.", 401)
        PIN_FAILS.pop("admin", None)
        return {"ok": True}, self.new_session(con, "admin")

    def get_admin_day(self, con, q, b):
        self.need_admin(con)
        day = q.get("date") or now().strftime("%Y-%m-%d")
        if not valid_day(day):
            raise ApiError("Bad date")
        linked = {r["win_user"] for r in con.execute("SELECT win_user FROM staff WHERE win_user<>''")}
        seen = {r["win_user"] for r in con.execute("SELECT DISTINCT win_user FROM auto_day")}
        return {"day": day, "today": now().strftime("%Y-%m-%d"), "now": now().strftime("%H:%M"),
                "rows": rows_for(con, [day]), "agents_seen": len(seen), "agents_unlinked": sorted(seen - linked)}

    def get_admin_month(self, con, q, b):
        self.need_admin(con)
        month = q.get("month") or now().strftime("%Y-%m")
        if not valid_day(month + "-01"):
            raise ApiError("Bad month")
        return month_summary(con, month)

    def get_admin_export(self, con, q, b):
        self.need_admin(con)
        month = q.get("month") or now().strftime("%Y-%m")
        if not valid_day(month + "-01"):
            raise ApiError("Bad month")
        data = month_summary(con, month)
        buf = io.StringIO()
        w = csv.writer(buf)
        w.writerow(["Date", "Staff", "Reporting time", "PC first activity", "Declared arrival", "Admin arrival",
                    "PC last activity", "Declared leaving", "Admin leaving", "Status", "Late by (min)",
                    "Hours", "PC active (min)", "Flags", "Staff note", "Admin remark"])
        for r in sorted(data["rows"], key=lambda r: (r["day"], r["name"])):
            w.writerow([r["day"], r["name"], r["shift_start"], r["auto_in"], r["manual_in"], r["adm_in"],
                        r["auto_out"], r["manual_out"], r["adm_out"], r["status"], r["late_by"],
                        r["hours"] if r["hours"] is not None else "", r["active_min"], "; ".join(r["flags"]),
                        " / ".join(x for x in (r["in_note"], r["out_note"]) if x), r["adm_remark"]])
        w.writerow([])
        w.writerow(["Summary", "Present", "On time", "Late", "Absent", "Punctuality %", "Avg arrival",
                    "Avg hours", "Total late min", "Time mismatches", "Arrival not declared", "No PC record"])
        for a in data["summary"]:
            w.writerow([a["name"], a["present"], a["on_time"], a["late"], a["absent"], a["punctuality"],
                        a["avg_arrival"], a["avg_hours"], a["late_min"], a["mismatch"], a["not_declared"],
                        a["no_pc"]])
        return ("﻿" + buf.getvalue(), 200, "text/csv",
                {"Content-Disposition": f'attachment; filename="attendance-{month}.csv"'})

    def get_admin_staff(self, con, q, b):
        self.need_admin(con)
        rows = con.execute("SELECT id,name,win_user,shift_start,active FROM staff ORDER BY active DESC,name").fetchall()
        mapped = {r["win_user"] for r in rows if r["win_user"]}
        seen = con.execute("SELECT win_user, MAX(host) host, MAX(last_seen) last_seen FROM auto_day "
                           "GROUP BY win_user ORDER BY last_seen DESC").fetchall()
        names = {r["win_user"]: r["name"] for r in rows if r["win_user"]}
        return {"staff": [dict(r) for r in rows],
                "unmapped": [dict(r) for r in seen if r["win_user"] not in mapped],
                "agents": [dict(r, staff=names.get(r["win_user"], "")) for r in seen],
                "now": now().strftime("%Y-%m-%d %H:%M:%S")}

    def post_admin_staff(self, con, q, b):
        self.need_admin(con)
        name = str(b.get("name", "")).strip()[:80]
        win = str(b.get("win_user", "")).strip().lower()[:64]
        shift = str(b.get("shift_start", "")).strip()
        pin = str(b.get("pin", "")).strip()
        active = 1 if b.get("active", True) else 0
        if not name:
            raise ApiError("Name is required.")
        if shift and not valid_time(shift):
            raise ApiError("Reporting time must be HH:MM.")
        if pin and not (pin.isdigit() and 4 <= len(pin) <= 8):
            raise ApiError("PIN must be 4 to 8 digits.")
        sid = b.get("id")
        if win and con.execute("SELECT 1 FROM staff WHERE win_user=? AND id IS NOT ?", (win, sid)).fetchone():
            raise ApiError("That Windows user name is already linked to another staff member.")
        if sid:
            con.execute("UPDATE staff SET name=?, win_user=?, shift_start=?, active=? WHERE id=?",
                        (name, win, shift, active, sid))
            if pin:
                con.execute("UPDATE staff SET pin_hash=? WHERE id=?", (hash_secret(pin), sid))
                con.execute("DELETE FROM sessions WHERE staff_id=?", (sid,))
                PIN_FAILS.pop(sid, None)
            self.audit(con, "admin", f"updated staff {name}" + (" (PIN reset)" if pin else ""))
        else:
            if not pin:
                raise ApiError("Set a starting PIN (4 to 8 digits).")
            con.execute("INSERT INTO staff(name,win_user,pin_hash,shift_start,active,joined) VALUES(?,?,?,?,?,?)",
                        (name, win, hash_secret(pin), shift, active, now().strftime("%Y-%m-%d")))
            self.audit(con, "admin", f"added staff {name}")

    def get_admin_settings(self, con, q, b):
        self.need_admin(con)
        s = get_settings(con)
        s.pop("admin_hash", None)
        s["lan_ip"] = lan_ip()
        return s

    def post_admin_settings(self, con, q, b):
        self.need_admin(con)
        if not valid_time(str(b.get("office_start", ""))):
            raise ApiError("Reporting time must be HH:MM.")
        for k in ("grace_min", "tolerance_min"):
            if not str(b.get(k, "")).isdigit():
                raise ApiError("Minutes must be a whole number.")
        hol = [x.strip() for x in str(b.get("holidays", "")).splitlines() if x.strip()]
        if any(not valid_day(h) for h in hol):
            raise ApiError("Holidays must be one date per line as YYYY-MM-DD.")
        offs = ",".join(str(int(x)) for x in b.get("weekly_off", []) if str(x).isdigit() and 0 <= int(x) <= 6)
        vals = {"office_start": b["office_start"], "grace_min": str(int(b["grace_min"])),
                "tolerance_min": str(int(b["tolerance_min"])), "weekly_off": offs,
                "holidays": "\n".join(hol), "firm_name": str(b.get("firm_name", "")).strip()[:80] or DEFAULTS["firm_name"]}
        for k, v in vals.items():
            con.execute("INSERT OR REPLACE INTO settings VALUES(?,?)", (k, v))
        pw = str(b.get("new_password", ""))
        if pw:
            if len(pw) < 6:
                raise ApiError("Password must be at least 6 characters.")
            con.execute("INSERT OR REPLACE INTO settings VALUES('admin_hash',?)", (hash_secret(pw),))
        self.audit(con, "admin", "changed settings")

    def post_admin_override(self, con, q, b):
        self.need_admin(con)
        sid, day = b.get("staff_id"), str(b.get("day", ""))
        a_in, a_out = str(b.get("adm_in", "")).strip(), str(b.get("adm_out", "")).strip()
        remark = str(b.get("remark", "")).strip()[:200]
        st = con.execute("SELECT name FROM staff WHERE id=?", (sid,)).fetchone()
        if not st or not valid_day(day):
            raise ApiError("Bad request")
        if any(t and not valid_time(t) for t in (a_in, a_out)):
            raise ApiError("Times must be HH:MM.")
        if (a_in or a_out) and not remark:
            raise ApiError("A remark is required for every correction.")
        con.execute(
            "INSERT INTO manual(staff_id,day,adm_in,adm_out,adm_remark) VALUES(?,?,?,?,?) "
            "ON CONFLICT(staff_id,day) DO UPDATE SET adm_in=excluded.adm_in, adm_out=excluded.adm_out, "
            "adm_remark=excluded.adm_remark", (sid, day, a_in or None, a_out or None, remark or None))
        self.audit(con, "admin", f"correction for {st['name']} on {day}: in={a_in or '-'} out={a_out or '-'} ({remark})")

    def get_admin_audit(self, con, q, b):
        self.need_admin(con)
        return [dict(r) for r in con.execute("SELECT * FROM audit ORDER BY ts DESC LIMIT 200")]


def main():
    ap = argparse.ArgumentParser(description="Office Attendance Server")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--host", default="0.0.0.0")
    args = ap.parse_args()
    init_db()
    srv = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Office Attendance Server running on port {args.port}")
    print(f"  Staff page : http://{lan_ip()}:{args.port}/")
    print(f"  Admin page : http://{lan_ip()}:{args.port}/admin")
    print(f"  Database   : {DB_PATH}")
    print("Keep this window open. Press Ctrl+C to stop.")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
