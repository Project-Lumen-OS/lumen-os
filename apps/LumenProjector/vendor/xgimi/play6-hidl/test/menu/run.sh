#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Play 6 menu checks (the shared projector menu on the Play 6, apps/core/lumen-menu the design notes): Play6MenuGates
# against the real profile devices/dragon_healthy/menu.json read by the module's parser and the wire values of the
# Play 6 HAL clients (Play6Gmpf, the shared GmpfBase, the value enums) and game/GameMode, then the wiring of the
# adapter, the owner, the keys and the recipe.
#   sh test/menu/run.sh            (the play6-hidl adapter or the assembled tree; LUMEN_GSI = the gsi checkout)
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
T=$HERE/test/menu
GSI=${LUMEN_GSI:-$(cd "$HERE/../../../../.." && pwd)}
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)}
export JAVA_HOME
MENU=$HERE/src/org/lumen/menu
[ -d "$MENU" ] || MENU=$GSI/apps/core/lumen-menu/src/org/lumen/menu
PROFILE=$GSI/devices/dragon_healthy/menu.json
[ -f "$PROFILE" ] || PROFILE=$HERE/assets/lumen_menu/menu.json
P=$HERE/src/org/lumen/projector
# a source of the assembled tree, else of the Play 6 family adapter
src() { if [ -f "$P/$1" ]; then printf '%s' "$P/$1"; else printf '%s' "$GSI/apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/$1"; fi; }
# a source of the assembled tree, else of the core (the Play 6 core feature menu-play6)
core() { if [ -f "$P/$1" ]; then printf '%s' "$P/$1"; else printf '%s' "$GSI/apps/LumenProjector/core/src/org/lumen/projector/$1"; fi; }
# a hal source of the assembled tree, else of its layer in the recipe's order (the Play 6 family adapter first: its
# PictureMode holds the Play 6 values, the XGIMI gmpf one another model's; then lumen-gmpf and the gmpf value types)
hal() {
  for d in "$P/hal" "$GSI/apps/LumenProjector/vendor/xgimi/play6-hidl/src/org/lumen/projector/hal" \
           "$GSI/apps/core/lumen-gmpf/src/org/lumen/projector/hal" "$GSI/apps/LumenProjector/vendor/xgimi/gmpf/src/org/lumen/projector/hal"; do
    [ -f "$d/$1.java" ] && { printf '%s' "$d/$1.java"; return; }
  done
  echo "menu: no hal source $1" >&2; exit 1
}
GM=$(src game/GameMode.java)
HAL=$(hal Play6Gmpf):$(hal GmpfBase):$(hal PictureMode):$(hal AspectRatio):$(hal HdmiRange):$(hal SoundEffect)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
GSI=$(python3 -B "$GSI/tools/devices/menu_layers.py" --view "$GSI" "$OUT/menu_view")   # menu profiles as readers see them (devices/README.md)
PROFILE=$GSI/devices/dragon_healthy/menu.json
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$MENU"/*.java \
  "$(core panel/Play6MenuGates.java)" "$T/Play6MenuGatesTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.Play6MenuGatesTest "$PROFILE" "$HAL" \
  "$GM"
LUMEN_GSI=$GSI python3 "$T/check_wiring.py" "$HERE"
echo "menu: all passed"
