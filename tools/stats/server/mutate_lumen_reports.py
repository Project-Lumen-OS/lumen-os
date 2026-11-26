#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the problem report end that test_lumen_reports.py must kill (each one a rule of the upload: headers,
size, zip checks, limits, privacy of the metadata, the store): python3 tools/stats/server/mutate_lumen_reports.py.
Works on copies in a temporary folder; every anchor must be in its file once; exit 1 on a surviving mutant."""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
R, S = "lumen_reports.py", "lumen_stats.py"
MUTANTS = [
    (R, "zip signature", 'if f.read(4) != b"PK\\x03\\x04":', "if False:"),
    (R, "entry names", 'if n.startswith("/") or "\\\\" in n or ".." in n.split("/"):', "if False:"),
    (R, "unpacked size", "if sum(i.file_size for i in infos) > (DUMP_MAX_UNPACKED if kind == DUMP_KIND else ZIP_MAX_UNPACKED):", "if False:"),
    (R, "entry count", "if not infos or len(infos) > ZIP_MAX_ENTRIES:", "if not infos:"),
    (R, "size limit", "if n > (PART_MAX if part else MAX_BYTES):", "if n > (PART_MAX if part else MAX_BYTES) * 2:"),
    (R, "dump total", "if total > max_total(kind):", "if False:"),
    (R, "dump only large", "return DUMP_MAX_BYTES if kind == DUMP_KIND else MAX_BYTES", "return DUMP_MAX_BYTES"),
    (R, "install id format", 'INSTALL_RE = re.compile(r"^[0-9a-f]{16,64}$")', 'INSTALL_RE = re.compile(r"^[0-9a-fA-F]{8,64}$")'),
    (R, "model format", 'MODEL_RE = re.compile(r"^[a-z0-9_]{1,32}$")', 'MODEL_RE = re.compile(r"^[A-Za-z0-9_]{1,32}$")'),
    (R, "kind check", "if kind not in KINDS and kind != TEST_KIND:", "if False:"),
    (R, "content type", 'if ctype != ("application/octet-stream" if part else "application/zip"):', "if False:"),
    (R, "length required", "if length is None:\n        return 411", "if False:\n        return 411"),
    (R, "per address an hour", "if len(hour) >= RATE_HOUR:", "if False:"),
    (R, "per install a day", "(i, INSTALL_DAY)", "(i, GLOBAL_DAY)"),
    (R, "dumps per install a day", "checks += [(di, DUMP_INSTALL_DAY), (self.dumps, DUMP_DAY)]", "checks += [(self.dumps, DUMP_DAY)]"),
    (R, "all reports a day", "GLOBAL_DAY = 300", "GLOBAL_DAY = 3000"),
    (R, "install id hashed", '"install": self.install_hash(fields["install"])', '"install": fields["install"]'),
    (R, "test kind stores nothing", 'if f["kind"] == TEST_KIND:\n                return 200', 'if False:\n                return 200'),
    (R, "retention", 'if m.get("received_ts", 0) < now - keep * DAY:', "if False:"),
    (R, "dumps kept longer", "keep = DUMP_KEEP_DAYS if is_dump(m) else KEEP_DAYS", "keep = KEEP_DAYS"),
    (R, "open upload kept a day", "if now - os.path.getmtime(p) > OPEN_KEEP:", "if now - os.path.getmtime(p) > HOUR:"),
    (R, "size cap", "if self.total_bytes(cls) + more > self.cap(cls):", "if False:"),
    (R, "dump cap of its own", "return DUMP_STORE_MAX_BYTES if dumps else STORE_MAX_BYTES", "return STORE_MAX_BYTES"),
    (R, "disk floor", 'if shutil.disk_usage(self.store.root).free - need - reserved < DISK_FLOOR:', "if False:"),
    (R, "open bytes cap", 'if sum(map(self._total, opened)) + f["total"] > OPEN_MAX_BYTES:', "if False:"),
    (R, "upload bound to install", '("report-upload:%s:%s" % (install, upload))', '("report-upload:%s" % upload)'),
    (R, "no install id in clear", 'same.update(install=self.store.install_hash(f["install"]),', 'same.update(install=f["install"],'),
    (R, "no serial in clear", 'serial=hmac.new(self.salt, b"report-serial:" + f["serial"].encode(), hashlib.sha256).hexdigest()[:32]', 'serial=f["serial"]'),
    (R, "open uploads per sender", "if sum(1 for v in self.open_by.values() if v == who) >= OPEN_PER_SENDER:", "if False:"),
    (R, "finished frees the place", "self.open_by = {k: v for k, v in self.open_by.items() if k in keys}", "pass"),
    (R, "only idle ones evicted", "and now - ops[0][2] > OPEN_EVICT_IDLE:", "and True:"),
    (R, "eviction makes room", "while ops and (len(ops) >= OPEN_MAX", "while False and (len(ops) >= OPEN_MAX"),
    (R, "open uploads reserve disk", "free - need - reserved < DISK_FLOOR", "free - need < DISK_FLOOR"),
    (R, "part slots", "if not self.slots.acquire(timeout=PART_WAIT):", "if False:"),
    (R, "IPv6 by its /64", 'return str(ipaddress.ip_network("%s/64" % ip, strict=False))', "return str(ip)"),
    (R, "limits per sender", "wait = self.limits.check(sender(addr),", "wait = self.limits.check(addr,"),
    (R, "summary capped", "s = fh.read(limit).decode", "s = fh.read().decode"),
    (R, "store dir private", "os.chmod(d, 0o700)", "pass"),
    (R, "parts in any order", "if out and s <= out[-1][1]:", "if out and s < out[-1][1]:"),
    (R, "query creates nothing", 'elif f["query"]:\n                    return 404', 'elif False:\n                    return 404'),
    (R, "finished answers again", "if os.path.exists(dp):", "if False:"),
    (R, "ranges cap", "if len(nxt) > RANGES_MAX:", "if False:"),
    (R, "store full", 'return "store full"', "return None"),
    (R, "code alphabet", 'CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"', 'CODE_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUV"'),
    (R, "short body", 'if not b:\n                        return 400, {"error": "short body"}, {}', 'if not b:\n                        break'),
    (R, "upload leftovers", "if os.path.exists(tmp):\n                os.remove(tmp)", "if False:\n                os.remove(tmp)"),
    (R, "private files", "os.chmod(tmp_path, 0o600)", "pass"),
    (R, "retention 30 days", "KEEP_DAYS = 30", "KEEP_DAYS = 60"),
    (S, "report route", 'if self.path in ("/lumen/v1/report", "/lumen/v1/report/part") and reports is not None:', "if False:"),
    (S, "no caching of answers", 'self.send_header("Cache-Control", "no-store")', "pass"),
]


def main():
    src = {f: open(os.path.join(HERE, f)).read() for f in (R, S)}
    bad = [n for f, n, a, _ in MUTANTS if src[f].count(a) != 1]
    if bad:
        print("BROKEN anchors: %s" % ", ".join(bad))
        return 2
    tmp = tempfile.mkdtemp()
    try:
        shutil.copy(os.path.join(HERE, "test_lumen_reports.py"), tmp)
        alive = []
        for f, n, a, b in [(None, "unmutated", "", "")] + MUTANTS:
            for g in (R, S):
                open(os.path.join(tmp, g), "w").write(src[g].replace(a, b, 1) if g == f else src[g])
            r = subprocess.run([sys.executable, "-B", os.path.join(tmp, "test_lumen_reports.py")], capture_output=True,
                               text=True)
            if f is None:
                if r.returncode != 0:
                    print("FAIL unmutated: " + r.stderr[-1500:])
                    return 1
                continue
            print("%-30s %s" % (n, "killed" if r.returncode else "ALIVE"))
            if r.returncode == 0:
                alive.append(n)
        print("mutants: %d killed, %d alive" % (len(MUTANTS) - len(alive), len(alive)))
        return 1 if alive else 0
    finally:
        shutil.rmtree(tmp)


if __name__ == "__main__":
    sys.exit(main())
