#!/bin/sh
# Wire golden of the gmpf clients (GmpfClient, Gmpf2Client and their shared base): the HAL sources of the assembled
# app are compiled against recording stubs of android.os and driven by WireTrace (every public member with fixed
# arguments and reply patterns, then scripted sessions). The trace (every transaction: interface, code, flags, each
# argument word; every result or exception) must equal golden/<codename>.txt, the trace of the clients before they
# shared one body: same transactions, same results, same exception classes (compare.py; exception messages may differ).
#
#   sh apps/core/lumen-gmpf/test/wire/run.sh [horse_popular|goat_elegant|dragon_healthy ...]   compare (default: all)
#   RECORD=1 sh .../run.sh ...       write the golden instead
#   TREE=DIR sh .../run.sh ...       trace another checkout (e.g. a git archive of the base) into OUT=FILE
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../../../.." && pwd)
TREE=${TREE:-$G}
JH=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
W=$(mktemp -d "${TMPDIR:-/tmp}/gmpf_wire.XXXXXX")
trap 'rm -rf "$W"' EXIT
[ $# -gt 0 ] || set -- horse_popular goat_elegant dragon_healthy
RC=0
for D in "$@"; do
  case $D in
    horse_popular) APP=Z9xProjector; DEV=z9x; MAIN=WireTrace ;;
    goat_elegant) APP=HbProjector; DEV=hb; MAIN=WireTrace ;;
    dragon_healthy) APP=Play6Projector; DEV=p6; MAIN=P6WireTrace ;;
    *) echo "wire: unknown codename $D" >&2; exit 2 ;;
  esac
  T=$W/$D
  python3 -B "$TREE/tools/devices/assemble_app.py" --root "$TREE" --device "$D" --app "$APP" --out "$T/tree" --quiet
  H=$T/tree/src/org/lumen/projector/hal
  mkdir -p "$T/stub/org/lumen/projector/hal"
  find "$H" -name '*.java' ! -name HbFeatures.java > "$T/srcs"
  [ -d "$T/tree/src/org/lumen/hal/gmpf" ] && find "$T/tree/src/org/lumen/hal/gmpf" -name '*.java' >> "$T/srcs"
  if [ -f "$H/HbFeatures.java" ]; then
    # the real feature names, every one ON (allOn) or OFF; require() refuses as the real one does
    NAMES=$(awk '/public enum F \{/{f=1;next} f&&/^    \}/{exit} f' "$H/HbFeatures.java" \
      | grep -oE '^        [A-Z][A-Z0-9_]*\(' | tr -d ' (' | paste -sd, -)
    cat > "$T/stub/org/lumen/projector/hal/HbFeatures.java" <<EOF
package org.lumen.projector.hal;
public final class HbFeatures {
    public enum State { ON, OFF, PENDING }
    public enum F { $NAMES }
    static boolean allOn = true;
    public static State state(F f) { return allOn ? State.ON : State.OFF; }
    public static boolean on(F f) { return allOn; }
    static void require(F f, String what) throws AidlCaller.NotOnHbException {
        android.os.Wire.log("GATE " + f + " " + what);
        if (!allOn) throw new AidlCaller.NotOnHbException(what + " (" + f + " is " + state(f) + ")");
    }
}
EOF
    echo "$T/stub/org/lumen/projector/hal/HbFeatures.java" >> "$T/srcs"
  fi
  find "$HERE/stubs" -name '*.java' >> "$T/srcs"
  echo "$HERE/$MAIN.java" >> "$T/srcs"
  [ "$MAIN" = P6WireTrace ] && echo "$HERE/P6Api.java" >> "$T/srcs"
  mkdir -p "$T/classes"
  sed 's/.*/"&"/' "$T/srcs" > "$T/srcs.q"
  "$JH/bin/javac" -encoding UTF-8 -nowarn -proc:none -source 17 -target 17 -d "$T/classes" @"$T/srcs.q" 2> "$T/javac.log" \
    || { cat "$T/javac.log" >&2; echo "wire: $D does not compile" >&2; exit 1; }
  "$JH/bin/java" -cp "$T/classes" org.lumen.projector.hal.$MAIN "$DEV" > "$T/trace.txt"
  GOLD=$HERE/golden/$D.txt
  if [ -n "${OUT:-}" ]; then
    cp "$T/trace.txt" "$OUT.$D.txt"; echo "wire: $D trace -> $OUT.$D.txt ($(grep -c '^C ' "$T/trace.txt") calls)"
  elif [ "${RECORD:-0}" = 1 ]; then
    mkdir -p "$HERE/golden"; cp "$T/trace.txt" "$GOLD"; echo "wire: $D golden recorded ($(grep -c '^C ' "$GOLD") calls)"
  elif cmp -s "$T/trace.txt" "$GOLD"; then
    echo "wire: $D OK, identical to the golden ($(grep -c '^C ' "$GOLD") calls, $(grep -c '^  TX ' "$GOLD") transactions)"
  elif python3 -B "$HERE/compare.py" "$GOLD" "$T/trace.txt" > "$T/compare.txt"; then
    echo "wire: $D OK, $(tail -1 "$T/compare.txt" | sed 's/^compare: //')"
  else
    echo "wire: $D differs from the golden:"; cat "$T/compare.txt"; RC=1
  fi
done
exit $RC
