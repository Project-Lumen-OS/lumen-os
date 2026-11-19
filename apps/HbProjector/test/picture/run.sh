#!/bin/sh
# The Z9X HB colour space row (panel/ColorSpaceRule, plain Java): shown under an HDR source as stock shows it, hidden
# only in the AI picture modes the vendor refuses, the HDR note only when the read-back disagrees; and ProPicture uses
# the rule. sh test/picture/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/picture
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/panel/ColorSpaceRule.java" "$T/ColorSpaceRuleTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.ColorSpaceRuleTest
P=$HERE/src/org/lumen/projector/panel/ProPicture.java
bad=0
grep -qF 'showChoice(colorSpace, cs, ok && ColorSpaceRule.shown(st.pictureMode, st.colorSpace));' "$P" \
  || { echo "FAIL ProPicture: the colour space row does not follow ColorSpaceRule.shown"; bad=1; }
grep -qF 'ColorSpaceRule.hdrRefused(' "$P" || { echo "FAIL ProPicture: no HDR note after a refused write"; bad=1; }
if grep -n 'csLocked' "$P"; then echo "FAIL ProPicture: the old HDR lock is back"; bad=1; fi
[ "$bad" = 0 ] && echo "ok   HB colour space row wiring"
exit $bad
