#!/bin/sh
# Host tests of the device-neutral updater layer. tools/devices/check.sh
# runs this in the assembled tree (test/*/run.sh, LUMEN_GSI = gsi/); by hand: sh apps/LumenUpdater/test/ota/run.sh
# (the tree is then assembled first, into a temp dir, from the first devices/*/app/LumenUpdater.layers).
#   1. recipe copies: every devices/*/app/LumenUpdater.layers is a byte copy of apps/LumenUpdater/LumenUpdater.layers
#     
#   2. the Target's compressed-snapshot wire ids equal devices/*/ota.conf vabc=yes of the models with this recipe
#      (NeutralTargetTest compares);
#   3. ChannelTest, PreflightHbTest, PreflightGateTest and BetaTest on the assembled neutral updater (Ota and
#      Preflight with the SystemProperties stand-in of apps/core/lumen-updater/test/stubs; the gate probe with a fake
#      gate; the Beta updates switch and no downgrade). The shared code (strings, Outcome, worker, parity) has its
#      own checks: apps/core/lumen-updater/test/run.sh
# Mac only, no device, no network. Writes only a temp dir.
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
G=${LUMEN_GSI:-$(cd "$HERE/../.." && pwd)}
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_updater_test.XXXXXX")
trap 'rm -rf "$OUT"' EXIT

n=0
for r in "$G"/devices/*/app/LumenUpdater.layers; do
  [ -f "$r" ] || continue
  if ! cmp -s "$G/apps/LumenUpdater/LumenUpdater.layers" "$r"; then
    echo "FAIL ${r#"$G"/} is not a byte copy of apps/LumenUpdater/LumenUpdater.layers"
    exit 1
  fi
  n=$((n + 1))
  [ -n "${RCP:-}" ] || RCP=$r
done
[ $n -gt 0 ] || { echo "FAIL no device copy devices/*/app/LumenUpdater.layers"; exit 1; }
echo "recipe copies: $n identical to apps/LumenUpdater/LumenUpdater.layers"
# the Target's compressed-snapshot table = the wire ids of devices/*/ota.conf vabc=yes among the models with this recipe
VABC=$(python3 -B -c 'import glob, os, sys
sys.path.insert(0, os.path.join(sys.argv[1], "tools/devices/ota"))
import ota_conf
ids = []
for r in glob.glob(os.path.join(sys.argv[1], "devices/*/app/LumenUpdater.layers")):
    c = ota_conf.parse(os.path.join(os.path.dirname(os.path.dirname(r)), "ota.conf"))
    if c["vabc"] == "yes":
        ids.append(c["wire_id"])
print(",".join(sorted(ids)))' "$G")
echo "ota.conf vabc=yes: ${VABC:-none}"

T=$OUT/tree
python3 -B "$G/tools/devices/assemble_app.py" --root "$G" "$RCP" --out "$T" --quiet

STUB=$G/apps/core/lumen-updater/test/stubs/android/os/SystemProperties.java
mkdir -p "$OUT/j"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/j" -cp "$HJ:$AJ" -sourcepath "$T/src" "$STUB" \
  "$HERE/test/org/lumen/updater/ChannelTest.java" "$HERE/test/org/lumen/updater/PreflightHbTest.java" \
  "$HERE/test/org/lumen/updater/PreflightGateTest.java" "$HERE/test/org/lumen/updater/BetaTest.java" \
  "$HERE/test/org/lumen/updater/NeutralTargetTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/j:$AJ:$HJ" org.lumen.updater.ChannelTest
"$JAVA_HOME/bin/java" -cp "$OUT/j:$AJ:$HJ" org.lumen.updater.PreflightHbTest
"$JAVA_HOME/bin/java" -cp "$OUT/j:$AJ:$HJ" org.lumen.updater.PreflightGateTest
"$JAVA_HOME/bin/java" -cp "$OUT/j:$AJ:$HJ" org.lumen.updater.BetaTest
"$JAVA_HOME/bin/java" -cp "$OUT/j:$AJ:$HJ" org.lumen.updater.NeutralTargetTest "$VABC"
echo "LumenUpdater host tests: green"
