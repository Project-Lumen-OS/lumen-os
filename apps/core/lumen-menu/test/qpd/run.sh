#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
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
G=$(python3 -B "$G/tools/devices/menu_layers.py" --view "$G" "$OUT/menu_view")   # menu profiles as readers see them (devices/README.md)
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/panel/QpdLayout.java" "$HERE/src/org/lumen/ui/Palette.java" "$T/QpdLayoutTest.java"
# the dark menu's card, ink and MUTE (QpdStyle DARK entries 0, 3, 4) for the disabled grey's contrast checks
DARK=$(python3 - "$HERE/src/org/lumen/projector/panel/QpdStyle.java" <<'EOF'
import re, sys
row = re.search(r'int\[\] DARK = \{(.*?)\};', open(sys.argv[1]).read(), re.S).group(1)
v = re.findall(r'0x([0-9A-Fa-f]{8})', row)
print(','.join([v[0], v[3], v[4]]))
EOF
)
# the light range of every profile (min, max, Boost level or -1): the slider spans exactly that range
RANGES=$(python3 - "$G/devices" <<'EOF'
import glob, json, os, sys
out = []
for p in sorted(glob.glob(os.path.join(sys.argv[1], "*", "menu.json"))):
    f = json.load(open(p))["features"]
    if "lamp" in f:
        out.append("%s:%d:%d:%d" % (os.path.basename(os.path.dirname(p)), f["lamp"]["min"], f["lamp"]["max"],
                                    f.get("boost", {}).get("level", -1)))
print(",".join(out))
EOF
)
"$JAVA_HOME/bin/java" -Dlamp.ranges="$RANGES" -Dqpd.dark="$DARK" -cp "$OUT" org.lumen.projector.panel.QpdLayoutTest
# All settings pages of every profile as list models: greyed rows at the end come into view, headers never alone
FP=$(mktemp -d)
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$FP" "$HERE/src/org/lumen/projector/panel/QpdLayout.java" \
  "$HERE"/src/org/lumen/menu/*.java "$HERE"/src/org/lumen/menu/utils/*.java "$T/FullPageTest.java"
"$JAVA_HOME/bin/java" -cp "$FP" org.lumen.projector.panel.FullPageTest "$G" || { rm -rf "$FP"; exit 1; }
rm -rf "$FP"
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
  [ "$lvl" = 11 ] || { echo "FAIL profile: Z9X boost level $lvl (want 11)"; bad=1; }
fi
want 'final int bl = spec.boostLevel();' "Boost level from the profile"
want 'lamp.setBoostLevel(spec.boostLevel());' "the slider gets the profile's Boost level"
want 'lamp = new QpdSliderRow(app, s(MenuLabels.title(FeatureId.LAMP)), lampMin(), lampMax(), lampSlide(),' "the slider spans the profile's range"
# x50-iris-quick: the slider tiles come from MenuSpec.sliders (the iris under the brightness slider, a choice slider over
# the profile's options that commits through choose, the stock refusal first), the card steps them itself
want 'for (MenuSpec.Tile t : spec.sliders) {' "slider tiles from MenuSpec.sliders"
want 'if (token != null) choose(f, token);' "a choice slider step commits as a pick of its page row"
want 'if (!sliderBusy(r)) r.setValue(step);' "a choice slider shows the step of the value read"
want 'if (r.isRowEnabled()) r.setAlpha(LockedRow.alpha(LockedRow.locked(dev, f, st)));' "a choice slider is dimmed while stock keeps its row closed"
grep -qF 'int[] to = QpdLayout.move(rows, cell[0], cell[1], dir, host::hasPopup, card::isSlider, right);' "$HERE/src/org/lumen/projector/panel/QpdPanel.java" \
  || { echo "FAIL card: the grid move does not know the slider tiles or the menu's side"; bad=1; }
# menu on the right (MenuLook): every pane is placed from the menu's side, slides start at that edge, keys between
# panes are mirrored, All settings lays out hint card, list, rail from the left
QP="$HERE/src/org/lumen/projector/panel/QpdPanel.java"
n=$(grep -c 'leftMargin = px(' "$QP" || true)
[ "$n" = 1 ] || { echo "FAIL side: $n left margins in QpdPanel (only side() may set one)"; bad=1; }
n=$(grep -c 'side(.*, .*QpdLayout\.\(CARD_X\|SIDE_X\|ALL_X\)' "$QP" || true)
[ "$n" = 5 ] || { echo "FAIL side: $n panes placed by side() (want card, popup, All settings, side sheet, sheet page)"; bad=1; }
grep -q 'translationX(-Theme\|TranslationX(-Theme' "$QP" && { echo "FAIL side: a slide that ignores the menu's side"; bad=1; }
grep -qF 'int pane = QpdLayout.toward(dir, right);' "$QP" || { echo "FAIL side: pane keys not mirrored"; bad=1; }
n=$(grep -c 'pane == QpdLayout.LEFT' "$QP" || true)
[ "$n" = 2 ] || { echo "FAIL side: $n pane LEFT checks (want the sheet page and the popup)"; bad=1; }
grep -qF 'return allKey(pane, down, ev.getRepeatCount());' "$QP" || { echo "FAIL side: All settings keys not mirrored"; bad=1; }
QE="$HERE/src/org/lumen/projector/panel/QuickEditMode.java"
grep -qF 'if (QpdLayout.toward(dir, right()) == QpdLayout.RIGHT) focusSheet(null);' "$QE" \
  || { echo "FAIL side: the editor's card does not reach its sheet on the menu's side"; bad=1; }
grep -qF 'if (QpdLayout.toward(dir, right()) == QpdLayout.LEFT) {' "$QE" \
  || { echo "FAIL side: the editor's sheet does not go back to the card on the menu's side"; bad=1; }
grep -qF 'private boolean right() { return host.panel().isRight(); }' "$QE" \
  || { echo "FAIL side: the editor does not read the menu's side"; bad=1; }
grep -qF 'float fit = QpdLayout.clockFit(time.measureText(t), getWidth());' "$HERE/src/org/lumen/projector/panel/QpdCard.java" \
  || { echo "FAIL card: the clock is not fitted to its box"; bad=1; }
grep -qF 'c.scale(fit, fit, 0, clockBase);' "$HERE/src/org/lumen/projector/panel/QpdCard.java" \
  || { echo "FAIL card: the clock fit is not applied"; bad=1; }
grep -qF 'popup.setPivotX(right ? px(QpdLayout.SIDE_W) : 0f);' "$QP" || { echo "FAIL side: the popup grows from the wrong edge"; bad=1; }
grep -qF 'View[] order = panel.isRight() ? new View[]{tipCard, listCard, railCard} : new View[]{railCard, listCard, tipCard};' \
  "$HERE/src/org/lumen/projector/panel/QpdAll.java" || { echo "FAIL side: All settings panes not mirrored"; bad=1; }
grep -qF 'tipCard.setTranslationX(panel.isRight() ? px(14) : -px(14));' "$HERE/src/org/lumen/projector/panel/QpdAll.java" \
  || { echo "FAIL side: the hint card slides in from the wrong side"; bad=1; }
grep -q 'QpdLayout.fill(lv, getMin(), getMax())' "$HERE/src/org/lumen/projector/panel/QpdSliderRow.java" \
  || { echo "FAIL slider: the fill does not use the row's range"; bad=1; }
# x50-picture-stock: a page slider row draws its fill with fillSpan (a two-way slider from 0, tested in QpdLayoutTest)
grep -q 'float\[\] on = QpdLayout.fillSpan(s.getValue(), s.getMin(), s.getMax());' "$HERE/src/org/lumen/projector/panel/QpdRowFrame.java" \
  || { echo "FAIL slider: a page slider row does not draw a two-way fill from 0"; bad=1; }
want '!boostOn() && state != null && state.on(FeatureId.BOOST) != null' "Boost step only where 51 reads"
# tile order of the owner's layout (2026-11-13): round theme button (voice search until 2026-11-18) and gear, wide
# autocorrect, picture, input, game, small keystone, zoom, sound, focus, bluetooth, network, projection (gives its place to screen mirroring), eye protection.
# 2026-11-14 (a Z9X user): on a model with stock 3D the 3D tile takes the network place, network goes up as a round
# button left of the theme button. 2026-11-18: the signal panel is a developer feature, no round button (its one
# row is on the For developers page, the last of All settings, 2026-11-19)
grep -q 'ROUND_TILES = {FeatureId.NETWORK, THEME, GENERAL};' "$SP" || { echo "FAIL controller: round tiles"; bad=1; }
grep -q 'PUSHES_UP = {{FeatureId.PICTURE_3D, FeatureId.NETWORK}};' "$SP" || { echo "FAIL controller: 3D pushes network up"; bad=1; }
# For developers opens only inside All settings (2026-11-19), like General: never as a side popup
want 'if (MenuSpec.allSettingsOnly(section)) { openAll(section, focusRow); return; }' "For developers opens in All settings"
want 'if (MenuSpec.LAMP.equals(key) || MenuSpec.allSettingsOnly(key)) return null;' "no popup page for the All settings only pages"
# the card is the user's quick menu (pencil, 2026-11-18); nothing stored = QuickDefaults over the spec's runtime rule
want 'quickDefaults = QuickDefaults.of(spec, this::tilePresent);' "the default card from the spec's runtime rule (QuickDefaults)"
want 'quickShown = QuickLayoutJson.reconcile(quickStored, quickCatalog, quickDefaults);' "the stored menu repaired on every read"
QD="$HERE/src/org/lumen/menu/quick/QuickDefaults.java"
grep -qF 'for (MenuSpec.Tile t : spec.roundRow(usable)) {' "$QD" || { echo "FAIL controller: round row from the spec's runtime rule"; bad=1; }
want 'if (has(FeatureId.PICTURE_3D)) threeDPopup();' "the 3D popup only with the feature"
order=$(sed -n '/TILE_ROWS = {/,/^    };/p' "$SP" | grep -o '{SMALL, [A-Za-z0-9_.]*\(, [A-Za-z0-9_.]*\)*}\|{WIDE, [A-Za-z0-9_.]*}' | tr '\n' ' ')
exp='{WIDE, FeatureId.KEYSTONE_AK_NOW} {SMALL, KEYSTONE} {SMALL, FeatureId.ZOOM, FeatureId.ZOOM_DIGITAL} {WIDE, PICTURE} {SMALL, SOUND} {SMALL, FOCUS} {WIDE, FeatureId.INPUT} {SMALL, FeatureId.BLUETOOTH} {SMALL, FeatureId.PICTURE_3D, FeatureId.NETWORK} {WIDE, GAME} {SMALL, PROJECTION, null, YIELDS} {SMALL, FeatureId.MIRRORING} {SMALL, FeatureId.EYE} '
[ "$order" = "$exp" ] || { echo "FAIL controller: tile order"; echo "$order"; bad=1; }
grep -q 'USB_FILES\|usb_files' "$SP" && { echo "FAIL MenuSpec: a USB tile"; bad=1; }
# the card is MenuSpec.cardRows over what is usable now: mirroring only with its receiver; the theme button always
grep -qF 'for (List<MenuSpec.Tile> row : spec.cardRows(usable)) {' "$QD" || { echo "FAIL controller: card rows from MenuSpec.cardRows"; bad=1; }
want 'if (has(FeatureId.MIRRORING) && QpdMirror.installed(app)) {' "mirroring popup gated on the receiver"
want 'if (FeatureId.MIRRORING.id.equals(id)) return mirror != null;' "mirroring tile only with the receiver"
want 'if (MenuSpec.GENERAL.equals(id) || MenuSpec.THEME.equals(id) || QuickCatalog.EDIT.equals(id)) return true;' \
  "theme button and pencil on every model"
want 'if (MenuSpec.THEME.equals(id)) { cycleTheme(); return; }' "theme button cycles the theme in place, the menu stays open"
want 'ThemeMode next = LumenTheme.mode(app).next();' "theme button goes Светлая, Тёмная, Авто"
want 'if (!LumenTheme.setMode(app, next)) {' "theme button writes the shared switch and says when it cannot"
[ -f "$HERE/src/org/lumen/projector/panel/QpdVoice.java" ] && { echo "FAIL voice: QpdVoice is back"; bad=1; }
# eye protection tile: OK switches it (QpdLayout.eyeToggle through the row's own path), its popup only with modes
want 'case EYE: toggleEye(); return;' "eye tile OK switches eye protection"
want 'choose(FeatureId.EYE, to);' "eye tile goes the row's way (refusal, question, read back)"
want 'if (FeatureId.EYE.id.equals(key) && !eyeHasPopup()) return false;' "eye tile: RIGHT opens nothing on a switch-only model"
want 'if (FeatureId.EYE.id.equals(id) && eyeHasPopup()) t.setOnLongOk(' "eye tile: held OK opens the popup on a model with modes"
grep -q 'Runnable r = longOk ? onLongOk : onOk;' "$HERE/src/org/lumen/projector/panel/QpdTile.java" \
  || { echo "FAIL tile: a held OK does not run on the release"; bad=1; }
for m in light dark auto; do
  [ -f "$HERE/res/drawable/lumen_qpd_ic_theme_$m.xml" ] || { echo "FAIL res: no theme icon $m"; bad=1; }
done
[ -f "$HERE/res/drawable/lumen_qpd_ic_usb.xml" ] && { echo "FAIL res: the USB tile icon is back"; bad=1; }
# every animated icon stays inside its own box at every frame (the 3D icon's screens were cut while the tile took the focus)
python3 -B "$T/icon_bounds.py" "$HERE/res" || bad=1
# All settings (gear) is its own screen: the gear tile opens it, never a popup
want 'if (MenuSpec.GENERAL.equals(id)) { openAll(null, null); return; }' "gear opens All settings"
want 'return !MenuSpec.allSettingsOnly(key) && popupPage(key) != null;' "gear and For developers not a popup"
# a section tile's popup leaves out the rows with a tile of their own (MenuSpec.popups): the full page's views minus
# those, built after the extension rows and again when an extension adds rows; All settings keeps the full page
want '        for (String sec : spec.popups.keySet()) tilePopup(sec);' "section popups without the card's own tiles"
want '        List<FeatureId> keep = spec.popupRowsOf(sec);' "popup rows from MenuSpec.popupRowsOf"
want '            if (f != null && !keep.contains(f.parent != null ? f.parent : f)) continue;' "popup leaves out the tiled rows"
want '            if (v instanceof HeaderRow && !keepsTitle((HeaderRow) v, keep)) continue;' "popup drops a header without rows"
want '        return group == null || !Collections.disjoint(group, keep);' "a module's own title stays with its rows in a popup"
want '        if (full == null || MenuSpec.allSettingsOnly(sec) || keep.isEmpty()' "no tile popup of an All settings only section"
want '        tilePopup(sec);' "extension rows reach the popup"
want '        if (sum != 0) featureOf.put(p.add(new TextRow(app, s(sum))), f);' "a summary goes with its row"
# Input tile: the device's input popup (pages API)
want 'if (FeatureId.INPUT.id.equals(key)) return devicePages == null ? null : devicePages.page(FeatureId.INPUT, this);' "Input tile popup from the device pages"
want 'if (FeatureId.MIRRORING.id.equals(key)) return mirror == null ? null : mirror.page;' "mirroring tile opens its popup"
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
# Input tile: the Source key's step on every model (apps/core/lumen-source), no input popup of a device
[ -f "$HERE/src/org/lumen/projector/panel/QpdSource.java" ] && { echo "FAIL input: a device input popup (QpdSource) is back"; bad=1; }
Z="$HERE/src/org/lumen/projector/panel/Z9xFeatures.java"
if [ -f "$Z" ]; then
  grep -qF 'case INPUT: org.lumen.source.SourceKey.press(app, "quick_menu"); return true;' "$Z" \
    || { echo "FAIL input: the Z9X Input tile does not run the Source key step"; bad=1; }
  grep -q 'case INPUT' "$HERE/src/org/lumen/projector/panel/Z9xMenuPages.java" && { echo "FAIL input: the Z9X input popup page is back"; bad=1; }
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
# All settings list: UP / DOWN through QpdLayout's list rules, the list scrolls to the end at the last usable row
A="$HERE/src/org/lumen/projector/panel/QpdAll.java"
grep -qF 'int to = QpdLayout.listTarget(focusable, at, down);' "$A" \
  && grep -qF 'if (QpdLayout.listEnd(focusable, to, down)) scroll.smoothScrollTo(0, down ? list.getHeight() : 0);' "$A" \
  && grep -qF 'focusable[i] = ((QpdRowFrame) c).isFocusableRow();' "$A" \
  || { echo "FAIL All settings list: moveInList does not run QpdLayout.listTarget / listEnd"; bad=1; }
grep -qF 'if (down) all.moveInList(f, dir == QpdLayout.DOWN);' "$HERE/src/org/lumen/projector/panel/QpdPanel.java" \
  || { echo "FAIL All settings list: UP / DOWN no longer go through moveInList"; bad=1; }
# radio popups land on the checked row (stock Android TV lists), also when the value is read after the popup opened
# (the remote's 3D key on a menu just built): QpdLayout.popupFocus in apply() until the first key
want '        popupUntouched = target != null && focusRow == null;' "a direct popup open follows its checked row"
want '        popupUntouched = true;' "a tile's popup follows its checked row"
want '        if (down) popupUntouched = false; ' "a key ends the follow"
want 'repaint.repaint(() -> p.refreshPage(QpdLayout.popupFocus(popupUntouched && !p.isAllMode(), checkedIndex(p.currentPage()), -1)));' \
  "apply puts the focus by QpdLayout.popupFocus"
first=$(awk '/private boolean onKey\(KeyEvent ev\) \{/ { on = 1 } on && /popupUntouched = false;/ { print "key"; exit } on && /return / { print "return"; exit }' "$C")
[ "$first" = key ] || { echo "FAIL popup focus: a key can leave onKey before it ends the follow"; bad=1; }
[ "$(grep -A1 -xF '    private void openAll(String category, View focusRow) {' "$C" | tail -1)" = '        popupUntouched = false;' ] \
  || { echo "FAIL popup focus: All settings follows a popup's checked row"; bad=1; }
QP="$HERE/src/org/lumen/projector/panel/QpdPanel.java"
grep -qxF '    public void refreshPage() { refreshPage(-1); }' "$QP" \
  && grep -qF 'PageAccess.setFocusIndex(stack.peek(), focusIndex >= 0 ? focusIndex : focusedIndex());' "$QP" \
  || { echo "FAIL popup focus: QpdPanel.refreshPage moves the focus by itself"; bad=1; }
# a small tile's label: smaller down to 11 px where that fits, else full size and running like the wide tiles (QpdLayout)
QT="$HERE/src/org/lumen/projector/panel/QpdTile.java"
grep -qF 'labelP.setTextSize(px(QpdLayout.LABEL) * QpdLayout.labelScale(tw, room));' "$QT" \
  && grep -qF 'if (!QpdLayout.labelRuns(tw, room)) {' "$QT" \
  && grep -qF 'dx = QpdLayout.marqueeShift(tw, room) * QpdLayout.marqueePhase(Math.max(0, t),' "$QT" \
  && grep -qF 'QpdStyle.drawFaded(c, title, labelP, (w - room) / 2f, room, base + labelP.ascent() - px(4),' "$QT" \
  && ! grep -q 'ellipsize' "$QT" \
  || { echo "FAIL small tile label: not QpdLayout.labelScale / labelRuns with the wide tiles' marquee, or an ellipsis"; bad=1; }
# a disabled row: one opaque grey for its text and icons (QpdLayout.disabled through QpdStyle), never transparency
QF="$HERE/src/org/lumen/projector/panel/QpdRowFrame.java" QS="$HERE/src/org/lumen/projector/panel/QpdStyle.java"
RJ="$HERE/src/org/lumen/projector/ui/Row.java" TJ="$HERE/src/org/lumen/projector/ui/Theme.java"
grep -qF 'int save = enabled ? c.save() : c.saveLayer(0, top, w, getHeight(), QpdStyle.disabledLayer());' "$QF" \
  && ! grep -q 'saveLayerAlpha' "$QF" \
  || { echo "FAIL disabled row: the frame does not draw a greyed row in QpdStyle.disabledLayer"; bad=1; }
grep -qF 'disabledLayer = Row.disabledPaint(DISABLED);' "$QS" && grep -qF 'Row.setDisabledInk(DISABLED);' "$QS" \
  && grep -qF 'DISABLED = QpdLayout.disabled(lightNow);' "$QS" \
  || { echo "FAIL disabled row: the theme does not take the menu's disabled grey"; bad=1; }
grep -qF 'int grey = rowEnabled ? -1 : c.saveLayer(0, 0, getWidth(), getHeight(), disabledInk);' "$RJ" \
  && grep -qF '0, 0, 0, DISABLED_GAIN, 0}));' "$RJ" && ! grep -q 'setAlpha(enabled ? 1f : 0' "$RJ" \
  || { echo "FAIL disabled row: a row that draws itself is not greyed by its ink layer (or still by transparency)"; bad=1; }
dg=$(sed -n 's/.*DISABLED_DARK = 0x\([0-9A-Fa-f]*\),.*/\1/p' "$HERE/src/org/lumen/projector/panel/QpdLayout.java")
grep -qF "TEXT_DISABLED = 0x$dg;" "$TJ" || { echo "FAIL disabled row: Theme.TEXT_DISABLED is not the dark menu's grey $dg"; bad=1; }
[ $bad = 0 ] && echo "controller wiring: ok"
exit $bad
