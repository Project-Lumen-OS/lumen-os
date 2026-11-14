#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutants of lumen_stats.py that test_lumen_stats.py must kill (each one a counting rule of the server): python3
tools/stats/server/mutate_lumen_stats.py. Every anchor must be in the file once; exit 1 on a surviving mutant."""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MUTANTS = [
    ("serial dedupe", 'DEVICE = "COALESCE(NULLIF(serial_hash, \'\'), id_hash)"', 'DEVICE = "id_hash"'),
    ("installed row first", "PICK = \"(kind = 'installed') DESC, version_code DESC, id_hash\"",
     "PICK = \"(kind = 'installed') ASC, version_code DESC, id_hash\""),
    ("channel from the version", 'p["channel"] = channel_of(p["version"])', 'p["channel"] = obj["channel"]'),
    ("prerelease rule", 'PRERELEASE_RE = re.compile(r"-(beta|rc)[0-9]*$")', 'PRERELEASE_RE = re.compile(r"-(beta)[0-9]*$")'),
    ("codename by model", "return FAMILIES[m].get(xgimi_model, m)", "return m"),
    ("play6 alias", '"play6": "dragon_healthy"', '"play6x": "dragon_healthy"'),
    ("trial kind", 'p["kind"] = "" if trial is None else ("trial" if trial else "installed")',
     'p["kind"] = "" if trial is None else "installed"'),
    ("daily one row per projector", '") AS rn FROM pings2 WHERE day >= ?) WHERE rn = 1 GROUP BY 1, 2, 3, 4 ORDER BY 1"',
     '") AS rn FROM pings2 WHERE day >= ?) GROUP BY 1, 2, 3, 4 ORDER BY 1"'),
    ("new = first day of a projector", '"WHERE rn = 1 AND day >= ? GROUP BY 1, 2 ORDER BY 1"', '"WHERE day >= ? GROUP BY 1, 2 ORDER BY 1"'),
    ("updates without first", "\"day >= ? AND method != 'first' GROUP BY 1, 2 ORDER BY 1\"", "\"day >= ? GROUP BY 1, 2 ORDER BY 1\""),
    ("pairs per projector", '"FROM updates2 WHERE day >= ? AND from_code > 0 GROUP BY 1, 2, 3, 4 "',
     '"FROM updates2 WHERE day >= ? GROUP BY 1, 2, 3, 4 "'),
    ("migration backup", "self.backup = backup(self.db, db, now)", "self.backup = None"),
    ("migration channel", 'self.db.execute("UPDATE pings2 SET channel = ? WHERE version = ?", (channel_of(v), v))', "pass"),
    ("migration model", 'self.db.execute("UPDATE pings2 SET model = ? WHERE model = ? AND xgimi_model = ?", (codename(m, x), m, x))', "pass"),
    ("migration version mark", 'self.db.execute("PRAGMA user_version = %d" % SCHEMA_VERSION)', "pass"),
    ("units of the window", 'a = {"since": since, "units": tot[0], "ids": tot[1]}', 'a = {"since": since, "units": tot[1], "ids": tot[0]}'),
    ("trial accepted only as a boolean", "if trial is not None and not isinstance(trial, bool):", "if False:"),
]


def main():
    src = open(os.path.join(HERE, "lumen_stats.py")).read()
    bad = [n for n, a, _ in MUTANTS if src.count(a) != 1]
    if bad:
        print("BROKEN anchors: %s" % ", ".join(bad))
        return 2
    tmp = tempfile.mkdtemp()
    try:
        shutil.copy(os.path.join(HERE, "test_lumen_stats.py"), tmp)
        alive = []
        for n, a, b in [("unmutated", "", "")] + MUTANTS:
            open(os.path.join(tmp, "lumen_stats.py"), "w").write(src.replace(a, b, 1) if a else src)
            r = subprocess.run([sys.executable, "-B", os.path.join(tmp, "test_lumen_stats.py")], capture_output=True,
                               text=True, env=dict(os.environ, LUMEN_GSI=os.path.join(HERE, "..", "..", "..")))
            if not a:
                if r.returncode != 0:
                    print("FAIL unmutated: " + r.stderr[-800:])
                    return 1
                continue
            print("%-36s %s" % (n, "killed" if r.returncode else "ALIVE"))
            if r.returncode == 0:
                alive.append(n)
        print("mutants: %d killed, %d alive" % (len(MUTANTS) - len(alive), len(alive)))
        return 1 if alive else 0
    finally:
        shutil.rmtree(tmp)


if __name__ == "__main__":
    sys.exit(main())
