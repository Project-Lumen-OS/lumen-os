#!/bin/bash
# check_aidl_hal_unban_test.sh: the per-app unban list and the argument ranges of tools/devices/check_aidl_hal.py
# (t20, decision 2026-11-10: the X50 menu apps/X50Projector may send correctKeystone 11, setTiltShiftMove 88 and
# autoFocus 3, BANNED rows of devices/goat_elegant/research/banned.tsv, each only with its argument range;
# devices/g0111/research/unbanned_x50projector.tsv).
#
#   bash tools/devices/test/check_aidl_hal_unban_test.sh
#
# Must pass: the menu with its unban list. Must fail: the menu without it (rule 4 for each of the three); the eye guard
# apps/X50EyeGuard (or the menu's sources under another app name) given a 11 Call and the menu's list (the list is for
# another app: nothing of it applies); a 11 Call whose range differs from the list, or has none; a list row whose
# range differs from the allow list, a REVIEW row (56) in the list, a list row that is no Call; a malformed range and a
# Call range that differs from its allow-list row (rule 8). Registry: check_hal.py passes --unbanned only to the menu's
# run (never to X50EyeGuard, never to another device), devices.py validate refuses a hal_unbanned entry for an app that is no HAL
# app of the device or with another file name, and no other device has hal_unbanned. The tree is only read.
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
R=$G/devices/g0111/research
B=$G/devices/goat_elegant/research/banned.tsv
MOD=$G/apps/core/lumen-hal
W=$(mktemp -d "${TMPDIR:-/tmp}/unban_test.XXXXXX")
trap 'rm -rf "$W"' EXIT
PASS=0 FAIL=0
ok() { PASS=$((PASS + 1)); echo "ok   $1"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL $1"; sed 's/^/     | /' "$W/out" | tail -6; }
# chk APPDIR ALLOWED [UNBANNED]
chk() {
  local extra=()
  [ -n "${3:-}" ] && extra=(--unbanned "$3")
  python3 -B "$G/tools/devices/check_aidl_hal.py" --app "$1" --codes "$R/aidl_codes.tsv" --banned "$B" --allowed "$2" \
    --module "$MOD" ${extra[@]+"${extra[@]}"} > "$W/out" 2>&1
}
expect() { # name want_rc [text]
  local rc=$RC
  if [ "$rc" = "$2" ] && { [ -z "${3:-}" ] || grep -qF -- "$3" "$W/out"; }; then ok "$1"; else bad "$1 (exit $rc, want $2${3:+, '$3'})"; fi
}
sub() { # FILE OLD NEW: replace the first OLD
  python3 - "$1" "$2" "$3" <<'PY' || { echo "text not found in $1"; exit 3; }
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
if old not in s:
    sys.exit(1)
open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}
menu() { rm -rf "$W/X50Projector"; mkdir -p "$W/X50Projector"; cp -R "$G/apps/X50Projector/src" "$W/X50Projector/src"
         cp "$R/allowed_x50projector.tsv" "$W/allowed.tsv"; cp "$R/unbanned_x50projector.tsv" "$W/unbanned_x50projector.tsv"; }
GM=src/org/lumen/x50/projector/GmpfCall.java
UN=$W/unbanned_x50projector.tsv

menu; chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect menu_with_its_list 0 "OK: 101 calls"
menu; chk "$W/X50Projector" "$W/allowed.tsv"; RC=$?; expect menu_without_list_11 1 "Call.CORRECT_KEYSTONE: DisplayManager 11 correctKeystone is banned"
grep -qF "Call.SET_TILT_SHIFT_MOVE: ProjectorFocusManager 88 setTiltShiftMove is banned" "$W/out" && grep -qF "Call.AUTO_FOCUS: ProjectorFocusManager 3 autoFocus is banned" "$W/out" \
  && ok menu_without_list_88_3 || bad menu_without_list_88_3

# the eye guard with a 11 Call, its own allow row and the menu's list: refused (the list names another app)
rm -rf "$W/X50EyeGuard"; mkdir -p "$W/X50EyeGuard"; cp -R "$G/apps/X50EyeGuard/src" "$W/X50EyeGuard/src"
cp "$R/allowed_x50eyeguard.tsv" "$W/eye_allowed.tsv"
EG=$(grep -rl 'enum GmpfCall implements AidlTransport.Method' "$W/X50EyeGuard/src" | head -1)
python3 - "$EG" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
m = re.search(r'(enum GmpfCall implements AidlTransport\.Method \{\n)', s)
s = s[:m.end()] + '    CORRECT_KEYSTONE(GmpfIface.DISPLAY, 11, "correctKeystone", "50"),\n' + s[m.end():]
open(p, "w", encoding="utf-8").write(s)
PY
printf 'DisplayManager\t11\tcorrectKeystone\tint(byte)\targs:50\ttest\n' >> "$W/eye_allowed.tsv"
grep -q 'DISPLAY(' "$EG" && grep -q 'String args()' "$EG" || true
chk "$W/X50EyeGuard" "$W/eye_allowed.tsv" "$R/unbanned_x50projector.tsv"; RC=$?
expect eyeguard_cannot_use_menu_list 1 "is for apps/X50Projector, not for X50EyeGuard"
grep -qF "Call.CORRECT_KEYSTONE: DisplayManager 11 correctKeystone is banned" "$W/out" && ok eyeguard_11_still_banned || bad eyeguard_11_still_banned

# the menu's own sources under another app name: refused as well
menu; rm -rf "$W/OtherApp"; mv "$W/X50Projector" "$W/OtherApp"
chk "$W/OtherApp" "$W/allowed.tsv" "$UN"; RC=$?; expect other_app_name_refused 1 "is for apps/X50Projector, not for OtherApp"

# the Call's range must be the list's
menu; sub "$W/X50Projector/$GM" '"correctKeystone", "50")' '"correctKeystone", "50;51")'
chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect wider_range_refused 1 "unbanned only with args '50', the Call has '50;51'"
menu; sub "$W/X50Projector/$GM" '"correctKeystone", "50")' '"correctKeystone")'
chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect no_range_refused 1 "unbanned only with args '50', the Call has None"
menu; sub "$W/X50Projector/$GM" '"setTiltShiftMove", "1..5,f:0;13,f:0")' '"setTiltShiftMove", "1..13,f:0")'
chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect shift_range_widened_refused 1 "Call.SET_TILT_SHIFT_MOVE: ProjectorFocusManager 88 setTiltShiftMove is banned"
# the list's range must be the allow list's
menu; sub "$UN" $'correctKeystone\targs:50\t' $'correctKeystone\targs:0..100\t'
chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect list_range_not_allow_range 1 "args '0..100' is not the allow list's args '50'"
# only BANNED rows can be unbanned, and every row must be used
menu; printf 'apps/X50Projector\tDisplayManager\t56\tgenerateLogoByTrapezoid\targs:0\ttest\n' >> "$UN"
chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect review_row_not_unbannable 1 "56 generateLogoByTrapezoid is no BANNED row"
menu; printf 'apps/X50Projector\tProjectorFocusManager\t89\tsetTiltShiftSpeed\targs:0,0\ttest\n' >> "$UN"
chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect unused_row_refused 1 "89 setTiltShiftSpeed is no Call of the app"
menu; printf 'apps/X50EyeGuard\tDisplayManager\t11\tcorrectKeystone\targs:50\ttest\n' >> "$UN"
chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect mixed_app_list_refused 1 "is for apps/X50EyeGuard, not for X50Projector"
# rule 8: ranges well formed and equal to the allow list
menu; sub "$W/X50Projector/$GM" '"setDlpLumensLevel", "1..10")' '"setDlpLumensLevel", "10..1")'
sub "$W/allowed.tsv" $'setDlpLumensLevel\tvoid(byte)\targs:1..10\t' $'setDlpLumensLevel\tvoid(byte)\targs:10..1\t'
chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect malformed_range_refused 1 "rule 8: Call.SET_DLP_LUMENS_LEVEL: bad range"
menu; sub "$W/X50Projector/$GM" '"setDlpLumensLevel", "1..10")' '"setDlpLumensLevel", "1..11")'
chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect range_differs_from_row 1 "rule 8: Call.SET_DLP_LUMENS_LEVEL: args '1..11', the allowed.tsv row has '1..10'"
menu; sub "$W/allowed.tsv" $'zoomStart\tint(int)\targs:0\t' $'zoomStart\tint(int)\t'
chk "$W/X50Projector" "$W/allowed.tsv" "$UN"; RC=$?; expect row_without_range_refused 1 "rule 8: Call.ZOOM_START: args '0', the allowed.tsv row has None"

# registry: check_hal passes the list to the menu only; devices.py refuses a misplaced entry
python3 -B - "$G" > "$W/out" 2>&1 <<'PY'
import os, shutil, sys, tempfile
G = sys.argv[1]
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import check_hal, devices
bad = []
reg = devices.load(G)
d = reg["g0111"]
cm = check_hal.command(G, d, app=os.path.join(G, "apps/X50Projector"), allowed="devices/g0111/research/allowed_x50projector.tsv",
                       app_rel="apps/X50Projector")
ce = check_hal.command(G, d, app=os.path.join(G, "apps/X50EyeGuard"), allowed=d["hal_allowed"], app_rel="apps/X50EyeGuard")
if "--unbanned" not in cm or not cm[cm.index("--unbanned") + 1].endswith("devices/g0111/research/unbanned_x50projector.tsv"):
    bad.append("the menu's check gets no --unbanned: %s" % cm)
if "--unbanned" in ce:
    bad.append("the eye guard's check gets --unbanned: %s" % ce)
# the X50 family apps: the X50 Ultra Max g0100 (2026-11-11) and the RS30 Ultra Max g0155 (2026-11-12) run the same menu
# with the same exceptions, row for row
X50 = ("g0111", "g0100", "g0155")
rows = lambda f: [l for l in open(os.path.join(G, f), encoding="utf-8").read().splitlines() if l.strip() and not l.startswith("#")]
for c in X50[1:]:
    if rows("devices/%s/research/unbanned_x50projector.tsv" % c) != rows("devices/g0111/research/unbanned_x50projector.tsv"):
        bad.append("the %s exceptions are not the g0111 rows" % c)
for c, v in sorted(reg.items()):
    if c not in X50 and v.get("hal_unbanned"):
        bad.append("%s has hal_unbanned %s" % (c, v["hal_unbanned"]))
    if c not in X50 and v.get("hal_check") == "aidl-table":
        for app, _ in check_hal.entries(v):
            if "--unbanned" in check_hal.command(G, v, app=os.path.join(G, app), app_rel=app):
                bad.append("%s %s gets --unbanned" % (c, app))
# devices.py: a copy of the registry with a wrong entry must not load
t = tempfile.mkdtemp()
try:
    shutil.copytree(os.path.join(G, "devices"), os.path.join(t, "devices"))
    for a in ("apps/X50Projector", "apps/X50EyeGuard", "apps/X50PowerOverlay", "apps/LumenDsuInstall", "apps/X50TvInput"):
        if os.path.isdir(os.path.join(G, a)):
            os.makedirs(os.path.join(t, a), exist_ok=True)
    conf = os.path.join(t, "devices/g0111/device.conf")
    base = open(conf, encoding="utf-8").read()
    for name, old, new, want in [
        ("eye guard entry", "hal_unbanned=apps/X50Projector:", "hal_unbanned=apps/X50EyeGuard:", "the list must be"),
        ("not a HAL app", "hal_unbanned=apps/X50Projector:", "hal_unbanned=apps/X50PowerOverlay:", "is not a HAL app"),
        ("other file name", "unbanned_x50projector.tsv", "unbanned_other.tsv", "the list must be")]:
        open(conf, "w", encoding="utf-8").write(base.replace(old, new, 1))
        errs = devices.validate(t)[0]
        if not any("hal_unbanned" in e and want in e for e in errs):
            bad.append("devices.py validate accepted the %s: %s" % (name, [e for e in errs if "hal_unbanned" in e]))
    open(conf, "w", encoding="utf-8").write(base)
    if any("hal_unbanned" in e for e in devices.validate(t)[0]):
        bad.append("devices.py validate refuses the real entry")
except Exception as e:
    bad.append("registry copy: %r" % e)
finally:
    shutil.rmtree(t, ignore_errors=True)
print("\n".join(bad) or "registry: OK")
sys.exit(1 if bad else 0)
PY
RC=$?; expect registry_menu_only 0 "registry: OK"

echo "check_aidl_hal_unban_test: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
