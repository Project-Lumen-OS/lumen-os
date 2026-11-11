#!/bin/sh
# Quick panel layout D logic (panel/QpdLayout is plain Java): sh test/qpd/run.sh
# Also checks that the controller wires what the tests assume (tile order, Boost level, Boost gate).
set -eu
T=$(cd "$(dirname "$0")" && pwd)                 # this suite (apps/core/lumen-menu/test/<suite>)
G=$(cd "$T/../../../../.." && pwd)                 # gsi/
# the suite runs against the ASSEMBLED app: lumen-menu + the device app (default the Z9X)
TREE=$(mktemp -d "${TMPDIR:-/tmp}/lumen_menu_tree.XXXXXX")
python3 -B "$G/tools/devices/assemble_app.py" --root "$G" --quiet --device "${MENU_DEVICE:-horse_popular}" \
  --app "${MENU_APP:-Z9xProjector}" --out "$TREE/app" || { echo "FAIL: the app does not assemble"; rm -rf "$TREE"; exit 1; }
HERE=$TREE/app
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT" "$TREE"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/panel/QpdLayout.java" "$T/QpdLayoutTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.QpdLayoutTest
# the controller is the shared engine (MenuController + MenuSpec + the device profile)
C="$HERE/src/org/lumen/projector/panel/MenuController.java"
SP="$HERE/src/org/lumen/menu/MenuSpec.java"
DEV=${MENU_DEVICE:-horse_popular}
PJ="$G/devices/$DEV/menu.json"
bad=0
want() { grep -q -- "$1" "$C" || { echo "FAIL controller: missing $2"; bad=1; }; }
# Boost: the level from the profile, QpdLayout's 11 on the Z9X; the Boost step only where 51 reads
lvl=$(python3 -c "import json,sys; f=json.load(open(sys.argv[1]))['features']; print(f.get('boost',{}).get('level',''))" "$PJ")
if [ "$DEV" = horse_popular ]; then
  [ "$lvl" = 11 ] || { echo "FAIL profile: Z9X boost level $lvl (want 11, QpdLayout.BOOST_LEVEL)"; bad=1; }
  grep -q 'BOOST_LEVEL = 11' "$HERE/src/org/lumen/projector/panel/QpdLayout.java" || { echo "FAIL QpdLayout.BOOST_LEVEL is not 11"; bad=1; }
fi
want 'final int bl = spec.boostLevel();' "Boost level from the profile"
want '!boostOn() && state != null && state.on(Feature.BOOST) != null' "Boost step only where 51 reads"
# tile order of the owner's layout: round eye and gear; wide autocorrect, picture, input, game; small keystone, zoom,
# sound, focus, bluetooth, network, projection, then screen mirroring (or USB)
grep -q 'ROUND_TILES = {{EYE, Feature.EYE}, {GENERAL, null}};' "$SP" || { echo "FAIL controller: round tiles"; bad=1; }
order=$(sed -n '/TILE_ROWS = {/,/^    };/p' "$SP" | grep -o '{SMALL, [A-Za-z_.]*\(, [A-Za-z_.]*\)\?}\|{WIDE, [A-Za-z_.]*}' | tr '\n' ' ')
exp='{WIDE, Feature.KEYSTONE_AK_NOW} {SMALL, KEYSTONE} {SMALL, Feature.ZOOM, Feature.ZOOM_DIGITAL} {WIDE, PICTURE} {SMALL, SOUND} {SMALL, FOCUS} {WIDE, Feature.INPUT} {SMALL, Feature.BLUETOOTH} {SMALL, Feature.NETWORK} {WIDE, GAME} {SMALL, PROJECTION} {SMALL, Feature.MIRRORING, Feature.USB_FILES} '
[ "$order" = "$exp" ] || { echo "FAIL controller: tile order"; echo "$order"; bad=1; }
# last small slot (2026-11-11): screen mirroring in place of USB, from QpdLayout.lastSmallTile; USB only as the
# fallback where the receiver is missing, and only with Lumen Home's Files
want 'String l = QpdLayout.lastSmallTile(mirror != null, has(Feature.USB_FILES) && usbFilesAvailable());' "last small tile from QpdLayout.lastSmallTile"
want 'if (has(Feature.MIRRORING) && QpdMirror.installed(app)) {' "mirroring popup gated on the receiver"
# All settings (gear) is its own screen: the gear tile opens it, never a popup
want 'case MenuSpec.GENERAL: openAll(null, null); return;' "gear opens All settings"
want '@Override public boolean hasPopup(String key) { return !MenuSpec.GENERAL.equals(key) && popupPage(key) != null; }' "gear not a popup"
# Input tile: the device's input popup (pages API)
want 'if (Feature.INPUT.equals(key)) return devicePages == null ? null : devicePages.page(Feature.INPUT, this);' "Input tile popup from the device pages"
if [ "$DEV" = horse_popular ]; then
  # Z9X eye protection is one switch on stock (more_eye): no mode list; hint pictures only where stock has one
  python3 - "$PJ" <<'EOF' || bad=1
import json, sys
p = json.load(open(sys.argv[1]))
f = p["features"]; bad = 0
if f.get("eye", {}).get("options", ["off", "on"]) != ["off", "on"]: print("FAIL profile: eye protection modes on the Z9X"); bad = 1
if sorted(p.get("hints", {})) != ["picture.mode", "projection.rear"]: print("FAIL profile: Z9X hint blocks", sorted(p.get("hints", {}))); bad = 1
sys.exit(bad)
EOF
  P9="$HERE/src/org/lumen/projector/panel/Z9xMenuPages.java"
  grep -q 'case Feature.INPUT: return source().page;' "$P9" || { echo "FAIL Z9X: Input tile has no popup"; bad=1; }
fi
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
if grep -q '"mirroring"' "$PJ"; then                # the profile has the mirroring tile: the app may call the receiver
  grep -q 'uses-permission android:name="org.lumen.miracast.permission.CONTROL"' "$HERE/AndroidManifest.xml" \
    || { echo "FAIL manifest: no org.lumen.miracast.permission.CONTROL"; bad=1; }
fi
[ -f "$HERE/res/drawable/lumen_qpd_ic_mirror.xml" ] || { echo "FAIL res: no qpd_ic_mirror icon"; bad=1; }
# Input popup: picking does what the Source overlay does (openPort, HOME intent)
S="$HERE/src/org/lumen/projector/panel/QpdSource.java"
if [ -f "$S" ]; then                                # the device has an input popup of its own (the Z9X)
  grep -q 'SourceOverlay.openPort(app, r.port, "quick_panel")' "$S" || { echo "FAIL source popup: HDMI not opened like the overlay"; bad=1; }
  # the source popup only reads the vendor (696); no setter
  n=$(grep -c 'gm\.set\|g\.set\|g2\.set' "$S" || true)
  [ "$n" = 0 ] || { echo "FAIL source popup: $n vendor setter call(s)"; bad=1; }
fi
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
