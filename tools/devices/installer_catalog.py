#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""installer_catalog.py: the signed catalog of Lumen Installer (apps/LumenInstaller, 1.0.3 and later).

A projector can take Lumen OS from the app when, and only when, this catalog lists a build for its model code. The
catalog is made from what is really published: the device descriptors say where a model's builds are and which
certificates sign them, the GitHub releases of Project-Lumen-OS/lumen-os say which builds exist. Nothing else.

  inputs   devices/<codename>/catalog.conf      tag (release tag, {version}), image (file name, {version} optional),
                                                certs (when installer.conf has none), stable_manifest / beta_manifest
                                                (the update channel the updater reads: must name the same build),
                                                the way back (restore_file, restore_files, restore_stock: below)
                                                app_update = FILE LUMEN... (Lumen Installer 1.0.6: an app of an
                                                installed Lumen OS it updates on those Lumen OS versions, FILE with
                                                {version} once, an .apk asset of a release of this tag)
           devices/<codename>/device.conf       name, models, platform, hardware (board), vendor_firmware, slots
           devices/<codename>/installer.conf    via installer_index.py: the stock rows of each model (which stock
                                                versions, install from the trial per version), product, config check,
                                                refused versions with their texts, certificates
           the releases                         per channel (a pre-release is beta, a release is stable) the newest
                                                version whose release has the image, SHA256SUMS and SHA256SUMS.sig;
                                                the signature must verify with the model's certificates, the image
                                                must be listed once; a beta older than the stable build is left out
  rules    a model is listed only with at least one build; a build is 'trial' (the app starts it as a trial) on an
           A/B projector (/metadata and gsid), 'computer' on a single-slot one (the guide's computer installer);
           'install' = the image carries the installer that installs from the trial (erofs lookup of its two files
           over HTTP ranges, cached by sha256); the build's stock list is the model's stock versions, narrowed to the
           channel manifest's when it names this build; a channel manifest that names another version stops the
           build (the updater and the app must offer the same build); 'clears_leftovers' = the build installs
           from the trial and is at least the version of catalog.conf leftovers_cleared_from (its in-trial installer
           clears what a finished XGIMI update left in super, so the app may say so). An app_update row comes from
           the newest release of the tag that carries FILE: its package and version code read from the APK, which must
           be signed with the model's platform certificate (devices/<codename>/certs/platform.x509.pem, the key of the
           installed app it replaces); no release carries it = no row
  trial    a trial starts from the newest beta: Lumen Installer 1.0.3 to 1.0.5 start the stable build unless the user
           picks Beta, so a stable trial build keeps only the stock versions no beta trial build of the model takes
           and is left out when none is left (the app then reads the beta without a choice). Every trial build that
           stays must carry the reader that gives a trial the projector's own vendor files (tools/ota/image/
           z9x_blobs.sh with its blobs.conf, or the X50 family's older blobs_stock_dsu.sh: sound path, codecs, secure
           video): erofs lookup over HTTP ranges, cached by sha256 in readers.json next to the --cache file. A trial
           build without it, or an image that cannot be read, stops the build

  restore  the way back to the XGIMI system (Settings > About > Back to the XGIMI system, apps/core/lumen-about):
           restore_file = the file name the XGIMI recovery USB stick needs, restore_files = the official XGIMI files
           (VERSION:SIZE:URL, on XGIMI's firmware server under that name), restore_stock = per model the stock versions
           and the official version each goes back with (MODEL:STOCK>VERSION,STOCK>VERSION, the guide's Back to stock
           table). A model of restore_stock gets "restore": {"file", "files": [{"stock", "version", "size", "url"}]}.
           Never an older file: the stick may refuse a file older than the projector's firmware (XGIMI's anti-rollback
           compares build times). A stock goes back with its own version, or with a newer official one only when its
           own has no file. The address must name its version and build time (..._6.15.62_20260325-154151/), and a
           higher version must be built later, else the version number says nothing about what the stick takes.

  files    two files from one build, one serial: catalog.json for Lumen Installer 1.0.3, which refuses that WHOLE file
           for a model code other than a G and four digits, so it holds the G rows only; catalog-v2.json for 1.0.4 and
           later, every row (a capital letter and four digits). Each file has its own serial: the app keeps and
           compares a copy of the file it reads only, so --live checks each file against its own live copy

    installer_catalog.py build [--serial N] [--out-dir DIR] [--out FILE] [--out-v2 FILE] [--live DIR]
                               [--from DIR] [--record DIR] [--cache FILE]
        --out-dir DIR   DIR/catalog.json and DIR/catalog-v2.json; --out FILE = catalog.json only (also the default
                        on stdout), --out-v2 FILE = catalog-v2.json only; --live DIR holds the live files (the index
                        release): per file the serial may not go back, changed rows need a newer one, the changed
                        models are printed
    installer_catalog.py check FILE [--v2] [--from DIR]   FILE says the same as a fresh build (the serial aside)
    installer_catalog.py sign FILE --key PK8        FILE.sig (RSA PKCS#1 v1.5 SHA-256, the list key)
    installer_catalog.py verify FILE SIG CERT [--v2]   the signature and the strict format
    installer_catalog.py show FILE                  one line per model and build
--v2 (or the file name catalog-v2.json) reads FILE with the rules of catalog-v2.json.
--from DIR reads a recorded snapshot (releases.json, files/<tag>/<name>, handover.json, readers.json) instead of
the network, --record DIR writes one. GH_TOKEN, when set, is sent to the GitHub API only.
"""
import hashlib
import json
import os
import re
import struct
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile
from pathlib import Path

G = Path(os.path.abspath(__file__)).parents[2]
sys.path.insert(0, str(G / "tools" / "devices"))
sys.path.insert(0, str(G / "tools" / "lumen"))
import apkinfo  # noqa: E402
import devices as registry  # noqa: E402
import installer_index  # noqa: E402

KIND = "lumen-installer-catalog"
REPO = "Project-Lumen-OS/lumen-os"
RELEASES = "https://github.com/%s/releases" % REPO
GUIDE = "https://github.com/%s/blob/main/docs/install/en.md" % REPO
# the report addresses of the stock system info (Lumen Installer "Send info"), one per line, a direct one before the
# Cloudflare one: the place of ro.lumen.report.url on the XGIMI system. No file or no line: no "reports" key, the app
# sends to its default (Cloudflare, 12 KiB parts pass the Russian choke).
REPORTS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "installer_reports.txt")
REPORT_RE = re.compile(r"^https://[A-Za-z0-9.-]{1,200}(:[0-9]{2,5})?/lumen/v1/report$")


def report_urls(path=None):
    """the addresses of REPORTS_FILE in order (comments and blank lines skipped), each checked"""
    path = REPORTS_FILE if path is None else path
    if not os.path.isfile(path):
        return []
    out = []
    with open(path) as f:
        for line in f:
            u = line.split("#", 1)[0].strip()
            if not u:
                continue
            if not REPORT_RE.match(u) or u in out:
                die("%s: bad or repeated address %r" % (path, u))
            out.append(u)
    if len(out) > 4:
        die("%s: at most 4 addresses" % path)
    return out
API = "https://api.github.com/repos/%s/releases?per_page=100&page=%d"
VERSION = r"[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}(?:-(?:beta|rc)[0-9]{1,2})?"
CONF_KEYS = {"tag", "image", "certs", "stable_manifest", "beta_manifest", "leftovers_cleared_from", "restore_file",
             "restore_files", "restore_stock", "app_update"}
RESTORE_KEYS = ("restore_file", "restore_files", "restore_stock")
STICK = r"XGIMI_[A-Z0-9_]{1,48}\.bin"
XGIMI_FIRMWARE = "https://product-library.static.xgimi.com/firmware/"
STOCK = r"[vV][0-9]{1,4}(?:\.[0-9]{1,4}){1,4}"
MAX_STOCK_FILE = 8 << 30
HANDOVER = ("/system/priv-app/LumenDsuInstall/LumenDsuInstall.apk", "/system/etc/init/lumen_dsu_install.rc")
# image paths of the reader that gives a trial the projector's own vendor files (newest first): the shared one
# (tools/ota/image/z9x_blobs.sh with its model settings), the X50 family's before it
STOCK_READERS = (
    ("system/etc/z9x/blobs.conf", "shared reader"),
    ("system/etc/z9x/blobs_stock_dsu.sh", "x50 reader"),
)
READERS = "readers.json"
MAX_IMAGE = 4 << 30
MAX_APK = 64 << 20
PKG = r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){1,7}"
BS = 4096
# file version -> its name on the index release and the model codes it may carry (1.0.3 refuses all of catalog.json
# for any other code, Catalog.java CODE)
FILES = {1: "catalog.json", 2: "catalog-v2.json"}
CODES = {1: r"G[0-9]{4}", 2: r"[A-Z][0-9]{4}"}


def die(msg):
    print("installer_catalog: " + msg, file=sys.stderr)
    sys.exit(2)


def note(msg):
    print("installer_catalog: " + msg, file=sys.stderr)


def version_key(v):
    m = re.fullmatch(r"([0-9]+)\.([0-9]+)\.([0-9]+)(?:-(beta|rc)([0-9]+))?", v)
    if not m:
        raise ValueError(v)
    stage = {"beta": 0, "rc": 1, None: 2}[m.group(4)]
    return int(m.group(1)), int(m.group(2)), int(m.group(3)), stage, int(m.group(5) or 0)


# ------------------------------------------------------------------------------------------------ network
class Net:
    """GitHub over HTTPS, or a recorded snapshot (--from); --record keeps what was read."""

    def __init__(self, src=None, record=None, cache=None):
        self.src = Path(src) if src else None
        self.record = Path(record) if record else None
        self.cache = Path(cache) if cache else (Path(src) / "handover.json" if src else
                                                 Path.home() / ".cache" / "lumen-catalog" / "handover.json")
        self.handover = json.loads(self.cache.read_text()) if self.cache.is_file() else {}
        self.kept = {}
        self.rcache = self.cache.with_name(READERS)
        self.readers = json.loads(self.rcache.read_text()) if self.rcache.is_file() else {}
        self.rkept = {}

    def _open(self, url, headers=None):
        req = urllib.request.Request(url, headers=dict(headers or {}, **{"User-Agent": "lumen-catalog"}))
        for i in range(4):
            try:
                return urllib.request.urlopen(req, timeout=60)
            except OSError as e:
                if i == 3 or getattr(e, "code", 500) in (403, 404):
                    raise
                time.sleep(2 * (i + 1))

    def releases(self):
        if self.src:
            return json.loads((self.src / "releases.json").read_text())
        out, page = [], 1
        tok = os.environ.get("GH_TOKEN", "")
        while True:
            h = {"Accept": "application/vnd.github+json"}
            if tok:
                h["Authorization"] = "Bearer " + tok
            with self._open(API % (REPO, page), h) as r:
                got = json.loads(r.read())
            for x in got:
                out.append({"tag_name": x["tag_name"], "prerelease": x["prerelease"], "draft": x["draft"],
                            "assets": [{"name": a["name"], "size": a["size"]} for a in x["assets"]]})
            if len(got) < 100:
                break
            page += 1
        out.sort(key=lambda x: x["tag_name"])
        if self.record:
            self.record.mkdir(parents=True, exist_ok=True)
            (self.record / "releases.json").write_text(json.dumps(out, indent=1) + "\n")
        return out

    def file(self, tag, name, limit=1 << 20):
        if self.src:
            p = self.src / "files" / tag / name
            return p.read_bytes() if p.is_file() else None
        try:
            with self._open("https://github.com/%s/releases/download/%s/%s" % (REPO, tag, name)) as r:
                b = r.read(limit + 1)
        except OSError as e:
            if getattr(e, "code", 0) == 404:
                return None
            raise
        if len(b) > limit:
            die("%s/%s is larger than %d bytes" % (tag, name, limit))
        if self.record:
            p = self.record / "files" / tag / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(b)
        return b

    def install(self, tag, name, sha):
        """1 the image carries the installer that installs from the trial, 0 it does not, -1 cannot tell"""
        if sha not in self.handover:
            if self.src:
                die("no recorded install-from-trial lookup for %s/%s (%s)" % (tag, name, sha))
            url = "https://github.com/%s/releases/download/%s/%s" % (REPO, tag, name)
            self.handover[sha] = handover(lambda off, n: self._range(url, off, n))
            self.cache.parent.mkdir(parents=True, exist_ok=True)
            self.cache.write_text(json.dumps(self.handover, indent=1, sort_keys=True) + "\n")
        if self.record:
            self.kept[sha] = self.handover[sha]
            (self.record / "handover.json").write_text(json.dumps(self.kept, indent=1, sort_keys=True) + "\n")
        return self.handover[sha]

    def reader(self, tag, name, sha):
        """the reader that gives a trial of this image the projector's own vendor files: its name, '' when the image
        has none. An image that cannot be read stops the build (nothing is cached for it)"""
        if sha not in self.readers:
            if self.src:
                die("no recorded vendor file reader lookup for %s/%s (%s)" % (tag, name, sha))
            url = "https://github.com/%s/releases/download/%s/%s" % (REPO, tag, name)
            how = stock_reader(lambda off, n: self._range(url, off, n))
            if how is None:
                die("%s/%s: the image cannot be read, so nobody can tell what its trial gets" % (tag, name))
            self.readers[sha] = how
            self.rcache.parent.mkdir(parents=True, exist_ok=True)
            self.rcache.write_text(json.dumps(self.readers, indent=1, sort_keys=True) + "\n")
        if self.record:
            self.rkept[sha] = self.readers[sha]
            self.record.mkdir(parents=True, exist_ok=True)
            (self.record / READERS).write_text(json.dumps(self.rkept, indent=1, sort_keys=True) + "\n")
        return self.readers[sha]

    def _range(self, url, off, n):
        with self._open(url, {"Range": "bytes=%d-%d" % (off, off + n - 1)}) as r:
            if r.status != 206:
                raise OSError("no range answer (%d) for %s" % (r.status, url))
            b = r.read(n)
        if len(b) != n:
            raise OSError("short range read")
        return b


# ------------------------------------------------------------------------------------------------ erofs
class Erofs:
    """Path lookup in an erofs image through read(off, n) (the app's former core/Erofs, directories only)."""
    MAGIC = 0xE0F5E1E2

    def __init__(self, read):
        self.read = read
        sb = read(1024, 128)
        if struct.unpack_from("<I", sb, 0)[0] != self.MAGIC:
            raise ValueError("not erofs")
        self.bits = sb[12]
        if not 9 <= self.bits <= 16:
            raise ValueError("erofs block size bits %d" % self.bits)
        self.blk = 1 << self.bits
        self.root = struct.unpack_from("<H", sb, 14)[0]
        self.meta = struct.unpack_from("<I", sb, 40)[0]

    def inode(self, nid):
        base = self.meta * self.blk + nid * 32
        h = self.read(base, 32)
        fmt, xcount, mode = struct.unpack_from("<HHH", h, 0)
        if fmt & 1:
            e = self.read(base, 64)
            size, blkaddr, isize = struct.unpack_from("<Q", e, 8)[0], struct.unpack_from("<I", e, 16)[0], 64
        else:
            size, blkaddr, isize = struct.unpack_from("<I", h, 8)[0], struct.unpack_from("<I", h, 16)[0], 32
        xsize = 0 if xcount == 0 else 12 + (xcount - 1) * 4
        return {"layout": (fmt >> 1) & 7, "mode": mode, "size": size, "blkaddr": blkaddr, "base": base,
                "isize": isize, "xsize": xsize}

    def _block(self, d, i):
        n = (d["size"] + self.blk - 1) // self.blk
        if i >= n:
            raise ValueError("dir block out of range")
        ln = min(self.blk, d["size"] - i * self.blk)
        if d["layout"] == 0:
            return self.read((d["blkaddr"] + i) * self.blk, ln)
        if d["layout"] == 2:
            if i < d["size"] // self.blk:
                return self.read((d["blkaddr"] + i) * self.blk, self.blk)
            return self.read(d["base"] + d["isize"] + d["xsize"], ln)
        raise ValueError("directory layout %d" % d["layout"])

    def find(self, d, name):
        if d["mode"] & 0xF000 != 0x4000:
            return -1
        for i in range((d["size"] + self.blk - 1) // self.blk):
            b = self._block(d, i)
            if len(b) < 12:
                continue
            first = struct.unpack_from("<H", b, 8)[0]
            count = first // 12
            if count <= 0 or first > len(b):
                raise ValueError("bad dirent block")
            for k in range(count):
                noff = struct.unpack_from("<H", b, k * 12 + 8)[0]
                end = struct.unpack_from("<H", b, k * 12 + 20)[0] if k + 1 < count else len(b)
                if noff > end or end > len(b):
                    raise ValueError("bad dirent name")
                nm = b[noff:end].split(b"\0", 1)[0]
                if nm == name.encode():
                    return struct.unpack_from("<Q", b, k * 12)[0]
        return -1

    def exists(self, path):
        cur = self.inode(self.root)
        for part in [p for p in path.split("/") if p]:
            nid = self.find(cur, part)
            if nid < 0:
                return False
            cur = self.inode(nid)
        return True


def handover(read):
    try:
        e = Erofs(read)
        if not e.exists("/system/build.prop"):
            return -1
        return 1 if all(e.exists(p) for p in HANDOVER) else 0
    except (ValueError, OSError, struct.error):
        return -1


def reader_of(e):
    """the name of the vendor file reader in the erofs image e (STOCK_READERS), '' when it has none"""
    for path, how in STOCK_READERS:
        if e.exists(path):
            return how
    return ""


def stock_reader(read):
    """reader_of the erofs image behind read(off, n), None when it cannot be read"""
    try:
        e = Erofs(read)
        if not e.exists("/system/build.prop"):
            return None
        return reader_of(e)
    except (ValueError, OSError, struct.error):
        return None


# ------------------------------------------------------------------------------------------------ descriptors
def read_conf(path):
    out = {}
    for n, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if "=" not in line:
            die("%s:%d: no '='" % (path, n))
        k, v = (x.strip() for x in line.split("=", 1))
        if k not in CONF_KEYS:
            die("%s:%d: unknown key %s" % (path, n, k))
        if k in out:
            die("%s:%d: %s twice" % (path, n, k))
        out[k] = v
    for k in ("tag", "image"):
        if not out.get(k):
            die("%s: %s missing" % (path, k))
    if out["tag"].count("{version}") != 1 or out["image"].count("{version}") > 1:
        die("%s: tag needs {version} once, image at most once" % path)
    if "app_update" in out:
        w = out["app_update"].split()
        if (len(w) < 2 or w[0].count("{version}") != 1 or not re.fullmatch(r"[A-Za-z0-9._{}-]{1,120}\.apk", w[0])
                or not all(re.fullmatch(VERSION, v) for v in w[1:]) or len(set(w[1:])) != len(w) - 1):
            die("%s: app_update needs FILE.apk with {version} once and the Lumen OS versions it is for" % path)
    return out


def stock_key(v):
    """v6.15.58 -> (6, 15, 58): XGIMI versions compare by number (V6.15.100 is newer than V6.15.99)"""
    return tuple(int(x) for x in v[1:].split("."))


def official_file(url, name):
    """an address on XGIMI's firmware server whose last part is the stick file name (Lumen About keeps no other)"""
    return (isinstance(url, str) and url.startswith(XGIMI_FIRMWARE) and url.endswith("/" + name) and len(url) <= 300
            and ".." not in url and re.fullmatch(r"[A-Za-z0-9._~/:-]+", url) is not None)


def restore_of(d, conf):
    """{model: restore entry} of catalog.conf's way back, {} without one; dies on anything odd"""
    have = [k for k in RESTORE_KEYS if conf.get(k)]
    where = d["codename"] + "/catalog.conf"
    if not have:
        return {}
    if len(have) != len(RESTORE_KEYS):
        die("%s: the way back needs %s together" % (where, ", ".join(RESTORE_KEYS)))
    name = conf["restore_file"]
    if not re.fullmatch(STICK, name):
        die("%s: restore_file %r is no XGIMI stick file name" % (where, name))
    files, built_at = {}, {}
    for item in conf["restore_files"].split():
        parts = item.split(":", 2)
        if len(parts) != 3 or not re.fullmatch(STOCK, parts[0]) or not parts[1].isdigit():
            die("%s: restore_files %r is not VERSION:SIZE:URL" % (where, item))
        v, size, url = parts[0].lower(), int(parts[1]), parts[2]
        if v in files or not 0 < size <= MAX_STOCK_FILE or not official_file(url, name):
            die("%s: restore_files %s: twice, an odd size or not %s on XGIMI's firmware server" % (where, v, name))
        built = re.search(r"_%s_([0-9]{8}-[0-9]{6})/[^/]+$" % re.escape(v[1:]), url)
        if not built:
            die("%s: restore_files %s: the address does not name this version and its build time" % (where, v))
        files[v] = {"version": v, "size": size, "url": url}
        built_at[v] = built.group(1)
    order = [built_at[v] for v in sorted(files, key=stock_key)]
    if order != sorted(order):
        die("%s: restore_files: a higher version was built earlier, its number says nothing about the stick" % where)
    out = {}
    for item in conf["restore_stock"].split():
        model, _, pairs = item.partition(":")
        if model not in d["models"] or model in out or not pairs:
            die("%s: restore_stock %r: not a model of %s, or twice" % (where, item, d["codename"]))
        rows = []
        for pair in pairs.split(","):
            stock, _, v = (x.lower() for x in pair.partition(">"))
            if stock not in d["vendor_firmware"] or v not in files or stock in [r["stock"] for r in rows]:
                die("%s: restore_stock %s %r: stock not in device.conf vendor_firmware, no such file or twice"
                    % (where, model, pair))
            if stock_key(v) < stock_key(stock):
                die("%s: restore_stock %s %r: a file older than the stock, the stick may refuse it" % (where, model, pair))
            if v != stock and stock in files:
                die("%s: restore_stock %s %r: the stock has its own file, offer that one" % (where, model, pair))
            rows.append(dict({"stock": stock}, **files[v]))
        out[model] = {"file": name, "files": rows}
    return out


def verify_sig(data, sig, pems):
    """RSA PKCS#1 v1.5 SHA-256 by any of the certificates (openssl, as the app's Release.verify)"""
    if data is None or sig is None:
        return False
    with tempfile.TemporaryDirectory() as t:
        d, s = Path(t) / "data", Path(t) / "sig"
        d.write_bytes(data)
        s.write_bytes(sig)
        for i, pem in enumerate(pems):
            c, k = Path(t) / ("c%d" % i), Path(t) / ("k%d" % i)
            c.write_text(pem)
            r = subprocess.run(["openssl", "x509", "-in", str(c), "-pubkey", "-noout", "-out", str(k)], capture_output=True)
            if r.returncode == 0 and subprocess.run(["openssl", "dgst", "-sha256", "-verify", str(k), "-signature", str(s),
                                                      str(d)], capture_output=True).returncode == 0:
                return True
    return False


def sums_line(sums, name):
    found = None
    for line in sums.decode("utf-8", "replace").splitlines():
        f = line.strip().split(None, 1)
        if len(f) != 2:
            continue
        n = f[1][1:] if f[1].startswith("*") else f[1]
        if n != name:
            continue
        if found or not re.fullmatch(r"[0-9a-f]{64}", f[0]):
            return None
        found = f[0]
    return found


def model_rows(devs):
    """the stock rows of each model: installer_index's supported entries, else the registry's versions"""
    rows = {}
    for e in installer_index.build(serial=1)["models"]:
        if e["status"] == "supported":
            rows[e["model"]] = e
    return rows


def entries_of(d, conf, rows, iconf):
    out = []
    ways = restore_of(d, conf)
    for i, m in enumerate(d["models"]):
        name = d["name"] if i == 0 else "%s (%s)" % (d["name"], m)
        r = rows.get(m)
        if r:
            if conf.get("certs"):
                die("%s: certs are in installer.conf already" % d["codename"])
            e = {"model": m, "name": r["name"], "board": r["board"], "product": r["product"],
                 "config_check": r["config_check"], "stock": [{"version": s["version"], "install": s["install"]}
                                                              for s in r["stock"] if s["trial"]],
                 "refused": r.get("refused", []), "certs": r["certs"]}
        else:
            certs = [installer_index.pem(x) for x in conf.get("certs", "").split()]
            if not certs:
                die("%s: certs missing (catalog.conf, no installer.conf entry)" % d["codename"])
            e = {"model": m, "name": name, "board": d["hardware"], "product": None,
                 "config_check": bool(iconf.get(d["installer_id"], {}).get("MODEL_CONFIG")),
                 "stock": [{"version": v, "install": False} for v in d["vendor_firmware"]], "refused": [],
                 "certs": certs}
        e["platform"] = d["platform"]
        if m in ways:
            e["restore"] = ways[m]
        out.append(e)
    return out


def manifest(net, url, pems, what):
    tag, name = url.split("/releases/download/", 1)[1].split("/", 1)
    b, s = net.file(tag, name, 1 << 20), net.file(tag, name + ".sig", 4096)
    if b is None:
        die("%s: channel manifest %s not published" % (what, url))
    if not verify_sig(b, s, pems):
        die("%s: channel manifest %s does not verify with the model's certificates" % (what, url))
    return json.loads(b)


def builds_of(d, conf, pems, rels, net):
    """the newest build per channel of one device: [(channel, build dict without stock)]"""
    pat = re.compile(re.escape(conf["tag"]).replace(re.escape("{version}"), "(" + VERSION + ")") + "$")
    newest = {}
    for r in rels:
        m = pat.match(r["tag_name"])
        if r.get("draft") or not m:
            continue
        v = m.group(1)
        name = conf["image"].replace("{version}", v)
        names = {a["name"]: a["size"] for a in r["assets"]}
        if name not in names or "SHA256SUMS" not in names or "SHA256SUMS.sig" not in names:
            note("%s: release %s has no %s with SHA256SUMS(.sig), not a build" % (d["codename"], r["tag_name"], name))
            continue
        ch = "beta" if r["prerelease"] else "stable"
        if ch not in newest or version_key(v) > version_key(newest[ch][0]):
            newest[ch] = (v, r["tag_name"], name, names[name])
    if "beta" in newest and "stable" in newest and version_key(newest["beta"][0]) <= version_key(newest["stable"][0]):
        del newest["beta"]
    way = "computer" if d["slots"] == "single" else "trial"
    out = []
    for ch in ("stable", "beta"):
        if ch not in newest:
            continue
        v, tag, name, size = newest[ch]
        allowed = None
        if conf.get(ch + "_manifest"):
            man = manifest(net, conf[ch + "_manifest"], pems, d["codename"])
            if man.get("version") != v:
                die("%s: the %s channel manifest names %s, the newest %s release is %s: publish or flip the channel first"
                    % (d["codename"], ch, man.get("version"), ch, v))
            allowed = man["requires"]["vendor_incremental"]
        sums, sig = net.file(tag, "SHA256SUMS", 65536), net.file(tag, "SHA256SUMS.sig", 4096)
        if not verify_sig(sums, sig, pems):
            die("%s: %s SHA256SUMS does not verify with the model's certificates" % (d["codename"], tag))
        sha = sums_line(sums, name)
        if not sha:
            die("%s: %s SHA256SUMS does not list %s exactly once" % (d["codename"], tag, name))
        if size <= 0 or size % BS or size > MAX_IMAGE:
            die("%s: %s %s has %d bytes (4 KiB blocks, at most 4 GiB)" % (d["codename"], tag, name, size))
        base = "https://github.com/%s/releases/download/%s/" % (REPO, tag)
        b = {"version": v, "channel": ch, "edition": "gms" if "-gms-" in name else "nogms", "way": way,
             "image": base + name, "size": size, "sha256": sha, "sums": base + "SHA256SUMS",
             "install": way == "trial" and net.install(tag, name, sha) == 1}
        since = conf.get("leftovers_cleared_from")
        if since and not re.fullmatch(VERSION, since):
            die("%s: leftovers_cleared_from %r is not a version" % (d["codename"], since))
        b["clears_leftovers"] = b["install"] and bool(since) and version_key(v) >= version_key(since)
        if allowed is not None:
            img = man.get("image")
            if img and (img.get("sha256") != sha or img.get("size") != size or img.get("name") != name):
                die("%s: the %s channel manifest's image is not %s" % (d["codename"], ch, name))
        out.append((b, allowed))
    return out


def app_updates_of(d, conf, rels, net):
    """the app update row of a device (catalog.conf app_update = FILE LUMEN...): FILE of the newest release of the
    device's tag that carries it; package and version code from the APK, signed with the model's platform certificate"""
    if not conf.get("app_update"):
        return []
    words = conf["app_update"].split()
    pat = re.compile(re.escape(conf["tag"]).replace(re.escape("{version}"), "(" + VERSION + ")") + "$")
    tags = []
    for r in rels:
        m = pat.match(r["tag_name"])
        if m and not r.get("draft"):
            tags.append((version_key(m.group(1)), m.group(1), r))
    for _, v, r in sorted(tags, key=lambda x: x[0], reverse=True):
        name = words[0].replace("{version}", v)
        size = {a["name"]: a["size"] for a in r["assets"]}.get(name)
        if size is None:
            continue
        tag = r["tag_name"]
        apk = net.file(tag, name, MAX_APK)
        if apk is None or len(apk) != size:
            die("%s: %s/%s cannot be read (%s bytes, the release says %d)" % (d["codename"], tag, name, None if apk is None else len(apk), size))
        cert = G / "devices" / d["codename"] / "certs" / "platform.x509.pem"
        if not cert.is_file():
            die("%s: app_update needs %s" % (d["codename"], cert.relative_to(G)))
        try:
            facts, signer = apkinfo.manifest_bytes(apk), apkinfo.signer_sha256(apk)
        except (ValueError, KeyError, IndexError, OSError, struct.error, zipfile.BadZipFile, subprocess.CalledProcessError) as e:
            die("%s: %s/%s is not a signed APK (%s)" % (d["codename"], tag, name, e))
        if signer != apkinfo.der_sha256_of_pem(str(cert)):
            die("%s: %s/%s is not signed with the model's platform certificate" % (d["codename"], tag, name))
        pkg, vc = facts.get("package"), facts.get("versionCode")
        if not isinstance(pkg, str) or not re.fullmatch(PKG, pkg) or not isinstance(vc, int) or vc <= 0:
            die("%s: %s/%s has package %r version code %r" % (d["codename"], tag, name, pkg, vc))
        return [{"package": pkg, "version_code": vc, "apk": "https://github.com/%s/releases/download/%s/%s" % (REPO, tag, name),
                 "size": size, "sha256": hashlib.sha256(apk).hexdigest(), "signer": signer, "lumen": words[1:]}]
    note("%s: no release carries %s yet, no app update row" % (d["codename"], words[0]))
    return []


def trial_from_beta(builds):
    """the builds of one model entry the app gets: a trial starts from the newest beta, so the stable build keeps only
    the stock versions no beta trial build takes, and is left out when none is left (computer builds take no trial:
    their device has no beta trial build)"""
    beta = {v for b in builds if b["way"] == "trial" and b["channel"] == "beta" for v in b["stock"]}
    out = []
    for b in builds:
        if b["channel"] == "stable":
            b = dict(b, stock=[v for v in b["stock"] if v not in beta])
            if not b["stock"]:
                continue
        out.append(b)
    return out


def check_readers(e, net):
    """every trial build of the entry carries the reader of the projector's own vendor files, else the build stops"""
    for b in e["builds"]:
        if b["way"] != "trial":
            continue
        tag, name = b["image"].rsplit("/", 2)[1:]
        if not net.reader(tag, name, b["sha256"]):
            die("%s: %s %s (%s) has no reader of the projector's own vendor files, so its trial would run without "
                "the sound path, codecs and secure video: it may not be a trial build"
                % (e["model"], b["channel"], b["version"], tag))


def build(serial=None, net=None, trial_rules=True):
    """the catalog of the published releases. trial_rules=False keeps every newest build per channel without the
    trial rules (a trial from the newest beta, the vendor file reader), only for the app's host tests of the Version
    choice: a catalog that is signed always follows them"""
    net = net or Net()
    rels = net.releases()
    devs = [d for _, d in sorted(registry.load(str(G)).items())]
    rows, iconf = model_rows(devs), installer_index.installer_conf()
    models = []
    for d in devs:
        cp = G / "devices" / d["codename"] / "catalog.conf"
        if not cp.is_file():
            continue
        conf = read_conf(cp)
        ents = entries_of(d, conf, rows, iconf)
        found = builds_of(d, conf, ents[0]["certs"], rels, net)
        apps = app_updates_of(d, conf, rels, net)
        for e in ents:
            if e["certs"] != ents[0]["certs"]:
                die("%s: its models have different certificates" % d["codename"])
            mine = [s["version"] for s in e["stock"]]
            e["builds"] = []
            for b, allowed in found:
                st = [v for v in mine if allowed is None or v in allowed]
                if st:
                    e["builds"].append(dict(b, stock=st))
                else:
                    note("%s: %s %s fits none of its stock versions %s" % (e["model"], b["channel"], b["version"], mine))
            kept = trial_from_beta(e["builds"]) if trial_rules else e["builds"]
            for b in e["builds"]:
                k = [x for x in kept if x["channel"] == b["channel"]]
                if not k:
                    note("%s: stable %s left out, a trial starts from the beta" % (e["model"], b["version"]))
                elif k[0]["stock"] != b["stock"]:
                    note("%s: stable %s only on %s, a trial starts from the beta" % (e["model"], b["version"], k[0]["stock"]))
            e["builds"] = kept
            if trial_rules:
                check_readers(e, net)
            if not e["refused"]:
                del e["refused"]
            if apps:
                e["app_updates"] = apps
            if e["builds"]:
                models.append(e)
            else:
                note("%s: no build published, not in the catalog" % e["model"])
    codes = [e["model"] for e in models]
    if len(codes) != len(set(codes)):
        die("a model code twice: %s" % codes)
    out = {"schema": 1, "kind": KIND, "serial": int(serial if serial is not None else time.time()),
           "releases": RELEASES, "guide": GUIDE, "models": models}
    reports = report_urls()
    if reports:
        out["reports"] = reports
    return out


def dump(c):
    return (json.dumps(c, ensure_ascii=False, indent=1) + "\n").encode("utf-8")


def for_file(c, v):
    """the catalog of file version v: catalog.json (1) without the rows whose code Lumen Installer 1.0.3 refuses"""
    if v == 2:
        return c
    keep = [e for e in c["models"] if re.fullmatch(CODES[1], e["model"])]
    for e in c["models"]:
        if e not in keep:
            note("%s: only in %s (Lumen Installer 1.0.3 refuses all of %s for this code)" % (e["model"], FILES[2], FILES[1]))
    return dict(c, models=keep)


def version_of(path, args):
    return 2 if "--v2" in args or Path(path).name == FILES[2] else 1


def live_check(c, v, live_dir):
    """serial rules per file, against the live copy of the same file: the serial never goes back, changed rows get a
    newer serial; prints the models that changed"""
    p = Path(live_dir) / FILES[v]
    if not p.is_file():
        note("%s: no live copy in %s, nothing to compare" % (FILES[v], live_dir))
        return
    live = json.loads(p.read_text(encoding="utf-8"))
    new, old = {e["model"]: e for e in c["models"]}, {e["model"]: e for e in live["models"]}
    changed = sorted(m for m in set(new) | set(old) if new.get(m) != old.get(m))
    rest = [k for k in set(c) | set(live) if k not in ("serial", "models") and c.get(k) != live.get(k)]
    if c["serial"] < live["serial"]:
        die("%s: serial %d is older than the live %d" % (FILES[v], c["serial"], live["serial"]))
    if (changed or rest) and c["serial"] <= live["serial"]:
        die("%s: %s changed, the serial must grow past the live %d" % (FILES[v], " ".join(changed + rest), live["serial"]))
    for m in changed:
        print("installer_catalog: %s: %s %s" % (FILES[v], m, "added" if m not in old else "removed" if m not in new else "changed"))
    print("installer_catalog: %s: serial %d (live %d), %d of %d models changed" % (FILES[v], c["serial"], live["serial"],
                                                                                 len(changed), len(new)))


# ------------------------------------------------------------------------------------------------ strict format
def strict(c, v=1):
    """the app's own rules (core/Catalog.java) for file version v, so a catalog the app would refuse is never signed"""
    def need(ok, what):
        if not ok:
            raise ValueError(what)

    def https(u):
        need(isinstance(u, str) and u.startswith("https://") and len(u) <= 300 and ".." not in u
             and not re.search(r"[\s\"'<>\\]", u), "address %r" % u)

    need(c.get("schema") == 1 and c.get("kind") == KIND, "schema/kind")
    need(isinstance(c.get("serial"), int) and c["serial"] > 0, "serial")
    https(c.get("releases"))
    https(c.get("guide"))
    if "reports" in c:
        need(isinstance(c["reports"], list) and 0 < len(c["reports"]) <= 4 and len(set(c["reports"])) == len(c["reports"])
             and all(isinstance(u, str) and REPORT_RE.match(u) for u in c["reports"]), "reports")
    seen = set()
    for e in c["models"]:
        code = e.get("model", "")
        need(re.fullmatch(CODES[v], code) and code not in seen, "model %r" % code)
        seen.add(code)
        need(re.fullmatch(r"[a-z0-9_]{1,32}", e.get("board", "")), code + " board")
        need(e.get("stock") and all(re.fullmatch(r"[vV][0-9]{1,4}(\.[0-9]{1,4}){1,4}", s["version"])
                                    and isinstance(s["install"], bool) for s in e["stock"]), code + " stock")
        need(1 <= len(e.get("certs", [])) <= 4, code + " certs")
        need(e.get("builds"), code + " builds")
        for b in e["builds"]:
            need(re.fullmatch(VERSION, b["version"]) and b["channel"] in ("stable", "beta")
                 and b["way"] in ("trial", "computer") and b["edition"] in ("gms", "nogms"), code + " build")
            https(b["image"])
            https(b["sums"])
            need(re.fullmatch(r"[0-9a-f]{64}", b["sha256"]) and 0 < b["size"] <= MAX_IMAGE and b["size"] % BS == 0,
                 code + " image")
            need(b["stock"] and set(b["stock"]) <= {s["version"] for s in e["stock"]}, code + " build stock")
            need(isinstance(b["install"], bool) and (b["way"] == "trial" or not b["install"]), code + " install")
            need(isinstance(b.get("clears_leftovers"), bool) and (b["install"] or not b["clears_leftovers"]), code + " clears_leftovers")
        need(len({b["channel"] for b in e["builds"]}) == len(e["builds"]), code + " one build per channel")
        if "restore" in e:
            r = e["restore"]
            need(isinstance(r, dict) and re.fullmatch(STICK, r.get("file", "")) and r.get("files"), code + " restore")
            for f in r["files"]:
                need(re.fullmatch(STOCK, f.get("stock", "")) and re.fullmatch(STOCK, f.get("version", ""))
                     and isinstance(f.get("size"), int) and 0 < f["size"] <= MAX_STOCK_FILE
                     and official_file(f.get("url"), r["file"]), code + " restore file %r" % f)
                need(stock_key(f["version"]) >= stock_key(f["stock"]), code + " restore: a file older than its stock")
            need(len({f["stock"] for f in r["files"]}) == len(r["files"]), code + " restore: one file per stock")
        for a in e.get("app_updates", []):
            https(a.get("apk"))
            need(re.fullmatch(PKG, a.get("package", "")) and isinstance(a.get("version_code"), int) and a["version_code"] > 0
                 and re.fullmatch(r"[A-Za-z0-9._-]{1,120}\.apk", a["apk"].rsplit("/", 1)[-1])
                 and isinstance(a.get("size"), int) and 0 < a["size"] <= MAX_APK
                 and re.fullmatch(r"[0-9a-f]{64}", a.get("sha256", "")) and re.fullmatch(r"[0-9a-f]{64}", a.get("signer", ""))
                 and a.get("lumen") and all(re.fullmatch(VERSION, v) for v in a["lumen"]) and len(set(a["lumen"])) == len(a["lumen"]),
                 code + " app update")


def openssl(*a):
    r = subprocess.run(["openssl"] + list(a), capture_output=True)
    return r.returncode, r.stdout + r.stderr


def arg(args, k, default=None):
    return args[args.index(k) + 1] if k in args else default


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    cmd, args = argv[1], argv[2:]
    net = lambda: Net(arg(args, "--from"), arg(args, "--record"), arg(args, "--cache"))  # noqa: E731
    if cmd == "build":
        c = build(arg(args, "--serial"), net())
        outs = {}
        if "--out-dir" in args:
            d = Path(arg(args, "--out-dir"))
            d.mkdir(parents=True, exist_ok=True)
            outs = {v: d / FILES[v] for v in FILES}
        for v, k in ((1, "--out"), (2, "--out-v2")):
            if k in args:
                outs[v] = Path(arg(args, k))
        for v in FILES:
            x = for_file(c, v)
            strict(x, v)
            if (v in outs or not outs and v == 1) and "--live" in args:
                live_check(x, v, arg(args, "--live"))
            if v in outs:
                outs[v].write_bytes(dump(x))
            elif not outs and v == 1:
                sys.stdout.buffer.write(dump(x))
        return 0
    if cmd == "check":
        got = json.loads(Path(args[0]).read_text(encoding="utf-8"))
        want = for_file(build(got.get("serial", 1), net()), version_of(args[0], args))
        if got != want:
            import difflib
            a, b = dump(got).decode().splitlines(), dump(want).decode().splitlines()
            print("\n".join(list(difflib.unified_diff(b, a, "fresh build", args[0], lineterm=""))[:80]))
            print("installer_catalog: %s does NOT say what the releases say" % args[0])
            return 1
        print("installer_catalog: %s = the published releases (%d models)" % (args[0], len(got["models"])))
        return 0
    if cmd == "sign":
        rc, out = openssl("dgst", "-sha256", "-sign", arg(args, "--key"), "-keyform", "DER", "-out", args[0] + ".sig", args[0])
        if rc:
            die("openssl sign failed: " + out.decode(errors="replace")[-300:])
        print("installer_catalog: %s.sig" % args[0])
        return 0
    if cmd == "verify":
        f, sig, cert = args[0], args[1], args[2]
        data = Path(f).read_bytes()
        ok = verify_sig(data, Path(sig).read_bytes(), [Path(cert).read_text()])
        try:
            c = json.loads(data)
            strict(c, version_of(f, args))
        except (ValueError, KeyError, TypeError) as e:
            print("installer_catalog: %s NOT VERIFIED (format: %s)" % (f, e))
            return 1
        print("installer_catalog: %s %s (serial %s, %d models)" % (f, "VERIFIED" if ok else "NOT VERIFIED (signature)",
                                                                  c["serial"], len(c["models"])))
        return 0 if ok else 1
    if cmd == "show":
        c = json.loads(Path(args[0]).read_text(encoding="utf-8"))
        for e in c["models"]:
            for b in e["builds"]:
                print("%s %-28s %-6s %-14s %-8s install=%s stock=%s %s" % (e["model"], e["name"], b["channel"], b["version"],
                                                                         b["way"], b["install"], ",".join(b["stock"]),
                                                                         b["image"].rsplit("/", 2)[1]))
            for a in e.get("app_updates", []):
                print("%s app %s %d for %s %s" % (e["model"], a["package"], a["version_code"], ",".join(a["lumen"]), a["apk"].rsplit("/", 2)[1]))
        return 0
    sys.exit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
