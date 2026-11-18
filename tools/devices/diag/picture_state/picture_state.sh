#!/bin/sh
# picture_state.sh: runs the read-only picture state dump (PictureState.java) on a Z9X over adb and saves its text.
#   sh picture_state.sh [-s SERIAL] OUT.txt
# Works on the stock system and on Lumen OS, as the plain adb shell user: no root, nothing installed, no key or
# setting touched. The only write is the temporary /data/local/tmp/picture_state.jar, removed at the end (and
# checked). It never changes adb root: when adbd already runs as root it says so and you run `adb unroot` yourself.
# Needs picture_state.jar next to this script (sh build.sh, or the one of the tester bundle).
set -u
H=$(cd "$(dirname "$0")" && pwd)
S=
if [ "${1:-}" = "-s" ]; then S=$2; shift 2; fi
[ $# = 1 ] || { echo "usage: picture_state.sh [-s SERIAL] OUT.txt" >&2; exit 2; }
OUT=$1
JAR=$H/picture_state.jar
[ -f "$JAR" ] || { echo "picture_state.sh: no $JAR (sh build.sh)" >&2; exit 2; }
T=/data/local/tmp/picture_state.jar
A() { if [ -n "$S" ]; then perl -e 'alarm shift; exec @ARGV' "$1" adb -s "$S" shell "$2"; else perl -e 'alarm shift; exec @ARGV' "$1" adb shell "$2"; fi; }
P() { if [ -n "$S" ]; then perl -e 'alarm 60; exec @ARGV' adb -s "$S" push "$JAR" "$T"; else perl -e 'alarm 60; exec @ARGV' adb push "$JAR" "$T"; fi; }
M=$(A 20 'getprop ro.boot.xgimi.modelname' | tr -d '\r')
[ "$M" = G0082 ] || { echo "picture_state.sh: model '$M' is not G0082 (Z9X), nothing done" >&2; exit 1; }
ID=$(A 20 id | tr -d '\r')
echo "device: $M, $ID" >&2
{
  echo "[context]"
  echo "date=$(date '+%Y-%m-%d %H:%M:%S')"
  echo "id=$ID"
  A 30 'dumpsys window 2>/dev/null | grep -E "mCurrentFocus|mFocusedApp" | head -4' | tr -d '\r' | sed 's/^ *//'
  A 30 'dumpsys media_session 2>/dev/null | grep -E "state=PlaybackState|active=true|package=" | head -12' | tr -d '\r' | sed 's/^ *//'
} > "$OUT.context"
cat "$OUT.context" >&2
P > /dev/null || { echo "picture_state.sh: adb push failed" >&2; exit 1; }
A 240 "CLASSPATH=$T app_process /system/bin PictureState 2>/dev/null" | tr -d '\r' > "$OUT.body"
RC=$?
A 20 "rm -f $T"
LEFT=$(A 20 "ls $T 2>/dev/null" | tr -d '\r')
cat "$OUT.body" "$OUT.context" > "$OUT" && rm -f "$OUT.body" "$OUT.context"
[ -z "$LEFT" ] && echo "removed $T from the projector" >&2 || echo "WARNING: $T is still on the projector, remove it: adb shell rm $T" >&2
case "$ID" in uid=0*) echo "NOTE: adbd runs as root; this tool does not need it (adb unroot)" >&2 ;; esac
grep -q '^# done' "$OUT" || { echo "picture_state.sh: the dump is incomplete (exit $RC), see $OUT" >&2; exit 1; }
echo "saved $OUT: $(grep -c ' = ' "$OUT") HAL rows, $(grep -c ' = ERROR' "$OUT") errors, $(grep -c ' = SKIPPED' "$OUT") skipped" >&2
