#!/bin/bash
# lumen-signal host tests: bash apps/core/lumen-signal/test/run.sh
#  1. SignalTest: formatting (3840×2160 @ 60 Hz), judder line, the button's states, the HAL budget, row hiding, items,
#     the place (title safe, below the header, smaller than before), light and dark from the Lumen 2.0 palette;
#  2. check_signal.py: texts in English and Russian, per-model data (signal.tsv), recipes, readers without banned calls;
#  3. hdmirx_bridge_test.sh + HdmiRxTest + check_hdmirx.py: the HDMI receiver bridge on a fake root with the real Z9X
#     and Z9X HB captures, the parser, the refresh and stale rules, the pinned card, and that the bridge only ever reads
#     the node or writes "help".
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_signal.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
S="$HERE/src/org/lumen/signal"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$S/SignalItem.java" "$S/SignalValues.java" "$S/SignalReader.java" "$S/SignalBudget.java" "$S/PanelMode.java" \
  "$S/PanelToggle.java" "$S/Judder.java" "$S/SignalFormat.java" "$S/SignalText.java" "$S/SignalSheet.java" \
  "$S/RowTone.java" "$S/SignalItems.java" "$S/SignalLook.java" "$S/ReadSpeed.java" "$S/SignalPlace.java" \
  "$S/HdmiRxInfo.java" "$S/FormatWatch.java" "$S/SignalRefresh.java" \
  "$HERE/../lumen-ui/src/org/lumen/ui/Palette.java" "$HERE/test/SignalTest.java" "$HERE/test/HdmiRxTest.java"
bad=0
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.signal.SignalTest || bad=1
bash "$HERE/test/hdmirx_bridge_test.sh" "$OUT/bridge_info.txt" "$OUT/bridge_info_hb.txt" || bad=1
"$JAVA_HOME/bin/java" -Dfixture="$HERE/test/fixtures/z9x_hdmirx_ps5_4k60.txt" -Dbridge.out="$OUT/bridge_info.txt" \
  -Dhb.fixture="$HERE/test/fixtures/hb_hdmirx_yuv420_4k60.txt" -Dhb.bridge.out="$OUT/bridge_info_hb.txt" \
  -cp "$OUT" org.lumen.signal.HdmiRxTest || bad=1
python3 -B "$HERE/test/check_signal.py" || bad=1
python3 -B "$HERE/test/check_hdmirx.py" || bad=1
[ "$bad" = 0 ] && echo "ok   lumen-signal: all green" || echo "FAIL lumen-signal"
exit $bad
