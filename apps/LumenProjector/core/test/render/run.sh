#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# Z9X render parity (core-menu): the Z9X on the shared menu against the Z9X 1.0.10 quick panel.
#   NewWalk: the lumen-menu tables (MenuSpec, MenuProfile, MenuLabels) + Z9xMenuRules, resolved with the assembled app res/
#   OldWalk: the 1.0.10 QuickPanelController build / apply / handler rules (transcribed, QPC:line) on the 1.0.10
#            PanelRules, ColorTempRule, SoundState, Lamp3d and the 1.0.10 res/
# Both print every tile, popup, page row, option, value and note per locale and scenario; the outputs must be equal
# except the lines listed in deltas.txt (each one a decided 1.1 change, with its reason).
#   sh apps/LumenProjector/core/test/render/run.sh [--keep DIR]
set -eu
T=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$T/../../../../.." && pwd)
REF=${Z9X_REF:-}  # a git ref of the 1.0.10 tree, else ref-1.0.10.tgz (its res/values* and the five rule files, release/z9x-1.0.10 acf6b2f0)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
ROOT=$(python3 -B "$ROOT/tools/devices/menu_layers.py" --view "$ROOT" "$OUT/menu_view")   # menu profiles as readers see them (devices/README.md)
KEEP=""
[ "${1:-}" = "--keep" ] && KEEP=${2:?dir}
trap 'rm -rf "$OUT"' EXIT
fail() { echo "FAIL render: $*"; exit 1; }
MC="$ROOT/apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java"
grep -q 'static final Set<FeatureId> INLINE_RADIOS = set(FeatureId.PROJECTION_MODE);' "$MC" \
  || fail "MenuController.INLINE_RADIOS changed: update NewWalk.INLINE_RADIOS"

# ---- 1.0.10
P=apps/ProjectorBase/src/org/lumen/projector/panel   # the 1.0.10 reference keeps the folder name of that release
mkdir -p "$OUT/old" "$OUT/oc" "$OUT/nc"
if [ -n "$REF" ]; then
  git -C "$ROOT" archive "$REF" apps/ProjectorBase/res "$P/PanelRules.java" "$P/ColorTempRule.java" "$P/SoundState.java" \
    "$P/Lamp3d.java" "$P/KstGameLock.java" | tar -x -C "$OUT/old"
else tar -xzf "$T/ref-1.0.10.tgz" -C "$OUT/old"; fi
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/oc" "$OUT/old/$P"/*.java \
  "$ROOT/apps/core/lumen-menu/src/org/lumen/menu/MenuJson.java" "$T/WalkLib.java" "$T/OldWalk.java"
"$JAVA_HOME/bin/java" -cp "$OUT/oc" org.lumen.projector.panel.OldWalk "$OUT/old/apps/ProjectorBase/res" \
  "$T/scenarios.json" "$T/probes.json" > "$OUT/old.txt"

# ---- the shared menu on the Z9X
# RENDER_TREE / RENDER_PROFILE: an assembled tree and a profile to walk instead (mutate.py)
if [ -n "${RENDER_TREE:-}" ]; then cp -R "$RENDER_TREE" "$OUT/tree"
else python3 -B "$ROOT/tools/devices/assemble_app.py" --root "$ROOT" --device horse_popular --app Z9xProjector --out "$OUT/tree" >/dev/null; fi
PROFILE=${RENDER_PROFILE:-$ROOT/devices/horse_popular/menu.json}
N="$OUT/tree/src/org/lumen/projector/panel"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/nc" "$OUT/tree/src/org/lumen/menu"/*.java \
  "$OUT/tree/src/org/lumen/menu/utils/Texts.java" \
  "$N/Z9xMenuRules.java" "$N/PanelRules.java" "$N/RefusalReason.java" "$N/ColorTempRule.java" "$N/SoundState.java" "$N/Lamp3d.java" \
  "$N/KstGameLock.java" "$T/WalkLib.java" "$T/NewWalk.java"
"$JAVA_HOME/bin/java" -cp "$OUT/nc" org.lumen.projector.panel.NewWalk "$PROFILE" \
  "$N/MenuLabels.java" "$OUT/tree/res" "$T/scenarios.json" "$T/probes.json" > "$OUT/new.txt"

[ -n "$KEEP" ] && { mkdir -p "$KEEP"; cp "$OUT/old.txt" "$OUT/new.txt" "$KEEP/"; }
python3 -B "$T/compare.py" "$OUT/old.txt" "$OUT/new.txt" "$T/deltas.txt"
