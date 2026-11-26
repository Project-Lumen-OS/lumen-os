#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# lumen-hdmi host tests: HdmiLink (the HDMI picture rules: signal status, game profile gate, relink decision, EDID
# flip), EdidModes (the stock HDMI mode rows and their vendor values), HdmiRelink and RelinkHost compiled against the
# platform headers, and the wiring of every model (Z9X and HB through source/GmpfRelinkHost, the X50 family through
# X50RelinkHost and its HdmiHal.EDID): sh apps/core/lumen-hdmi/test/run.sh
# HdmiAudioKeys (the stock audio HAL keys of the HDMI input) runs with the rules, HdmiAudio compiles with the relink.
# SourceOff replays test/source_off_traces.tsv (the owner's PS5 rest and power off, Yandex Station cases).
# Targeted mutants: python3 apps/core/lumen-hdmi/test/mutate.py
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
S=$G/apps/core/lumen-hdmi/src/org/lumen/hdmi
AJ=${ANDROID_SDK:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar
HJ=$G/apps/sdk/framework-full-headers.jar
OUT=$(mktemp -d "${TMPDIR:-/tmp}/hdmilink.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/rules" "$OUT/glue"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/rules" "$S/HdmiLink.java" "$S/EdidModes.java" \
  "$S/HdmiAudioKeys.java" "$HERE/HdmiLinkTest.java" "$HERE/EdidModesTest.java" "$HERE/HdmiAudioKeysTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/rules" org.lumen.hdmi.HdmiLinkTest
"$JAVA_HOME/bin/java" -cp "$OUT/rules" org.lumen.hdmi.EdidModesTest
"$JAVA_HOME/bin/java" -cp "$OUT/rules" org.lumen.hdmi.HdmiAudioKeysTest
# SourceOff (the watched HDMI source turned off: the projector follows it) with the live PS5 traces replayed
mkdir -p "$OUT/off"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/off" "$S/SourceOff.java" "$HERE/SourceOffTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/off" org.lumen.hdmi.SourceOffTest "$HERE"
# SleepCec (the TV takes the active source back before the sleep) against the stock sniffer and the Lumen live trace,
# InactiveSleep (the rule behind Family.INACTIVE_SOURCE_SLEEP, off everywhere) on both live PS5 histories
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/off" "$S/CecLog.java" "$S/SleepCec.java" \
  "$S/InactiveSleep.java" "$HERE/SleepCecTest.java" "$HERE/InactiveSleepTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/off" org.lumen.hdmi.SleepCecTest "$HERE"
"$JAVA_HOME/bin/java" -cp "$OUT/off" org.lumen.hdmi.InactiveSleepTest "$HERE"
# HdmiAudio itself in virtual time (the android stubs of test/sim): a recorded session with slow HAL calls injected
mkdir -p "$OUT/replay"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/replay" -sourcepath "$HERE/sim" \
  "$S/HdmiAudioKeys.java" "$S/HdmiAudio.java" "$HERE/HdmiAudioReplayTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/replay" org.lumen.hdmi.HdmiAudioReplayTest "$HERE"
# the Android side compiles on its own (no app class): the host interface is all it needs of a model
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/glue" -cp "$HJ:$AJ" "$S/HdmiLink.java" \
  "$S/EdidModes.java" "$S/RelinkHost.java" "$S/HdmiRelink.java" "$S/HdmiAudioKeys.java" "$S/HdmiAudio.java" \
  "$S/SourceOff.java" "$S/SourceFollow.java" "$S/CecLog.java" "$S/SleepCec.java" "$S/InactiveSleep.java" \
  "$S/SleepSource.java"
echo "HdmiRelink, HdmiAudio and SourceFollow: compile against the platform headers"

# wiring: each line FILE<TAB>TEXT must be found (fixed string) in FILE; FILE<TAB>!TEXT must not be in FILE
fail=0
while IFS='	' read -r f t; do
  [ -n "$f" ] || continue
  case "$t" in
    !*) if grep -qF -- "${t#!}" "$G/$f"; then echo "FAIL wiring: $f still has: ${t#!}"; fail=1; fi ;;
    *) if ! grep -qF -- "$t" "$G/$f"; then echo "FAIL wiring: $f lacks: $t"; fail=1; fi ;;
  esac
done < "$HERE/wiring.tsv"
# one relink in the tree: no app keeps a copy of its own
copies=$(grep -rlF --include=*.java "class HdmiRelink" "$G/apps" | grep -v "/build/" | grep -vF "apps/core/lumen-hdmi/" || true)
if [ -n "$copies" ]; then echo "FAIL a per-app HdmiRelink copy: $copies"; fail=1; fi
# every app that installs the relink carries the module
for r in horse_popular/app/Z9xProjector goat_elegant/app/HbProjector _family/mt9681/app/X50Projector; do
  grep -q '^module    lumen-hdmi$' "$G/devices/$r.layers" || { echo "FAIL devices/$r.layers lacks lumen-hdmi"; fail=1; }
done
# one sleep entry: no shared or projector code calls PowerManager.goToSleep itself (SleepSource sends the
# <Active Source> first)
direct=$(grep -rlE --include=*.java "\.goToSleep\(SystemClock" "$G/apps/core" "$G/apps/LumenProjector" 2>/dev/null \
  | grep -v "/build/" | grep -v "/test/" | grep -vF "apps/core/lumen-hdmi/src/org/lumen/hdmi/SleepSource.java" \
  | grep -vF "apps/LumenProjector/core/src/org/lumen/projector/power/Play6PowerKey.java" || true)
# (Play 6: its recipe has no lumen-hdmi and no lumen-cec, so its power key has no CEC frame to send first)
if [ -n "$direct" ]; then echo "FAIL goToSleep outside SleepSource: $direct"; fail=1; fi
[ $fail -eq 0 ] || exit 1
echo "HdmiLink wiring: $(grep -c . "$HERE/wiring.tsv") lines, 7 recipes, no per-app copy"
