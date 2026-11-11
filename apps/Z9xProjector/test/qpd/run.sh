#!/bin/sh
# Quick panel layout D logic (panel/QpdLayout is plain Java): sh test/qpd/run.sh
# Also checks that the controller wires what the tests assume (tile order, Boost level, Boost gate).
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/panel/QpdLayout.java" "$HERE/test/qpd/QpdLayoutTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.QpdLayoutTest
C="$HERE/src/org/lumen/projector/panel/QuickPanelController.java"
bad=0
want() { grep -q -- "$1" "$C" || { echo "FAIL controller: missing $2"; bad=1; }; }
want 'private static final int BOOST_LEVEL = 11;' "BOOST_LEVEL 11 (QpdLayout.BOOST_LEVEL)"
want 'snap != null && snap.boost != null) safe("boost"' "Boost step only where 51 reads"
# tile order of the owner's layout: wide autocorrect, picture, input, game; small keystone, zoom, sound, focus,
# bluetooth, network, projection, then screen mirroring (or USB), checked below
order=$(grep -o -E '^        (wide|small|round)\([A-Za-z_.]+' "$C" | sed -E 's/^ +//')
exp='round(QuickPanel.SECTION_EYE
round(QuickPanel.SECTION_GENERAL
wide(TILE_AK
small(QuickPanel.SECTION_KEYSTONE
small(TILE_ZOOM
wide(QuickPanel.SECTION_PICTURE
small(QuickPanel.SECTION_SOUND
small(QuickPanel.SECTION_FOCUS
wide(QuickPanel.SECTION_INPUT
small(TILE_BT
small(TILE_NET
wide(QuickPanel.SECTION_GAME
small(QuickPanel.SECTION_PROJECTION_MODE'
[ "$order" = "$exp" ] || { echo "FAIL controller: tile order"; echo "$order"; bad=1; }
# last small slot (2026-11-11): screen mirroring in place of USB, from QpdLayout.lastSmallTile; USB only as the
# fallback where the receiver is missing, and only with Lumen Home's Files
grep -q 'String lastSmall = QpdLayout.lastSmallTile(mirrorD != null, usbFilesAvailable());' "$C" \
  || { echo "FAIL controller: last small tile not from QpdLayout.lastSmallTile"; bad=1; }
grep -q 'if (TILE_MIRROR.equals(lastSmall)) small(TILE_MIRROR, R.drawable.qpd_ic_mirror, R.string.qpd_mirroring);' "$C" \
  || { echo "FAIL controller: no screen mirroring tile"; bad=1; }
grep -q 'else if (TILE_USB.equals(lastSmall)) small(TILE_USB, R.drawable.qpd_ic_usb, R.string.qpd_usb);' "$C" \
  || { echo "FAIL controller: USB fallback tile gone"; bad=1; }
n=$(grep -c 'small(TILE_USB' "$C" || true)
[ "$n" = 1 ] || { echo "FAIL controller: $n USB tiles (want 1, the fallback)"; bad=1; }
grep -q 'if (QpdMirror.installed(app)) {' "$C" || { echo "FAIL controller: mirroring popup not gated on the receiver"; bad=1; }
grep -q 'if (TILE_MIRROR.equals(key)) return mirrorD == null ? null : mirrorD.page;' "$C" \
  || { echo "FAIL controller: mirroring tile has no popup"; bad=1; }
M="$HERE/src/org/lumen/projector/panel/QpdMirror.java"
# the receiver owns the values: the popup only calls its provider, off the main thread, and shows the read back
grep -q 'resolveContentProvider(AUTHORITY, 0) != null' "$M" || { echo "FAIL mirroring: tile not gated on the provider"; bad=1; }
grep -q 'getContentResolver().call(URI, method, arg, null)' "$M" || { echo "FAIL mirroring: provider call changed"; bad=1; }
grep -q 'else row.setChecked(QpdLayout.settled(null, before));' "$M" || { echo "FAIL mirroring: a switch without the read back"; bad=1; }
n=$(grep -c 'getContentResolver().call(' "$M" || true)
[ "$n" = 1 ] || { echo "FAIL mirroring: $n provider call sites (want 1, inside post())"; bad=1; }
grep -q 'State s = call(' "$M" && ! grep -B3 'State s = call(' "$M" | grep -q 'post(() -> {' \
  && { echo "FAIL mirroring: a provider call outside the worker"; bad=1; }
grep -q 'i.putExtra("edit", "name")' "$M" || { echo "FAIL mirroring: Name row does not open the name field"; bad=1; }
grep -q 'uses-permission android:name="org.lumen.miracast.permission.CONTROL"' "$HERE/AndroidManifest.xml" \
  || { echo "FAIL manifest: no org.lumen.miracast.permission.CONTROL"; bad=1; }
[ -f "$HERE/res/drawable/qpd_ic_mirror.xml" ] || { echo "FAIL res: no qpd_ic_mirror icon"; bad=1; }
# Z9X eye protection is one switch on stock (more_eye): no mode list in the popup
grep -q 'eye_follow\|be_at_ease\|EYE_MODE' "$C" && { echo "FAIL controller: eye protection modes on the Z9X"; bad=1; }
# hint pictures only where stock has one (picture mode, rear projection)
n=$(grep -c 'new QpdTip(' "$C")
[ "$n" = 2 ] || { echo "FAIL controller: $n hint blocks (want 2)"; bad=1; }
# All settings (gear) is its own screen: the gear tile opens it, never a popup, and the RIGHT key on the gear does not
grep -q 'case QuickPanel.SECTION_GENERAL:' "$C" && grep -A1 'case QuickPanel.SECTION_GENERAL:' "$C" | grep -q 'openAll(null, null)' \
  || { echo "FAIL controller: gear does not open All settings"; bad=1; }
grep -q 'return !QuickPanel.SECTION_GENERAL.equals(key) && popupPage(key) != null;' "$C" \
  || { echo "FAIL controller: gear treated as a popup"; bad=1; }
# Input tile: the popup of the inputs (QpdSource); picking does what the Source overlay does (openPort, HOME intent)
grep -q 'if (QuickPanel.SECTION_INPUT.equals(key)) return sourceD == null ? null : sourceD.page;' "$C" \
  || { echo "FAIL controller: Input tile has no popup"; bad=1; }
S="$HERE/src/org/lumen/projector/panel/QpdSource.java"
grep -q 'SourceOverlay.openPort(app, r.port, "quick_panel")' "$S" || { echo "FAIL source popup: HDMI not opened like the overlay"; bad=1; }
# the source popup only reads the vendor (696); no setter
n=$(grep -c 'gm\.set\|g\.set\|g2\.set' "$S" || true)
[ "$n" = 0 ] || { echo "FAIL source popup: $n vendor setter call(s)"; bad=1; }
# rows: radio dot on the left for every CheckRow, prototype switch for ToggleRow
F="$HERE/src/org/lumen/projector/panel/QpdRowFrame.java"
grep -q 'if (c == CheckRow.class) return RADIO;' "$F" || { echo "FAIL rows: CheckRow is not a radio"; bad=1; }
grep -q 'if (c == ToggleRow.class) return TOGGLE;' "$F" || { echo "FAIL rows: ToggleRow is not a switch"; bad=1; }
# the frame draws a row; the row itself keeps the focus and its action (no click handler of our own)
grep -q 'onActivate\|setOnClickListener' "$F" && { echo "FAIL rows: the frame acts on its own"; bad=1; }
# PageAccess reads synced row fields by name: each must still exist
UI="$HERE/src/org/lumen/projector/ui"
for f in "NavRow:private boolean chevron" "ChoiceRow:private CharSequence\[\] options" "ChoiceRow:private CharSequence placeholder" \
         "SliderRow:private Formatter formatter" "HeaderRow:private CharSequence text" "TextRow:private CharSequence text"; do
  cls=${f%%:*}; pat=${f#*:}
  grep -q -- "$pat" "$UI/$cls.java" || { echo "FAIL PageAccess: $cls field gone ($pat)"; bad=1; }
done
fields=$(grep -o '"[A-Za-z]*Row\.[a-z]*"' "$UI/PageAccess.java" | sort | tr '\n' ' ' || true)
[ "$fields" = '"ChoiceRow.options" "ChoiceRow.placeholder" "HeaderRow.text" "NavRow.chevron" "SliderRow.formatter" "TextRow.text" ' ] \
  || { echo "FAIL PageAccess: field list changed ($fields), update this check"; bad=1; }
[ $bad = 0 ] && echo "controller wiring: ok"
exit $bad
