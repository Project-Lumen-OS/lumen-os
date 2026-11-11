#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Host test of tools/lumen_v1.sh gapps_preflight (the image rule for the boot side of the Google services
# add-on) on its own, without a full 'lint': the function and its helpers are cut out of lumen_v1.sh and run on
# the tree's z9x_gapps.sh, then on copies with one forbidden change each. The tree's script must pass, every
# copy must be refused. bash (lumen_v1.sh is bash), on the Mac, no projector:
#   bash tools/ota/test/gapps_preflight.sh
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
GSI=$(cd "$HERE/../../.." && pwd)
WORK=$(mktemp -d "${TMPDIR:-/tmp}/gapps_preflight.XXXXXX") || exit 1
trap 'rm -rf "$WORK"' EXIT
LV=$GSI/tools/lumen_v1.sh
{
  echo 'die() { echo "DIE: $*"; exit 1; }'
  sed -n '/^need() {/p; /^shlint() {/,/^}/p; /^nocomment() {/p' "$LV"
  sed -n '/^gapps_preflight() {/,/^}/p' "$LV"
  echo 'GAPPS_SH=z9x_gapps.sh; GAPPS_RC=z9x_gapps.rc; GAPPS_ALLOW=gapps_allow.txt; GAPPS_XML=z9x-gapps-sysconfig.xml'
  echo 'gapps_preflight && echo PREFLIGHT_OK'
} > "$WORK/pf.sh"
grep -q '^gapps_preflight() {' "$WORK/pf.sh" || { echo "FAIL: gapps_preflight not found in $LV"; exit 1; }
PASS=0; FAIL=0
prep() {  # NAME: a tree copy with what the preflight reads (gapps_allow.py reads tools/lumen/nogms_remove.txt)
  rm -rf "$WORK/$1"; mkdir -p "$WORK/$1/tools/ota" "$WORK/$1/tools/lumen"
  cp -R "$GSI/tools/ota/image" "$WORK/$1/tools/ota/image"; cp "$GSI/tools/ota/gapps_allow.py" "$WORK/$1/tools/ota/"
  cp "$GSI/tools/lumen/nogms_remove.txt" "$WORK/$1/tools/lumen/"
}
run() { (cd "$WORK" && OTAIMG="$WORK/$1/tools/ota/image" TOOLS="$WORK/$1/tools" bash "$WORK/pf.sh" 2>&1); }
want_ok() {
  prep tree; o=$(run tree)
  case "$o" in *PREFLIGHT_OK*) PASS=$((PASS + 1)); echo "ok   tree passes" ;;
    *) FAIL=$((FAIL + 1)); echo "FAIL tree refused: $o" ;; esac
}
# refused NAME SED-EXPRESSION: the tree's z9x_gapps.sh changed by the expression must be refused
refused() {
  prep "$1"; s=$WORK/$1/tools/ota/image/z9x_gapps.sh
  sed -i.bak "$2" "$s"; rm -f "$s.bak"
  if cmp -s "$s" "$GSI/tools/ota/image/z9x_gapps.sh"; then FAIL=$((FAIL + 1)); echo "FAIL $1: the change did not apply"; return; fi
  o=$(run "$1")
  case "$o" in *PREFLIGHT_OK*) FAIL=$((FAIL + 1)); echo "FAIL $1 passed the preflight" ;;
    *DIE:*) PASS=$((PASS + 1)); echo "ok   $1 refused (${o#*DIE: })" ;;
    *) FAIL=$((FAIL + 1)); echo "FAIL $1: $o" ;; esac
}
want_ok
refused write_elsewhere 's|^glog "ok: \$n files (\$fs)"$|glog "ok: $n files ($fs)"; echo x > "$R/metadata/other"|'
refused log_on_data 's|^LOG=\$R/metadata/z9x_gapps/log.txt|LOG=$R/data/z9x_gapps.log|'
refused log_other_dir 's|^LOG=\$R/metadata/z9x_gapps/log.txt|LOG=$R/metadata/log.txt|'
refused marker_other_dir 's|^OFF=\$R/metadata/z9x_gapps/off|OFF=$R/metadata/off|'
refused extra_rm 's|^glog "ok: \$n files (\$fs)"$|rm -f "$OFF"; glog "ok: $n files ($fs)"|'
refused extra_cp 's|^glog "ok: \$n files (\$fs)"$|cp "$LOG" "$OFF"; glog "ok: $n files ($fs)"|'
refused tee_write 's|^glog "ok: \$n files (\$fs)"$|echo x \| tee "$R/x"; glog "ok: $n files ($fs)"|'
refused append_elsewhere 's|2>/dev/null >> "\$LOG"|2>/dev/null >> "$R/metadata/other.txt"|'
refused rw_mount 's|ro,nosuid,nodev,context=\$CTX "\$dev"|rw,nosuid,nodev,context=$CTX "$dev"|'
refused persist_prop 's|^setprop sys.z9x.gapps ok$|setprop persist.z9x.gapps ok|'
echo "gapps_preflight: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
