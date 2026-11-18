#!/bin/sh
# x50-feedback1 screens on host stubs (no device): sh apps/core/lumen-menu/test/screens/run.sh [PNG_DIR]
#  1. ScreensTest: the REAL QpdTip (focus page remote picture per remote kind), CornerView (four-corner keystone page,
#     stock KeyStoneWind look) and ShiftRangeView over ShiftRange (lens shift range, stock DisplacementCell), drawn
#     offscreen over the projection test's android.graphics stubs; pixel checks, the stock position rule, the texts in
#     every language. PNG_DIR: the review renders (English and Russian).
#  2. Wiring the test cannot run: the focus texts per kind (MenuLabels), the X50 corner and lens screens on the views.
set -eu
T=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$T/../../../../.." && pwd)
M=$G/apps/core/lumen-menu
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/screens.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/c"
find "$M/test/projection/stubs" -name '*.java' | sed 's/.*/"&"/' > "$OUT/src.txt"
for f in "$M"/src/org/lumen/menu/*.java "$M"/src/org/lumen/menu/utils/*.java "$M/src/org/lumen/projector/panel/QpdTip.java" \
         "$M/src/org/lumen/projector/panel/HintPicture.java" "$M/src/org/lumen/projector/panel/CornerView.java" \
         "$M/src/org/lumen/projector/panel/ShiftRangeView.java" "$T/VectorXml.java" "$T/ScreensTest.java"; do
  printf '"%s"\n' "$f" >> "$OUT/src.txt"
done
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/c" @"$OUT/src.txt"
bad=0
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -Dtip.scale=1.5 -Dfont.inter="$M/assets/fonts/InterVariable.ttf" \
  -cp "$OUT/c" org.lumen.projector.panel.ScreensTest "$G" ${1:+"$1"} || bad=1
ML=$M/src/org/lumen/projector/panel/MenuLabels.java
for w in '            case "focus_guide_front": return R.string.lumen_qpd_tip_focus_guide_front;' \
         '            case "focus_guide_side": return R.string.lumen_qpd_tip_focus_guide;'; do
  grep -qxF -- "$w" "$ML" || { echo "FAIL wiring: MenuLabels lost: $w"; bad=1; }
done
QT=$M/src/org/lumen/projector/panel/QpdTip.java
for w in '        else if (kind == HintPicture.FOCUS_FRONT) drawRemoteFocus(c, w, ih, R.drawable.lumen_focus_guide_remote_front);' \
         '        else if (kind == HintPicture.FOCUS_SIDE) drawRemoteFocus(c, w, ih, R.drawable.lumen_focus_guide_remote_side);'; do
  grep -qxF -- "$w" "$QT" || { echo "FAIL wiring: QpdTip lost: $w"; bad=1; }
done
X=$G/apps/X50Projector/src/org/lumen/x50/projector
grep -qxF '        view = new CornerView(this);' "$X/CornerActivity.java" || { echo "FAIL wiring: the X50 corner page is not the CornerView"; bad=1; }
grep -qF 'ShiftRange.of(HAL.shiftPosition(0), HAL.shiftPosition(1), HAL.putMode())' "$X/LensActivity.java" \
  || { echo "FAIL wiring: the X50 shift page does not read the position for the range"; bad=1; }
grep -qxF '            if (!isFinishing()) range.show(now);' "$X/LensActivity.java" \
  || { echo "FAIL wiring: the X50 shift page does not draw the position"; bad=1; }
[ "$bad" = 0 ] && echo "ok   screens: wiring" || exit 1
