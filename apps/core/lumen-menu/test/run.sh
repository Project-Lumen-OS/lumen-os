#!/bin/sh
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
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE"/src/org/lumen/menu/*.java "$HERE"/src/org/lumen/menu/utils/*.java "$HERE/test/MenuSpecTest.java" \
  "$HERE/test/GearPressTest.java" "$HERE/test/MenuValuesTest.java" "$HERE/test/LampOffTest.java"
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
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.MenuSpecTest "$G" "$G"/devices/*/menu.json || bad=1
# the gear key: one shared press rule (GearPress); that every app runs it is checked by tools/devices/test/keylayout_gear_test.py
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.GearPressTest || bad=1
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.utils.MenuValuesTest || bad=1
# the stock "Turn off screen" (LampOff): one key and recovery rule for every app that offers lamp.off
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.menu.LampOffTest "$G" || bad=1
# the brightness slider: LEFT at its minimum goes the "Turn off screen" row's own way (menu closes first), lamp.off only
LMC="$HERE/src/org/lumen/projector/panel/MenuController.java"
if grep -qxF '                if (LampOff.offFromSlider(has(FeatureId.LAMP_OFF), row.isKnown() ? row.getValue() : null, row.getMin(), dir, 0)) {' "$LMC" \
  && grep -qxF '                    safe("lamp off", () -> action(FeatureId.LAMP_OFF));' "$LMC" \
  && grep -qxF '            FeatureId.LAMP_OFF);' "$LMC"; then
  echo "ok   lamp slider: left at the minimum runs the lamp.off action"
else
  echo "FAIL lamp slider: left at the minimum does not run the lamp.off action of the profile"; bad=1
fi
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
# snapshots without a profile are stale
for s in "$HERE"/test/snapshots/*.txt; do
  [ -f "$s" ] || continue
  c=$(basename "$s" .txt)
  [ -f "$G/devices/$c/menu.json" ] || { echo "FAIL stale snapshot $s (no devices/$c/menu.json)"; bad=1; }
done
[ "$bad" = 0 ] && echo "lumen-menu: $n profile(s), all green" || { echo "lumen-menu: RED"; exit 1; }
