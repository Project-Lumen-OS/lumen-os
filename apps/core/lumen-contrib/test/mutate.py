#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the Sparks client decisions (apps/core/lumen-contrib): what this projector earned (Mine), what the card
shows and whether the report button leads (Shown), the signed ask (Contrib.mine). test/run.sh must fail for each.
Mutates the files IN PLACE and restores them: run it on a frozen copy (a builder clone), never on a tree someone edits.
    python3 apps/core/lumen-contrib/test/mutate.py [NAME_PART...]"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = "src/org/lumen/contrib/"
MUTANTS = [
    (S + "Shown.java", "base code ignored", "if (mine.asked && mine.has) {", "if (mine.asked && mine.has && false) {"),
    (S + "Shown.java", "base code last second", "!mine.code.isEmpty() && mine.expires >= now", "!mine.code.isEmpty() && mine.expires > now"),
    (S + "Shown.java", "base code without code", "if (!mine.taken && !mine.code.isEmpty() && ", "if (!mine.taken && "),
    (S + "Shown.java", "taken not shown", "if (mine.taken) return new Shown(Card.TAKEN", "if (false) return new Shown(Card.TAKEN"),
    (S + "Shown.java", "kept over base none", "} else if (!mine.asked) {", "} else {"),
    (S + "Shown.java", "kept last second", "if (c.code == null || c.expires < now) continue;", "if (c.code == null || c.expires <= now) continue;"),
    (S + "Shown.java", "kept newest", "if (best == null || c.earned < best.earned) best = c;", "if (best == null || c.earned > best.earned) best = c;"),
    (S + "Shown.java", "kept first in the list", "if (best == null || c.earned < best.earned) best = c;", "if (best == null) best = c;"),
    (S + "Shown.java", "kept last in the list", "if (best == null || c.earned < best.earned) best = c;", "best = c;"),
    (S + "Shown.java", "offer after own report", "offers.has(Kind.REPORT) && !reportSent && !mine.reportSent", "offers.has(Kind.REPORT) && !mine.reportSent"),
    (S + "Shown.java", "offer after base report", "offers.has(Kind.REPORT) && !reportSent && !mine.reportSent", "offers.has(Kind.REPORT) && !reportSent"),
    (S + "Shown.java", "offer without lead", 'return new Shown(Card.SEND_INFO, "", 0, 0, false, "", true);', 'return new Shown(Card.SEND_INFO, "", 0, 0, false, "", false);'),
    (S + "Shown.java", "installed before lost", 'return "installed_before".equals(why) ?', 'return !"installed_before".equals(why) ?'),
    (S + "Mine.java", "error status trusted", "if (status != 200) return OFFLINE;", "if (false) return OFFLINE;"),
    (S + "Mine.java", "taken inverted", "(Long) t == 1L", "(Long) t != 1L"),
    (S + "Mine.java", "any code", '!((String) c).matches("([A-Z2-9]{8})?")', "false"),
    (S + "Mine.java", "any link", '|| !(((String) l).isEmpty() || ((String) l).startsWith("https://t.me/"))', ""),
    (S + "Mine.java", "report sent lost", "return sent ? new Mine(true, false,", "return false ? new Mine(true, false,"),
    (S + "Mine.java", "kind unchecked", '!((String) k).matches("report|trial|install")', "false"),
    (S + "Contrib.java", "mine as earn", 'signed("/v1/mine", Kind.MINE,', 'signed("/v1/earn", Kind.MINE,'),
    (S + "Contrib.java", "mine body kind", 'signed("/v1/mine", Kind.MINE,', 'signed("/v1/mine", Kind.REPORT,'),
    (S + "Contrib.java", "mine sent as event", "if (kind == Kind.MINE) return Claim.OFFLINE;", ""),
    (S + "Claim.java", "answer time lost", "return new Claim(code, expires, waiting, link, why, earnedSeconds);", "return new Claim(code, expires, waiting, link, why, earned);"),
]


def run():
    r = subprocess.run(["sh", os.path.join(HERE, "test/run.sh")], capture_output=True, text=True)
    return r.returncode == 0, r.stdout + r.stderr


def main(parts):
    ok, out = run()
    if not ok:
        print("baseline red:\n" + out[-2000:])
        return 2
    killed = total = 0
    for path, name, old, new in MUTANTS:
        if parts and not any(p in name for p in parts):
            continue
        total += 1
        p = os.path.join(HERE, path)
        src = open(p, encoding="utf-8").read()
        if src.count(old) != 1:
            print("ERROR %-28s anchor found %d times" % (name, src.count(old)))
            continue
        try:
            open(p, "w", encoding="utf-8").write(src.replace(old, new))
            green, out = run()
        finally:
            open(p, "w", encoding="utf-8").write(src)
        if "error:" in out and "FAIL" not in out:
            print("ERROR %-28s does not compile" % name)
            continue
        killed += not green
        print("%s %s" % ("KILLED" if not green else "ALIVE ", name))
    print("lumen-contrib mutants: %d of %d killed" % (killed, total))
    return 0 if killed == total else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
