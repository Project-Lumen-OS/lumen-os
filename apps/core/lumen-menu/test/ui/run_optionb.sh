#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# All settings option B key walk on an EMULATOR (never a user's projector): per model the test APK of its real app
# (test/ui/build.sh, MenuWalkActivity) and test/ui/optionb_walk.py in English, dark menu. SHOTS=dir also walks the
# first model in ru / en x light / dark and keeps its screens (General top, Help, General end, For developers).
#   sh apps/core/lumen-menu/test/ui/run_optionb.sh SERIAL OUT DEVICE:APP ...
set -eu
SERIAL=${1:?serial}; OUT=${2:?out}; shift 2
ADB=${ADB:-adb}
export ADB
HERE=$(cd "$(dirname "$0")" && pwd)
"$ADB" -s "$SERIAL" shell settings put global verifier_verify_adb_installs 0 >/dev/null
"$ADB" -s "$SERIAL" shell settings put global package_verifier_enable 0 >/dev/null
bad=0
first=
for m in "$@"; do
  d=${m%%:*} a=${m#*:}
  bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null
  p=$(cat "$OUT/$d/package")
  "$ADB" -s "$SERIAL" shell cmd locale set-app-locales "$p" --locales en >/dev/null
  "$ADB" -s "$SERIAL" shell settings put secure lumen_ui_theme dark
  echo "== $d ($a, $p)"
  python3 "$HERE/optionb_walk.py" "$SERIAL" "$p" en || bad=1
  [ -n "$first" ] || first=$m
done
if [ -n "${SHOTS:-}" ] && [ -n "$first" ]; then
  d=${first%%:*} a=${first#*:}
  bash "$HERE/build.sh" "$OUT/$d" "$d" "$a" >/dev/null
  "$ADB" -s "$SERIAL" install -r -g "$OUT/$d/walk.apk" >/dev/null
  p=$(cat "$OUT/$d/package")
  mkdir -p "$SHOTS"
  for lang in ru en; do
    for th in light dark; do
      "$ADB" -s "$SERIAL" shell cmd locale set-app-locales "$p" --locales $lang >/dev/null
      "$ADB" -s "$SERIAL" shell settings put secure lumen_ui_theme $th
      SHOTS="$SHOTS/${d}_${lang}_$th" python3 "$HERE/optionb_walk.py" "$SERIAL" "$p" $lang > "$SHOTS/${d}_${lang}_$th.log" 2>&1 || bad=1
      echo "$lang $th: $(tail -1 "$SHOTS/${d}_${lang}_$th.log")"
    done
  done
fi
exit $bad
