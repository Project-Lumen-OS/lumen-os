#!/bin/bash
# build_check_test.sh: the image builder's dry run (tools/build/build.py check, no build): every descriptor of the
# tree checks, a plan resolves to the published names, and broken descriptors and variants are refused.
#   bash tools/devices/test/build_check_test.sh
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/build_check.XXXXXX") || exit 2
trap 'rm -rf "$W"' EXIT
PASS=0 FAIL=0
t() { # name want command...
  local n=$1 want=$2; shift 2
  "$@" > "$W/out" 2>&1; local rc=$?
  if [ "$rc" = "$want" ]; then PASS=$((PASS + 1)); echo "ok   $n"
  else FAIL=$((FAIL + 1)); echo "FAIL $n (exit $rc, want $want)"; sed 's/^/     | /' "$W/out" | tail -5; fi
}
b() { python3 -B "$G/tools/build/build.py" "$@"; }
has() { # WORDS CODENAME VERSION [OPTION...]: the plan's first line holds WORDS
  local w=$1; shift
  b plan "$@" | head -1 | grep -q -- "$w"
}

t every_descriptor 0 b check
t hb_release_tag 0 has 'tag=hb-1.0.10-beta10-release .*ota_channel=hb-nogms' goat_elegant 1.0.10-beta10 --release
t hb_otatest_channel 0 has ota_channel=hb-test goat_elegant 1.0.10 --release --ota-test
t x50_max_tag 0 has tag=x50max-1.0.10-beta3-pub g0100 1.0.10-beta3
t z9x_version_code 0 has version_code=10012 horse_popular 1.0.10
t z9x_beta1_published 0 has version_code=10013 horse_popular 1.0.11-beta1
t z9x_beta_o1 0 has version_code=1001102 horse_popular 1.0.11-beta2
t z9x_final_o1 0 has version_code=1001199 horse_popular 1.0.11
t play6_gms_tag 0 has tag=t40_gms dragon_healthy t40 --gms
t refuse_pub_without_release 1 b plan goat_elegant 1.0.10
t refuse_usbadb_release 1 b plan goat_elegant t20 --usb-adb --release
t refuse_unknown_option 1 b plan goat_elegant t20 --no-such-option
t refuse_bad_version 1 b plan goat_elegant 1.0.10-gamma1 --release
t refuse_g0100_test 1 b plan g0100 t20
t rs30_net_tag 0 has 'tag=rs30-t2-net .*ota_channel=none' g0155 t2
t rs30_unsigned_tag 0 has 'tag=rs30-t2-unsigned .*flags=test,unsigned' g0155 t2 --unsigned
t refuse_rs30_version 1 b plan g0155 1.0.10
# the RS30 placeholders: a 0-byte file for every allow-listed file without bind=, each after an absent check
rs30_placeholders() {
  python3 -B - "$G/devices/g0155/image/rs30_blobs_allow.txt" "$G/devices/g0155/image/steps.tsv" <<'EOF'
import re, sys
allow = {l.split()[1] for l in open(sys.argv[1]) if re.match(r"^[0-9a-f]{64}\s", l) and " bind=" not in l}
rows = [l.rstrip("\n").split("\t") for l in open(sys.argv[2]) if not l.startswith("#") and l.strip()]
placed = {r[3][len("{sys}/"):] for r in rows if r[1] == "place" and r[2] == "-"}
absent = {r[2][len("{sys}/"):] for r in rows if r[1] == "absent"}
first = {r[2]: i for i, r in reversed(list(enumerate(rows))) if r[1] in ("place", "absent")}
order = all(first["{sys}/" + p] < max(i for i, r in enumerate(rows) if r[1] == "place" and r[3] == "{sys}/" + p) for p in placed)
print("allow", sorted(allow), "placed", sorted(placed), "absent", sorted(absent))
sys.exit(0 if allow and allow == placed == absent and order else 1)
EOF
}
t rs30_placeholders 0 rs30_placeholders

# the parser on a scratch descriptor: a missing key, an unknown key, a bad steps row
python3 -B - "$G/tools/build" "$W" > "$W/py.out" 2>&1 <<'EOF'
import os, shutil, sys
sys.path.insert(0, sys.argv[1])
import recipe
w = sys.argv[2]
good = os.path.join(os.path.dirname(sys.argv[1]), "..", "devices", "g0111", "build.conf")
lines = open(good).read().split("\n")
bad = 0
for name, edit in (("missing_steps", lambda l: [x for x in l if not x.startswith("steps=")]),
                   ("unknown_key", lambda l: l + ["colour=blue"])):
    p = os.path.join(w, name + ".conf")
    open(p, "w").write("\n".join(edit(lines)) + "\n")
    try:
        recipe.Descriptor(p, "g0111")
        print("FAIL %s: accepted" % name); bad += 1
    except recipe.RecipeError as e:
        print("ok   %s: %s" % (name, str(e)[:90]))
p = os.path.join(w, "steps.tsv")
open(p, "w").write("*\n")
try:
    recipe.read_steps(p)
    print("FAIL bad_steps_row: accepted"); bad += 1
except recipe.RecipeError as e:
    print("ok   bad_steps_row: %s" % str(e)[-60:])
# engine ops on scratch files: same (byte for byte), out_eq (a script's exact output)
import engine
class D: raw = {"root": "user", "pack": "erofs"}
class V: d = D(); vals = {}
e = engine.Engine(V(), w)
open(os.path.join(w, "a"), "w").write("x\n"); open(os.path.join(w, "b"), "w").write("x\n"); open(os.path.join(w, "c"), "w").write("y\n")
open(os.path.join(w, "say.py"), "w").write("import sys\nprint(sys.argv[1])\n")
for name, fn, ok in (("same_equal", lambda: e.op_same(os.path.join(w, "a"), "b"), True),
                     ("same_differs", lambda: e.op_same(os.path.join(w, "a"), "c"), False),
                     ("same_missing", lambda: e.op_same(os.path.join(w, "nope"), "b"), False),
                     ("out_eq_equal", lambda: e.op_out_eq("false", "say.py", "false"), True),
                     ("out_eq_differs", lambda: e.op_out_eq("false", "say.py", "true"), False)):
    try:
        fn(); got = True
    except engine.BuildError:
        got = False
    if got == ok:
        print("ok   %s" % name)
    else:
        print("FAIL %s" % name); bad += 1
sys.exit(1 if bad else 0)
EOF
if [ $? = 0 ]; then PASS=$((PASS + 8)); cat "$W/py.out"; else FAIL=$((FAIL + 1)); cat "$W/py.out"; fi
echo "build_check_test: $PASS passed, $FAIL failed"
[ $FAIL = 0 ]
