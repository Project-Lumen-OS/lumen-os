#!/usr/bin/env python3
"""Compare the two render walks block by block (locale + scenario); deltas.txt lists the decided 1.1 changes:
   '- <line>' a 1.0.10 line that is gone, '+ <line>' a line that is new, '~ <old> => <new>' a changed line,
   '= <start>' a line starting so that moved within its block (same lines, other place);
   '^ <line> => <new> | <new>' new lines right after <line> (that place only: the same lines elsewhere stay compared);
   '<tab># reason' after each. A delta matches in every block, or only in the blocks of the scenarios it names in
   '[<scenario>,<scenario>] ' after the sign. Prints the remaining differences."""
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
gone, added, changed, moved, runs = {}, {}, {}, {}, {}
for raw in open(sys.argv[3], encoding="utf-8").read().splitlines():
    line = raw.split("\t#", 1)[0].rstrip()
    if not line or line.startswith("#"): continue
    sign, body = line[:2], line[2:]
    scopes = [None]
    m = re.match(r"\[([A-Za-z0-9_,]+)\] (.*)", body)
    if m: scopes, body = m.group(1).split(","), m.group(2)
    for scope in scopes:
        if sign == "- ": gone.setdefault(scope, set()).add(body)
        elif sign == "+ ": added.setdefault(scope, set()).add(body)
        elif sign == "~ ":
            a, b = body.split(" => ", 1); changed.setdefault(scope, {})[a] = b
        elif sign == "= ": moved.setdefault(scope, set()).add(body)
        elif sign == "^ ":                                 # (one line may take several runs: one per locale)
            a, b = body.split(" => ", 1); runs.setdefault(scope, {}).setdefault(a, []).append(b.split(" | "))

def scoped(d, scenario, empty):
    out = type(empty)(d.get(None, empty))
    out.update(d.get(scenario, empty))
    return out

bad = 0
if set(old) != set(new):
    print("FAIL render: blocks differ:", sorted(set(old) ^ set(new))[:10]); sys.exit(1)
lines = 0
for k in old:
    sc = k.split(" ", 1)[1] if " " in k else k
    g, ad, ch, mv = scoped(gone, sc, set()), scoped(added, sc, set()), scoped(changed, sc, {}), scoped(moved, sc, set())
    rn = scoped(runs, sc, {})
    is_moved = lambda x: any(x.startswith(s) for s in mv)
    a = [ch.get(x, x) for x in old[k] if x not in g]
    nb, b, i = [x for x in new[k] if x not in ad], [], 0
    while i < len(nb):                                    # a run of new lines right after its line ('^')
        b.append(nb[i])
        for r in rn.get(nb[i], []):
            if nb[i + 1:i + 1 + len(r)] == r:
                i += len(r)
                break
        i += 1
    lines += len(old[k])
    if sorted(filter(is_moved, a)) != sorted(filter(is_moved, b)):
        bad += 1
        print("DIFF [%s] moved lines: 1.0.10 %s, menu %s" % (k, sorted(filter(is_moved, a)), sorted(filter(is_moved, b))))
    a, b = [x for x in a if not is_moved(x)], [x for x in b if not is_moved(x)]
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
      % (len(old), len({k.split()[0] for k in old}), lines,
         sum(len(v) for d in (gone, added, changed, moved, runs) for v in d.values())))
