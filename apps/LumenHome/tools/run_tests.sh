#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Lumen Home JVM tests (SPEC T12 IntentGuard; 1.0.1: hero art layout,
# UI modes 1080p / 2K / 4K, the files Install from USB offers; 1.0.6: the network folders' SMB client)
# on the Mac JDK; android.jar only to link.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
HJ=$HERE/../sdk/framework-full-headers.jar
[ -f "$HERE/build/gen/org/lumen/home/R.java" ] || { echo "build the APK first (needs build/gen/R.java)"; exit 2; }
# -sourcepath pulls in only what the tested classes reference; runtime never calls an android.jar stub
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" -cp "$HJ:$AJ" \
  -sourcepath "$HERE/src:$HERE/build/gen:$HERE/../core/lumen-ui/src" "$HERE"/test/org/lumen/home/data/*.java \
  "$HERE"/test/org/lumen/home/sky/*.java 2>&1 | grep -v "^Note:" || true
"$JAVA_HOME/bin/java" -Dhome.dir="$HERE" -cp "$OUT:$AJ:$HJ" org.lumen.home.data.AllTests
# Install from USB (1.0.1): the files of a stick that are offered; android.jar first (PackageManager classes)
"$JAVA_HOME/bin/java" -cp "$OUT:$AJ:$HJ" org.lumen.home.data.UsbApksTest
# screensaver one-shot decisions (1.0.1)
"$JAVA_HOME/bin/java" -cp "$OUT:$HJ:$AJ" org.lumen.home.sky.DreamDefaultTest
# the sky's bitmaps at 1080p / 2K / 4K (1.0.1); android.jar first: verifying SkyRenderer loads
# android.graphics classes, which the header jar has without code
"$JAVA_HOME/bin/java" -cp "$OUT:$AJ:$HJ" org.lumen.home.sky.SkySizeTest
# living sky (org.lumen.home.sky: SolarCalc + SkyLook, plain JDK, no R.java)
sh "$HERE/test/sky/run.sh"
# Home 2.0 rules (rows, Continue texts, app order, D-pad map; plain JDK)
sh "$HERE/test/home2/run.sh"
# network folders (org.lumen.home.net: MD4, AES-CMAC, NTLMv2, SPNEGO, srvsvc RPC, Smb2 on a loopback fake server;
# plain JDK, no R.java)
sh "$HERE/test/net/run.sh"
# the tabs (Home, Apps on every model, no Projector tab) and the Apps tab (each app once; plain JDK, no R.java)
# Google Play without a Google account: the Google sign-in first (PlayGate; plain JDK, no R.java)
sh "$HERE/test/play/run.sh"
# the weather city search: Latin and Cyrillic names on any UI language, no IP location (CitySearch; plain JDK, no R.java)
sh "$HERE/test/weather/run.sh"
# mouse and touch: a tap focuses the item under it and presses OK (TapGesture, Hit; plain JDK, no R.java)
