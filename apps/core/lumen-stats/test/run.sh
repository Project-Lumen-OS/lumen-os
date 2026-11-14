#!/bin/sh
# Host checks of the shared statistics client (apps/core/lumen-stats): the protocol (Ping: body, rules, channel,
# version code, update fields), the model from the device registry (Models is the registry's, Device.model) and the
# release an image without an updater reports (Device.release), the wiring of every host (the updaters' Stats
# adapter, the projector apps of the Play 6 and the X50 family). sh apps/core/lumen-stats/test/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
python3 -B "$G/tools/stats/gen_models.py" --check
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
S=$HERE/src/org/lumen/stats
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" -cp "$HJ:$AJ" \
  "$HERE/test/stubs/android/os/SystemProperties.java" "$S/Ping.java" "$S/Models.java" "$S/Release.java" "$S/Device.java" \
  "$HERE/test/org/lumen/stats/PingTest.java"
# the stub first: android.jar's SystemProperties is hidden and throws
"$JAVA_HOME/bin/java" -cp "$OUT:$AJ" org.lumen.stats.PingTest "$G"
