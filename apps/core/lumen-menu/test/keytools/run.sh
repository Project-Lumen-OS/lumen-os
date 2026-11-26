#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# The shared tool key rule (org.lumen.menu.KeyTools): sh apps/core/lumen-menu/test/keytools/run.sh
#  1. KeyToolsTest: the rule alone, then the REAL window hosts (OverlayHost and Panel of apps/common/projector,
#     ActivityPanelHost, PanelActivity and ToolActivity of lumen-menu) on host stubs of android.* over a fake screen that
#     gives the key focus as WindowManager does. Owner bugs 2026-11-13: quick menu open, focus key held, LEFT moved the
#     menu instead of the lens; the gear on the fine corner adjustment opened the quick menu.
#  2. wiring.py: every projector app sends the gear through the rule and every tool screen and tool panel joins it.
set -eu
T=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$T/../../../../.." && pwd)
M=$G/apps/core/lumen-menu/src
C=$G/apps/common/projector/src
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/keytools.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
find "$T/stubs" -name '*.java' | sed 's/.*/"&"/' > "$OUT/stubs.txt"   # paths with spaces
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" @"$OUT/stubs.txt" \
  "$M/org/lumen/menu/KeyTools.java" "$C/org/lumen/projector/ui/OverlayHost.java" "$C/org/lumen/projector/ui/Panel.java" \
  "$M/org/lumen/projector/ui/PanelHost.java" "$M/org/lumen/projector/ui/ActivityPanelHost.java" \
  "$M/org/lumen/projector/ui/PanelActivity.java" "$M/org/lumen/projector/ui/ToolActivity.java" "$T/KeyToolsTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.ui.KeyToolsTest
python3 -B "$T/wiring.py" "$G"
