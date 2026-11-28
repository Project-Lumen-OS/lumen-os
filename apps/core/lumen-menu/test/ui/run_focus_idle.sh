#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Manual focus idle walk on an EMULATOR (never a user's projector): builds a test APK of each model with its REAL manual
# focus screen (test/ui/build.sh EXTRA_SRC + EXTRA_ACTIVITIES) and runs test/ui/focus_idle_walk.py (no idle close,
# 2026-11-28: open at 31 s without keys, BACK closes). The X50 copy of the tree answers its model check as a
# G0111 (TREE_PATCH, test APK only), since FocusActivity finishes off an X50.
#   sh apps/core/lumen-menu/test/ui/run_focus_idle.sh SERIAL [OUT]
set -eu
SERIAL=${1:?serial}
OUT=${2:-$(mktemp -d)}
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
: "${OUT:?}"
mkdir -p "$OUT"
# one walk per emulator: two runs on one serial uninstall the screen and press BACK into each other's walk (a base and a
# head run side by side closed the Z9X screen at 15 s, 2026-11-28); fixed path, not TMPDIR, so every job sees the lock
if command -v flock >/dev/null; then
  exec 9>"/tmp/lumen-walk-$SERIAL.lock"
  flock -n 9 || { echo "BUSY: another walk runs on $SERIAL"; exit 2; }
fi
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null </dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null </dev/null
cat > "$OUT/x50_model.sh" <<'EOP'
set -eu
f="$1/src/org/lumen/x50/projector/App.java"
sed -i.bak -e 's|^    static boolean isX50() {$|&\n        if (true) return true;                  // focus idle walk: an X50 on the emulator|' \
  -e 's|^    static String bootModel() {$|&\n        if (true) return "G0111";               // focus idle walk|' "$f"
rm -f "$f.bak"
grep -q 'focus idle walk: an X50' "$f"
EOP
bad=0
# DEVICE:APP:ACTIVITY:SOURCES (comma list: the screen and the views its layout names):PATCH
for m in horse_popular:Z9xProjector:org.lumen.projector.ManualFocusActivity:org/lumen/projector/ManualFocusActivity.java,org/lumen/icons/KeyHintView.java: \
         g0111:X50Projector:org.lumen.x50.projector.FocusActivity:org/lumen/x50/projector/FocusActivity.java:x50; do
  d=${m%%:*}; r=${m#*:}; a=${r%%:*}; r=${r#*:}; act=${r%%:*}; r=${r#*:}; src=${r%%:*}; patch=${r#*:}
  for p in $(cat "$OUT"/*/package 2>/dev/null); do "$ADB" -s "$SERIAL" uninstall "$p" >/dev/null 2>&1 </dev/null || true; done
  EXTRA_SRC=$(echo "$src" | tr , ' ') EXTRA_ACTIVITIES=$act TREE_PATCH=${patch:+$OUT/x50_model.sh} bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null </dev/null
  echo "== $d ($a, $(cat "$OUT/$d/package"))"
  python3 "$HERE/focus_idle_walk.py" "$SERIAL" "$(cat "$OUT/$d/package")" "$act" </dev/null || bad=1
done
exit $bad
