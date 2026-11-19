#!/bin/sh
# lumen-hdmi host tests: HdmiLink (the HDMI picture rules: signal status, game profile gate, relink decision, EDID
# flip), EdidModes (the stock HDMI mode rows and their vendor values), HdmiRelink and RelinkHost compiled against the
# platform headers, and the wiring of every model (Z9X and HB through source/GmpfRelinkHost, the X50 family through
# X50RelinkHost and its HdmiHal.EDID): sh apps/core/lumen-hdmi/test/run.sh
# HdmiAudioKeys (the stock audio HAL keys of the HDMI input) runs with the rules, HdmiAudio compiles with the relink.
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
# the Android side compiles on its own (no app class): the host interface is all it needs of a model
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/glue" -cp "$HJ:$AJ" "$S/HdmiLink.java" \
  "$S/EdidModes.java" "$S/RelinkHost.java" "$S/HdmiRelink.java" "$S/HdmiAudioKeys.java" "$S/HdmiAudio.java"
echo "HdmiRelink and HdmiAudio: compile against the platform headers"

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
for r in horse_popular/app/Z9xProjector goat_elegant/app/HbProjector g0111/app/X50Projector g0100/app/X50Projector \
         g0155/app/X50Projector h0061/app/X50Projector h0062/app/X50Projector h0063/app/X50Projector; do
  grep -q '^module    lumen-hdmi$' "$G/devices/$r.layers" || { echo "FAIL devices/$r.layers lacks lumen-hdmi"; fail=1; }
done
[ $fail -eq 0 ] || exit 1
echo "HdmiLink wiring: $(grep -c . "$HERE/wiring.tsv") lines, 7 recipes, no per-app copy"
