#!/bin/sh
# Host checks of the command core and its phone remote adapter (apps/core/lumen-control): the per-model table is the
# generator's, the stock protocol answers replayed field for field, the key tables, TCP framing, the ucontrolMDNS key
# text, the LAN filter and the sockets end to end on loopback; the Android driver compiles against the SDK.
# sh apps/core/lumen-control/test/run.sh   (ANDROID_SDK and JAVA_HOME as for apps/core/lumen-stats/test/run.sh)
# Targeted mutants: python3 apps/core/lumen-control/test/mutate.py -j 4
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
python3 -B "$G/tools/control/gen_vcontrol.py" --check
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
ST=$G/apps/core/lumen-stats/src/org/lumen/stats
find "$HERE/src" "$HERE/test/org" -name '*.java' > "$OUT/sources"
printf '%s\n' "$ST/Device.java" "$ST/Models.java" "$ST/Release.java" "$ST/Ping.java" >> "$OUT/sources"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -Xlint:all -Werror -Xlint:-options -Xlint:-classfile -proc:none -source 11 -target 11 \
  -d "$OUT/classes" -cp "$HJ:$AJ" @"$OUT/sources"
"$JAVA_HOME/bin/java" -cp "$OUT/classes:$AJ" org.lumen.control.vcontrol.VcontrolTest "$G"
