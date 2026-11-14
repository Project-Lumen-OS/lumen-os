#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Lumen OS usage statistics: the server end of the projector's daily ping (apps/core/lumen-stats, the one client of
every model: the updater on the Z9X and the HB, the projector app on the Play 6, the X50 and the RS30).

    POST /lumen/v1/ping    {"v":3,"id":"<uuid>","serial":..,"model":..,"xgimi_model":..,"version":..,
                           "version_code":..,"build":..,"channel":..,"trial":true|false,"prev_version":..,
                           "prev_version_code":..,"method":..}  (v 1 and v 2 bodies are still taken) -> 204
    GET  /lumen/v1/stats   -> JSON aggregates (see Store.stats)

Stored per install and UTC day: HMAC of the install id, HMAC of the serial (server secret; SERIAL_RAW keeps the
serial itself), model (the Lumen codename of the device registry, from the model and the XGIMI model the client
reports), XGIMI model, version, version code, build, channel (beta for a pre-release version, else stable, whatever
the client's beta switch says), kind (trial = a DSU trial run on the stock system, installed, or "" from clients
before v 3) and country. Every count is of projectors (a serial; an install without one by its install id), never
of install ids: a reset, a reinstall and every trial run make a new install id on the same projector. The country comes
from the client address through an offline GeoIP database (DB-IP Lite Country, CC BY 4.0, "IP Geolocation by
DB-IP", https://db-ip.com); no city is derived and the address itself is never stored, the rate limit per address
lives in memory only and nginx logs nothing for /lumen/v1/. A version change reported by the client (prev_version_code) is also kept as
an update event (from, to, method). Python 3 standard library, plus python3-maxminddb for the GeoIP lookup
(without it the country stays empty). Runs on 127.0.0.1 behind nginx.
Usage: lumen_stats.py [--db PATH] [--salt PATH] [--geoip PATH] [--port N]
"""
import argparse
import datetime
import hashlib
import hmac
import ipaddress
import json
import os
import re
import sqlite3
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    import maxminddb
except ImportError:      # the host test runs without it
    maxminddb = None

MAX_BODY = 2048
RATE_MAX, RATE_WINDOW = 20, 3600          # pings per client address per hour
KEEP_DAYS = 400                            # rows older than this are deleted
STATS_CACHE_S = 60
SERIAL_RAW = False                         # True stores the serial itself instead of its keyed hash
# images without ro.lumen.device send ro.product.vendor.device (the Z9X up to 1.0.8)
MODEL_ALIASES = {"mt5877": "horse_popular", "play6": "dragon_healthy"}
# the device registry (devices/<codename>/device.conf: family = ro.product.device, models = ro.boot.xgimi.modelname,
# name), copied here because the server has no checkout; test_lumen_stats.py proves it equals the registry. XGIMI
# builds several models from one product image, so the family alone does not name the projector.
FAMILIES = {
    "horse_popular": {"G0082": "horse_popular", "G0100": "g0100", "G0111": "g0111", "G0155": "g0155"},
    "goat_elegant": {"G0130": "goat_elegant", "G0148": "goat_elegant"},
    "dragon_healthy": {"G0058": "dragon_healthy"},
}
NAMES = {"horse_popular": "XGIMI Z9X", "goat_elegant": "XGIMI Z9X HB", "dragon_healthy": "XGIMI Play 6",
         "g0111": "XGIMI X50 Ultra", "g0100": "XGIMI X50 Ultra Max", "g0155": "XGIMI RS30 Ultra Max"}
CODENAMES = {c for m in FAMILIES.values() for c in m.values()}
PRERELEASE_RE = re.compile(r"-(beta|rc)[0-9]*$")
KINDS = ("", "trial", "installed")
SCHEMA_VERSION = 3
METHODS = ("", "first", "ota_full", "ota_delta", "ota", "usb", "installer")

UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
MODEL_RE = re.compile(r"^[a-z0-9_]{1,32}$")
XMODEL_RE = re.compile(r"^[A-Za-z0-9_\-]{0,16}$")
SERIAL_RE = re.compile(r"^[A-Za-z0-9._\-]{0,40}$")
VERSION_RE = re.compile(r"^[0-9A-Za-z.\-]{1,24}$")
BUILD_RE = re.compile(r"^[0-9A-Za-z._\-]{0,64}$")
CHANNELS = ("stable", "beta")

# one projector: its serial hash, or the install id hash for an install that reports no serial
DEVICE = "COALESCE(NULLIF(serial_hash, ''), id_hash)"
# the row that stands for a projector when it sent several (several install ids on one day, a trial and then the
# installed system): the installed one first, then the newest version
PICK = "(kind = 'installed') DESC, version_code DESC, id_hash"

SCHEMA = """
CREATE TABLE IF NOT EXISTS pings2 (day TEXT NOT NULL, id_hash TEXT NOT NULL, serial_hash TEXT NOT NULL,
  model TEXT NOT NULL, xgimi_model TEXT NOT NULL, version TEXT NOT NULL, version_code INTEGER NOT NULL,
  build TEXT NOT NULL, channel TEXT NOT NULL, country TEXT NOT NULL, kind TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (day, id_hash));
CREATE TABLE IF NOT EXISTS updates2 (day TEXT NOT NULL, id_hash TEXT NOT NULL, serial_hash TEXT NOT NULL,
  model TEXT NOT NULL, from_version TEXT NOT NULL, from_code INTEGER NOT NULL, to_version TEXT NOT NULL,
  to_code INTEGER NOT NULL, method TEXT NOT NULL, PRIMARY KEY (id_hash, from_code, to_code));
DROP TABLE IF EXISTS pings;
DROP TABLE IF EXISTS updates;
"""


def today(now=None):
    return datetime.datetime.fromtimestamp(now if now is not None else time.time(), datetime.timezone.utc).date()


def codename(model, xgimi_model):
    """The registry codename of what a client reports: a codename stays, a family (or an alias of one, e.g. the
    vendor device name of the Z9X up to 1.0.8, ro.lumen.device=play6 of the Play 6 images) is resolved by the XGIMI
    model; an unknown XGIMI model of a known family stays the family."""
    m = MODEL_ALIASES.get(model, model)
    if m in FAMILIES:
        return FAMILIES[m].get(xgimi_model, m)
    return m


def channel_of(version):
    """beta for a pre-release build (x.y.z-betaN, -rcN, also with a model prefix such as x50-), else stable."""
    return "beta" if PRERELEASE_RE.search(version) else "stable"


def _str(obj, key, rx, default=""):
    v = obj.get(key, default)
    if not isinstance(v, str) or not rx.match(v):
        raise ValueError(key)
    return v


def _int(obj, key, default=0):
    v = obj.get(key, default)
    if not isinstance(v, int) or isinstance(v, bool) or not 0 <= v < 10 ** 9:
        raise ValueError(key)
    return v


def validate(obj):
    """The ping as a dict of checked fields, or raise ValueError."""
    if not isinstance(obj, dict) or obj.get("v") not in (1, 2, 3):
        raise ValueError("v")
    p = {"id": _str(obj, "id", UUID_RE, None)}
    model = obj.get("model")
    if not isinstance(model, str):
        raise ValueError("model")
    p["serial"] = _str(obj, "serial", SERIAL_RE)
    p["xgimi_model"] = _str(obj, "xgimi_model", XMODEL_RE)
    model = codename(model, p["xgimi_model"])
    p["model"] = model if MODEL_RE.match(model) else "other"
    p["version"] = _str(obj, "version", VERSION_RE, None)
    p["version_code"] = _int(obj, "version_code", None)
    p["build"] = _str(obj, "build", BUILD_RE)
    if obj.get("channel") not in CHANNELS:
        raise ValueError("channel")
    # clients before v 3 sent their beta updates switch here (a beta build with the switch off said stable)
    p["channel"] = channel_of(p["version"])
    trial = obj.get("trial")
    if trial is not None and not isinstance(trial, bool):
        raise ValueError("trial")
    p["kind"] = "" if trial is None else ("trial" if trial else "installed")
    p["prev_version_code"] = _int(obj, "prev_version_code")
    p["prev_version"] = _str(obj, "prev_version", re.compile(r"^[0-9A-Za-z.\-]{0,24}$"))
    if obj.get("method", "") not in METHODS:
        raise ValueError("method")
    p["method"] = obj.get("method", "")
    return p


class Geo:
    """The country of an address from the DB-IP Lite Country file (a City file works too: only the country is read);
    reopened when the file changes."""

    def __init__(self, path):
        self.path, self.db, self.mtime, self.checked = path, None, 0, 0
        self.lock = threading.Lock()

    def _reader(self):
        now = time.time()
        if maxminddb is None or not self.path or now - self.checked < 300:
            return self.db
        self.checked = now
        try:
            m = os.stat(self.path).st_mtime
        except OSError:
            return self.db
        if m != self.mtime:
            try:
                self.db, self.mtime = maxminddb.open_database(self.path), m
            except Exception:
                pass
        return self.db

    def lookup(self, addr):
        try:
            ip = ipaddress.ip_address(addr)
            if not ip.is_global:
                return ""
            with self.lock:
                db = self._reader()
            r = db.get(str(ip)) if db else None
        except Exception:
            return ""
        if not r:
            return ""
        return ((r.get("country") or {}).get("iso_code") or "")[:2]


class Store:
    def __init__(self, db, salt, now=None):
        self.salt = salt
        self.lock = threading.Lock()
        self.db = sqlite3.connect(db, check_same_thread=False)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.backup = None
        if self.db.execute("PRAGMA user_version").fetchone()[0] < SCHEMA_VERSION and self._tables():
            self.backup = backup(self.db, db, now)
        self.db.executescript(SCHEMA)
        if "city" in self._cols("pings2"):
            self.db.execute("ALTER TABLE pings2 DROP COLUMN city")      # 2026-11-04: no city any more
            self.db.commit()
            self.db.execute("PRAGMA secure_delete=ON")
            self.db.execute("VACUUM")                                   # the dropped values leave the file too
            self.db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        self._migrate()
        self.db.commit()
        self.pruned = None
        self.cache = (0.0, b"")

    def _tables(self):
        return {r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}

    def _cols(self, table):
        return {r[1] for r in self.db.execute("PRAGMA table_info(%s)" % table)}

    def _migrate(self):
        """Schema 3 (2026-11-13): the kind column, and the rows of older clients corrected in place: channel from
        the version (they sent the beta switch), model as the registry codename (no row count changes)."""
        if self.db.execute("PRAGMA user_version").fetchone()[0] >= SCHEMA_VERSION:
            return
        before = [self.db.execute("SELECT COUNT(*) FROM %s" % t).fetchone()[0] for t in ("pings2", "updates2")]
        if "kind" not in self._cols("pings2"):
            self.db.execute("ALTER TABLE pings2 ADD COLUMN kind TEXT NOT NULL DEFAULT ''")
        for v, in self.db.execute("SELECT DISTINCT version FROM pings2").fetchall():
            self.db.execute("UPDATE pings2 SET channel = ? WHERE version = ?", (channel_of(v), v))
        for m, x in self.db.execute("SELECT DISTINCT model, xgimi_model FROM pings2").fetchall():
            if codename(m, x) != m:
                self.db.execute("UPDATE pings2 SET model = ? WHERE model = ? AND xgimi_model = ?", (codename(m, x), m, x))
        for m, in self.db.execute("SELECT DISTINCT model FROM updates2").fetchall():
            # updates2 has no XGIMI model: the codename of the same install's pings, else the alias
            self.db.execute("UPDATE updates2 SET model = COALESCE((SELECT p.model FROM pings2 p WHERE p.id_hash = "
                            "updates2.id_hash ORDER BY p.day DESC LIMIT 1), ?) WHERE model = ?",
                            (MODEL_ALIASES.get(m, m), m))
        after = [self.db.execute("SELECT COUNT(*) FROM %s" % t).fetchone()[0] for t in ("pings2", "updates2")]
        if before != after:
            self.db.rollback()
            raise RuntimeError("schema %d migration changed the row counts %s -> %s" % (SCHEMA_VERSION, before, after))
        self.db.execute("PRAGMA user_version = %d" % SCHEMA_VERSION)

    def h(self, kind, value):
        if not value:
            return ""
        return hmac.new(self.salt, (kind + ":" + value).encode(), hashlib.sha256).hexdigest()[:32]

    def add(self, p, country="", now=None):
        d = today(now).isoformat()
        ih = self.h("id", p["id"])
        sh = p["serial"] if SERIAL_RAW else self.h("serial", p["serial"])
        with self.lock:
            self.db.execute("INSERT OR REPLACE INTO pings2 (day, id_hash, serial_hash, model, xgimi_model, version, "
                            "version_code, build, channel, country, kind) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            (d, ih, sh, p["model"], p["xgimi_model"], p["version"], p["version_code"],
                             p["build"], p["channel"], country, p.get("kind", "")))
            if p["method"] == "first" or p["prev_version_code"]:
                self.db.execute("INSERT OR IGNORE INTO updates2 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                (d, ih, sh, p["model"], p["prev_version"], p["prev_version_code"], p["version"],
                                 p["version_code"], p["method"] or "unknown"))
            if self.pruned != d:
                old = (today(now) - datetime.timedelta(days=KEEP_DAYS)).isoformat()
                self.db.execute("DELETE FROM pings2 WHERE day < ?", (old,))
                self.db.execute("DELETE FROM updates2 WHERE day < ?", (old,))
                self.pruned = d
            self.db.commit()
            self.cache = (0.0, b"")

    def _rows(self, sql, args, keys):
        return [dict(zip(keys, r)) for r in self.db.execute(sql, args)]

    def stats(self, now=None):
        t = time.time() if now is None else now
        with self.lock:
            if now is None and t - self.cache[0] < STATS_CACHE_S:
                return self.cache[1]
            d = today(t)
            out = {"generated": datetime.datetime.fromtimestamp(t, datetime.timezone.utc).isoformat(
                timespec="seconds"), "day": d.isoformat(), "schema": SCHEMA_VERSION,
                "geo": "IP Geolocation by DB-IP (db-ip.com)", "names": NAMES, "active": {}}
            for n in (7, 30):
                since = (d - datetime.timedelta(days=n - 1)).isoformat()
                # each projector once, at its latest row in the window (on that day the installed system before a
                # trial run, then the newest version): a reset, a reinstall or a trial run is not a new projector
                latest = ("(SELECT * FROM (SELECT model, xgimi_model, version, channel, country, kind, ROW_NUMBER() "
                          "OVER (PARTITION BY " + DEVICE + " ORDER BY day DESC, " + PICK + ") AS rn FROM pings2 "
                          "WHERE day >= ?) WHERE rn = 1)")
                tot = self.db.execute("SELECT COUNT(DISTINCT " + DEVICE + "), COUNT(DISTINCT id_hash) FROM pings2 "
                                      "WHERE day >= ?", (since,)).fetchone()
                a = {"since": since, "units": tot[0], "ids": tot[1]}
                a["by_version"] = self._rows("SELECT model, version, channel, COUNT(*) FROM " + latest +
                                             " GROUP BY 1, 2, 3 ORDER BY 4 DESC, 1, 2", (since,),
                                             ("model", "version", "channel", "units"))
                a["by_model"] = self._rows("SELECT model, xgimi_model, COUNT(*) FROM " + latest +
                                           " GROUP BY 1, 2 ORDER BY 3 DESC, 1", (since,),
                                           ("model", "xgimi_model", "units"))
                a["by_country"] = self._rows("SELECT country, COUNT(*) FROM " + latest +
                                             " GROUP BY 1 ORDER BY 2 DESC, 1", (since,), ("country", "units"))
                a["by_kind"] = self._rows("SELECT model, kind, COUNT(*) FROM " + latest +
                                          " GROUP BY 1, 2 ORDER BY 1, 2", (since,), ("model", "kind", "units"))
                out["active"][str(n)] = a
            since = (d - datetime.timedelta(days=89)).isoformat()
            # one row per projector and day: the day's sum is the number of projectors seen that day
            out["daily"] = self._rows("SELECT day, model, version, channel, COUNT(*) FROM (SELECT day, model, version, "
                                      "channel, ROW_NUMBER() OVER (PARTITION BY day, " + DEVICE + " ORDER BY " + PICK +
                                      ") AS rn FROM pings2 WHERE day >= ?) WHERE rn = 1 GROUP BY 1, 2, 3, 4 ORDER BY 1",
                                      (since,), ("day", "model", "version", "channel", "units"))
            # projectors seen for the first time (in the kept history): the true new installs, a reinstall or a trial
            # run of a known projector is not one
            out["new_daily"] = self._rows("SELECT day, model, COUNT(*) FROM (SELECT day, model, ROW_NUMBER() OVER "
                                          "(PARTITION BY " + DEVICE + " ORDER BY day, " + PICK + ") AS rn FROM pings2) "
                                          "WHERE rn = 1 AND day >= ? GROUP BY 1, 2 ORDER BY 1", (since,),
                                          ("day", "model", "units"))
            out["updates_daily"] = self._rows("SELECT day, method, COUNT(DISTINCT " + DEVICE + ") FROM updates2 WHERE "
                                              "day >= ? AND method != 'first' GROUP BY 1, 2 ORDER BY 1", (since,),
                                              ("day", "method", "units"))
            out["pairs"] = self._rows("SELECT model, from_version, to_version, method, COUNT(DISTINCT " + DEVICE + ") "
                                      "FROM updates2 WHERE day >= ? AND from_code > 0 GROUP BY 1, 2, 3, 4 "
                                      "ORDER BY 5 DESC, 1, 2, 3", (since,),
                                      ("model", "from_version", "to_version", "method", "units"))
            # a projector seen with more than one install id in 90 days: reset, reinstalled or run as a trial
            out["reinstalled"] = self.db.execute(
                "SELECT COUNT(*) FROM (SELECT serial_hash FROM pings2 WHERE day >= ? AND serial_hash != '' "
                "GROUP BY serial_hash HAVING COUNT(DISTINCT id_hash) > 1)", (since,)).fetchone()[0]
            body = json.dumps(out, separators=(",", ":")).encode()
            if now is None:
                self.cache = (t, body)
            return body


def backup(con, path, now=None):
    """A copy of the database next to it (backup-<UTC date and time>.db, the state directory) before a migration."""
    stamp = datetime.datetime.fromtimestamp(now if now is not None else time.time(), datetime.timezone.utc)
    dst = os.path.join(os.path.dirname(os.path.abspath(path)), "backup-%s.db" % stamp.strftime("%Y%m%d-%H%M%S"))
    out = sqlite3.connect(dst)
    con.backup(out)
    out.close()
    os.chmod(dst, 0o600)
    return dst


class RateLimit:
    def __init__(self):
        self.lock = threading.Lock()
        self.seen = {}

    def allow(self, addr, now=None):
        t = time.time() if now is None else now
        with self.lock:
            if len(self.seen) > 50000:
                self.seen = {a: v for a, v in self.seen.items() if t - v[1] < RATE_WINDOW}
            n, start = self.seen.get(addr, (0, t))
            if t - start >= RATE_WINDOW:
                n, start = 0, t
            if n >= RATE_MAX:
                return False
            self.seen[addr] = (n + 1, start)
            return True


def make_handler(store, limit, geo):
    class H(BaseHTTPRequestHandler):
        server_version = "lumen-stats"
        sys_version = ""

        def log_message(self, *a):      # no access log: it would hold client addresses
            pass

        def send(self, code, body=b""):
            self.send_response(code)
            if body:
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Cache-Control", "max-age=60")
            else:
                self.send_header("Content-Length", "0")
            self.end_headers()
            if body:
                self.wfile.write(body)

        def client(self):
            # nginx: X-Lumen-CF = Cloudflare's CF-Connecting-IP (through the site), else X-Lumen-Remote = the peer
            return (self.headers.get("X-Lumen-CF") or self.headers.get("X-Lumen-Remote")
                    or self.client_address[0]).strip()

        def do_GET(self):
            if self.path.split("?")[0] == "/lumen/v1/stats":
                self.send(200, store.stats())
            else:
                self.send(404)

        def do_POST(self):
            if self.path != "/lumen/v1/ping":
                return self.send(404)
            addr = self.client()
            if not limit.allow(addr):
                return self.send(429)
            try:
                n = int(self.headers.get("Content-Length", "-1"))
            except ValueError:
                n = -1
            if not 0 < n <= MAX_BODY:
                return self.send(413 if n > MAX_BODY else 400)
            try:
                p = validate(json.loads(self.rfile.read(n).decode("utf-8")))
            except (ValueError, UnicodeDecodeError):
                return self.send(400)
            store.add(p, geo.lookup(addr))
            self.send(204)

    return H


def load_salt(path):
    if not os.path.exists(path):
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as f:
            f.write(os.urandom(32))
    with open(path, "rb") as f:
        return f.read()


def main():
    ap = argparse.ArgumentParser()
    state = os.environ.get("STATE_DIRECTORY", ".")
    ap.add_argument("--db", default=os.path.join(state, "stats.db"))
    ap.add_argument("--salt", default=os.path.join(state, "salt"))
    ap.add_argument("--geoip", default="/opt/lumen-stats/geo/dbip-country-lite.mmdb")
    ap.add_argument("--port", type=int, default=8095)
    a = ap.parse_args()
    store = Store(a.db, load_salt(a.salt))
    srv = ThreadingHTTPServer(("127.0.0.1", a.port), make_handler(store, RateLimit(), Geo(a.geoip)))
    srv.daemon_threads = True
    srv.serve_forever()


if __name__ == "__main__":
    main()
