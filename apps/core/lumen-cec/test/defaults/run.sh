#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Host checks of the stock HDMI-CEC defaults (apps/core/lumen-cec CecDefaults, CecSetting, CecStockDefaults): the real
# table of every image, the parser, the one-time step over a fake unit, the same settings as the image tool
# (tools/devices/cec_defaults.py) and the wiring; the Android glue (CecStockDefaults, CecSettings) compiles against the
# platform headers. sh apps/core/lumen-cec/test/defaults/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/cecdefaults.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
G=$(python3 -B "$G/tools/devices/menu_layers.py" --view "$G" "$OUT/menu_view")   # menu profiles as readers see them (devices/README.md)
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
C=$HERE/src/org/lumen/cec
P=$HERE/src/org/lumen/projector/cec
mkdir -p "$OUT/glue" "$OUT/rules"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/glue" -cp "$HJ:$AJ" "$C/CecRow.java" "$C/CecSetting.java" \
  "$C/CecDefaults.java" "$P/CecSettings.java" "$P/NightGuard.java" "$P/CecStockDefaults.java" "$G/apps/core/lumen-menu/src/org/lumen/menu/MenuJson.java"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/rules" "$C/CecRow.java" "$C/CecSetting.java" "$C/CecDefaults.java" \
  "$G/apps/core/lumen-testkit/src/org/lumen/testkit/Check.java" "$HERE/test/defaults/org/lumen/cec/CecDefaultsTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/rules" org.lumen.cec.CecDefaultsTest "$G"
