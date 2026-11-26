# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Tests of the Sparks dump check (report_check.py): python3 tools/stats/server/test_report_check.py"""
import hashlib
import hmac
import io
import json
import os
import sys
import tempfile
import threading
import unittest
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import lumen_reports  # noqa: E402
import report_check  # noqa: E402

SERIAL = "XG1234567890"
KEY = b"a1" * 32


def apk(big=True, manifest=True, dex=True):
    b = io.BytesIO()
    with zipfile.ZipFile(b, "w", zipfile.ZIP_DEFLATED) as z:
        if manifest:
            z.writestr("AndroidManifest.xml", b"\x03\x00" * 100)
        if dex:
            z.writestr("classes.dex", b"dex\n035\x00")
        z.writestr("res/raw/pad.bin", os.urandom(report_check.MIN_APK + 10 if big else 1000))
    return b.getvalue()


def getprop(**over):
    p = {"ro.boot.xgimi.modelname": "G0169", "ro.build.version.incremental": "v1.1.66",
         "ro.build.fingerprint": "XGIMI/Product_athena35ultrafs/horse_popular:14/UQ1A.240105.004.A1/v1.1.66:user/test-keys",
         "ro.product.brand": "XGIMI", "ro.product.manufacturer": "XGIMI", "ro.build.type": "user",
         "ro.serialno": SERIAL, "ro.boot.serialno": SERIAL, "ro.product.device": "horse_popular"}
    for i in range(report_check.MIN_PROPS):
        p["vendor.pad.%03d" % i] = str(i)
    for k, v in over.items():
        k = k.replace("__", ".")
        if v is None:
            p.pop(k, None)
        else:
            p[k] = v
    return "".join("[%s]: [%s]\n" % kv for kv in p.items())


def dump(path, gp=None, build="MODEL XGIMI TV DOHKO\nDEVICE horse_popular\nHARDWARE mt9681\n", app=None, skip=(),
         extra=()):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in extra:
            z.writestr(name, data)
        if "getprop" not in skip:
            z.writestr("app/getprop.txt", gp if gp is not None else getprop())
        if "build" not in skip:
            z.writestr("app/build.txt", build)
        if "apk" not in skip:
            z.writestr("apk/com.android.newsettings/release_838_newsettings.slim.apk", app if app is not None else apk())
        z.writestr("summary.txt", "x")
    return path


class Check(unittest.TestCase):
    def setUp(self):
        self.d = tempfile.mkdtemp()
        self.n = 0

    def zip(self, **kw):
        self.n += 1
        return dump(os.path.join(self.d, "d%d.zip" % self.n), **kw)

    def fails(self, path, model="g0169", version="v1.1.66", serial=SERIAL):
        return report_check.check(path, model, version, serial)[0]

    def test_a_real_dump_passes(self):
        self.assertEqual(report_check.check(self.zip(), "g0169", "v1.1.66", SERIAL), ([], "G0169", "v1.1.66"))
        self.assertEqual(self.fails(self.zip(), version="V1.1.66", model="G0169"), [])
        self.assertEqual(self.fails(self.zip(gp=getprop(ro__serialno=None))), [])

    def test_each_check(self):
        cases = {
            "model differs": dict(gp=getprop(ro__boot__xgimi__modelname="G0155")),
            "no model code": dict(gp=getprop(ro__boot__xgimi__modelname="g0169")),
            "version differs": dict(gp=getprop(ro__build__version__incremental="v1.1.65",
                                                ro__build__fingerprint="X/y/z:14/A/v1.1.65:user/k")),
            "no version": dict(gp=getprop(ro__build__version__incremental="v 1")),
            "fingerprint differs": dict(gp=getprop(ro__build__fingerprint="X/y/z:14/A/v1.1.65:user/k")),
            "not an XGIMI build": dict(gp=getprop(ro__product__brand="MediaTek", ro__product__manufacturer="MTK")),
            "not a user build": dict(gp=getprop(ro__build__type="userdebug")),
            "Lumen OS, not stock": dict(gp=getprop(ro__lumen__version="1.0.10")),
            "serial differs": dict(gp=getprop(ro__serialno="OTHER", ro__boot__serialno="OTHER")),
            "build info differs": dict(build="DEVICE goat_elegant\n"),
            "no stock settings app": dict(app=apk(big=False)),
        }
        for want, kw in cases.items():
            self.assertIn(want, self.fails(self.zip(**kw)), want)
        self.assertEqual(self.fails(self.zip(gp=getprop(ro__product__brand="MediaTek"))), [])
        self.assertIn("no stock settings app", self.fails(self.zip(app=apk(manifest=False))))
        self.assertIn("no stock settings app", self.fails(self.zip(app=apk(dex=False))))
        self.assertIn("no stock settings app", self.fails(self.zip(app=b"PK" + os.urandom(report_check.MIN_APK))))
        self.assertIn("no stock settings app", self.fails(self.zip(skip=("apk",))))
        self.assertIn("serial differs", self.fails(self.zip(), serial=""))
        self.assertIn("serial differs", self.fails(self.zip(gp=getprop(ro__serialno=None, ro__boot__serialno=None)),
                                                   serial=""))
        self.assertEqual(self.fails(self.zip(extra=[("apk/a/small.apk", apk(big=False))])), [])
        self.assertIn("build info differs", self.fails(self.zip(build="DEVICE horse_popular\nDEVICE x\n")))

    def test_short_or_missing_files(self):
        short = "".join("[k%d]: [v]\n" % i for i in range(report_check.MIN_PROPS - 12)) + getprop().split("[vendor.pad")[0]
        self.assertIn("getprop too short", self.fails(self.zip(gp=short)))
        self.assertEqual(self.fails(self.zip(skip=("getprop",))), ["no app/getprop.txt"])
        self.assertEqual(self.fails(self.zip(skip=("getprop", "build"))), ["no app/getprop.txt, app/build.txt"])
        p = os.path.join(self.d, "bad.zip")
        open(p, "wb").write(b"not a zip")
        self.assertEqual(report_check.check(p, "g0169", "v1.1.66", SERIAL), (["broken zip"], "", ""))
        big = getprop() + "x" * (report_check.MAX_TEXT + 1)
        self.assertEqual(self.fails(self.zip(gp=big)), ["app/getprop.txt too large"])

    def test_verdict_body(self):
        body = json.loads(report_check.verdict("LR-AB12CD", self.zip(), "g0169", "v1.1.66", SERIAL, 1800000000.7))
        self.assertEqual(body, {"r": "LR-AB12CD", "m": "G0169", "fw": "v1.1.66", "ok": 1, "why": "", "t": 1800000000,
                                "sh": hashlib.sha256(b"LUMEN-SERIAL-V1|" + SERIAL.encode()).hexdigest()})
        body = json.loads(report_check.verdict("LR-AB12CD", self.zip(build="DEVICE x\n"), "g0169", "v1.1.66", SERIAL, 1))
        self.assertEqual((body["ok"], body["why"]), (0, "build info differs"))
        body = json.loads(report_check.verdict("LR-AB12CD", self.zip(skip=("getprop",)), "g0169", "V1.1.66", SERIAL, 1))
        self.assertEqual((body["m"], body["fw"], body["ok"]), ("G0169", "v1.1.66", 0))
        self.assertIsNone(report_check.verdict("LR-AB12CD", self.zip(), "g0169", "v1.1.66", "", 1))
        self.assertIsNone(report_check.verdict("LR-AB12CD", self.zip(skip=("getprop",)), "unknown", "v1", SERIAL, 1))
        self.assertIsNone(report_check.verdict("LR-AB12CD", self.zip(skip=("getprop",)), "g0169", "v 1", SERIAL, 1))
        many = self.zip(gp=getprop(ro__build__type="eng", ro__lumen__x="1", ro__product__brand="A",
                                   ro__product__manufacturer="B", ro__serialno="Z" * 150, ro__boot__serialno="Z"))
        self.assertLessEqual(len(json.loads(report_check.verdict("LR-AB12CD", many, "g0169", "v1.1.66", SERIAL, 1))["why"]), 200)
        real = report_check.check
        report_check.check = lambda *a: (["x" * 150, "y" * 150], "G0169", "v1.1.66")
        try:
            why = json.loads(report_check.verdict("LR-AB12CD", many, "g0169", "v1.1.66", SERIAL, 1))["why"]
        finally:
            report_check.check = real
        self.assertEqual(why, ("x" * 150 + "; " + "y" * 150)[:200])

    def test_serial_hash_is_the_installer_one(self):
        self.assertEqual(report_check.serial_hash(" AB12 "), hashlib.sha256(b"LUMEN-SERIAL-V1|AB12").hexdigest())


class Api(BaseHTTPRequestHandler):
    got, answers = [], []

    def do_POST(self):
        body = self.rfile.read(int(self.headers["Content-Length"]))
        Api.got.append((body, self.headers.get("X-Lumen-Check"), self.headers.get("Content-Type")))
        code = Api.answers.pop(0) if Api.answers else 200
        self.send_response(code)
        self.send_header("Content-Length", "2")
        self.end_headers()
        self.wfile.write(b"{}")

    def log_message(self, *a):
        pass


class Send(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.srv = ThreadingHTTPServer(("127.0.0.1", 0), Api)
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()
        cls.url = "http://127.0.0.1:%d/v1/check" % cls.srv.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()

    def setUp(self):
        Api.got, Api.answers, self.logs, self.waits = [], [], [], []
        self.s = report_check.Sender(KEY, self.url, sleep=self.waits.append, log=self.logs.append)

    def test_signed_and_delivered_once(self):
        self.assertTrue(self.s.deliver(b'{"r": 1}'))
        self.assertEqual(Api.got, [(b'{"r": 1}', hmac.new(KEY, b"LUMEN-REPORT-CHECK-V1\n" + b'{"r": 1}', hashlib.sha256)
                                    .hexdigest(), "application/json")])
        self.assertEqual(self.waits, [0])

    def test_retries_on_server_trouble_and_429_stops_on_refusal(self):
        Api.answers = [503, 429, 200]
        self.assertTrue(self.s.deliver(b"{}"))
        self.assertEqual((len(Api.got), self.waits), (3, [0, 60, 600]))
        Api.got, self.waits[:] = [], []
        Api.answers = [403]
        self.assertTrue(self.s.deliver(b"{}"))
        self.assertEqual(len(Api.got), 1)
        self.assertIn("report check refused: 403", self.logs)
        Api.got = []
        Api.answers = [500, 500, 500]
        self.assertFalse(self.s.deliver(b"{}"))
        self.assertEqual(len(Api.got), 3)
        self.assertIn("report check not delivered", self.logs)

    def test_unreachable(self):
        s = report_check.Sender(KEY, "http://127.0.0.1:9/v1/check", sleep=lambda w: None, log=self.logs.append)
        self.assertFalse(s.deliver(b"{}"))

    def test_send_runs_the_check_in_a_thread(self):
        d = tempfile.mkdtemp()
        z = dump(os.path.join(d, "a.zip"))
        self.s.send("LR-AB12CD", z, "g0169", "v1.1.66", SERIAL, 1800000000).join(10)
        self.assertEqual(json.loads(Api.got[0][0])["ok"], 1)
        Api.got = []
        self.s.send("LR-AB12CD", z, "g0169", "v1.1.66", "", 1).join(10)
        self.s.send("LR-AB12CD", None, "g0169", "v1.1.66", SERIAL, 1).join(10)
        self.assertEqual(Api.got, [])
        self.assertEqual([l for l in self.logs if l.startswith("report check: ")], ["report check: AttributeError"])

    def test_key_and_load(self):
        with self.assertRaises(ValueError):
            report_check.Sender(b"k" * 31)
        d = tempfile.mkdtemp()
        self.assertIsNone(report_check.load(os.path.join(d, "none")))
        p = os.path.join(d, "key")
        open(p, "wb").write(b"\n")
        self.assertIsNone(report_check.load(p))
        open(p, "wb").write(KEY + b"\n")
        s = report_check.load(p, self.url)
        self.assertEqual((s.key, s.url), (KEY, self.url))
        self.assertEqual(report_check.load(p).url, "https://spark.lumen-os.net/v1/check")


class Wiring(unittest.TestCase):
    def test_a_stored_dump_goes_to_the_check_and_a_report_does_not(self):
        root = tempfile.mkdtemp()
        calls = []
        r = lumen_reports.Reports(root, b"s" * 32, on_check=lambda *a: calls.append(a))
        src = dump(os.path.join(tempfile.mkdtemp(), "x.zip"))
        f = {"kind": "dump", "model": "g0169", "version": "v1.1.66", "install": "ab" * 16, "serial": SERIAL}
        code, meta = r.store.keep(src, f, 1800000000)
        r.stored(f, meta, 1800000000)
        self.assertEqual(calls, [(code, r.store.find(code), "g0169", "v1.1.66", SERIAL, 1800000000)])
        r.stored(dict(f, kind="crash"), meta, 1)
        self.assertEqual(len(calls), 1)

    def test_a_failing_check_never_breaks_the_store(self):
        def broken(*a):
            raise RuntimeError("down")
        r = lumen_reports.Reports(tempfile.mkdtemp(), b"s" * 32, on_check=broken)
        r.stored({"kind": "dump", "serial": SERIAL}, {"code": "LR-AB12CD", "model": "g0169", "version": "v1"}, 1)


if __name__ == "__main__":
    unittest.main()
