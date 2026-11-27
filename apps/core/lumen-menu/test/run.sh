#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# lumen-menu host tests: sh apps/core/lumen-menu/test/run.sh [--update]
#  1. every devices/*/menu.json is a valid profile whose adapter names every feature it declares;
#  2. the menu snapshot of every profile equals test/snapshots/<codename>.txt (--update rewrites them: review the diff);
#  3. MenuSpecTest: removing any feature of any profile removes exactly its tiles/rows/hints, broken profiles show
#     no menu at all.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
G=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/lumen_menu.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
LAYERS_ROOT=$G   # the layer files themselves (the view below has them merged)
G=$(python3 -B "$G/tools/devices/menu_layers.py" --view "$G" "$OUT/menu_view")   # menu profiles as readers see them (devices/README.md)
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE"/src/org/lumen/menu/*.java "$HERE"/src/org/lumen/menu/utils/*.java "$HERE/test/MenuSpecTest.java" "$HERE/test/MenuSnapshot.java" \
  "$HERE/test/GearPressTest.java" "$HERE/test/MenuValuesTest.java" "$HERE/test/LampOffTest.java" "$HERE/test/PowerPressTest.java" "$HERE/test/SleepRuleTest.java" "$HERE/test/SignalRuleTest.java" "$HERE/test/HdrFormatTest.java" "$HERE/test/HeaderTest.java" "$HERE/test/SliderOffTest.java" \
  "$HERE/test/IrisRuleTest.java" "$HERE/test/FocusGuideTest.java" "$HERE/test/ElderColorRuleTest.java" "$HERE/test/ChoiceStepTest.java" \
  "$HERE/test/GeneralChoicesTest.java" \
  "$HERE/test/PictureSettingsRuleTest.java" "$HERE/test/LockedRowTest.java" "$HERE/test/HdmiRangeRuleTest.java" "$HERE/test/CustomModesTest.java" "$HERE/test/TitanUxMenuTest.java" \
  "$HERE"/src/org/lumen/menu/quick/*.java "$HERE/test/quick/QuickTest.java" "$HERE/test/quick/FullLayoutTest.java"
bad=0
n=0
for j in "$G"/devices/*/menu.json; do
  [ -f "$j" ] || continue
  c=$(basename "$(dirname "$j")")
  n=$((n + 1))
  exp="$HERE/test/snapshots/$c.txt"
  "$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.MenuSnapshot "$j" > "$OUT/$c.txt" || { echo "FAIL $c: invalid profile"; cat "$OUT/$c.txt"; bad=1; continue; }
  if [ "${1:-}" = "--update" ]; then
    cp "$OUT/$c.txt" "$exp"; echo "updated $exp"
  elif [ ! -f "$exp" ]; then
    echo "FAIL $c: no expected snapshot $exp (run with --update and commit it after review)"; bad=1
  elif ! cmp -s "$OUT/$c.txt" "$exp"; then
    echo "FAIL $c: menu snapshot differs from $exp"; diff "$exp" "$OUT/$c.txt" || true; bad=1
  else
    echo "ok   $c: menu snapshot equal"
  fi
done
[ "$n" -gt 0 ] || { echo "FAIL no devices/*/menu.json found"; exit 1; }
# the build check (apps/build_apk.sh): every profile names only FeatureId ids, a typo fails it
IDS="$HERE/tools/menu_ids.py"; FID="$HERE/src/org/lumen/menu/FeatureId.java"
if python3 "$IDS" "$FID" "$G"/devices/*/menu.json; then echo "ok   menu_ids: every profile id is a FeatureId"; else bad=1; fi
sed 's/"keystone.ak_now"/"keystone.ak_nwo"/' "$G/devices/horse_popular/menu.json" > "$OUT/typo.json"
if python3 "$IDS" "$FID" "$OUT/typo.json" 2>/dev/null; then echo "FAIL menu_ids: a typo id passed"; bad=1; else echo "ok   menu_ids: a typo id fails"; fi
sed 's/{"rows": \["keystone.realtime", /{"rows": ["keystone.realtme", /' "$G/devices/horse_popular/menu.json" > "$OUT/typo2.json"
if cmp -s "$G/devices/horse_popular/menu.json" "$OUT/typo2.json" || python3 "$IDS" "$FID" "$OUT/typo2.json" 2>/dev/null; then
  echo "FAIL menu_ids: a typo in a stock order row passed"; bad=1
else echo "ok   menu_ids: a typo in a stock order row fails"; fi
sed 's/"picture.dynamic_black", "picture.black_tuning"\]/"picture.dynamic_black", "picture.black_tunng"]/' "$G/devices/g0111/menu.json" > "$OUT/typo3.json"
if cmp -s "$G/devices/g0111/menu.json" "$OUT/typo3.json" || python3 "$IDS" "$FID" "$OUT/typo3.json" 2>/dev/null; then
  echo "FAIL menu_ids: a typo in a row of a stock sub page passed"; bad=1
else echo "ok   menu_ids: a typo in a row of a stock sub page fails"; fi
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.MenuSpecTest "$G" "$G"/devices/*/menu.json || bad=1
# the quick menu editor (pencil): default parity, catalogue snapshot per profile (test/snapshots/quick), storage, repair,
# capacity, every look of every item kind, moves
if [ "${1:-}" = "--update" ]; then "$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.quick.QuickTest "$G" --update || bad=1
else "$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.quick.QuickTest "$G" || bad=1; fi
# the gear key: one shared press rule (GearPress); that every app runs it is checked by tools/devices/test/keylayout_gear_test.py
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.GearPressTest || bad=1
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.quick.FullLayoutTest || bad=1
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.ElderColorRuleTest || bad=1
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.GeneralChoicesTest || bad=1
# the focus page's remote picture: the model's own remote (devices/_remotes/focus_guide.tsv, stock RemoteFocusCell)
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.FocusGuideTest "$G" || bad=1
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.utils.MenuValuesTest || bad=1
# a stepped choice the device refused shows its value at once (ChoiceStep, HB output row 2026-11-14)
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.ChoiceStepTest || bad=1
grep -qxF '        if (ChoiceStep.forget(r.kind)) steppedAt.remove(f);           // a step the device did not take: its value at once' \
  "$HERE/src/org/lumen/projector/panel/MenuController.java" \
  || { echo "FAIL choice step: MenuController keeps a refused step on the row"; bad=1; }
# section headers never stand without rows: MenuSpec.headerShown, the rule MenuController.apply runs
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.HeaderTest "$G" || bad=1
SMC="$HERE/src/org/lumen/projector/panel/MenuController.java"
for w in '            boolean shown = above == null ? spec.headerShown(e.getValue(), st) : spec.lineShown(above, e.getValue(), st);' \
         '            if (ok) e.getKey().setVisibility(shown ? View.VISIBLE : View.GONE);'; do
  grep -qxF -- "$w" "$SMC" || { echo "FAIL headers: MenuController shows headers by a rule of its own (lost: $w)"; bad=1; }
done
# the Keystone section in each model's stock order (sections.keystone = its stock tree, test/stock/projection_screen.json)
python3 -B "$HERE/test/stock_order.py" "$G" || bad=1
# the Picture section of the X50 family in each model's stock image page order, with its picture settings page
# (sections.picture = its stock tree, test/stock/picture_image.json), the tuning range and the Boost step per tree
# the layers themselves: a row one of them has off with the reason is not part of the shown order
python3 -B "$HERE/test/stock_picture.py" "$LAYERS_ROOT" || bad=1
python3 -B "$HERE/test/stock_modes.py" "$LAYERS_ROOT" || bad=1     # picture mode list per content kind = stock
# the stock line between two groups: no text, drawn as the frame's divider, never opening a page or popup
for w in '        if (MenuSpec.LINE.equals(id)) return "";' \
         '                if (MenuSpec.LINE.equals(g.header)) lineAbove.put(hr, new ArrayList<>(above));' \
         '            if (lineAbove.containsKey(v) && !rowAbove) continue;                // a line never opens the popup' \
         '            if (f != null) rowAbove = true;'; do
  grep -qxF -- "$w" "$SMC" || { echo "FAIL line: MenuController lost: $w"; bad=1; }
done
grep -qxF '                f.setDivider(prev == QpdRowFrame.RADIO && f.kind == QpdRowFrame.NAV || f.isLine());' \
  "$HERE/src/org/lumen/projector/panel/QpdPanel.java" || { echo "FAIL line: the row frames do not draw a line header"; bad=1; }
grep -qxF '        return kind == HEADER && PageAccess.headerText((HeaderRow) row).length() == 0' \
  "$HERE/src/org/lumen/projector/panel/QpdRowFrame.java" \
  && grep -qxF '                && ((HeaderRow) row).getState().length() == 0;' "$HERE/src/org/lumen/projector/panel/QpdRowFrame.java" \
  || { echo "FAIL line: QpdRowFrame.isLine is not the header without text and without a state (signal format)"; bad=1; }
# options that follow the content (stock support lists)
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.SignalRuleTest "$G" || bad=1
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.HdrFormatTest "$G" || bad=1
for w in 'want.removeAll(SignalRule.greyed(profile, f, st));' \
         'pr.get(i).setRowEnabled(!greyed.contains(opts.get(i)));' \
         '}))).setRowEnabled(!greyed.contains(o));' \
         'r.setRowEnabled(val != null && !greyed.contains(opts.get(i)));' \
         'if (SignalRule.greyed(profile, f, state).contains(token)) { refresh(); return; }' \
         'this.signal = new SignalWatch(dev, this::ours, () -> state, this::refresh);'; do
  grep -qF -- "$w" "$SMC" || { echo "FAIL signal rule: MenuController lost: $w"; bad=1; }
done
# picture2: a PAGE row opens a page of the menu's own rows, only when it has a row, after the adapter's refusal check;
# greyed and hidden like a SCREEN row
for w in '                if (sub.rows().isEmpty()) return;                // no row of it the adapter implements: no row' \
         '                row = p.add(new NavRow(app, title, () -> safe("page " + f, () -> openPage(f, sub))));' \
         '            boolean opens = f.kind == FeatureKind.SCREEN || f.kind == FeatureKind.PAGE;' \
         '            if (opens) ((NavRow) v).setRowEnabled(st == null || !st.disabled(f));'; do
  grep -qF -- "$w" "$SMC" || { echo "FAIL pages: MenuController lost: $w"; bad=1; }
done
[ "$(awk '/private void openPage\(FeatureId f, Page page\) \{/ {getline; print; exit}' "$SMC")" = '        Note n = dev.blocked(f, null, state);' ] \
  || { echo "FAIL pages: openPage does not ask the adapter first"; bad=1; }
# sections, popups and the pages of the menu's own rows (picture2: the HDR page follows the content)
[ "$(grep -c 'watchSignal();' "$SMC")" = 3 ] || { echo "FAIL signal rule: the watch does not start on every shown page and popup"; bad=1; }
SW="$HERE/src/org/lumen/projector/panel/SignalWatch.java"
grep -qF 'if (now != null && open.getAsBoolean() && st != null && now != st.format()) reread.run();' "$SW" \
  && grep -qF 'if (!open.getAsBoolean()) return;' "$SW" \
  || { echo "FAIL signal watch: no re-read on a content change, or it polls with the menu closed"; bad=1; }
# the lens iris row and its light level rule (IrisRule): the slider step asks the rule before the device
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.IrisRuleTest "$G" || bad=1
# the stock picture settings page refusal (game mode, AI 10, Performance) every adapter asks (PictureSettingsRule)
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.PictureSettingsRuleTest "$G" || bad=1
for w in '            Note n = feat == null ? null : IrisRule.blocked(profile, feat, state);' \
         '            if (n == null && feat != null) n = dev.blocked(feat, null, state);'; do
  grep -qxF -- "$w" "$SMC" || { echo "FAIL iris rule: MenuController lost: $w"; bad=1; }
done
# a locked row never shows a value the device did not take (Slava's HB, beta15, AI mode: the colour temperature row
# stepped Standard / Movie under the refusal): a slider or choice step asks the adapter BEFORE the row moves, a refusal
# at the commit drops the step and the slider's touch at once
for w in '        if (step && (f instanceof SliderRow || f instanceof ChoiceRow)) {' \
         '            steppedAt.remove(f);                              // a refused step shows the device value at once' \
         '                    if (n != null) { row.setTag(null); note(n, f); refresh(); return; }   // the device value back at once'; do
  grep -qxF -- "$w" "$SMC" || { echo "FAIL locked row: MenuController lost: $w"; bad=1; }
done
[ "$(awk '/private void choose\(FeatureId f, String token\) \{/ { on = 1 } on && /dev.blocked\(f, token, state\)/ { getline; getline; print; exit }' "$SMC")" \
  = '            steppedAt.remove(f);                              // a refused step shows the device value at once' ] \
  || { echo "FAIL locked row: choose does not drop the refused step first"; bad=1; }
# ...and looks closed (2026-11-15: the rows looked fully active in AI): every enabled row of the menu takes the
# stock disabled alpha while the adapter's locked hook (the shared PictureSettingsRule) closes it, keeps its focus
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.LockedRowTest || bad=1
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.HdmiRangeRuleTest || bad=1
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.CustomModesTest || bad=1
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.TitanUxMenuTest "$G" || bad=1
grep -qxF '        if (v instanceof Row && ((Row) v).isRowEnabled()) v.setAlpha(LockedRow.alpha(LockedRow.locked(dev, f, st)));' "$SMC" \
  || { echo "FAIL locked row: MenuController.apply does not draw closed rows dimmed"; bad=1; }
# (one row's look in applyRow, called for every row and every twin of a row moved onto a second page)
grep -qxF '        for (Map.Entry<FeatureId, View> e : rows.entrySet()) applyRow(e.getKey(), e.getValue(), st);' "$SMC" \
  && [ "$(awk '/    private void applyRow\(FeatureId f, View v, MenuState st\) \{/ { on = 1 } on && /LockedRow.alpha\(/ { print "in"; exit } on && /^    }$/ { exit }' "$SMC")" = in ] \
  || { echo "FAIL locked row: the closed look is not in the loop over every row"; bad=1; }
# the stock "Turn off screen" (LampOff): one key and recovery rule for every app that offers lamp.off
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.LampOffTest || bad=1
# the remote power key an app receives (X50 family): one shared rule (PowerPress) and the stock power menu items
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.PowerPressTest || bad=1
# its hold is the Z9X HB one (one behaviour everywhere): PowerPress.HOLD_MS = Z9xProjector PowerKey.LONG_PRESS_MS
ZPK="$G/apps/LumenProjector/core/src/org/lumen/projector/power/PowerKey.java"
if [ -f "$ZPK" ]; then
  z=$(sed -n 's/.*static final long LONG_PRESS_MS = \([0-9]*\);.*/\1/p' "$ZPK")
  m=$(sed -n 's/.*public static final long HOLD_MS = \([0-9]*\);.*/\1/p' "$HERE/src/org/lumen/menu/PowerPress.java")
  if [ -n "$z" ] && [ "$z" = "$m" ]; then echo "ok   power hold = the Z9X HB hold ($m ms)"
  else echo "FAIL power hold '$m' ms, the Z9X HB PowerKey holds '$z' ms"; bad=1; fi
fi
# the shortest wake is the Z9X HB wake echo: PowerPress.WAKE_ECHO_MS = PowerKey.WAKE_GUARD_MS
if [ -f "$ZPK" ]; then
  z=$(sed -n 's/.*static final long WAKE_GUARD_MS = \([0-9]*\);.*/\1/p' "$ZPK")
  m=$(sed -n 's/.*public static final long WAKE_ECHO_MS = \([0-9]*\);.*/\1/p' "$HERE/src/org/lumen/menu/PowerPress.java")
  if [ -n "$z" ] && [ "$z" = "$m" ]; then echo "ok   wake echo = the Z9X HB wake echo ($m ms)"
  else echo "FAIL wake echo '$m' ms, the Z9X HB PowerKey guards '$z' ms"; bad=1; fi
fi
# the stock sleep or power off rule (getShutdownMode: setup, the 15th sleep of a boot)
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.SleepRuleTest || bad=1
# the brightness slider: LEFT at its minimum asks like Boost (SliderOff), LEFT again while the hint is up goes the
# "Turn off screen" row's own way (menu closes first), lamp.off only. Boost asks through the same PressAgain
# lamp.off is a row of the base profile: every model with a light level offers it unless one of its layers switches it
# off with the reason, so the models that must ask come from the layers, not from a list here
LAMP_OFF_MODELS=$(python3 -B - "$LAYERS_ROOT" <<'PY'
import glob, os, sys
sys.path.insert(0, os.path.join(sys.argv[1], "tools", "devices"))
import menu_layers
out = []
for p in sorted(glob.glob(os.path.join(sys.argv[1], "devices", "*", "menu.json"))):
    if not menu_layers.is_profile(p):
        continue
    layers = menu_layers.chain(p)
    if "lamp.off" not in (layers[0][1].get("features") or {}):
        sys.exit("lamp.off is not a row of the base profile %s" % layers[0][0])
    off = [why for _, d in layers for r, why in (d.get("off") or {}).items() if r == "lamp.off"]
    if "lamp" in menu_layers.effective(p).get("features", {}) and not (off and all(off)):
        out.append(os.path.basename(os.path.dirname(p)))
print(" ".join(out))
PY
) || { echo "FAIL lamp.off models: $LAMP_OFF_MODELS"; bad=1; }
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.SliderOffTest "$G" "$LAMP_OFF_MODELS" || bad=1
LMC="$HERE/src/org/lumen/projector/panel/MenuController.java"
lw() { grep -qxF -- "$1" "$LMC" || { echo "FAIL lamp slider: MenuController lost: $2"; bad=1; }; }
lw '            switch (sliderOff.edge(has(FeatureId.LAMP_OFF), row.isKnown() ? row.getValue() : null, row.getMin(), dir, 0,' \
  "the edge asks SliderOff with the profile's lamp.off"
lw '                case HINT: host.note(IconText.keys(app, s(R.string.lumen_m_lamp_off_press_again))); return true;' "the first LEFT shows the hint"
lw '                case OFF: safe("lamp off", () -> action(FeatureId.LAMP_OFF)); return true;' "the second LEFT runs lamp.off"
lw '            FeatureId.LAMP_OFF);' "lamp.off closes the menu first"
lw '    private final SliderOff sliderOff = new SliderOff(Theme.NOTIFY_SLIDE_MS + Theme.NOTIFY_HOLD_MS);' \
  "the second LEFT counts while the hint is on screen"
lw '        if (down) sliderOff.key(onLamp && k == towardsStart);    // any other key drops the light-off question' \
  "any other key drops the question"
lw '    private final PressAgain boostAsk = new PressAgain(BOOST_PRESS_WINDOW_MS);' "Boost asks through PressAgain"
lw '        int sliderDir = !onLamp ? 0 : k == towardsEnd ? 1 : k == towardsStart ? -1 : 0;' \
  "the Boost question's keys: towards the end enters, towards the start leaves"
lw '        if (down) boostAsk.key(sliderDir);    // any other key drops the Boost question too' \
  "any other key drops the Boost question"
# x50-picture-stock: the light level row of a section page (the stock image page starts with it) is the card slider's
# twin: the same steps, light-off edge and Boost step, one key rule for both
lw '                    addLampRow(p);                            // the light slider (the lamp page: its tile look opens it)' "a page's light level row is the slider's twin"
lw '        boolean onLamp = isLamp(f);' "the keys ask whether the focus is on a light slider"
lw '    private boolean isLamp(View v) { return v != null && (v == lamp || pageLamps.contains(v)); }' "the page twins are light sliders"
[ "$(grep -cxF '        wireLamp(r);' "$LMC")" = 1 ] && [ "$(grep -cxF '                wireLamp(lamp);' "$LMC")" = 1 ] \
  || { echo "FAIL lamp slider: the card slider and its page twins are not wired by one method"; bad=1; }
[ "$(grep -cxF '        for (SliderRow r : pageLamps) applyLampRow(r, st);' "$LMC")" = 1 ] && [ "$(grep -cxF '        applyLampRow(lamp, st);' "$LMC")" = 1 ] \
  || { echo "FAIL lamp slider: the page twins do not show the card slider's value"; bad=1; }
# a slider whose profile range goes below 0 is two-way (stock TwoTowardProgressComponent, the dynamic black level
# tuning -40..40): its value shows its sign, the fill grows from 0 (apps/common/projector SliderRow)
lw '                if (lo < 0) sr.setFormatter(v -> v > 0 ? "+" + v : Integer.toString(v));   // a two-way slider, 0 in the middle' \
  "a two-way slider shows the sign of its value"
SRW="$G/apps/common/projector/src/org/lumen/projector/ui/SliderRow.java"
if [ -f "$SRW" ]; then
  for w in '            if (min < 0 && max > 0) {                 // a two-way slider (stock TwoTowardProgressComponent): from 0' \
           '                r.left = Math.min(zero, at);' '                r.right = Math.max(zero, at);'; do
    grep -qxF -- "$w" "$SRW" || { echo "FAIL two-way slider: SliderRow lost: $w"; bad=1; }
  done
fi
first=$(awk '/private boolean onKey\(KeyEvent ev\) \{/ { on = 1 } on && /boostAsk\.key\(/ { print "key"; exit } on && /return / { print "return"; exit }' "$LMC")
[ "$first" = key ] || { echo "FAIL boost: a key can leave onKey before it drops the Boost question"; bad=1; }
lw '        if (boostAsk.press(dir, SystemClock.uptimeMillis())) {' "Boost answers through PressAgain"
first=$(awk '/private boolean onKey\(KeyEvent ev\) \{/ { on = 1 } on && /sliderOff\.key\(/ { print "key"; exit } on && /return / { print "return"; exit }' "$LMC")
[ "$first" = key ] || { echo "FAIL lamp slider: a key can leave onKey before it drops the light-off question"; bad=1; }
[ "$(grep -A1 -xF '            boostAsk.cancel();' "$LMC" | tail -1)" = '            sliderOff.cancel();' ] \
  || { echo "FAIL lamp slider: a closed menu keeps the Boost or the light-off question"; bad=1; }
# the slider's hints in every language: the arrow of the key towards the slider's start (light off, leaving Boost) or
# its end (Boost); Arabic mirrors the slider (Row swaps LEFT and RIGHT), so its arrows point the other way
if python3 - "$HERE/res" <<'PY'
import glob, os, sys
import xml.etree.ElementTree as ET
HINTS = {"lumen_m_lamp_off_press_again": ("lumen_strings_v3.xml", "start"),
         "lumen_m_panel_boost_leave_press_again": ("lumen_strings_v3.xml", "start"),
         "lumen_m_panel_boost_press_again": ("lumen_strings_v3.xml", "end"),
         "lumen_m_panel_lamp_hint": ("lumen_strings_menu.xml", "end")}
bad = []
dirs = sorted(glob.glob(os.path.join(sys.argv[1], "values*")))
for d in dirs:
    loc = os.path.basename(d)
    for name, (f, towards) in HINTS.items():
        p = os.path.join(d, f)
        t = [e.text or "" for e in ET.parse(p).getroot() if e.get("name") == name] if os.path.isfile(p) else []
        left = (towards == "start") != (loc == "values-ar")
        arrow, wrong = ("\u2190", "\u2192") if left else ("\u2192", "\u2190")
        if len(t) != 1 or arrow not in t[0] or wrong in t[0]:
            bad.append(loc + ": " + name + " without " + arrow)
        elif any(c in t[0] for c in "\u2014\u2013;\uff1b"):
            bad.append(loc + ": " + name + " has a dash or a semicolon")
if len(dirs) != 18:
    bad.append("%d values dirs, want 18 (17 languages, pt and pt-rBR)" % len(dirs))
for b in bad: print("FAIL lamp slider hint " + b)
sys.exit(1 if bad else 0)
PY
then echo "ok   lamp slider: the light-off and Boost hints in every language"; else bad=1; fi
# every label of the menu in every language: each translatable name of res/values in all 17 locales
if python3 - "$HERE/res" <<'PY'
import os
import sys
import xml.etree.ElementTree as ET
res = sys.argv[1]
for d in (os.path.abspath(res), os.getcwd()):         # the tree of res, else the tree the test runs in
    while d != os.path.dirname(d) and not os.path.isfile(os.path.join(d, "tools/i18n/res_strings.py")):
        d = os.path.dirname(d)
    if os.path.isfile(os.path.join(d, "tools/i18n/res_strings.py")):
        break
sys.path.insert(0, os.path.join(d, "tools/i18n"))
import res_strings  # strings files as the APK resolves them (aliases of lumen-common-strings)
def names(d, all_=False):
    out = set()
    for f in os.listdir(os.path.join(res, "values")):
        if f.endswith(".xml"):
            for e in ET.fromstring(res_strings.text(os.path.join(res, d, f))):
                if e.tag in ("string", "plurals", "string-array") and (all_ or e.get("translatable") != "false"):
                    out.add(e.get("name"))
    return out
want = names("values")
dirs = sorted(d for d in os.listdir(res) if d.startswith("values-") and os.path.isdir(os.path.join(res, d)))
bad = ["%s: %s" % (d, " ".join(sorted(want - names(d, True)))) for d in dirs if want - names(d, True)]
if len(dirs) != 17:
    bad.append("%d locales, want 17" % len(dirs))
for b in bad: print("FAIL menu labels missing in " + b)
sys.exit(1 if bad else 0)
PY
then echo "ok   menu labels: every label in all 17 locales"; else bad=1; fi
# the HDMI colour range: the shown tokens of HdmiRangeRule.autoShown have module labels (Auto (Full), Auto (Limited))
for t in auto_full auto_limited; do
  grep -qF "case \"hdmi.range|$t\": return R.string.lumen_m_hdmi_range_$t;" "$HERE/src/org/lumen/projector/panel/MenuLabels.java" \
    && echo "ok   hdmi range: $t has its label" || { echo "FAIL hdmi range: no label for $t"; bad=1; }
done
grep -qF 'String as = state == null ? null : state.shownAs(f, token);' "$LMC" \
  && echo "ok   hdmi range: option labels follow MenuState.shownAs" || { echo "FAIL option labels ignore MenuState.shownAs"; bad=1; }
# a row that leaves the menu for another app's page waits for the first-run setup and closes the panel first
leave=0
for id in SETTINGS_SYSTEM SETTINGS_UI_RESOLUTION SETTINGS_RECENTS SETTINGS_LAUNCHER VOICE_KEY; do
  for set in SETUP_GATED CLOSE_FIRST; do
    if ! sed -n "/static final Set<FeatureId> $set = set(/,/);/p" "$LMC" | grep -q "FeatureId\.$id[,)]"; then
      echo "FAIL leave the menu: $id not in MenuController.$set"; leave=1; bad=1
    fi
  done
done
# the HDMI page is a page of the menu (HdmiPage): setup-gated, the menu stays
grep -q "if (!setupDone(FeatureId.SETTINGS_HDMI.id)) return;" "$LMC" \
  && echo "ok   HDMI page: setup-gated, shown inside the menu" || { echo "FAIL HDMI page: not setup-gated"; bad=1; }
[ "$leave" = 0 ] && echo "ok   leave the menu: system, projector, recents and launcher pages are setup-gated and close the panel first"
# the Bluetooth and Network tiles: each opens the menu's page (Bluetooth, the Network popup), else its Android page through the shared lumen-system module (SystemPages walks the
# ways of SystemIntent.chain, apps/core/lumen-system/test/run.sh), a note when no way starts
printf '%s\n' '                case BLUETOOTH:                               // the menu'"'"'s Bluetooth page, else Android'"'"'s' \
  '                    if (!setupDone(id)) return;' '                    if (popupPage(id) == null) {' \
  '                        closeThen(() -> openSystemPage(SettingsPage.BLUETOOTH));' '                        return;' \
  '                    }' '                    break;' '                case NETWORK:                                 // the popup with the hotspot where it works, else Wi-Fi' \
  '                    if (!setupDone(id)) return;' '                    if (popupPage(id) == null) {' \
  '                        closeThen(() -> openSystemPage(SettingsPage.NETWORK));' '                        return;' \
  '                    }' '                    watchHotspot();' '                    break;' > "$OUT/tiles.exp"
cat > "$OUT/sys.exp" <<'EOF'
    private void openSystemPage(SettingsPage page) {
        Ui.wakeFromDream(app);
        if (!SystemPages.open(app, page)) host.toast(R.string.lumen_m_toast_failed);
    }
EOF
grep -A14 -F '                case BLUETOOTH:  ' "$LMC" > "$OUT/tiles.got" || true
sed -n '/^    private void openSystemPage(SettingsPage page) {$/,/^    }$/p' "$LMC" > "$OUT/sys.got"
if cmp -s "$OUT/tiles.exp" "$OUT/tiles.got" && cmp -s "$OUT/sys.exp" "$OUT/sys.got" \
  && [ -z "$(grep -nE 'Settings\.ACTION_|"android\.settings\.' "$LMC" || true)" ] && grep -qE '^depends=(.* )?lumen-system( .*)?$' "$HERE/module.conf"; then
  echo "ok   system tiles: bluetooth and network open their page through lumen-system (SystemPages)"
else
  echo "FAIL system tiles: MenuController does not open bluetooth / network through lumen-system"; bad=1
fi
# the Network popup: no popup without a built hotspot row (the tile opens Wi-Fi then), the module's
# own action and its text screens over the menu, the hotspot values only where the profile has them
MSC="$HERE/src/org/lumen/projector/panel/ModuleScreens.java"
for w in '        if (MenuSpec.NETWORK.equals(key) && !rows.containsKey(FeatureId.NETWORK_HOTSPOT)) return null;   // Wi-Fi alone: no popup' \
         '            if (!ModuleScreens.run(app, profile, f)) host.toast(R.string.lumen_m_toast_failed);' \
         '        if (MenuSpec.MODULE_SCREENS.contains(f) && ModuleScreens.overMenu(f)) {   // the menu steps aside (KeyTools)' \
         '            if (gen == refreshGen) apply(ModuleScreens.merge(app, profile, st, rows.containsKey(FeatureId.NETWORK_HOTSPOT)));' \
         '        if (MenuSpec.module(f)) return profile.has(f) && ModuleScreens.available(app, profile, f);' \
         '                if (panel != null && panel.isShowing()) { refresh(); return; }'; do
  grep -qxF -- "$w" "$LMC" || { echo "FAIL hotspot: MenuController lost: $w"; bad=1; }
done
for w in '        if (MenuSpec.HOTSPOT_ROWS.contains(f)) return Hotspot.supported(c, p.text(FeatureId.NETWORK_HOTSPOT, "iface"));' \
         '        if (hotspotRows) hotspot(c, p, b);' \
         '        if (on) return;' \
         '        b.disable(FeatureId.NETWORK_HOTSPOT_NAME).disable(FeatureId.NETWORK_HOTSPOT_PASSWORD);' \
         '        b.hide(FeatureId.NETWORK_HOTSPOT_RESET);' \
         '        Hotspot.first(c, p.text(FeatureId.NETWORK_HOTSPOT, "name"));' \
         '            if (ON.equals(value)) Hotspot.start(c, hotspotNotes(c));' \
         '            else Hotspot.stop(c, hotspotNotes(c));' \
         '        return f == FeatureId.NETWORK_HOTSPOT_NAME || f == FeatureId.NETWORK_HOTSPOT_PASSWORD;' \
         '        if (f == FeatureId.NETWORK_WIFI) return SystemPages.open(c, SettingsPage.NETWORK);'; do
  grep -qxF -- "$w" "$MSC" || { echo "FAIL hotspot: ModuleScreens lost: $w"; bad=1; }
done
TE="$HERE/src/org/lumen/projector/panel/TextEditActivity.java"
for w in '        if (problem != 0) {' '            showProblem(problem);' '        if (!field.save(this, t)) {' \
         'public final class TextEditActivity extends ToolActivity {'; do
  grep -qxF -- "$w" "$TE" || { echo "FAIL hotspot: TextEditActivity lost: $w"; bad=1; }
done
# the HB manifest is the core one plus its patch
HBM=$(mktemp -d); python3 -B "$G/tools/devices/patch_file.py" "$G/apps/LumenProjector/core/AndroidManifest.xml" "$G/devices/goat_elegant/app/HbProjector.manifest.diff" > "$HBM/AndroidManifest.xml"
P6M=$(mktemp -d); cp "$G/devices/dragon_healthy/app/Play6Projector.manifest.xml" "$P6M/AndroidManifest.xml"
for m in "$G"/apps/LumenProjector/core "$HBM" "$P6M" "$G"/apps/LumenProjector/host/activity; do
  for w in TETHER_PRIVILEGED OVERRIDE_WIFI_CONFIG NETWORK_SETTINGS ACCESS_WIFI_STATE; do
    grep -q "<uses-permission android:name=\"android.permission.$w\" />" "$m/AndroidManifest.xml" \
      || { echo "FAIL hotspot: $(basename "$m") lacks $w"; bad=1; }
  done
  grep -q 'android:name="org.lumen.projector.panel.TextEditActivity"' "$m/AndroidManifest.xml" \
    || { echo "FAIL hotspot: $(basename "$m") does not declare TextEditActivity"; bad=1; }
done
[ "$bad" = 0 ] && echo "ok   hotspot: popup, module rows, text screen and manifests wired"
# the module never touches a HAL or a device class: device code lives in the adapters
leak=$(grep -rnE 'import org\.lumen\.projector\.(Hal|HalController|hal\.|game\.|source\.|cec\.|audio\.|fit\.)|[^a-zA-Z]Hal\.(query|run|is)|GmpfClient|Gmpf2Client|PanelRules|KstPoint' "$HERE/src" | grep -vE '^[^:]*:[0-9]+: *(\*|//|/\*)' || true)
[ -z "$leak" ] || { echo "FAIL device code in the module: $leak"; bad=1; }
# windows only through the PanelHost: the overlay host stays the default of the 3-argument
# constructor and of GameMenu.install(c, io) (Z9X, HB unchanged); the activity host never adds a window of its own
code() { grep -nE "$1" "$2" | grep -vE '^[0-9]+: *(\*|//|/\*)' || true; }
MC="$HERE/src/org/lumen/projector/panel/MenuController.java"
AH="$HERE/src/org/lumen/projector/ui/ActivityPanelHost.java"
OH="$HERE/src/org/lumen/projector/ui/OverlayPanelHost.java"
[ -z "$(code 'OverlayHost|Notify\.|Ui\.toast' "$MC")" ] || { echo "FAIL MenuController bypasses its PanelHost: $(code 'OverlayHost|Notify\.|Ui\.toast' "$MC")"; bad=1; }
[ -z "$(code '[a-z]\.dismiss\(\)' "$MC")" ] || { echo "FAIL MenuController closes a panel past its host"; bad=1; }
grep -qF 'this(c, dev, devicePages, null);' "$MC" && grep -qF 'this.host = host != null ? host : OverlayPanelHost.get(app);' "$MC" \
  || { echo "FAIL MenuController: the overlay host is not the default"; bad=1; }
[ -z "$(code 'TYPE_APPLICATION_OVERLAY|OverlayHost\.|getSystemService\(WindowManager|addView\(root, lp\)' "$AH")" ] \
  || { echo "FAIL ActivityPanelHost adds a window of its own"; bad=1; }
# the device's gate before every new panel, fail closed (X50: never over X50EyeGuard's cover)
grep -qF '        if (!allowed()) {' "$AH" && grep -qF '            return gate.mayShow();' "$AH" \
  && [ "$(grep -A3 '            return gate.mayShow();' "$AH" | grep -c 'return false;')" = 1 ] \
  || { echo "FAIL ActivityPanelHost shows a panel past the device's gate"; bad=1; }
for w in 'public boolean show(Panel p) { return overlay().show(p); }' 'public void dismiss(Panel p) { if (p != null) p.dismiss(); }' \
         'public void dismissAll() { overlay().dismissAll(true); }' 'public Panel current() { return overlay().current(); }' \
         'public void note(CharSequence text) { Notify.show(app, text); }' 'public void toast(int res) { Ui.toast(app, res); }'; do
  grep -qF -- "$w" "$OH" || { echo "FAIL OverlayPanelHost no longer the old overlay call: $w"; bad=1; }
done
# no layers over the game in the activity host: the game menu leaves its crosshair and frame counter out there
grep -qF '@Override public boolean hasStaticLayers() { return false; }' "$AH" \
  && grep -qF 'default boolean hasStaticLayers() { return true; }' "$HERE/src/org/lumen/projector/ui/PanelHost.java" \
  || { echo "FAIL hosts: the activity host claims layers over the game, or the overlay host lost them"; bad=1; }
PM="$HERE/src/org/lumen/projector/panel/PowerMenu.java"
# the power menu (PowerMenu): the stock items the profile offers, only through the menu's own host (a device's gate
# holds for it too), Turn off screen the lamp.off row's own action; the round tiles of every power menu (
# 2026-11-26: the short press opens the round menu as before, never the side list)
if grep -qF '        panel = new Tiles(PowerItem.offered(menu::offers));' "$PM" && grep -qxF '        return menu.host().show(panel);' "$PM" \
  && grep -qF 'private final class Tiles extends RoundTilePanel {' "$PM" && [ -z "$(code 'new PagedPanel\(' "$PM")" ] \
  && [ -z "$(code 'OverlayHost|Notify\.|Ui\.toast|panel\.dismiss\(\)' "$PM")" ] \
  && grep -qxF '                closeThen(() -> menu.act(FeatureId.LAMP_OFF));' "$PM"; then
  echo "ok   power menu: the profile's stock items as round tiles, in the menu's host, Turn off screen = the lamp.off row"
else
  echo "FAIL power menu: items past the profile, not the round tiles, a window past the menu's host, or Turn off screen past the lamp.off row"; bad=1
fi
# colour vision (rs30-parity-system2): every stock filter token shows its own label, off the shared Off
ML="$HERE/src/org/lumen/projector/panel/MenuLabels.java"
cv=0
for t in gray red_green green_red blue_yellow; do
  grep -qxF "            case \"settings.colour_vision|$t\": return R.string.lumen_m_colour_vision_$t;" "$ML" || cv=1
done
grep -qxF '            case "settings.colour_vision|off": return R.string.lumen_m_panel_off;' "$ML" || cv=1
if [ $cv = 0 ]; then
  echo "ok   colour vision: each stock filter its own label, off = Off"
else
  echo "FAIL colour vision: a filter token shows another filter's label or none"; bad=1
fi
# the sound output page (owner variant C): rows per model, the card, Auto, offline Bluetooth, Speaker keeps ARC, the HB jack
A="$G/apps/core/lumen-audio/src/org/lumen/audio"
mkdir -p "$OUT/outputs"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/outputs" "$HERE"/src/org/lumen/menu/*.java \
  "$HERE"/src/org/lumen/menu/utils/*.java "$HERE/src/org/lumen/menu/sound/SoundOutputs.java" "$A/OutputDevice.java" \
  "$A/SoundbarMode.java" "$G/apps/core/lumen-testkit/src/org/lumen/testkit/Check.java" "$HERE/test/outputs/SoundOutputsTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/outputs" org.lumen.menu.sound.SoundOutputsTest "$G" || bad=1
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/outputs" -cp "$OUT/outputs" \
  "$HERE/src/org/lumen/menu/sound/OutputRefresh.java" "$HERE/test/outputs/OutputRefreshTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/outputs" org.lumen.menu.sound.OutputRefreshTest "$G" || bad=1
# Power On Auto Boot (2026-11-19): model data, the stock call per model, no write without the user's flip
python3 -B "$HERE/test/power_on_ac_test.py" "$G" || bad=1
# snapshots without a profile are stale
# the menu's look (MenuLook) as the app stores it: only valid tokens, read through MenuLook, each under its own key,
# and the General row shows what was read back after a write
LP="$HERE/src/org/lumen/projector/panel/MenuLookPrefs.java"
for w in '        if (!MenuLook.valid(f, value)) return false;' \
  '            if (f == FeatureId.SETTINGS_MENU_SIDE) return MenuLook.side(prefs(c).getString(K_SIDE, null), rtl(c));' \
  '            if (f == FeatureId.SETTINGS_MENU_DENSITY) return MenuLook.density(prefs(c).getString(K_DENSITY, null));' \
  '            return prefs(c).edit().putString(f == FeatureId.SETTINGS_MENU_SIDE ? K_SIDE : K_DENSITY, value).commit();'; do
  grep -qxF -- "$w" "$LP" || { echo "FAIL menu look: MenuLookPrefs lost: $w"; bad=1; }
done
grep -qxF '            return MenuLookPrefs.set(c, f, value) && value.equals(MenuLookPrefs.get(c, f));' \
  "$HERE/src/org/lumen/projector/panel/ModuleScreens.java" || { echo "FAIL menu look: the row is not read back after a write"; bad=1; }
# the power menu's Power off of every family is Android's shutdown (Halt), never the power key's sleep (LR-YVHNX4)
mkdir -p "$OUT/halt"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/halt" "$HERE/src/org/lumen/projector/panel/Halt.java" \
  "$HERE/test/halt/stubs/android/os/PowerManager.java" "$HERE/test/halt/stubs/android/content/Context.java" \
  "$HERE/test/halt/stubs/android/util/Log.java" "$HERE/test/halt/HaltTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/halt" org.lumen.projector.panel.HaltTest || bad=1
if [ -d "$G/apps/LumenProjector/vendor" ]; then python3 "$HERE/test/poweroff_wiring.py" "$G" || bad=1; fi
python3 "$HERE/test/ui/poweroff_log_test.py" || bad=1
for s in "$HERE"/test/snapshots/*.txt; do
  [ -f "$s" ] || continue
  c=$(basename "$s" .txt)
  [ -f "$G/devices/$c/menu.json" ] || { echo "FAIL stale snapshot $s (no devices/$c/menu.json)"; bad=1; }
done
[ "$bad" = 0 ] && echo "lumen-menu: $n profile(s), all green" || { echo "lumen-menu: RED"; exit 1; }
