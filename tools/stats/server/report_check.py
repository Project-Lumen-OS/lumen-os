# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""The dump check of the report server for the Sparks board (2026-11-25: report Sparks the server can verify
are credited without a moderator). Every stored stock dump of Lumen Installer ("Send info") is read here once, as a
listing and three small members, never unpacked to disk: the stock files must be there (getprop, build info, the
stock settings app as a real APK), the model and version inside must be the ones the upload claimed, the build must
be a stock XGIMI user build and the serial inside must be the serial of the upload. The verdict goes to the Sparks API
signed with the key both servers share: {"r": report, "m": G-code, "fw": version, "sh": serial hash as Lumen
Installer makes it, "ok": 0|1, "why": what failed, "t": time}. The Sparks side decides the rest (a new pair, the
limits) and credits a passing report at once; a failing or missing verdict leaves the report to the moderator.
Nothing here can refuse or slow an upload: the check runs after the zip is stored, in its own thread.
"""
import hashlib
import hmac
import json
import re
import sys
import threading
import time
import urllib.error
import urllib.request
import zipfile

CONTEXT = b"LUMEN-REPORT-CHECK-V1\n"
URL = "https://spark.lumen-os.net/v1/check"
PROP_RE = re.compile(r"^\[([^\]]{1,200})\]: \[(.*)\]$")
GCODE_RE = re.compile(r"^[A-Z][0-9]{4}$")
FW_RE = re.compile(r"^[0-9a-z._-]{1,32}$")
MIN_PROPS = 200                  # a real getprop of a projector has 1000+ lines
MAX_TEXT = 4 * 1024 * 1024       # getprop or build info larger than this is not ours
MIN_APK = 1024 * 1024            # the stock settings app, slimmed, is 15 to 25 MB
TRIES = (0, 60, 600)             # seconds before each try to deliver a verdict


def serial_hash(serial):
    """The serial hash exactly as Lumen Installer sends it (lumen-contrib ContribBody.serialHash)."""
    return hashlib.sha256(("LUMEN-SERIAL-V1|" + serial.strip()).encode()).hexdigest()


def props(text):
    out = {}
    for line in text.splitlines():
        m = PROP_RE.match(line.strip())
        if m:
            out[m.group(1)] = m.group(2)
    return out


def _text(z, name):
    i = z.getinfo(name)
    if i.file_size > MAX_TEXT:
        raise ValueError("%s too large" % name)
    return z.read(name).decode("utf-8", "replace")


def _real_apk(z, info):
    """The stock app is a zip itself with its manifest and code (read through the outer zip, nothing on disk)."""
    if info.file_size < MIN_APK:
        return False
    try:
        with z.open(info) as f, zipfile.ZipFile(f) as apk:
            names = set(apk.namelist())
    except (zipfile.BadZipFile, OSError, ValueError, EOFError):
        return False
    return "AndroidManifest.xml" in names and "classes.dex" in names


def check(zip_path, model, version, serial):
    """-> (fails list, G-code read from the dump or "", version read from the dump or ""). model and version are what
    the upload claimed (lower case header form), serial the upload's X-Lumen-Serial."""
    fails = []
    try:
        z = zipfile.ZipFile(zip_path)
    except (zipfile.BadZipFile, OSError, ValueError):
        return ["broken zip"], "", ""
    with z:
        names = set(z.namelist())
        missing = [n for n in ("app/getprop.txt", "app/build.txt") if n not in names]
        if missing:
            return ["no " + ", ".join(missing)], "", ""
        try:
            p, build = props(_text(z, "app/getprop.txt")), _text(z, "app/build.txt")
        except ValueError as e:
            return [str(e)], "", ""
        g, inc = p.get("ro.boot.xgimi.modelname", ""), p.get("ro.build.version.incremental", "")
        if len(p) < MIN_PROPS:
            fails.append("getprop too short")
        if not GCODE_RE.match(g):
            fails.append("no model code")
        elif g.lower() != model.lower():
            fails.append("model differs")
        if not FW_RE.match(inc.lower()):
            fails.append("no version")
        elif inc.lower() != version.lower():
            fails.append("version differs")
        if "/%s:user/" % inc not in p.get("ro.build.fingerprint", ""):
            fails.append("fingerprint differs")
        if "XGIMI" not in (p.get("ro.product.brand", "").upper(), p.get("ro.product.manufacturer", "").upper()):
            fails.append("not an XGIMI build")
        if p.get("ro.build.type") != "user":
            fails.append("not a user build")
        if any(k.startswith("ro.lumen.") for k in p):
            fails.append("Lumen OS, not stock")
        unit = p.get("ro.serialno") or p.get("ro.boot.serialno") or ""
        if not serial or unit.strip() != serial.strip():
            fails.append("serial differs")
        device = [l.split(" ", 1)[1].strip() for l in build.splitlines() if l.startswith("DEVICE ")]
        if device != [p.get("ro.product.device", "")]:
            fails.append("build info differs")
        apks = sorted((i for i in z.infolist() if i.filename.startswith("apk/") and i.filename.endswith(".apk")),
                      key=lambda i: -i.file_size)
        if not apks or not _real_apk(z, apks[0]):
            fails.append("no stock settings app")
    return fails, g if GCODE_RE.match(g) else "", inc.lower() if FW_RE.match(inc.lower()) else ""


def verdict(code, zip_path, model, version, serial, now):
    """The signed body for the Sparks API, or None when it could name no projector (no serial, no model)."""
    if not serial:
        return None
    fails, g, fw = check(zip_path, model, version, serial)
    g = g or model.upper()
    fw = fw or version.lower()
    if not GCODE_RE.match(g) or not FW_RE.match(fw):
        return None
    why = "; ".join(fails)[:200]
    return json.dumps({"r": code, "m": g, "fw": fw, "sh": serial_hash(serial), "ok": int(not fails), "why": why,
                       "t": int(now)}, sort_keys=True).encode()


def sign(key, body):
    return hmac.new(key, CONTEXT + body, hashlib.sha256).hexdigest()


class Sender:
    """Delivers verdicts to the Sparks API in the background: a few tries, a refusal (4xx but 429) ends them."""

    def __init__(self, key, url=URL, tries=TRIES, timeout=15, sleep=time.sleep, log=None):
        if len(key) < 32:
            raise ValueError("check key too short")
        self.key, self.url, self.tries, self.timeout, self.sleep = key, url, tries, timeout, sleep
        self.log = log or (lambda s: print(s, file=sys.stderr))

    def post(self, body):
        """-> True when the API took it (or refused it for good), False to try again."""
        req = urllib.request.Request(self.url, body, {"Content-Type": "application/json",
                                                      "X-Lumen-Check": sign(self.key, body)}, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as r:
                return 200 <= r.status < 300
        except urllib.error.HTTPError as e:
            if 400 <= e.code < 500 and e.code != 429:
                self.log("report check refused: %d" % e.code)
                return True
            return False
        except (OSError, ValueError):
            return False

    def deliver(self, body):
        for wait in self.tries:
            self.sleep(wait)
            if self.post(body):
                return True
        self.log("report check not delivered")
        return False

    def send(self, code, zip_path, model, version, serial, now):
        """Called by lumen_reports for every stored dump: checks and delivers in a thread of its own."""
        def run():
            try:
                body = verdict(code, zip_path, model, version, serial, now)
                if body is not None:
                    self.deliver(body)
            except Exception as e:  # noqa: BLE001  a check never breaks the report server
                self.log("report check: %s" % e.__class__.__name__)
        t = threading.Thread(target=run, name="report-check", daemon=True)
        t.start()
        return t


def load(path, url=URL):
    """A Sender when the shared key file exists (FLASH check key, hex text), else None (no checks sent)."""
    try:
        with open(path, "rb") as f:
            key = f.read().strip()
    except OSError:
        return None
    return Sender(key, url) if key else None
