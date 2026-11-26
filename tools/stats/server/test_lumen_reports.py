#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Host test of lumen_reports.py (the problem report upload end): python3 tools/stats/server/test_lumen_reports.py
Headers, limits, size and zip checks, the store (codes, metadata without an address, retention, size cap), the ssh
commands, and the HTTP route through lumen_stats.make_handler with a real server."""
import io
import json
import os
import shutil
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import zipfile
from contextlib import redirect_stdout
from http.server import ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lumen_reports as lr  # noqa: E402
import lumen_stats as ls  # noqa: E402

INSTALL = "0123456789abcdef01234567"
NOW = 1_794_000_000.0


def zip_bytes(files=None):
    b = io.BytesIO()
    with zipfile.ZipFile(b, "w", zipfile.ZIP_DEFLATED) as z:
        for n, t in (files or {"summary.txt": "Lumen OS problem report (format 2)\nkind: crash\n"}).items():
            z.writestr(n, t)
    return b.getvalue()


def headers(n, **kw):
    h = {"X-Lumen-Report": "1", "Content-Type": "application/zip", "X-Lumen-Install": INSTALL,
         "X-Lumen-Version": "1.0.10", "X-Lumen-Model": "g0082", "X-Lumen-Kind": "crash", "Content-Length": str(n)}
    for k, v in kw.items():
        k = k.replace("_", "-")
        if v is None:
            h.pop(k, None)
        else:
            h[k] = v
    return h


class T(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, "reports")
        self.r = lr.Reports(self.root, b"salt-for-tests")

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def post(self, body, addr=None, now=NOW, **kw):
        if addr is None:                                                    # a fresh address: no limit in the way
            self.n = getattr(self, "n", 0) + 1
            addr = "198.51.100.%d" % self.n
        return self.r.handle(headers(len(body), **kw), io.BytesIO(body), addr, now)

    def test_limits_are_the_documented_ones(self):
        self.assertEqual((lr.MAX_BYTES, lr.KEEP_DAYS, lr.STORE_MAX_BYTES), (5 * 1024 * 1024, 30, 2 * 1024 ** 3))
        self.assertEqual((lr.RATE_HOUR, lr.RATE_DAY, lr.INSTALL_DAY, lr.GLOBAL_DAY), (6, 20, 10, 300))
        self.assertGreater(lr.MAX_BYTES, 5_000_000)            # the client's manual cap (ReportConfig.MAX_ZIP_BYTES)
        self.assertEqual((lr.DUMP_MAX_BYTES, lr.DUMP_KEEP_DAYS, lr.DUMP_STORE_MAX_BYTES, lr.PART_MAX),
                         (200 * 1024 * 1024, 90, 6 * 1024 ** 3, 1024 * 1024))
        self.assertEqual((lr.DUMP_DAY, lr.DUMP_INSTALL_DAY, lr.OPEN_KEEP, lr.OPEN_MAX_BYTES, lr.DISK_FLOOR),
                         (40, 3, 86400, 2 * 1024 ** 3, 3 * 1024 ** 3))
        self.assertEqual((lr.OPEN_PER_SENDER, lr.OPEN_EVICT_IDLE, lr.PART_SLOTS), (3, 3600, 12))

    def test_upload_stored(self):
        body = zip_bytes()
        code, ans, _ = self.post(body)
        self.assertEqual(code, 201)
        self.assertRegex(ans["code"], r"^LR-[0-9A-HJKMNP-TV-Z]{6}$")
        self.assertEqual(ans["expires"], lr.utc(NOW + 30 * 86400))
        z = self.r.store.find(ans["code"])
        self.assertTrue(z and open(z, "rb").read() == body)
        self.assertEqual(self.r.store.find(ans["code"].lower()), z)
        meta = json.load(open(z[:-4] + ".json"))
        self.assertEqual((meta["kind"], meta["model"], meta["version"], meta["bytes"]), ("crash", "g0082", "1.0.10", len(body)))
        self.assertEqual(meta["install"], self.r.store.install_hash(INSTALL))
        self.assertNotEqual(meta["install"], INSTALL)
        self.assertNotIn("198.51.100.", open(z[:-4] + ".json").read())
        self.assertEqual(oct(os.stat(z).st_mode & 0o777), "0o600")
        self.assertEqual(os.listdir(os.path.join(self.root, "tmp")), [])

    def part(self, data, off, n, addr="192.0.2.50", now=NOW, upload="a" * 32, **kw):
        """One part of an upload of data: the bytes off..off+n."""
        body = data[off:off + n]
        import hashlib
        f = dict(Content_Type="application/octet-stream", X_Lumen_Upload=upload, X_Lumen_Offset=str(off),
                 X_Lumen_Total=str(len(data)), X_Lumen_Sha256=hashlib.sha256(data).hexdigest())
        f.update(kw)
        return self.r.handle_part(headers(len(body), **f), io.BytesIO(body), addr, now)

    def big_zip(self):
        import random
        rnd = random.Random(7)
        return zip_bytes({"summary.txt": "Lumen Installer system info\n",
                          "noise.bin": bytes(rnd.getrandbits(8) for _ in range(40000))})

    def test_dump_kind_and_serial(self):
        got = []
        self.r.on_dump = lambda meta, serial, now: got.append((meta["code"], meta["kind"], meta["model"], serial))
        body = zip_bytes()
        code, ans, _ = self.post(body, X_Lumen_Kind="dump", X_Lumen_Model="g0111", X_Lumen_Version="6.17.12",
                                 X_Lumen_Serial="CZW1234567MK")
        self.assertEqual(code, 201)
        self.assertEqual(got, [(ans["code"], "dump", "g0111", "CZW1234567MK")])
        meta = open(self.r.store.find(ans["code"])[:-4] + ".json").read()
        self.assertNotIn("CZW1234567MK", meta)                              # the serial is never stored here
        self.assertEqual(self.post(body, X_Lumen_Serial="CZW1234567MK")[0], 400)  # a serial only with a dump
        self.assertEqual(self.post(body, X_Lumen_Kind="dump", X_Lumen_Serial="bad serial!")[0], 400)
        self.assertEqual(self.post(body, X_Lumen_Kind="crash")[0], 201)
        self.assertEqual(len(got), 1)                                       # only dumps are counted
        self.r.on_dump = lambda meta, serial, now: 1 / 0
        self.assertEqual(self.post(body, X_Lumen_Kind="dump")[0], 201)       # a counter failure keeps the dump

    def test_parts(self):
        data, step = self.big_zip(), 12 * 1024
        self.assertGreater(len(data), 3 * step)
        offs = list(range(0, len(data), step))
        for o in offs[:-1]:
            code, ans, _ = self.part(data, o, step, X_Lumen_Kind="dump")
            self.assertEqual((code, ans), (202, {"have": o + step, "got": o + step}))
        self.assertEqual(self.part(data, 0, step, X_Lumen_Kind="dump")[:2], (202, {"have": offs[-1], "got": offs[-1]}))
        self.assertEqual(self.part(data, offs[-1], step, X_Lumen_Kind="crash")[:2], (400, {"error": "upload changed"}))
        code, ans, _ = self.part(data, offs[-1], step, X_Lumen_Kind="dump")
        self.assertEqual(code, 201)
        z = self.r.store.find(ans["code"])
        self.assertEqual(open(z, "rb").read(), data)
        meta = json.load(open(z[:-4] + ".json"))
        self.assertEqual(meta["kind"], "dump")
        self.assertEqual(ans["expires"], lr.utc(NOW + lr.DUMP_KEEP_DAYS * 86400))           # dumps stay longer
        self.assertEqual([n for n in os.listdir(os.path.join(self.root, "tmp")) if not n.startswith("done-")], [])
        # a slower part of the finished upload and the state query get the same code, nothing new is stored
        self.assertEqual(self.part(data, offs[1], step, X_Lumen_Kind="dump")[:2], (200, {"code": ans["code"], "expires": ans["expires"]}))
        self.assertEqual(self.query(data, X_Lumen_Kind="dump")[:2], (200, {"code": ans["code"], "expires": ans["expires"]}))
        self.assertEqual(len(self.r.store.entries()), 1)
        # an upload the server does not have: the query says so, a part at any offset starts it
        self.assertEqual(self.query(data, upload="b" * 32)[:2], (404, {"error": "no upload"}))
        self.assertEqual(self.part(data, step, step, upload="b" * 32)[:2], (202, {"have": 0, "got": step}))

    def query(self, data, upload="a" * 32, addr="192.0.2.50", now=NOW, **kw):
        import hashlib
        f = dict(Content_Type="application/octet-stream", X_Lumen_Upload=upload, X_Lumen_Offset="0", X_Lumen_Query="1",
                 X_Lumen_Total=str(len(data)), X_Lumen_Sha256=hashlib.sha256(data).hexdigest())
        f.update(kw)
        return self.r.handle_part(headers(0, **f), io.BytesIO(b""), addr, now)

    def test_parts_any_order_and_resume(self):
        """parallel parts come in any order and twice; after a break the query tells where to go on"""
        data, step = self.big_zip(), 12 * 1024
        offs = list(range(0, len(data), step))
        order = offs[1:-1][::-1] + [offs[1]]                 # backwards, one twice, the first and the last missing
        for o in order:
            code, ans, _ = self.part(data, o, step, upload="9" * 32, X_Lumen_Kind="dump")
            self.assertEqual((code, ans["have"]), (202, 0))
        self.assertEqual(ans["got"], offs[-1] - step)
        self.assertEqual(self.query(data, upload="9" * 32, X_Lumen_Kind="dump")[:2], (202, {"have": 0, "got": offs[-1] - step}))
        self.assertEqual(self.part(data, 0, step, upload="9" * 32, X_Lumen_Kind="dump")[:2], (202, {"have": offs[-1], "got": offs[-1]}))
        code, ans, _ = self.part(data, offs[-1], step, upload="9" * 32, X_Lumen_Kind="dump")
        self.assertEqual(code, 201)
        self.assertEqual(open(self.r.store.find(ans["code"]), "rb").read(), data)
        # parts of different sizes overlap: still one byte image (12 KiB on a choked network, larger elsewhere)
        for o, n in ((0, 2 * step), (step, step), (step + 100, step), (2 * step, len(data))):
            code, ans, _ = self.part(data, o, n, upload="8" * 32)
        self.assertEqual(code, 201)
        self.assertEqual(open(self.r.store.find(ans["code"]), "rb").read(), data)
        # a query is only a query: no body, no new upload
        self.assertEqual(self.r.handle_part(headers(5, Content_Type="application/octet-stream", X_Lumen_Upload="7" * 32,
                                                    X_Lumen_Offset="0", X_Lumen_Total="10", X_Lumen_Query="1",
                                                    X_Lumen_Sha256="0" * 64), io.BytesIO(b"12345"), "a", NOW)[0], 400)
        # too many separate ranges: send from the complete start on
        lr_max = lr.RANGES_MAX
        try:
            lr.RANGES_MAX = 2
            for o in (1000, 3000):
                self.assertEqual(self.part(data, o, 100, upload="6" * 32)[0], 202)
            self.assertEqual(self.part(data, 5000, 100, upload="6" * 32)[:2], (409, {"have": 0}))
            self.assertEqual(self.part(data, 1100, 1900, upload="6" * 32)[:2], (202, {"have": 0, "got": 2100}))
        finally:
            lr.RANGES_MAX = lr_max

    def test_parts_checks(self):
        data, step = self.big_zip(), 12 * 1024
        self.assertEqual(self.part(bytes(lr.PART_MAX + 10), 0, lr.PART_MAX + 1)[0], 413)
        self.assertEqual(self.part(data, 0, step, Content_Type="application/zip")[0], 415)
        for kw in ({"X_Lumen_Upload": "xyz"}, {"X_Lumen_Sha256": "0" * 63}, {"X_Lumen_Offset": "x"}):
            h = dict(kw)
            self.assertEqual(self.part(data, 0, step, **h)[0], 400, kw)
        self.assertEqual(self.part(data, 0, step, X_Lumen_Total=str(step - 1))[0], 400)        # past the end
        self.assertEqual(self.part(data, 0, step, X_Lumen_Total=str(lr.MAX_BYTES + 1))[0], 413)  # a problem report
        self.assertEqual(self.part(data, 0, step, upload="1" * 32, X_Lumen_Kind="dump",
                                   X_Lumen_Total=str(lr.MAX_BYTES + 1))[0], 202)                 # a dump may be large
        self.assertEqual(self.part(data, 0, step, upload="2" * 32, X_Lumen_Kind="dump",
                                   X_Lumen_Total=str(lr.DUMP_MAX_BYTES + 1))[0], 413)
        # a wrong sha256 of the whole: refused at the end, nothing kept
        for o in range(0, len(data), step):
            code, ans, _ = self.part(data, o, step, upload="c" * 32, X_Lumen_Sha256="0" * 64)
        self.assertEqual((code, ans), (400, {"error": "checksum"}))
        self.assertNotIn("part-", " ".join(n for n in os.listdir(os.path.join(self.root, "tmp")) if "c" * 4 in n))
        # the test kind in parts: checked, nothing stored
        for o in range(0, len(data), step):
            code, ans, _ = self.part(data, o, step, upload="d" * 32, X_Lumen_Kind="test")
        self.assertEqual((code, ans), (200, {"code": "LR-TEST00", "dry": True}))
        # not a zip
        junk = b"x" * 30000
        for o in range(0, len(junk), step):
            code, ans, _ = self.part(junk, o, step, upload="e" * 32)
        self.assertEqual(code, 415)
        self.assertEqual(self.r.store.entries(), [])

    def test_parts_limits_and_prune(self):
        data, step = self.big_zip(), 12 * 1024
        # the limits count an upload once: six uploads of several parts from one address, the seventh waits
        for i in range(lr.RATE_HOUR):
            for o in range(0, len(data), step):
                code, _, _ = self.part(data, o, step, upload="%032x" % i, now=NOW + i, X_Lumen_Install="%024x" % i)
            self.assertEqual(code, 201)
        code, _, extra = self.part(data, 0, step, upload="%032x" % 99, now=NOW + 10, X_Lumen_Install="%024x" % 99)
        self.assertEqual(code, 429)
        self.assertIn("Retry-After", extra)
        # an upload with no part for OPEN_KEEP is dropped by the pruner, an hour or a night is not
        self.assertEqual(self.part(data, 0, step, addr="203.0.113.7", upload="f" * 32)[0], 202)
        tmp = os.path.join(self.root, "tmp")
        for n in os.listdir(tmp):
            os.utime(os.path.join(tmp, n), (NOW - 10 * 3600, NOW - 10 * 3600))
        self.r.store.prune(NOW)
        self.assertEqual(self.query(data, upload="f" * 32, addr="203.0.113.7")[:2], (202, {"have": step, "got": step}))
        for n in os.listdir(tmp):
            os.utime(os.path.join(tmp, n), (NOW - lr.OPEN_KEEP - 60, NOW - lr.OPEN_KEEP - 60))
        self.r.store.prune(NOW)
        self.assertEqual(os.listdir(tmp), [])
        self.assertEqual(self.query(data, upload="f" * 32, addr="203.0.113.7")[:2], (404, {"error": "no upload"}))
        # too many open uploads at once, or too many bytes in them
        lr_open, lr_bytes = lr.OPEN_MAX, lr.OPEN_MAX_BYTES
        try:
            lr.OPEN_MAX = 2
            self.assertEqual(self.part(data, 0, step, addr="203.0.113.8", upload="1" * 32)[0], 202)
            self.assertEqual(self.part(data, 0, step, addr="203.0.113.8", upload="2" * 32)[0], 202)
            self.assertEqual(self.part(data, 0, step, addr="203.0.113.8", upload="3" * 32)[:2], (507, {"error": "busy"}))
            lr.OPEN_MAX = 200
            lr.OPEN_MAX_BYTES = 3 * len(data) - 1
            self.assertEqual(self.part(data, 0, step, addr="203.0.113.9", upload="4" * 32)[:2], (507, {"error": "busy"}))
        finally:
            lr.OPEN_MAX, lr.OPEN_MAX_BYTES = lr_open, lr_bytes

    def test_dump_limits_and_caps(self):
        data, step = self.big_zip(), 12 * 1024
        # dumps a day per install: the fourth waits, a problem report of the same install still goes
        for i in range(lr.DUMP_INSTALL_DAY):
            self.assertEqual(self.part(data, 0, step, addr="198.18.0.%d" % i, upload="%032x" % (100 + i), now=NOW + i,
                                       X_Lumen_Kind="dump")[0], 202)
        code, _, extra = self.part(data, 0, step, addr="198.18.0.9", upload="%032x" % 200, now=NOW + 5, X_Lumen_Kind="dump")
        self.assertEqual(code, 429)
        self.assertEqual(self.part(data, 0, step, addr="198.18.0.9", upload="%032x" % 201, now=NOW + 5)[0], 202)
        # the dump store has its own cap: an old dump goes for a new one, problem reports stay
        lr_cap = lr.DUMP_STORE_MAX_BYTES
        try:
            body = zip_bytes()
            self.assertEqual(self.post(body, now=NOW - 10)[0], 201)
            first = self.post(body, now=NOW - 5, X_Lumen_Kind="dump", X_Lumen_Install="%024x" % 7)[1]["code"]
            lr.DUMP_STORE_MAX_BYTES = self.r.store.total_bytes(True) + 10
            second = self.post(body, now=NOW, X_Lumen_Kind="dump", X_Lumen_Install="%024x" % 8)[1]["code"]
            kinds = sorted(m["kind"] for m, _ in self.r.store.entries())
            self.assertEqual((kinds, self.r.store.find(first), bool(self.r.store.find(second))), (["crash", "dump"], None, True))
            lr.DUMP_STORE_MAX_BYTES = 10
            self.assertEqual(self.part(data, 0, step, addr="198.18.1.1", upload="%032x" % 300, X_Lumen_Kind="dump")[:2],
                             (507, {"error": "store full"}))
        finally:
            lr.DUMP_STORE_MAX_BYTES = lr_cap
        # dumps are kept DUMP_KEEP_DAYS, problem reports KEEP_DAYS
        self.assertEqual(self.post(zip_bytes(), now=NOW, X_Lumen_Kind="dump", X_Lumen_Install="%024x" % 9)[0], 201)
        self.r.store.prune(NOW + (lr.KEEP_DAYS + 1) * 86400)
        self.assertEqual([m["kind"] for m, _ in self.r.store.entries()], ["dump"])
        self.r.store.prune(NOW + (lr.DUMP_KEEP_DAYS + 1) * 86400)
        self.assertEqual(self.r.store.entries(), [])
        # never past the disk floor
        real = lr.shutil.disk_usage
        try:
            lr.shutil.disk_usage = lambda p: real(p)._replace(free=lr.DISK_FLOOR + len(data) - 1)
            self.assertEqual(self.part(data, 0, step, addr="198.18.1.2", upload="%032x" % 301, X_Lumen_Kind="dump")[:2],
                             (507, {"error": "disk full"}))
        finally:
            lr.shutil.disk_usage = real

    def test_upload_bound_to_install(self):
        """the upload id alone opens nothing: another install id with it is a separate upload, never this one"""
        data, step = self.big_zip(), 12 * 1024
        self.assertEqual(self.part(data, 0, step, upload="5" * 32, X_Lumen_Kind="dump", X_Lumen_Serial="CZW1234567MK")[0], 202)
        other = "%024x" % 77
        self.assertEqual(self.query(data, upload="5" * 32, X_Lumen_Install=other, X_Lumen_Kind="dump",
                                    X_Lumen_Serial="CZW1234567MK")[:2], (404, {"error": "no upload"}))
        # a stranger's part with the same upload id lands in an upload of its own, the maintainer's bytes stay
        self.assertEqual(self.part(b"\0" * len(data), step, step, addr="203.0.113.66", upload="5" * 32,
                                   X_Lumen_Install=other, X_Lumen_Sha256=lr.hashlib.sha256(data).hexdigest(),
                                   X_Lumen_Kind="dump")[:2], (202, {"have": 0, "got": step}))
        self.assertEqual(self.query(data, upload="5" * 32, X_Lumen_Kind="dump", X_Lumen_Serial="CZW1234567MK")[:2],
                         (202, {"have": step, "got": step}))
        # no install id and no serial in clear in the open upload's files
        tmp = os.path.join(self.root, "tmp")
        text = "".join(open(os.path.join(tmp, n), errors="replace").read() for n in os.listdir(tmp) if n.endswith(".json"))
        self.assertTrue(text)
        self.assertNotIn(INSTALL, text)
        self.assertNotIn("CZW1234567MK", text)
        self.assertNotIn("5" * 32, " ".join(os.listdir(tmp)))
        for o in range(step, len(data), step):
            code, ans, _ = self.part(data, o, step, upload="5" * 32, X_Lumen_Kind="dump", X_Lumen_Serial="CZW1234567MK")
        self.assertEqual(code, 201)
        self.assertEqual(open(self.r.store.find(ans["code"]), "rb").read(), data)
        # the finished upload's code goes only to its own install id
        self.assertEqual(self.query(data, upload="5" * 32, X_Lumen_Install="%024x" % 78, X_Lumen_Kind="dump")[0], 404)
        self.assertEqual(oct(os.stat(self.root).st_mode & 0o777), "0o700")
        self.assertEqual(oct(os.stat(tmp).st_mode & 0o777), "0o700")

    def test_sender(self):
        self.assertEqual(lr.sender("2001:db8:1:2:aaaa::1"), lr.sender("2001:db8:1:2:bbbb::9"))
        self.assertEqual(lr.sender("2001:db8:1:2:aaaa::1"), "2001:db8:1:2::/64")
        self.assertNotEqual(lr.sender("2001:db8:1:2::1"), lr.sender("2001:db8:1:3::1"))
        self.assertEqual(lr.sender(" 192.0.2.7 "), "192.0.2.7")
        self.assertEqual(lr.sender("::ffff:192.0.2.7"), "192.0.2.7")
        self.assertEqual(lr.sender("not an address"), "not an address")
        # the address limits count a /64 once
        body = zip_bytes()
        for i in range(lr.RATE_HOUR):
            self.assertEqual(self.post(body, addr="2001:db8:9:9::%x" % (i + 1), X_Lumen_Install="%024x" % i)[0], 201)
        self.assertEqual(self.post(body, addr="2001:db8:9:9::ff", X_Lumen_Install="%024x" % 50)[0], 429)

    def test_open_per_sender_and_evict(self):
        data, step = self.big_zip(), 12 * 1024
        for i in range(lr.OPEN_PER_SENDER):
            self.assertEqual(self.part(data, 0, step, addr="2001:db8:5:5::%x" % (i + 1), upload="%032x" % (400 + i),
                                       X_Lumen_Install="%024x" % (400 + i))[0], 202)
        code, ans, extra = self.part(data, 0, step, addr="2001:db8:5:5::99", upload="%032x" % 499,
                                     X_Lumen_Install="%024x" % 499)
        self.assertEqual((code, ans), (429, {"error": "too many open uploads"}))
        self.assertIn("Retry-After", extra)
        self.assertEqual(self.part(data, 0, step, addr="198.51.100.200", upload="%032x" % 498,
                                   X_Lumen_Install="%024x" % 498)[0], 202)        # another sender is not held up
        # a finished upload frees its place
        for o in range(step, len(data), step):
            code, _, _ = self.part(data, o, step, addr="2001:db8:5:5::1", upload="%032x" % 400, X_Lumen_Install="%024x" % 400)
        self.assertEqual(code, 201)
        self.assertEqual(self.part(data, 0, step, addr="2001:db8:5:5::99", upload="%032x" % 497,
                                   X_Lumen_Install="%024x" % 497)[0], 202)
        # full: uploads idle for OPEN_EVICT_IDLE make room, the longest idle first; fresh ones never go
        lr_open = lr.OPEN_MAX
        tmp = os.path.join(self.root, "tmp")
        try:
            lr.OPEN_MAX = len(self.r._open(tmp))
            self.assertEqual(self.part(data, 0, step, addr="198.51.100.201", upload="%032x" % 496,
                                       X_Lumen_Install="%024x" % 496)[:2], (507, {"error": "busy"}))
            idle = sorted(n for n in os.listdir(tmp) if n.startswith("part-") and n.endswith(".json"))[0]
            for n in (idle, idle[:-5] + ".bin"):
                os.utime(os.path.join(tmp, n), (NOW - lr.OPEN_EVICT_IDLE - 60, NOW - lr.OPEN_EVICT_IDLE - 60))
            self.assertEqual(self.part(data, 0, step, addr="198.51.100.201", upload="%032x" % 496,
                                       X_Lumen_Install="%024x" % 496)[0], 202)
            self.assertFalse(os.path.exists(os.path.join(tmp, idle)) or os.path.exists(os.path.join(tmp, idle[:-5] + ".bin")))
            self.assertEqual(len(self.r._open(tmp)), lr.OPEN_MAX)
        finally:
            lr.OPEN_MAX = lr_open

    def test_open_uploads_reserve_disk(self):
        """the disk floor counts the bytes the open uploads still have to write"""
        data, step = self.big_zip(), 12 * 1024
        self.assertEqual(self.part(data, 0, step, addr="198.51.100.210", upload="%032x" % 600)[0], 202)
        left = len(data) - step
        real = lr.shutil.disk_usage
        try:
            lr.shutil.disk_usage = lambda p: real(p)._replace(free=lr.DISK_FLOOR + len(data) + left - 1)
            self.assertEqual(self.part(data, 0, step, addr="198.51.100.211", upload="%032x" % 601,
                                       X_Lumen_Install="%024x" % 601)[:2], (507, {"error": "disk full"}))
            lr.shutil.disk_usage = lambda p: real(p)._replace(free=lr.DISK_FLOOR + len(data) + left)
            self.assertEqual(self.part(data, 0, step, addr="198.51.100.211", upload="%032x" % 601,
                                       X_Lumen_Install="%024x" % 601)[0], 202)
        finally:
            lr.shutil.disk_usage = real

    def test_part_slots(self):
        data, step = self.big_zip(), 12 * 1024
        lr_wait = lr.PART_WAIT
        try:
            lr.PART_WAIT = 0.05
            for _ in range(lr.PART_SLOTS):
                self.r.slots.acquire()
            code, ans, extra = self.part(data, 0, step, upload="%032x" % 700)
            self.assertEqual((code, ans, extra), (503, {"error": "busy"}, {"Retry-After": "2"}))
            self.r.slots.release()
            self.assertEqual(self.part(data, 0, step, upload="%032x" % 700)[0], 202)
        finally:
            lr.PART_WAIT = lr_wait

    def test_summary_read_is_capped(self):
        z = os.path.join(self.tmp, "big.zip")
        with open(z, "wb") as f:
            f.write(zip_bytes({"summary.txt": "x" * 100000}))
        names, s = lr.summary_of(z, limit=1000)
        self.assertEqual((len(s), names), (1000, ["summary.txt (100000)"]))

    def test_headers(self):
        body = zip_bytes()
        for kw, want in [({"X_Lumen_Report": None}, 400), ({"X_Lumen_Report": "2"}, 400),
                         ({"Content_Type": "application/json"}, 415), ({"X_Lumen_Install": "XYZ"}, 400),
                         ({"X_Lumen_Install": "0123abcd"}, 400), ({"X_Lumen_Install": "A" * 24}, 400),
                         ({"X_Lumen_Version": "1.0 beta;"}, 400), ({"X_Lumen_Model": "G0082"}, 400),
                         ({"X_Lumen_Kind": "spam"}, 400), ({"Content_Length": None}, 411),
                         ({"Content_Length": "x"}, 400), ({"Content_Length": "0"}, 400)]:
            self.assertEqual(self.post(body, **kw)[0], want, kw)
        self.assertEqual(self.post(body, X_Lumen_Kind=None)[0], 201)           # no kind = manual
        self.assertEqual(self.post(body, X_Lumen_Version=None, X_Lumen_Model=None)[0], 201)

    def test_size_and_zip(self):
        big = b"PK\x03\x04" + b"\0" * lr.MAX_BYTES
        self.assertEqual(self.r.handle(headers(len(big)), io.BytesIO(b""), "a", NOW)[0], 413)
        self.assertEqual(self.post(b"not a zip at all")[0], 415)
        self.assertEqual(self.post(b"PK\x03\x04broken")[0], 415)
        self.assertEqual(self.post(b"MZ" + zip_bytes())[0], 415)             # zipfile alone reads a prefixed zip
        self.assertEqual(self.post(zip_bytes({"../evil": "x"}))[0], 415)
        self.assertEqual(self.post(zip_bytes({"/abs": "x"}))[0], 415)
        many = {"f%d.txt" % i: "" for i in range(lr.ZIP_MAX_ENTRIES + 1)}
        self.assertEqual(self.post(zip_bytes(many))[0], 415)
        bomb = zip_bytes({"zero.txt": "\0" * (lr.ZIP_MAX_UNPACKED + 1)})
        self.assertLess(len(bomb), lr.MAX_BYTES)
        self.assertEqual(self.post(bomb)[0], 415)
        body = zip_bytes()
        self.assertEqual(self.r.handle(headers(len(body) + 10), io.BytesIO(body), "a", NOW)[0], 400)   # short body
        self.assertEqual(self.r.store.entries(), [])
        self.assertEqual(os.listdir(os.path.join(self.root, "tmp")), [])

    def test_test_kind_stores_nothing(self):
        code, ans, _ = self.post(zip_bytes(), X_Lumen_Kind="test")
        self.assertEqual((code, ans), (200, {"code": "LR-TEST00", "dry": True}))
        self.assertEqual(self.r.store.entries(), [])
        self.assertEqual(self.post(b"junk", X_Lumen_Kind="test")[0], 415)

    def test_rate_limits(self):
        body = zip_bytes()
        for i in range(lr.RATE_HOUR):
            self.assertEqual(self.post(body, addr="192.0.2.1", now=NOW + i, X_Lumen_Install="%024x" % i)[0], 201)
        code, _, extra = self.post(body, addr="192.0.2.1", now=NOW + 10, X_Lumen_Install="%024x" % 99)
        self.assertEqual(code, 429)
        self.assertGreater(int(extra["Retry-After"]), 3000)
        self.assertEqual(self.post(body, addr="203.0.113.1", now=NOW + 10, X_Lumen_Install="%024x" % 98)[0], 201)
        self.assertEqual(self.post(body, addr="192.0.2.1", now=NOW + 3601, X_Lumen_Install="%024x" % 97)[0], 201)

    def test_rate_per_day_and_install(self):
        body = zip_bytes()
        lim = lr.Limits()
        t = NOW
        for i in range(lr.RATE_DAY):
            self.assertEqual(lim.check("a", "i%d" % i, t), 0)
            t += 3600 / lr.RATE_HOUR + 1
        self.assertGreater(lim.check("a", "new", t), 0)                     # the address's day is full
        self.assertEqual(lim.check("a", "new", NOW + 86400 + 1), 0)
        lim = lr.Limits()
        for i in range(lr.INSTALL_DAY):
            self.assertEqual(lim.check("addr%d" % i, "same", NOW + i), 0)
        self.assertGreater(lim.check("other", "same", NOW + 20), 0)         # one projector, many addresses
        lim = lr.Limits()
        for i in range(lr.GLOBAL_DAY):
            self.assertEqual(lim.check("a%d" % i, "i%d" % i, NOW + i), 0)
        self.assertGreater(lim.check("fresh", "fresh", NOW + 400), 0)       # all reports of a day
        self.assertIsNotNone(body)

    def test_retention_and_cap(self):
        body = zip_bytes()
        old = self.post(body, now=NOW - 31 * 86400)[1]["code"]
        new = self.post(body, now=NOW, addr="b")[1]["code"]
        self.r.store.prune(NOW)
        self.assertIsNone(self.r.store.find(old))
        self.assertIsNotNone(self.r.store.find(new))
        orig = lr.STORE_MAX_BYTES
        try:
            size = self.r.store.total_bytes()
            lr.STORE_MAX_BYTES = size + len(body) + 400                     # room for one more report only
            third = self.post(body, now=NOW + 5, addr="c")[1]["code"]
            fourth = self.post(body, now=NOW + 6, addr="d")
            self.assertEqual(fourth[0], 201)                                # the oldest one made room
            self.assertIsNone(self.r.store.find(new))
            self.assertIsNotNone(self.r.store.find(third))
            lr.STORE_MAX_BYTES = 10
            self.assertEqual(self.post(body, now=NOW + 7, addr="e")[0], 507)
        finally:
            lr.STORE_MAX_BYTES = orig

    def test_codes_unique_and_alphabet(self):
        codes = {lr.new_code() for _ in range(2000)}
        self.assertEqual(len(codes), 2000)
        self.assertTrue(all(lr.CODE_RE.match(c) for c in codes))
        self.assertFalse(any(ch in c[3:] for c in codes for ch in "ILOU"))
        self.assertIsNone(self.r.store.find("../../etc/passwd"))

    def test_ssh_commands(self):
        body = zip_bytes()
        code = self.post(body)[1]["code"]
        out = io.StringIO()
        with redirect_stdout(out):
            self.assertEqual(lr.cli(["list"], self.root), 0)
        self.assertIn(code, out.getvalue())
        self.assertIn("1 reports", out.getvalue())
        out = io.StringIO()
        with redirect_stdout(out):
            self.assertEqual(lr.cli(["show", code.lower()], self.root), 0)
        self.assertIn("kind: crash", out.getvalue())
        self.assertIn("summary.txt (", out.getvalue())
        out = io.StringIO()
        with redirect_stdout(out):
            self.assertEqual(lr.cli(["path", code], self.root), 0)
        self.assertTrue(out.getvalue().strip().endswith(code + ".zip"))
        with redirect_stdout(io.StringIO()):
            self.assertEqual(lr.cli(["rm", code], self.root), 0)
        self.assertIsNone(self.r.store.find(code))
        self.assertEqual(lr.cli(["show", code], self.root), 1)

    def test_http_route(self):
        st = ls.Store(os.path.join(self.tmp, "s.db"), b"k")
        srv = ThreadingHTTPServer(("127.0.0.1", 0), ls.make_handler(st, ls.RateLimit(), ls.Geo(""), self.r))
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        url = "http://127.0.0.1:%d/lumen/v1/report" % srv.server_address[1]

        def post(body, **kw):
            h = headers(len(body), **kw)
            h.pop("Content-Length")
            req = urllib.request.Request(url, data=body, headers=h, method="POST")
            try:
                with urllib.request.urlopen(req) as r:
                    return r.status, json.loads(r.read()), dict(r.headers)
            except urllib.error.HTTPError as e:
                return e.code, json.loads(e.read() or b"{}"), dict(e.headers)
        code, ans, h = post(zip_bytes())
        self.assertEqual(code, 201)
        self.assertEqual(h.get("Cache-Control"), "no-store")
        self.assertIsNotNone(self.r.store.find(ans["code"]))
        self.assertEqual(post(b"nope")[0], 415)
        self.assertEqual(post(zip_bytes(), X_Lumen_Kind="test")[1], {"code": "LR-TEST00", "dry": True})
        try:
            urllib.request.urlopen(url)
            got = 200
        except urllib.error.HTTPError as e:
            got = e.code
        self.assertEqual(got, 404)                                          # no HTTP read path
        # the parts route, and a stored dump in the statistics counter
        import hashlib
        self.r.on_dump = st.add_dump
        data, step, have = self.big_zip(), 12 * 1024, None
        for o in range(0, len(data), step):
            b = data[o:o + step]
            h = headers(len(b), Content_Type="application/octet-stream", X_Lumen_Upload="9" * 32, X_Lumen_Offset=str(o),
                        X_Lumen_Total=str(len(data)), X_Lumen_Sha256=hashlib.sha256(data).hexdigest(),
                        X_Lumen_Kind="dump", X_Lumen_Model="h0062", X_Lumen_Serial="CZW1234567MK")
            h.pop("Content-Length")
            req = urllib.request.Request(url + "/part", data=b, headers=h, method="POST")
            with urllib.request.urlopen(req) as r:
                have = (r.status, json.loads(r.read()))
        self.assertEqual(have[0], 201)
        self.assertEqual(open(self.r.store.find(have[1]["code"]), "rb").read(), data)
        d = json.loads(st.stats(now=NOW))["dumps"]
        self.assertEqual((d["units"], d["sent"], d["by_model"][0]["model"], d["by_model"][0]["name"]),
                         (1, 1, "h0062", "XGIMI TITAN Noir Pro"))
        srv.shutdown()


if __name__ == "__main__":
    unittest.main()
