# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the Sparks dump check that test_report_check.py must kill (each one a rule of the check, the signature,
the delivery or the wiring): python3 tools/stats/server/mutate_report_check.py. Works on copies in a temporary
folder; every anchor must be in its file once; exit 1 on a surviving mutant."""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
C, R = "report_check.py", "lumen_reports.py"
MUTANTS = [
    (C, "context in the signature", 'return hmac.new(key, CONTEXT + body, hashlib.sha256).hexdigest()',
     'return hmac.new(key, body, hashlib.sha256).hexdigest()'),
    (C, "serial hash form", 'return hashlib.sha256(("LUMEN-SERIAL-V1|" + serial.strip()).encode()).hexdigest()',
     'return hashlib.sha256(serial.strip().encode()).hexdigest()'),
    (C, "getprop needed", 'for n in ("app/getprop.txt", "app/build.txt") if n not in names]',
     'for n in ("app/build.txt",) if n not in names]'),
    (C, "text size cap", "if i.file_size > MAX_TEXT:", "if False:"),
    (C, "props count", "if len(p) < MIN_PROPS:", "if False:"),
    (C, "model code form", "if not GCODE_RE.match(g):\n            fails.append(\"no model code\")",
     "if not g:\n            fails.append(\"no model code\")"),
    (C, "model matches", 'elif g.lower() != model.lower():', 'elif False:'),
    (C, "version matches", 'elif inc.lower() != version.lower():', 'elif False:'),
    (C, "version form", 'if not FW_RE.match(inc.lower()):\n            fails.append("no version")',
     'if not inc:\n            fails.append("no version")'),
    (C, "fingerprint", 'if "/%s:user/" % inc not in p.get("ro.build.fingerprint", ""):', "if False:"),
    (C, "XGIMI build", 'if "XGIMI" not in (p.get("ro.product.brand", "").upper(), p.get("ro.product.manufacturer", "").upper()):',
     "if False:"),
    (C, "user build", 'if p.get("ro.build.type") != "user":', "if False:"),
    (C, "stock not Lumen", 'if any(k.startswith("ro.lumen.") for k in p):', "if False:"),
    (C, "serial matches", 'if not serial or unit.strip() != serial.strip():', "if not serial:"),
    (C, "serial needed", 'if not serial or unit.strip() != serial.strip():', "if unit.strip() != serial.strip():"),
    (C, "build info", 'if device != [p.get("ro.product.device", "")]:', "if False:"),
    (C, "largest apk first", "key=lambda i: -i.file_size)", "key=lambda i: i.file_size)"),
    (C, "apk size", "if info.file_size < MIN_APK:", "if False:"),
    (C, "apk manifest", 'return "AndroidManifest.xml" in names and "classes.dex" in names', 'return "classes.dex" in names'),
    (C, "apk code", 'return "AndroidManifest.xml" in names and "classes.dex" in names', 'return "AndroidManifest.xml" in names'),
    (C, "no serial no verdict", "if not serial:\n        return None", "if False:\n        return None"),
    (C, "ok means no fails", '"ok": int(not fails)', '"ok": 1'),
    (C, "why capped", 'why = "; ".join(fails)[:200]', 'why = "; ".join(fails)'),
    (C, "model upper", "g = g or model.upper()", "g = g or model"),
    (C, "verdict needs a pair", "if not GCODE_RE.match(g) or not FW_RE.match(fw):\n        return None",
     "if not g:\n        return None"),
    (C, "refusal ends tries", "if 400 <= e.code < 500 and e.code != 429:", "if False:"),
    (C, "429 is retried", "if 400 <= e.code < 500 and e.code != 429:", "if 400 <= e.code < 500:"),
    (C, "all tries", "for wait in self.tries:\n            self.sleep(wait)", "for wait in self.tries[:1]:\n            self.sleep(wait)"),
    (C, "key length", "if len(key) < 32:", "if len(key) < 1:"),
    (C, "empty key file", "return Sender(key, url) if key else None", "return Sender(key, url)"),
    (C, "signature header", '"X-Lumen-Check": sign(self.key, body)}', '"X-Lumen-Chk": sign(self.key, body)}'),
    (R, "checks only dumps", 'if f["kind"] == DUMP_KIND and self.on_check is not None:', "if self.on_check is not None:"),
    (R, "check failure kept apart", 'except Exception as e:      # noqa: BLE001  the zip is kept, the report waits for the moderator',
     "except ZeroDivisionError as e:  # mutant"),
]


def main():
    survivors = 0
    for i, (f, name, a, b) in enumerate(MUTANTS):
        d = tempfile.mkdtemp(prefix="rcmut")
        try:
            for n in (C, R, "test_report_check.py"):
                shutil.copy(os.path.join(HERE, n), d)
            p = os.path.join(d, f)
            s = open(p).read()
            if s.count(a) != 1:
                print("BROKEN %-32s anchor found %d times" % (name, s.count(a)))
                survivors += 1
                continue
            open(p, "w").write(s.replace(a, b))
            r = subprocess.run([sys.executable, "-B", "test_report_check.py"], cwd=d, capture_output=True, timeout=300)
            ok = r.returncode != 0
            survivors += not ok
            print("%-8s %s" % ("killed" if ok else "SURVIVED", name))
        finally:
            shutil.rmtree(d, ignore_errors=True)
    print("%d of %d killed" % (len(MUTANTS) - survivors, len(MUTANTS)))
    return 1 if survivors else 0


if __name__ == "__main__":
    sys.exit(main())
