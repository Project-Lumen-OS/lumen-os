#!/usr/bin/env python3
"""Compare the two render walks block by block (locale + scenario); deltas.txt lists the decided 1.1 changes:
   '- <line>' a 1.0.10 line that is gone, '+ <line>' a line that is new, '~ <old> => <new>' a changed line;
   '<tab># reason' after each. A delta matches in every block. Prints the remaining differences."""
import re, sys
from difflib import SequenceMatcher

def blocks(path):
    out, cur, name = {}, None, None
    for line in open(path, encoding="utf-8").read().splitlines():
        if line.startswith("### "):
            name = line[4:]; cur = out.setdefault(name, [])
        elif cur is not None:
            cur.append(line)
    return out

old, new = blocks(sys.argv[1]), blocks(sys.argv[2])
gone, added, changed = set(), set(), {}
for raw in open(sys.argv[3], encoding="utf-8").read().splitlines():
    line = raw.split("\t#", 1)[0].rstrip()
    if not line or line.startswith("#"): continue
    if line.startswith("- "): gone.add(line[2:])
    elif line.startswith("+ "): added.add(line[2:])
    elif line.startswith("~ "):
        a, b = line[2:].split(" => ", 1); changed[a] = b
bad = 0
if set(old) != set(new):
    print("FAIL render: blocks differ:", sorted(set(old) ^ set(new))[:10]); sys.exit(1)
lines = 0
for k in old:
    a = [changed.get(x, x) for x in old[k] if x not in gone]
    b = [x for x in new[k] if x not in added]
    lines += len(old[k])
    if a == b: continue
    sm = SequenceMatcher(None, a, b, autojunk=False)
    for op, i1, i2, j1, j2 in sm.get_opcodes():
        if op == "equal": continue
        bad += 1
        if bad <= 60:
            print("DIFF [%s]" % k)
            for x in a[i1:i2]: print("  1.0.10: " + x)
            for x in b[j1:j2]: print("  menu  : " + x)
if bad:
    print("FAIL render: %d difference(s) against 1.0.10" % bad); sys.exit(1)
print("render: %d blocks (%d locales x scenarios), %d lines equal to 1.0.10 (%d decided deltas) - ok"
      % (len(old), len({k.split()[0] for k in old}), lines, len(gone) + len(added) + len(changed)))
