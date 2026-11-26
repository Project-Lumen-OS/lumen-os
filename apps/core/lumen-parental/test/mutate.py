#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of lumen-parental's plain parts: each one edits a copy of the module and test/run.sh must go red.
    python3 apps/core/lumen-parental/test/mutate.py      (exit 1 when a mutant survives or an anchor is gone)"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = "src/org/lumen/parental/"
MUTANTS = {
    "settings_without_lock": (S + "ParentalSettings.java", "if (!s.lockOn || key == null || value == null) return false;",
                              "if (key == null || value == null) return false;"),
    "shown_keeps_salt": (S + "ParentalSettings.java", '!l.startsWith("salt=") && ', ""),
    "read_drops_lock": (S + "ParentalSettings.java", 's.lockOn = shown != null && ("\\n" + shown).contains("\\nlock=1\\n");', ""),
    "any_minutes": (S + "ParentalSettings.java", "                if (o == v) {", "                if (o == v || v > 0) {"),
    "range_any": (S + "ParentalSettings.java", "if (r.token.equals(value)) {", "if (r.token.equals(value) || r == ControlRange.CHILD_ONLY) {"),
    "flag_any": (S + "ParentalSettings.java", "if (!ON.equals(value) && !OFF.equals(value)) return false;", ""),
    "lock_off_keeps": (S + "ParentalSettings.java", "            ParentalRules.lockOff(s);\n", ""),
}


def main():
    bad = 0
    clean = subprocess.run(["bash", os.path.join(HERE, "test/run.sh")], capture_output=True, text=True)
    if clean.returncode != 0:
        print("FAIL the unmutated module is red")
        return 1
    for name, (rel, old, new) in sorted(MUTANTS.items()):
        tmp = tempfile.mkdtemp()
        try:
            mod = os.path.join(tmp, "lumen-parental")
            shutil.copytree(HERE, mod)
            p = os.path.join(mod, rel)
            t = open(p, encoding="utf-8").read()
            if t.count(old) != 1:
                print("BROKEN %s: anchor found %d times" % (name, t.count(old)))
                bad = 1
                continue
            open(p, "w", encoding="utf-8").write(t.replace(old, new))
            r = subprocess.run(["bash", os.path.join(mod, "test/run.sh")], capture_output=True, text=True)
            killed = r.returncode != 0 or " 0 failed" not in r.stdout
            print(("killed   " if killed else "SURVIVED ") + name)
            bad |= 0 if killed else 1
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    return bad


if __name__ == "__main__":
    sys.exit(main())
