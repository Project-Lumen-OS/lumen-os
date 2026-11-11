#!/bin/sh
# gen_codes_test.sh: tools/hal/gen_codes.py on this tree (every AIDL model's table = base + its delta) and on broken
# scratch copies, each of which must be red: a changed base row, a delta row lost, a removal of a row the base lacks,
# an unreadable delta line.
#   sh tools/hal/gen_codes_test.sh
set -u
G=$(cd "$(dirname "$0")/../.." && pwd)
W=$(mktemp -d "${TMPDIR:-/tmp}/gen_codes_test.XXXXXX")
trap 'rm -rf "$W"' EXIT
PASS=0 FAIL=0
expect() { # name want command...
  n=$1 want=$2; shift 2
  "$@" > "$W/out" 2>&1; got=$?
  if [ "$got" = "$want" ]; then PASS=$((PASS + 1)); echo "ok   $n: $(tail -1 "$W/out")"
  else FAIL=$((FAIL + 1)); echo "FAIL $n (exit $got, want $want)"; sed 's/^/     /' "$W/out"; fi
}
copy() { rm -rf "$W/g"; mkdir -p "$W/g"; (cd "$G" && git ls-files devices tools/devices tools/hal | grep -v '^devices/.*/\(research/stock\|logs\)/' \
  | tar -cf - -T -) | tar -xf - -C "$W/g"; }
edit() { python3 -B - "$@" <<'PY'
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
assert s.count(old) >= 1, (p, old)
open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}
expect tree 0 python3 -B "$G/tools/hal/gen_codes.py" --check
copy; edit "$W/g/devices/_hal/gmpf-aidl/base.tsv" "getProductName" "getProductNameX"
expect base_row_changed 1 python3 -B "$W/g/tools/hal/gen_codes.py" --check
copy; edit "$W/g/devices/g0111/hal/codes.delta.tsv" "+	DisplayManager	208" "#	DisplayManager	208"
expect delta_row_lost 1 python3 -B "$W/g/tools/hal/gen_codes.py" --check
copy; printf -- '-\tNoSuchManager\t1\n' >> "$W/g/devices/goat_elegant/hal/codes.delta.tsv"
expect removal_of_missing_row 1 python3 -B "$W/g/tools/hal/gen_codes.py" --check
copy; printf 'frobnicate\n' >> "$W/g/devices/goat_elegant/hal/codes.delta.tsv"
expect unreadable_delta 1 python3 -B "$W/g/tools/hal/gen_codes.py" --check
echo "gen_codes_test: $PASS passed, $FAIL failed"
[ $FAIL = 0 ]
