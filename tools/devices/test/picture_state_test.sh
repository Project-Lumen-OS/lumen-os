#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# picture_state_test.sh: the read-only proof of tools/devices/diag/picture_state/PictureState.java.
# Every row of its READS table must be a getter of the stock Z9X proxy table (research/play6/z9x_gmpf_codes.txt):
# same interface, code and method name, a name that starts with get / is / Get, int32-only arguments in the same
# number, and the reply the table reads. No row may carry a code of the never-send power list, the three rows the
# project rules forbid on the Z9X must be gated, and the source must have exactly one transact call and no setter
# name. Also: picture_state_diff.sh on two synthetic dumps. The stock table is stock analysis data that is not in git:
# without it the table part is skipped (the rest still runs).
#   sh tools/devices/test/picture_state_test.sh
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
D=$G/tools/devices/diag/picture_state
W=$(mktemp -d "${TMPDIR:-/tmp}/picture_state_test.XXXXXX")
trap 'rm -rf "$W"' EXIT
python3 -B - "$D/PictureState.java" "$G/research/play6/z9x_gmpf_codes.txt" <<'PY' || exit 1
import os, re, sys
src = open(sys.argv[1], encoding="utf-8").read()
bad = []
body = src[src.index("static final String[][] READS"):]
rows = re.findall(r'\{"(\d)", "(\d+)", "(\w+)", "([\w,]*)", "([\w+:]+)", "([sv])", "([\w-]*)"\},', body[:body.index("};")])
if len(rows) < 80: bad.append("only %d rows parsed" % len(rows))
never = set(int(x) for x in re.search(r"NEVER = \{([\d, ]+)\}", src).group(1).split(","))
if never != {241, 422, 229, 233, 543}: bad.append("NEVER list changed: %s" % sorted(never))
seen = set()
for i, code, name, args, reply, sect, gate in rows:
    k = (i, int(code))
    if k in seen: bad.append("duplicate %s %s" % k)
    seen.add(k)
    if int(code) in never: bad.append("%s %s is on the never-send power list" % (name, code))
    if not re.match(r"(get|is|Get)[A-Z0-9]", name): bad.append("%s %s is not a getter name" % (code, name))
for k, n in ((("1", 194), "isPlayingHDRVideo"), (("1", 275), "getAmbientLight"), (("2", 252), "getCurHwCurrent")):
    g = [r[6] for r in rows if (r[0], int(r[1])) == k]
    if g != ["never-send"]: bad.append("%s must stay gated never-send, is %s" % (n, g))
if len(re.findall(r"\.transact\(", src)) != 1: bad.append("more than one transact call site")
if re.search(r'"(set|enable|start|stop|reset|connect|goTo|wakeUp|factory)[A-Z]\w*"', src): bad.append("a non-getter method name in the source")
if re.search(r"\b(su|setprop|settings put|input |am |pm |cmd )\b", re.sub(r"(?s)/\*.*?\*/|//[^\n]*", "", src)): bad.append("a shell command that is not a read")
table = sys.argv[2]
if not os.path.isfile(table):
    print("skip the stock table part: no %s" % table)
else:
    stock = {}
    for l in open(table, encoding="utf-8"):
        m = re.match(r"(BpHwGmpf2?) (\d+) _hidl_(\w+)\((.*)\) W=\s*(\S*)\s+R=\s*(\S*)\s*$", l.rstrip("\n"))
        if m: stock[("2" if m.group(1).endswith("2") else "1", int(m.group(2)))] = m.groups()[2:]
    want = {"u8": "readUint8", "i8": "readInt8", "bool": "readBool", "i32": "readInt32"}
    for i, code, name, args, reply, sect, gate in rows:
        s = stock.get((i, int(code)))
        if s is None: bad.append("%s %s %s: not in the stock table" % (i, code, name)); continue
        sname, sig, w, r = s
        if sname != name: bad.append("%s %s: stock names it %s, not %s" % (i, code, sname, name))
        n = len([a for a in args.split(",") if a])
        ws = [x for x in w.split(",") if x]
        if ws != ["writeInt32"] * n: bad.append("%s %s %s: stock writes %s, the row sends %d int32" % (i, code, name, w or "nothing", n))
        if reply in want:
            if r != want[reply]: bad.append("%s %s %s: stock reads %s, the row %s" % (i, code, name, r or "a struct", reply))
        elif r or "function<void" not in sig: bad.append("%s %s %s: a struct row, stock reads %s" % (i, code, name, r))
    print("stock table: %d rows checked against %d stock methods" % (len(rows), len(stock)))
for b in bad: print("FAIL " + b)
print("picture_state rows: %d, %s" % (len(rows), "FAIL" if bad else "ok"))
sys.exit(1 if bad else 0)
PY
mk() { printf '# picture_state 1\n[meta]\nuid=%s\n[hal]\nIGmpf  154 getALOnOff() = %s\nIGmpf  177 getDlpLumensLevel() = 10\n[hal-volatile]\nIGmpf  438 getSystemTemperature() = %s\n[props]\n[surfaceflinger]\n[settings]\n[xgimiconfig]\nabc  system.ini\n# done\n[context]\nid=x\n' "$1" "$2" "$3"; }
mk 2000 false 40 > "$W/a"; mk 0 false 55 > "$W/b"; mk 2000 true 40 > "$W/c"
sh "$D/picture_state_diff.sh" "$W/a" "$W/b" > "$W/o1" 2>&1 || { echo "FAIL diff: volatile-only change must be equal"; cat "$W/o1"; exit 1; }
sh "$D/picture_state_diff.sh" "$W/a" "$W/c" > "$W/o2" 2>&1 && { echo "FAIL diff: a [hal] change must be red"; exit 1; }
grep -q '^> IGmpf  154 getALOnOff() = true' "$W/o2" || { echo "FAIL diff: the changed row is not shown"; cat "$W/o2"; exit 1; }
echo "picture_state_diff: ok"
