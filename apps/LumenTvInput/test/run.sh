#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# LumenTvInput host tests: Route (one routing call per session that becomes main, replay of LR-5MVYWT), ExitSelect (when the TV takes the active source back after an HDMI session: at once when
# the session ends because the projector goes to sleep, as stock) and BootWindow (when the source chosen for power-on
# opens after a boot, the settle window of the plug and CEC switches) over recorded boots (test/boot_*.tsv), plus the
# wiring of HdmiSession and HdmiWatcher:
#   sh apps/LumenTvInput/test/run.sh
# Targeted mutants of the boot window: python3 apps/LumenTvInput/test/mutate.py
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
G=$(cd "$HERE/../../.." && pwd)
S=$HERE/../src/org/lumen/tvinput
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/tvinput.XXXXXX")
: "${OUT:?}"
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" "$S/ExitSelect.java" "$HERE/ExitSelectTest.java" \
  "$S/BootWindow.java" "$HERE/BootWindowTest.java" "$S/Route.java" "$HERE/RouteTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.tvinput.ExitSelectTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.tvinput.RouteTest
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.tvinput.BootWindowTest "$HERE"
# wiring: the session asks ExitSelect, with the power state
grep -qF 'ExitSelect.delayMs(interactive, Prefs.get(mContext, Prefs.CEC_CONTROL),' "$S/HdmiSession.java" || { echo "FAIL wiring: HdmiSession does not ask ExitSelect"; exit 1; }
grep -qF 'isInteractive()' "$S/HdmiSession.java" || { echo "FAIL wiring: HdmiSession does not read the power state"; exit 1; }
echo "ExitSelect wiring: OK"
# wiring of HdmiWatcher: each line FILE<TAB>TEXT must be found (fixed string) in FILE; FILE<TAB>!TEXT must not be in FILE
fail=0
while IFS='	' read -r f t; do
  case "$f" in ''|'#'*) continue ;; esac
  case "$t" in
    !*) if grep -qF -- "${t#!}" "$G/$f"; then echo "FAIL wiring: $f still has: ${t#!}"; fail=1; fi ;;
    *) if ! grep -qF -- "$t" "$G/$f"; then echo "FAIL wiring: $f lacks: $t"; fail=1; fi ;;
  esac
done < "$HERE/wiring.tsv"
[ "$fail" = 0 ] || exit 1
echo "BootWindow wiring: OK"
