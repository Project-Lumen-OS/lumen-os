#!/bin/sh
# lumen-ui host tests: sh apps/core/lumen-ui/test/run.sh
#  ThemeTest: the theme choice (Светлая default, Тёмная, Авто by sunrise and sunset), apps/common/tokens.xml equal to
#  Palette.java for both themes, WCAG AA (4.5:1) of every text pair, colour helpers.
#  ThemeFadeTest: the live theme cross fade (ThemeFollow rules with a recording screen) and the wiring of every screen
#  that follows the theme through ThemeFade.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
GSI=$(cd "$HERE/../../.." && pwd)
if [ -z "${JAVA_HOME:-}" ]; then JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17); fi
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_ui.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S="$HERE/src/org/lumen/ui"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$S/SolarCalc.java" "$S/ThemeMode.java" "$S/ThemeRule.java" "$S/Palette.java" "$HERE/test/ThemeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.ui.ThemeTest "$GSI/apps/common/tokens.xml"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" -cp "$OUT" \
  "$S/ThemeFollow.java" "$GSI/apps/core/lumen-testkit/src/org/lumen/testkit/Check.java" "$HERE/test/ThemeFadeTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.ui.ThemeFadeTest "$GSI"
