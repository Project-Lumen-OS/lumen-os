#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# x50_diag_test.sh: host tests of devices/g0111/image/x50_diag.py on a copy of the Z9X boot logger
# (overlay/v1/z9x_diag.sh = the base image's /system/etc/xgimi/z9x_diag.sh): the edit, its check, refusals.
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
PY=$G/devices/g0111/image/x50_diag.py
SRC=$G/overlay/v1/z9x_diag.sh
W=$(mktemp -d "${TMPDIR:-/tmp}/x50_diag.XXXXXX") || exit 1
trap 'rm -rf "$W"' EXIT
PASS=0 FAIL=0
t() {  # name want-rc cmd...
  n=$1; want=$2; shift 2
  "$@" > "$W/out" 2>&1; rc=$?
  if [ "$rc" = "$want" ]; then PASS=$((PASS + 1)); echo "ok   $n"; else FAIL=$((FAIL + 1)); echo "FAIL $n (exit $rc, want $want)"; sed 's/^/     | /' "$W/out" | tail -4; fi
}
cp "$SRC" "$W/base"; cp "$SRC" "$W/new"
t apply 0 python3 -I "$PY" apply "$W/new"
t check_ok 0 python3 -I "$PY" check "$W/base" "$W/new"
t no_gmpf_main_left 1 grep -q gmpf_main "$W/new"
t watches_xgimi_middle 0 grep -q 'gst=$(gp init.svc.xgimi_middle)' "$W/new"
t pidof_hal_process 0 grep -qF 'pidof xgimi.hardware.gmpf-service' "$W/new"
t facecutout_silenced 0 grep -qF "'Motion_det_ctl:S' 'XGIMI_PERCEPTION_DEBUG:S' 'FaceCutoutBin:S' '*:V'" "$W/new"
t still_shell 0 sh -n "$W/new"
t passive_no_setprop 1 sh -c "grep -v '^[[:space:]]*#' '$W/new' | grep -q setprop"
t apply_twice_refused 1 python3 -I "$PY" apply "$W/new"
t base_unchanged_refused 1 python3 -I "$PY" check "$W/base" "$W/base"
sed 's/xgimi_middle not running/gmpf_main not running/' "$W/new" > "$W/half"
t half_edit_refused 1 python3 -I "$PY" check "$W/base" "$W/half"
sed "s/'FaceCutoutBin:S' //" "$W/new" > "$W/nolog"
t log_filter_missing_refused 1 python3 -I "$PY" check "$W/base" "$W/nolog"
printf '#!/system/bin/sh\necho other\n' > "$W/other"
t other_base_refused 1 python3 -I "$PY" apply "$W/other"
t usage 2 python3 -I "$PY"
t z9x_file_untouched 0 cmp "$SRC" "$W/base"
V=$G/devices/g0111/image/verify_tree_x50.py
t verify_tree_runs_it 0 grep -q 'x50_diag.check(B\[DIAG\], N\[DIAG\])' "$V"
t compose_applies_it 0 grep -qE 'x50_diag.py.apply.[{]sys[}]/etc/xgimi/z9x_diag.sh' "$G/devices/g0111/image/steps.tsv"
echo "x50_diag_test: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
