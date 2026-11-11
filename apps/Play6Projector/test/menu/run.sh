#!/bin/sh
# Play 6 menu checks (the shared projector menu on the Play 6, apps/core/lumen-menu the design notes): Play6MenuGates
# against the real profile devices/dragon_healthy/menu.json read by the module's parser and the wire constants of
# hal/Play6Gmpf and game/GameMode, then the wiring of the adapter, the owner, the keys and the recipe.
#   sh test/menu/run.sh            (apps/Play6Projector or the assembled tree; LUMEN_GSI = the gsi checkout)
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/menu
GSI=${LUMEN_GSI:-$(cd "$HERE/../.." && pwd)}
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}
export JAVA_HOME
MENU=$HERE/src/org/lumen/menu
[ -d "$MENU" ] || MENU=$GSI/apps/core/lumen-menu/src/org/lumen/menu
PROFILE=$GSI/devices/dragon_healthy/menu.json
[ -f "$PROFILE" ] || PROFILE=$HERE/assets/lumen_menu/menu.json
P=$HERE/src/org/lumen/projector
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$MENU"/*.java \
  "$P/panel/Play6MenuGates.java" "$T/Play6MenuGatesTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.Play6MenuGatesTest "$PROFILE" "$P/hal/Play6Gmpf.java" \
  "$P/game/GameMode.java"
LUMEN_GSI=$GSI python3 "$T/check_wiring.py" "$HERE"
echo "menu: all passed"
