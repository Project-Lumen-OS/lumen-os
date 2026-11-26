#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# HB menu checks (the shared projector menu on the HB, apps/core/lumen-menu the design notes): HbMenuGates against the real
# profile devices/goat_elegant/menu.json read by the module's parser (gates per model, game items, tokens, HB wires),
# then the wiring of the adapter, the game menu io, the owner, the keys and the recipe.  sh test/menu/run.sh
# Runs in apps/HbProjector and in the assembled tree (build.sh: LUMEN_GSI set, the module and the profile inside).
set -eu
# in the repository this suite runs inside the HB app as its recipe assembles it (apps/LumenProjector/core.features)
if [ -f "$(dirname "$0")/../../../core.features" ]; then
  G=$(cd "$(dirname "$0")/../../../../.." && pwd); mkdir -p "$G/build"; A=$(mktemp -d "$G/build/hbt.XXXXXX")
  python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --device goat_elegant --app HbProjector --out "$A" --quiet
  rc=0; LUMEN_GSI=$G sh "$A/test/$(basename "$(dirname "$0")")/run.sh" || rc=$?; rm -rf "$A"; exit $rc
fi
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/menu
GSI=${LUMEN_GSI:-$(cd "$HERE/../.." && pwd)}
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}
export JAVA_HOME
# HbMenuGates: the assembled tree, else the HB family adapter of the one projector app
GATES=$HERE/src/org/lumen/projector/panel/HbMenuGates.java
[ -f "$GATES" ] || GATES=$GSI/apps/LumenProjector/vendor/xgimi/aidl-gmpf/src/org/lumen/projector/panel/HbMenuGates.java
MENU=$HERE/src/org/lumen/menu
[ -d "$MENU" ] || MENU=$GSI/apps/core/lumen-menu/src/org/lumen/menu
PROFILE=$GSI/devices/goat_elegant/menu.json
[ -f "$PROFILE" ] || PROFILE=$HERE/assets/lumen_menu/menu.json
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
GSI=$(python3 -B "$GSI/tools/devices/menu_layers.py" --view "$GSI" "$OUT/menu_view")   # menu profiles as readers see them (devices/README.md)
PROFILE=$GSI/devices/goat_elegant/menu.json
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$MENU"/*.java \
  "$GATES" "$T/HbMenuGatesTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.HbMenuGatesTest "$PROFILE"
LUMEN_GSI=$GSI python3 "$T/check_wiring.py" "$HERE"
echo "menu: all passed"
