#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The Projection hint picture (owner tester photo 2026-11-13: always the rear picture and text, whatever was chosen):
#   sh apps/core/lumen-menu/test/projection/run.sh [PNG_DIR]
#  1. ProjectionTipTest: the REAL QpdTip drawn offscreen (host stubs of android.graphics / android.text over Java2D):
#     each setup (table / ceiling / auto x front / rear) draws its picture and caption in every language, a changed
#     read-back redraws it, the same one does not, unknown hides it, the All settings copy follows. PNG_DIR: the review
#     renders (one per setup and a sheet).
#  2. The controller wiring the test cannot run: MenuController redraws every projection picture on each apply(),
#     builds it with the state it has, All settings shows a following copy.
set -eu
T=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$T/../../../../.." && pwd)
M=$G/apps/core/lumen-menu
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/projection_tip.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
G=$(python3 -B "$G/tools/devices/menu_layers.py" --view "$G" "$OUT/menu_view")   # menu profiles as readers see them (devices/README.md)
mkdir -p "$OUT/c"
find "$T/stubs" -name '*.java' | sed 's/.*/"&"/' > "$OUT/src.txt"   # paths with spaces
for f in "$M"/src/org/lumen/menu/*.java "$M"/src/org/lumen/menu/utils/*.java "$M/src/org/lumen/projector/panel/QpdTip.java" \
         "$M/src/org/lumen/projector/panel/HintPicture.java" "$T/ProjectionTipTest.java" \
         "$M/src/org/lumen/projector/panel/AdjustStrip.java" "$T/AdjustStripTest.java"; do
  printf '"%s"\n' "$f" >> "$OUT/src.txt"
done
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/c" @"$OUT/src.txt"
bad=0
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -Dtip.scale=1.5 -Dfont.inter="$M/assets/fonts/InterVariable.ttf" \
  -cp "$OUT/c" org.lumen.projector.panel.ProjectionTipTest "$G" ${1:+"$1"} || bad=1
# the stock picture adjustment (4PDA: OK on a picture setting hides the menu, a three row strip at the bottom centre)
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -Dtip.scale=1.5 -Dfont.inter="$M/assets/fonts/InterVariable.ttf" \
  -cp "$OUT/c" org.lumen.projector.panel.AdjustStripTest ${1:+"$1/adjust"} || bad=1
MCW=$(cat "$M/src/org/lumen/projector/panel/MenuController.java")
for w in '        if (adjust().onKey(ev, f)) return true;               // OK on a picture setting: the strip, its UP / DOWN / OK / BACK' \
         '        if (adjust != null) adjust.sync();                    // the strip shows the values read back' \
         '            if (adjust != null && panel == p) adjust.stop();   // closed while adjusting: the strip goes too' \
         '        }, v -> AdjustMode.adjustableKind(v) && pictureSetting(featureOf.get(v)));' \
         '        return f != null && f != FeatureId.LAMP && f.id.startsWith(MenuSpec.PICTURE + ".");' \
         '                    addLampRow(p);                            // the light slider (the lamp page: its tile look opens it)'; do
  printf '%s\n' "$MCW" | grep -qxF -- "$w" || { echo "FAIL wiring: MenuController lost: $w"; bad=1; }
done
grep -qxF '        for (int i = 0; i < root.getChildCount(); i++) root.getChildAt(i).setAlpha(on ? 0f : 1f);' "$M/src/org/lumen/projector/panel/QpdPanel.java" \
  || { echo "FAIL wiring: the adjust strip does not hide the menu panes"; bad=1; }
grep -qxF '        if (root.getBackground() != null) root.getBackground().setAlpha(on ? 0 : 255);' "$M/src/org/lumen/projector/panel/QpdPanel.java" \
  || { echo "FAIL wiring: the adjust strip keeps the scrim (the film is not at full brightness)"; bad=1; }
MC=$M/src/org/lumen/projector/panel/MenuController.java
AL=$M/src/org/lumen/projector/panel/QpdAll.java
body=$(sed -n '/^    private void apply(MenuState st) {$/,/^    }$/p' "$MC")
printf '%s\n' "$body" | grep -qxF '        for (QpdTip t : setupTips) showSetup(t, st);' \
  || { echo "FAIL wiring: apply() does not redraw the projection pictures"; bad=1; }
tipfn=$(sed -n '/^    private View tip(FeatureId f) {$/,/^    }$/p' "$MC")
for w in '        HintPicture kind = HintPicture.of(h);' '        showSetup(t, state);' '        setupTips.add(t);' \
         '        if (kind != HintPicture.PROJECTION) {                 // a picture alone where its text would only explain the row' \
         '            return new QpdTip(app, kind, text == 0 ? null : s(text));'; do
  printf '%s\n' "$tipfn" | grep -qxF -- "$w" || { echo "FAIL wiring: tip() lost: $w"; bad=1; }
done
grep -qF 'lumen_qpd_tip_rear' "$MC" "$AL" && { echo "FAIL wiring: a fixed rear text outside MenuLabels.tipSide"; bad=1; }
grep -qxF '        t.show(s, s == null ? null : Texts.sentences(s(MenuLabels.tipMount(s.mount)), s(MenuLabels.tipSide(s.side()))));' "$MC" \
  || { echo "FAIL wiring: showSetup does not draw the read-back setup with its two sentences"; bad=1; }
grep -qxF '        ProjectionSetup s = ProjectionSetup.of(st);' "$MC" || { echo "FAIL wiring: showSetup not from the read-back"; bad=1; }
[ "$(grep -c 'showSetup(' "$MC")" = 3 ] || { echo "FAIL wiring: showSetup called elsewhere"; bad=1; }
grep -qF 'QpdTip.REAR_PROJECTION' "$MC" "$AL" && { echo "FAIL wiring: the fixed rear picture is back"; bad=1; }
grep -qxF '        QpdTip t = src.copyFor(rowTitle);' "$AL" || { echo "FAIL wiring: All settings card does not follow its hint"; bad=1; }
grep -qxF '        if (!(popupTip instanceof QpdTip) || popupTip.getVisibility() == GONE) {' "$AL" \
  || { echo "FAIL wiring: All settings card shown for a hidden hint"; bad=1; }
[ "$bad" = 0 ] && echo "projection tip: all green" || { echo "projection tip: RED"; exit 1; }
