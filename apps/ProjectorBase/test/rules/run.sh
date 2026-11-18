#!/bin/sh
# Stock Z9X refusal rules and wire mappings of the quick panel (panel/PanelRules is plain Java) and the Z9X rows of
# the shared picture settings rule (Z9xMenuRules over lumen-menu): sh test/rules/run.sh
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/panel/PanelRules.java" "$HERE/src/org/lumen/projector/panel/RefusalReason.java" "$HERE/src/org/lumen/projector/panel/KstGameLock.java" \
  "$HERE/src/org/lumen/projector/panel/Lamp3d.java" "$HERE/test/rules/PanelRulesTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.PanelRulesTest
# the rows of the stock picture settings pages through the shared PictureSettingsRule (Z9xMenuRules.blocked)
# lumen-menu: merged into an assembled tree (src/org/lumen/menu), else the module next to this app
M="$HERE/src/org/lumen"
[ -d "$M/menu" ] || M="$HERE/../core/lumen-menu/src/org/lumen"
P="$HERE/src/org/lumen/projector/panel"
mkdir -p "$OUT/z"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/z" "$M"/menu/*.java "$M"/menu/utils/*.java \
  "$P/Z9xMenuRules.java" "$P/PanelRules.java" "$P/RefusalReason.java" "$P/ColorTempRule.java" "$P/Lamp3d.java" \
  "$P/KstGameLock.java" "$M/projector/panel/SoundState.java" "$HERE/test/rules/Z9xPictureSettingsTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/z" org.lumen.projector.panel.Z9xPictureSettingsTest
# the closed look of those rows (ProjectorFeatures.locked): the adapter hands the menu the same rule on its last read
grep -qxF '    @Override public Note locked(FeatureId f, MenuState now) { return Z9xMenuRules.locked(f, last); }' "$P/Z9xFeatures.java" \
  || { echo "FAIL locked look: Z9xFeatures does not draw the picture settings rows closed from Z9xMenuRules.locked"; exit 1; }
echo "ok   locked look: Z9xFeatures.locked = Z9xMenuRules.locked on the last read"
