#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Lumen OS problem reports: the upload end on our statistics server (the projector side is apps/core/lumen-report,
served by lumen_stats.py on the same port, behind the same nginx and Cloudflare).

    POST /lumen/v1/report   body = the report zip, at most MAX_BYTES. Headers: X-Lumen-Report: 1,
                            X-Lumen-Install: <16-64 lowercase hex, the random report id of the projector>,
                            X-Lumen-Version, X-Lumen-Model (codename or G code in lower case), X-Lumen-Kind (manual,
                            crash, anr, boot, dump = the stock system info of Lumen Installer; test = check everything,
                            store nothing: the deploy health check), X-Lumen-Serial (kind dump only, optional: kept
                            as the statistics HMAC in the dump counter, never stored raw)
        201 {"code":"LR-XXXXXX","expires":"<UTC>"}   200 {"code":"LR-TEST00","dry":true} for kind test
        400 bad header, 411 no length, 413 too big, 415 not a zip, 429 too many (Retry-After), 507 store full
    POST /lumen/v1/report/part  the same zip in parts of at most PART_MAX bytes, each part on its own connection
                            (networks in Russia freeze a connection to a Cloudflare address after about 20 KB, so
                            there the client sends parts of 12 KiB, elsewhere up to PART_MAX). Content-Type
                            application/octet-stream, the headers above plus X-Lumen-Upload (32 lowercase hex, new for
                            every upload), X-Lumen-Offset, X-Lumen-Total (bytes of the whole zip, at most MAX_BYTES, a
                            dump DUMP_MAX_BYTES) and X-Lumen-Sha256 (of the whole zip). Parts may come in any order
                            and twice (the client sends several at once): the server keeps the byte ranges it has.
                            The limits count an upload once, at its first part.
        202 {"have":N,"got":M} more parts wanted: N bytes from the start are complete, M bytes in all
        409 {"have":N} too many separate ranges: send from N on        201 / 200 as above when the last byte came
        A finished upload answers every later part of it with its code again (a slower parallel part).
        X-Lumen-Query: 1 with an empty body asks for the state: 202 {"have","got"}, 200 {"code"} when finished,
        404 when the server has no such upload (never started or dropped). An upload with no part for OPEN_KEEP
        seconds is dropped, so a projector that was switched off goes on the next day where it stopped; when the
        open uploads are full, the ones idle for OPEN_EVICT_IDLE go first.
        An upload is the pair (install id, upload id): both are random secrets of the sender that only travel inside
        TLS, so nobody else can add to, change or ask about another sender's upload (the same upload id with another
        install id is a different upload). At most OPEN_PER_SENDER unfinished uploads per sender, PART_SLOTS parts
        handled at once (503 with Retry-After beyond that).
        400 also for a changed upload or a wrong sha256, 507 when the store, the open uploads or the disk are full

There is no way to read reports over HTTP. They are read over ssh only:
    lumen_reports.py list [DAYS]    newest first: code, received (UTC), kind, model, version, size
    lumen_reports.py show CODE      metadata, the file list and summary.txt of one report
    lumen_reports.py cat CODE       the zip on stdout
    lumen_reports.py rm CODE        delete one report
    lumen_reports.py prune          retention and size cap now (the server also prunes every hour)
Stored under the state directory (reports/YYYYMMDD/<code>.zip and .json): the zip, its size and sha256, the HMAC of
the install id (the statistics secret), kind, model, version, the received time. No IP address and no country: the
address only feeds the in-memory limits. Limits: per address RATE_HOUR an hour and RATE_DAY a day, per install
INSTALL_DAY a day, GLOBAL_DAY a day in all, dumps DUMP_INSTALL_DAY per install and DUMP_DAY in all a day. Problem
reports are kept KEEP_DAYS days and at most STORE_MAX_BYTES, dumps DUMP_KEEP_DAYS days and at most
DUMP_STORE_MAX_BYTES, oldest first. Unfinished uploads take at most OPEN_MAX_BYTES, and no upload starts when the disk
would keep less than DISK_FLOOR free.
"""
import datetime
import hashlib
import hmac
import ipaddress
import json
import os
import re
import secrets
import shutil
import sys
import threading
import time
import zipfile

MAX_BYTES = 5 * 1024 * 1024
KEEP_DAYS = 30
STORE_MAX_BYTES = 2 * 1024 ** 3
RATE_HOUR, RATE_DAY = 6, 20
INSTALL_DAY = 10
GLOBAL_DAY = 300
ZIP_MAX_ENTRIES = 400
ZIP_MAX_UNPACKED = 64 * 1024 * 1024
CHUNK = 64 * 1024
KINDS = ("manual", "crash", "anr", "boot", "dump")
DUMP_KIND = "dump"
# the stock system info of Lumen Installer: the stock settings app (40 to 80 MB) and the full command set, about
# 100 MB, only in parts
DUMP_MAX_BYTES = 200 * 1024 * 1024
DUMP_MAX_UNPACKED = 768 * 1024 * 1024
DUMP_KEEP_DAYS = 90
DUMP_STORE_MAX_BYTES = 6 * 1024 ** 3                 # the VPS disk has about 15 GB free (2026-11-18)
DUMP_DAY, DUMP_INSTALL_DAY = 40, 3
PART_MAX = 1024 * 1024
OPEN_MAX = 200                                       # unfinished part uploads at one time
OPEN_MAX_BYTES = 2 * 1024 ** 3                       # the totals of the unfinished uploads together
OPEN_KEEP = 86400                                    # an unfinished upload without a part for a day is dropped
RANGES_MAX = 4096                                    # separate byte ranges of one unfinished upload
DISK_FLOOR = 3 * 1024 ** 3
OPEN_PER_SENDER = 3                                  # unfinished uploads of one sender (address, an IPv6 /64)
OPEN_EVICT_IDLE = 3600                               # under pressure an upload idle this long makes room
PART_SLOTS, PART_WAIT = 12, 5                        # parts handled at once (a part body is in memory), wait for one
UPLOAD_RE = re.compile(r"^[0-9a-f]{32}$")
SHA_RE = re.compile(r"^[0-9a-f]{64}$")
SERIAL_RE = re.compile(r"^[A-Za-z0-9._\-]{1,40}$")
TEST_KIND = "test"
CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"   # Crockford: no I, L, O, U
CODE_LEN = 6
CODE_RE = re.compile(r"^LR-[0-9A-HJKMNP-TV-Z]{6}$")
INSTALL_RE = re.compile(r"^[0-9a-f]{16,64}$")
VERSION_RE = re.compile(r"^[0-9A-Za-z._+\-]{1,32}$")
MODEL_RE = re.compile(r"^[a-z0-9_]{1,32}$")
HOUR, DAY = 3600, 86400


def utc(t):
    return datetime.datetime.fromtimestamp(t, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def new_code():
    return "LR-" + "".join(secrets.choice(CODE_ALPHABET) for _ in range(CODE_LEN))


def sender(addr):
    """The key of a client address for the limits: an IPv6 address by its /64 (a provider hands a whole /64 to one
    line, so a new address in it is not a new sender), IPv4 as it is. Kept in memory only."""
    a = (addr or "").strip()
    try:
        ip = ipaddress.ip_address(a)
    except ValueError:
        return a
    if ip.version == 6 and ip.ipv4_mapped:
        return str(ip.ipv4_mapped)
    if ip.version == 6:
        return str(ipaddress.ip_network("%s/64" % ip, strict=False))
    return str(ip)


def check_headers(h, part=False):
    """The validated header fields, or (status, reason) when a header is wrong. part: one part of an upload (an empty
    body only as the state query X-Lumen-Query: 1)."""
    if (h.get("X-Lumen-Report") or "").strip() != "1":
        return 400, "header X-Lumen-Report"
    ctype = (h.get("Content-Type") or "").split(";")[0].strip().lower()
    if ctype != ("application/octet-stream" if part else "application/zip"):
        return 415, "content type"
    install = (h.get("X-Lumen-Install") or "").strip()
    if not INSTALL_RE.match(install):
        return 400, "header X-Lumen-Install"
    version = (h.get("X-Lumen-Version") or "unknown").strip()
    if not VERSION_RE.match(version):
        return 400, "header X-Lumen-Version"
    model = (h.get("X-Lumen-Model") or "unknown").strip()
    if not MODEL_RE.match(model):
        return 400, "header X-Lumen-Model"
    kind = (h.get("X-Lumen-Kind") or "manual").strip()
    if kind not in KINDS and kind != TEST_KIND:
        return 400, "header X-Lumen-Kind"
    serial = (h.get("X-Lumen-Serial") or "").strip()
    if serial and (kind != DUMP_KIND or not SERIAL_RE.match(serial)):
        return 400, "header X-Lumen-Serial"
    query = part and (h.get("X-Lumen-Query") or "").strip() == "1"
    length = h.get("Content-Length")
    if length is None:
        return 411, "no length"
    try:
        n = int(length)
    except ValueError:
        return 400, "bad length"
    if n < 0 or (n == 0 and not query) or (query and n != 0):
        return 400, "empty" if n == 0 else "bad length"
    if n > (PART_MAX if part else MAX_BYTES):
        return 413, "too big"
    f = {"install": install, "version": version, "model": model, "kind": kind, "length": n, "serial": serial,
         "query": query}
    if part:
        upload, sha = (h.get("X-Lumen-Upload") or "").strip(), (h.get("X-Lumen-Sha256") or "").strip()
        if not UPLOAD_RE.match(upload):
            return 400, "header X-Lumen-Upload"
        if not SHA_RE.match(sha):
            return 400, "header X-Lumen-Sha256"
        try:
            total, offset = int(h.get("X-Lumen-Total") or ""), int(h.get("X-Lumen-Offset") or "")
        except ValueError:
            return 400, "bad offset"
        if total > max_total(kind):
            return 413, "too big"
        if total <= 0 or offset < 0 or offset + n > total:
            return 400, "bad offset"
        f.update(upload=upload, sha256=sha, total=total, offset=offset)
    return f


def max_total(kind):
    """the largest zip of a part upload: a dump may be large, a problem report not"""
    return DUMP_MAX_BYTES if kind == DUMP_KIND else MAX_BYTES


def check_zip(path, kind=""):
    """None when the file is a sound zip we may keep (a listing only, nothing is unpacked), else the reason."""
    with open(path, "rb") as f:
        if f.read(4) != b"PK\x03\x04":
            return "not a zip"
    try:
        with zipfile.ZipFile(path) as z:
            infos = z.infolist()
    except (zipfile.BadZipFile, OSError, ValueError):
        return "broken zip"
    if not infos or len(infos) > ZIP_MAX_ENTRIES:
        return "entry count"
    if sum(i.file_size for i in infos) > (DUMP_MAX_UNPACKED if kind == DUMP_KIND else ZIP_MAX_UNPACKED):
        return "unpacked size"
    for i in infos:
        n = i.filename
        if n.startswith("/") or "\\" in n or ".." in n.split("/"):
            return "entry name"
    return None


def merge(ranges, a, b):
    """byte ranges [[start, end), ...] sorted and joined, with a..b added"""
    out = []
    for s, e in sorted(ranges + [[a, b]]):
        if out and s <= out[-1][1]:
            out[-1][1] = max(out[-1][1], e)
        else:
            out.append([s, e])
    return out


class Limits:
    """Sliding counts in memory: per address an hour and a day, per install id a day, all reports a day, and dumps per
    install id and in all a day."""

    def __init__(self):
        self.lock = threading.Lock()
        self.addr = {}      # addr -> [times]
        self.install = {}   # install hash -> [times]
        self.total = []
        self.dump_install = {}
        self.dumps = []

    @staticmethod
    def _recent(times, now, window):
        return [t for t in times if now - t < window]

    def check(self, addr, install_hash, now, dump=False):
        """0 when allowed (and counted), else the seconds to wait."""
        with self.lock:
            if len(self.addr) > 50000:
                self.addr = {a: v for a, v in self.addr.items() if self._recent(v, now, DAY)}
            if len(self.install) > 50000:
                self.install = {a: v for a, v in self.install.items() if self._recent(v, now, DAY)}
            if len(self.dump_install) > 50000:
                self.dump_install = {a: v for a, v in self.dump_install.items() if self._recent(v, now, DAY)}
            a = self._recent(self.addr.get(addr, []), now, DAY)
            i = self._recent(self.install.get(install_hash, []), now, DAY)
            self.total = self._recent(self.total, now, DAY)
            hour = [t for t in a if now - t < HOUR]
            if len(hour) >= RATE_HOUR:
                return int(HOUR - (now - min(hour))) + 1
            checks = [(a, RATE_DAY), (i, INSTALL_DAY), (self.total, GLOBAL_DAY)]
            di = self._recent(self.dump_install.get(install_hash, []), now, DAY)
            self.dumps = self._recent(self.dumps, now, DAY)
            if dump:
                checks += [(di, DUMP_INSTALL_DAY), (self.dumps, DUMP_DAY)]
            for times, lim in checks:
                if len(times) >= lim:
                    return int(DAY - (now - min(times))) + 1
            a.append(now)
            i.append(now)
            self.total.append(now)
            self.addr[addr] = a
            self.install[install_hash] = i
            if dump:
                di.append(now)
                self.dumps.append(now)
                self.dump_install[install_hash] = di
            return 0


def is_dump(meta):
    return meta.get("kind") == DUMP_KIND


class Store:
    """reports/YYYYMMDD/<code>.zip + <code>.json under root; tmp/ for uploads in progress."""

    def __init__(self, root, salt):
        self.root = root
        self.salt = salt
        self.lock = threading.Lock()
        os.makedirs(os.path.join(root, "tmp"), mode=0o700, exist_ok=True)
        for d in (root, os.path.join(root, "tmp")):
            try:
                os.chmod(d, 0o700)
            except OSError:
                pass

    def install_hash(self, install):
        return hmac.new(self.salt, b"report-install:" + install.encode(), hashlib.sha256).hexdigest()[:32]

    def days(self):
        return sorted((d for d in os.listdir(self.root) if re.match(r"^\d{8}$", d)), reverse=True)

    def entries(self):
        """[(meta, zip path)] newest first."""
        out = []
        for d in self.days():
            dd = os.path.join(self.root, d)
            for n in os.listdir(dd):
                if n.endswith(".json"):
                    try:
                        with open(os.path.join(dd, n)) as f:
                            m = json.load(f)
                    except (OSError, ValueError):
                        continue
                    out.append((m, os.path.join(dd, n[:-5] + ".zip")))
        out.sort(key=lambda e: e[0].get("received_ts", 0), reverse=True)
        return out

    def find(self, code):
        code = code.strip().upper()
        if not CODE_RE.match(code):
            return None
        for d in self.days():
            z = os.path.join(self.root, d, code + ".zip")
            if os.path.isfile(z):
                return z
        return None

    def total_bytes(self, dumps=None):
        """bytes in the store: all (None), the dumps only (True) or the problem reports only (False)"""
        n = 0
        for m, z in self.entries():
            if dumps is None or is_dump(m) == dumps:
                for p in (z, z[:-4] + ".json"):
                    try:
                        n += os.path.getsize(p)
                    except OSError:
                        pass
        return n

    @staticmethod
    def cap(dumps):
        return DUMP_STORE_MAX_BYTES if dumps else STORE_MAX_BYTES

    def _remove(self, z):
        for p in (z, z[:-4] + ".json"):
            try:
                os.remove(p)
            except OSError:
                pass

    def prune(self, now, extra=0, dumps=False):
        """Drop what is older than its retention (KEEP_DAYS, dumps DUMP_KEEP_DAYS), then the oldest of each class while
        it is over its cap (extra: bytes about to come into the class named by dumps). Unfinished uploads go after
        OPEN_KEEP without a part."""
        removed = 0
        with self.lock:
            for m, z in self.entries():
                keep = DUMP_KEEP_DAYS if is_dump(m) else KEEP_DAYS
                if m.get("received_ts", 0) < now - keep * DAY:
                    self._remove(z)
                    removed += 1
            for cls in (False, True):
                more = extra if cls == dumps else 0
                if self.total_bytes(cls) + more > self.cap(cls):
                    for m, z in reversed(self.entries()):
                        if is_dump(m) != cls:
                            continue
                        self._remove(z)
                        removed += 1
                        if self.total_bytes(cls) + more <= self.cap(cls):
                            break
            for d in self.days():
                dd = os.path.join(self.root, d)
                if not os.listdir(dd):
                    os.rmdir(dd)
            tmp = os.path.join(self.root, "tmp")
            for f in os.listdir(tmp):
                p = os.path.join(tmp, f)
                if now - os.path.getmtime(p) > OPEN_KEEP:
                    os.remove(p)
        return removed

    def keep(self, tmp_path, fields, now):
        """Moves an accepted upload into the store; returns its code."""
        sha = hashlib.sha256()
        with open(tmp_path, "rb") as f:
            for b in iter(lambda: f.read(CHUNK), b""):
                sha.update(b)
        day = datetime.datetime.fromtimestamp(now, datetime.timezone.utc).strftime("%Y%m%d")
        dd = os.path.join(self.root, day)
        keep_days = DUMP_KEEP_DAYS if fields["kind"] == DUMP_KIND else KEEP_DAYS
        with self.lock:
            os.makedirs(dd, mode=0o700, exist_ok=True)
            for _ in range(20):
                code = new_code()
                if self.find(code) is None:
                    break
            else:
                raise OSError("no free code")
            meta = {"code": code, "received": utc(now), "received_ts": int(now), "expires": utc(now + keep_days * DAY),
                    "kind": fields["kind"], "model": fields["model"], "version": fields["version"],
                    "install": self.install_hash(fields["install"]), "bytes": os.path.getsize(tmp_path),
                    "sha256": sha.hexdigest()}
            os.chmod(tmp_path, 0o600)
            os.replace(tmp_path, os.path.join(dd, code + ".zip"))
            jt = os.path.join(dd, code + ".json.tmp")
            with open(jt, "w") as f:
                json.dump(meta, f, sort_keys=True)
            os.chmod(jt, 0o600)
            os.replace(jt, os.path.join(dd, code + ".json"))
        return code, meta


class Reports:
    """The POST /lumen/v1/report end: headers, limits, a streamed body, the zip check, the store."""

    def __init__(self, root, salt, on_dump=None):
        self.store = Store(root, salt)
        self.limits = Limits()
        self.salt = salt
        # on_dump(meta, serial, now): a stored dump for the statistics counter (lumen_stats.Store.add_dump)
        self.on_dump = on_dump
        self.parts = threading.Lock()
        self.slots = threading.BoundedSemaphore(PART_SLOTS)
        self.open_by = {}           # upload key -> sender, memory only (no address on disk)

    def stored(self, f, meta, now):
        """A dump goes to the counter too; a counter failure never loses the stored zip."""
        if f["kind"] == DUMP_KIND and self.on_dump is not None:
            try:
                self.on_dump(meta, f["serial"], now)
            except Exception as e:      # noqa: BLE001  the zip is kept, the counter misses one
                print("dump counter: %s" % e, file=sys.stderr)

    def room(self, kind, need, now, reserved=0):
        """None when need more bytes of this kind fit (the class cap after pruning, the disk floor with the reserved
        bytes the open uploads still have to write), else the reason."""
        if kind == TEST_KIND:
            return None
        dumps = kind == DUMP_KIND
        if self.store.total_bytes(dumps) + need > Store.cap(dumps):
            self.store.prune(now, need, dumps)
            if self.store.total_bytes(dumps) + need > Store.cap(dumps):
                return "store full"
        try:
            if shutil.disk_usage(self.store.root).free - need - reserved < DISK_FLOOR:
                return "disk full"
        except OSError:
            return "disk"
        return None

    def handle(self, headers, rfile, addr, now=None):
        """(status, json body or None, extra headers)."""
        now = time.time() if now is None else now
        f = check_headers(headers)
        if isinstance(f, tuple):
            return f[0], {"error": f[1]}, {}
        wait = self.limits.check(sender(addr), self.store.install_hash(f["install"]), now, f["kind"] == DUMP_KIND)
        if wait:
            return 429, {"error": "too many reports"}, {"Retry-After": str(wait)}
        why = self.room(f["kind"], f["length"], now)
        if why:
            return 507, {"error": why}, {}
        tmp = os.path.join(self.store.root, "tmp", "up-%d-%s" % (os.getpid(), secrets.token_hex(8)))
        try:
            left = f["length"]
            with open(tmp, "wb") as out:
                while left > 0:
                    b = rfile.read(min(CHUNK, left))
                    if not b:
                        return 400, {"error": "short body"}, {}
                    out.write(b)
                    left -= len(b)
            why = check_zip(tmp, f["kind"])
            if why:
                return 415, {"error": why}, {}
            if f["kind"] == TEST_KIND:
                return 200, {"code": "LR-TEST00", "dry": True}, {}
            code, meta = self.store.keep(tmp, f, now)
            self.stored(f, meta, now)
            return 201, {"code": code, "expires": meta["expires"]}, {}
        except OSError:
            return 503, {"error": "store"}, {}
        finally:
            if os.path.exists(tmp):
                os.remove(tmp)

    def _open(self, tmp):
        """the unfinished uploads: [(meta file name, its json, its last part time)]"""
        out = []
        for n in os.listdir(tmp):
            if n.startswith("part-") and n.endswith(".json"):
                try:
                    with open(os.path.join(tmp, n)) as fh:
                        out.append((n, json.load(fh), os.path.getmtime(os.path.join(tmp, n))))
                except (OSError, ValueError):
                    continue
        return out

    @staticmethod
    def _total(o):
        return o[1].get("same", {}).get("total", 0)

    @staticmethod
    def _left(o):
        """bytes an open upload still has to write: its disk reservation"""
        return max(0, Reports._total(o) - sum(e - s for s, e in o[1].get("ranges", [])))

    def _evict(self, tmp, opened, need, now):
        """Under pressure (OPEN_MAX uploads, or OPEN_MAX_BYTES with need more) the uploads idle for OPEN_EVICT_IDLE
        go, the longest idle first, so uploads that were opened and left can not hold the room for a day."""
        ops = sorted(opened, key=lambda o: o[2])
        while ops and (len(ops) >= OPEN_MAX or sum(map(self._total, ops)) + need > OPEN_MAX_BYTES) \
                and now - ops[0][2] > OPEN_EVICT_IDLE:
            n = ops.pop(0)[0]
            for q in (n, n[:-5] + ".bin"):
                try:
                    os.remove(os.path.join(tmp, q))
                except OSError:
                    pass
        return ops

    def upload_key(self, install, upload):
        """the file name of an upload: bound to the install id, so the upload id alone opens nothing"""
        return hmac.new(self.salt, ("report-upload:%s:%s" % (install, upload)).encode(), hashlib.sha256).hexdigest()[:32]

    def handle_part(self, headers, rfile, addr, now=None):
        """One part of an upload: (status, json body, extra headers). The upload lives in tmp/ as part-<key>.bin (the
        whole zip's size, the parts written where they belong) and .json (keyed hashes of its headers and the byte
        ranges it has), both touched by every part, so the pruner drops it after OPEN_KEEP without a part. A finished
        one leaves done-<key>.json (its code) for the parts still on their way and for the state query. At most
        PART_SLOTS parts at once: a part body is held in memory."""
        now = time.time() if now is None else now
        f = check_headers(headers, part=True)
        if isinstance(f, tuple):
            return f[0], {"error": f[1]}, {}
        if not self.slots.acquire(timeout=PART_WAIT):
            return 503, {"error": "busy"}, {"Retry-After": "2"}
        try:
            return self._part(f, rfile, sender(addr), now)
        finally:
            self.slots.release()

    def _part(self, f, rfile, who, now):
        body = rfile.read(f["length"]) if f["length"] else b""
        if len(body) != f["length"]:
            return 400, {"error": "short body"}, {}
        key = self.upload_key(f["install"], f["upload"])
        tmp = os.path.join(self.store.root, "tmp")
        data, mp = os.path.join(tmp, "part-%s.bin" % key), os.path.join(tmp, "part-%s.json" % key)
        dp = os.path.join(tmp, "done-%s.json" % key)
        # what must stay the same over the parts, as keyed hashes: no install id or serial in clear on disk
        same = {k: f[k] for k in ("version", "model", "kind", "total", "sha256")}
        same.update(install=self.store.install_hash(f["install"]),
                    serial=hmac.new(self.salt, b"report-serial:" + f["serial"].encode(), hashlib.sha256).hexdigest()[:32]
                    if f["serial"] else "")
        finished = None
        try:
            with self.parts:
                if os.path.exists(dp):
                    with open(dp) as fh:
                        done = json.load(fh)
                    if done.get("sha256") != f["sha256"]:
                        return 400, {"error": "upload changed"}, {}
                    os.utime(dp, (now, now))
                    return 200, {"code": done["code"], "expires": done["expires"]}, {}
                if os.path.exists(mp):
                    with open(mp) as fh:
                        st = json.load(fh)
                    if st.get("same") != same:
                        return 400, {"error": "upload changed"}, {}
                    ranges = st["ranges"]
                elif f["query"]:
                    return 404, {"error": "no upload"}, {}
                else:
                    opened = self._evict(tmp, self._open(tmp), f["total"], now)
                    if len(opened) >= OPEN_MAX:
                        return 507, {"error": "busy"}, {}
                    if sum(map(self._total, opened)) + f["total"] > OPEN_MAX_BYTES:
                        return 507, {"error": "busy"}, {}
                    keys = {o[0][5:-5] for o in opened}
                    self.open_by = {k: v for k, v in self.open_by.items() if k in keys}
                    if sum(1 for v in self.open_by.values() if v == who) >= OPEN_PER_SENDER:
                        return 429, {"error": "too many open uploads"}, {"Retry-After": "600"}
                    why = self.room(f["kind"], f["total"], now, sum(map(self._left, opened)))
                    if why:
                        return 507, {"error": why}, {}
                    wait = self.limits.check(who, self.store.install_hash(f["install"]), now, f["kind"] == DUMP_KIND)
                    if wait:
                        return 429, {"error": "too many reports"}, {"Retry-After": str(wait)}
                    with open(data, "wb") as out:
                        out.truncate(f["total"])
                    os.chmod(data, 0o600)
                    self.open_by[key] = who
                    ranges = []
                if f["length"]:
                    nxt = merge(ranges, f["offset"], f["offset"] + f["length"])
                    if len(nxt) > RANGES_MAX:
                        return 409, {"have": ranges[0][1] if ranges and ranges[0][0] == 0 else 0}, {}
                    with open(data, "r+b") as out:
                        out.seek(f["offset"])
                        out.write(body)
                    ranges = nxt
                have = ranges[0][1] if ranges and ranges[0][0] == 0 else 0
                if have < f["total"]:
                    t = mp + ".tmp"
                    with open(t, "w") as fh:
                        json.dump({"same": same, "ranges": ranges}, fh, sort_keys=True)
                    os.chmod(t, 0o600)
                    os.replace(t, mp)
                    os.utime(mp, (now, now))
                    os.utime(data, (now, now))
                    return 202, {"have": have, "got": sum(e - s for s, e in ranges)}, {}
                sha = hashlib.sha256()
                with open(data, "rb") as fh:
                    for b in iter(lambda: fh.read(CHUNK), b""):
                        sha.update(b)
                if os.path.exists(mp):
                    os.remove(mp)
                if sha.hexdigest() != f["sha256"]:
                    return 400, {"error": "checksum"}, {}
                why = check_zip(data, f["kind"])
                if why:
                    return 415, {"error": why}, {}
                if f["kind"] == TEST_KIND:
                    return 200, {"code": "LR-TEST00", "dry": True}, {}
                code, meta = self.store.keep(data, f, now)
                t = dp + ".tmp"
                with open(t, "w") as fh:
                    json.dump({"code": code, "expires": meta["expires"], "sha256": f["sha256"]}, fh)
                os.chmod(t, 0o600)
                os.replace(t, dp)
                finished = (code, meta)
            self.stored(f, finished[1], now)
            return 201, {"code": finished[0], "expires": finished[1]["expires"]}, {}
        except OSError:
            return 503, {"error": "store"}, {}
        finally:
            if not os.path.exists(mp) and os.path.exists(data):
                os.remove(data)

    def start_pruner(self, every=HOUR):
        def loop():
            while True:
                try:
                    self.store.prune(time.time())
                except OSError:
                    pass
                time.sleep(every)
        threading.Thread(target=loop, name="report-prune", daemon=True).start()


# ---------------------------------------------------------------------------------------------- reading side (ssh)
def summary_of(zip_path, limit=12000):
    try:
        with zipfile.ZipFile(zip_path) as z:
            names = [i.filename + " (%d)" % i.file_size for i in z.infolist()]
            s = ""
            if "summary.txt" in z.namelist():
                with z.open("summary.txt") as fh:        # a capped read: a packed bomb is never unpacked whole
                    s = fh.read(limit).decode("utf-8", "replace")
        return names, s
    except (zipfile.BadZipFile, OSError, KeyError, NotImplementedError, RuntimeError):
        return [], ""


def cli(argv, root):
    store = Store(root, b"")
    cmd = argv[0] if argv else "list"
    if cmd == "list":
        days = int(argv[1]) if len(argv) > 1 else DUMP_KEEP_DAYS
        cut = time.time() - days * DAY
        rows = [m for m, _ in store.entries() if m.get("received_ts", 0) >= cut]
        print("%-10s  %-20s  %-6s  %-14s  %-24s  %9s" % ("code", "received (UTC)", "kind", "model", "version", "bytes"))
        for m in rows:
            print("%-10s  %-20s  %-6s  %-14s  %-24s  %9d" % (m["code"], m["received"], m["kind"], m["model"],
                                                            m["version"], m["bytes"]))
        print("%d reports, %.1f MB in the store (dumps %.1f MB)" % (len(rows), store.total_bytes() / 1e6,
                                                                  store.total_bytes(True) / 1e6))
        return 0
    if cmd in ("show", "cat", "path", "rm") and len(argv) == 2:
        z = store.find(argv[1])
        if not z:
            print("no report %s" % argv[1], file=sys.stderr)
            return 1
        if cmd == "path":
            print(z)
        elif cmd == "cat":
            with open(z, "rb") as f:
                shutil.copyfileobj(f, sys.stdout.buffer)
        elif cmd == "rm":
            os.remove(z)
            os.remove(z[:-4] + ".json")
            print("deleted %s" % argv[1].upper())
        else:
            with open(z[:-4] + ".json") as f:
                print(json.dumps(json.load(f), indent=1, sort_keys=True))
            names, s = summary_of(z)
            print("\nfiles:\n  " + "\n  ".join(names) + "\n\nsummary.txt:\n" + s)
        return 0
    if cmd == "prune":
        print("removed %d" % store.prune(time.time()))
        return 0
    print(__doc__.split("There is no way")[1], file=sys.stderr)
    return 2


if __name__ == "__main__":
    state = os.environ.get("STATE_DIRECTORY", "/var/lib/private/lumen-stats")
    sys.exit(cli(sys.argv[1:], os.environ.get("LUMEN_REPORTS", os.path.join(state, "reports"))))
