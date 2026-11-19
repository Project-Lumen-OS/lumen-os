#!/bin/sh
# lumen-aipq host tests: sh apps/core/lumen-aipq/test/run.sh
#  1. AiPqTest: the stock AI picture quality (XRMService AIPQ) on a recording vendor, every path with its writes;
#  2. AiPqDriver (Android): every AiPq call under the one lock with the Android reads, the stock properties and broadcasts;
#  3. the projector apps carry the module and hand their own picture mode writes to it.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_aipq.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
bad=0
S=$HERE/src/org/lumen/aipq
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$S/AiPq.java" "$S/AiPqVendor.java" "$HERE/test/AiPqTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.aipq.AiPqTest || bad=1
cat > "$OUT/locked.exp" <<'EXP'
    private void locked(String what, AiPqVendor v, AiPqTask t) {
        synchronized (aipq) {
            try {
                t.run(new AndroidSide(v));
            } catch (Throwable e) {
                Log.w(tag, what + ": " + e);
            }
        }
    }
EXP
D=$S/AiPqDriver.java
sed -n '/^    private void locked(String what, AiPqVendor v, AiPqTask t) {$/,/^    }$/p' "$D" > "$OUT/locked.got"
if cmp -s "$OUT/locked.exp" "$OUT/locked.got" && grep -q '^        hal.run(v -> locked(what, v, t));$' "$D" \
  && grep -q 'locked("own write " + mode, v, x -> aipq.onTvEvent(x, AiPq.EVENT_PICTURE_MODE' "$D" \
  && grep -q 'PROP_RO = "ro.xgimi.default.aipq", PROP_PERSIST = "persist.xgimi.default.aipq"' "$D" \
  && grep -q 'PROP_MULTIWINDOW = "sys.mw.enable"' "$D" \
  && grep -q 'f.addAction(ACTION_YOUKU_BLURAY)' "$D" && grep -q 'ACTION_AI = "com.xgimi.ai.NOTIFICATION"' "$D"; then
  echo "ok   AiPqDriver: one lock over every AiPq call with the Android reads, the stock properties and broadcasts"
else
  echo "FAIL AiPqDriver: the lock, the Android reads, a stock property or a broadcast changed"
  diff "$OUT/locked.exp" "$OUT/locked.got" | head; bad=1
fi
# the projector apps: the module in every recipe, the own writes handed over (the files exist in a full tree only)
if [ -d "$G/devices/horse_popular" ]; then
  for r in horse_popular/app/Z9xProjector goat_elegant/app/HbProjector dragon_healthy/app/Play6Projector \
      g0111/app/X50Projector g0100/app/X50Projector g0155/app/X50Projector h0061/app/X50Projector h0062/app/X50Projector h0063/app/X50Projector; do
    grep -q '^module *lumen-aipq$' "$G/devices/$r.layers" || { echo "FAIL recipe $r.layers does not name lumen-aipq"; bad=1; }
  done
  for f in apps/ProjectorBase/src/org/lumen/projector/panel/Z9xPictureRows.java \
      apps/HbProjector/src/org/lumen/projector/panel/HbPictureRows.java \
      apps/Play6Projector/src/org/lumen/projector/panel/Play6PictureRows.java; do
    grep -q 'org.lumen.projector.AiScenePolicy.afterOwnWrite(g, ' "$G/$f" || { echo "FAIL $f writes a picture mode past the AI picture quality"; bad=1; }
  done
  [ $bad = 0 ] && echo "ok   the projector apps carry lumen-aipq and hand their picture mode writes to it"
fi
[ $bad = 0 ] && echo "lumen-aipq: all green"
exit $bad
