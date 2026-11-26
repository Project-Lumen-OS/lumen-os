#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host checks of the Play 6 updater data (apps/Play6Updater/src/org/lumen/updater/Target.java) on the assembled
# Play 6 updater (devices/dragon_healthy/app/Play6Updater.layers): the edition's manifest file and no beta channel
# (ChannelTest). The shared code has its own checks: apps/core/lumen-updater/test/run.sh. sh test/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
STUB=$G/apps/core/lumen-updater/test/stubs/android/os/SystemProperties.java
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device dragon_healthy --app Play6Updater --out "$OUT/tree" --quiet
A=$OUT/tree
grep -q '.device("play6")' "$A/src/org/lumen/updater/Target.java" || { echo "FAIL the assembled Target is not the Play 6 one"; exit 1; }
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/ch" -cp "$HJ:$AJ" -sourcepath "$A/src" \
  "$STUB" "$HERE/test/org/lumen/updater/ChannelTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/ch:$AJ:$HJ" org.lumen.updater.ChannelTest
