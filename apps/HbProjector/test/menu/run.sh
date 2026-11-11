#!/bin/sh
# HB menu checks (the shared projector menu on the HB, apps/core/lumen-menu the design notes): HbMenuGates against the real
# profile devices/goat_elegant/menu.json read by the module's parser (gates per model, game items, tokens, HB wires),
# then the wiring of the adapter, the game menu io, the owner, the keys and the recipe.  sh test/menu/run.sh
# Runs in apps/HbProjector and in the assembled tree (build.sh: LUMEN_GSI set, the module and the profile inside).
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/menu
GSI=${LUMEN_GSI:-$(cd "$HERE/../.." && pwd)}
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}
export JAVA_HOME
MENU=$HERE/src/org/lumen/menu
[ -d "$MENU" ] || MENU=$GSI/apps/core/lumen-menu/src/org/lumen/menu
PROFILE=$GSI/devices/goat_elegant/menu.json
[ -f "$PROFILE" ] || PROFILE=$HERE/assets/lumen_menu/menu.json
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$MENU"/*.java \
  "$HERE/src/org/lumen/projector/panel/HbMenuGates.java" "$T/HbMenuGatesTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.HbMenuGatesTest "$PROFILE"
LUMEN_GSI=$GSI python3 "$T/check_wiring.py" "$HERE"
echo "menu: all passed"
