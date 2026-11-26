#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host checks of the Z9X updater data (apps/Z9xUpdater/src/org/lumen/updater/Target.java) on the assembled Z9X
# updater (devices/horse_popular/app/Z9xUpdater.layers): the edition's manifest file (ChannelTest) and the Beta
# updates switch (BetaTest: the channels read, the higher one offered, no downgrade when it is turned off).
# The shared code has its own checks: apps/core/lumen-updater/test/run.sh. sh test/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
STUB=$G/apps/core/lumen-updater/test/stubs/android/os/SystemProperties.java
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device horse_popular --app Z9xUpdater --out "$OUT/tree" --quiet
A=$OUT/tree
for t in ChannelTest BetaTest; do
  mkdir -p "$OUT/$t"
  "$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/$t" -cp "$HJ:$AJ" -sourcepath "$A/src" \
    "$STUB" "$HERE/test/org/lumen/updater/$t.java"
  "$JAVA_HOME/bin/java" -Dlumen.gsi="$G" -cp "$OUT/$t:$AJ:$HJ" org.lumen.updater.$t
done
