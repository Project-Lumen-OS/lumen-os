#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The Z9X HB colour space row (panel/ColorSpaceRule, plain Java): shown under an HDR source as stock shows it, hidden
# only in the AI picture modes the vendor refuses, the HDR note only when the read-back disagrees; and ProPicture uses
# the rule. sh test/colorspace/run.sh
set -eu
# in the repository this suite runs inside the HB app as its recipe assembles it (apps/LumenProjector/core.features)
if [ -f "$(dirname "$0")/../../../core.features" ]; then
  G=$(cd "$(dirname "$0")/../../../../.." && pwd); mkdir -p "$G/build"; A=$(mktemp -d "$G/build/hbt.XXXXXX")
  python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device goat_elegant --app HbProjector --out "$A" --quiet
  rc=0; LUMEN_GSI=$G sh "$A/test/$(basename "$(dirname "$0")")/run.sh" || rc=$?; rm -rf "$A"; exit $rc
fi
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/colorspace
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/panel/ColorSpaceRule.java" "$T/ColorSpaceRuleTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.ColorSpaceRuleTest
P=$HERE/src/org/lumen/projector/panel/ProPicture.java
bad=0
# the one ProPicture asks the family (the HB Family of the assembled tree answers with ColorSpaceRule)
F=$HERE/src/org/lumen/projector/Family.java
grep -qF 'showChoice(colorSpace, cs, ok && Family.proColorSpaceShown(st.pictureMode, st.colorSpace));' "$P" \
  && grep -qF 'return org.lumen.projector.panel.ColorSpaceRule.shown(pictureMode, colorSpace);' "$F" \
  || { echo "FAIL ProPicture: the colour space row does not follow ColorSpaceRule.shown"; bad=1; }
grep -qF 'Family.proColorSpace(app, title, g, g2, cs, TAG)' "$P" && grep -qF 'ColorSpaceRule.hdrRefused(' "$F" \
  || { echo "FAIL ProPicture: no HDR note after a refused write"; bad=1; }
if grep -n 'csLocked' "$P"; then echo "FAIL ProPicture: the old HDR lock is back"; bad=1; fi
[ "$bad" = 0 ] && echo "ok   HB colour space row wiring"
exit $bad
