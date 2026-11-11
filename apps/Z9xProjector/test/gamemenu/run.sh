#!/bin/sh
# Lumen game menu, design A (panel/GmModel is plain Java): sh test/gamemenu/run.sh
# GmModelTest covers the focus map, presets, stock mutex rules, console mapping, frame player timing, capability
# gating and timers. The checks below pin the wiring the tests assume: gear long press, auto show once, HAL calls
# only from GameMenu with the stock argument ranges and a read-back, controller frames, strings, licences, HB drops.
set -eu
HERE=$(cd "$(dirname "$0")/../.." && pwd)
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$HERE/src/org/lumen/projector/panel/GmModel.java" "$HERE/test/gamemenu/GmModelTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.GmModelTest
# GmController (keys, redraws, device picture) on host stubs of android.* and of its panel neighbours; R from res/
mkdir -p "$OUT/ctl/gen/org/lumen/projector" "$OUT/ctl/classes"
python3 - "$HERE/res" "$OUT/ctl/gen/org/lumen/projector/R.java" <<'EOF'
import glob, os, sys, xml.etree.ElementTree as ET
names = sorted({e.get('name') for f in glob.glob(os.path.join(sys.argv[1], 'values', '*.xml'))
                for e in ET.parse(f).getroot().iter('string')})
body = ''.join('        public static final int %s = %d;\n' % (n, 0x7f100000 + i) for i, n in enumerate(names))
open(sys.argv[2], 'w').write('package org.lumen.projector;\npublic final class R {\n    public static final class string {\n'
                             + body + '    }\n}\n')
EOF
find "$HERE/test/gamemenu/stubs" -name '*.java' | sed 's/.*/"&"/' > "$OUT/ctl/stubs.txt"   # paths with spaces
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT/ctl/classes" @"$OUT/ctl/stubs.txt" \
  "$OUT/ctl/gen/org/lumen/projector/R.java" \
  "$HERE/src/org/lumen/projector/panel/GmModel.java" "$HERE/src/org/lumen/projector/panel/GmController.java" \
  "$HERE/test/gamemenu/GmControllerTest.java"
"$JAVA_HOME/bin/java" -cp "$OUT/ctl/classes" org.lumen.projector.panel.GmControllerTest
SRC="$HERE/src/org/lumen/projector"
GM="$SRC/panel/GameMenu.java"
CTL="$SRC/panel/GmController.java"
VIEW="$SRC/panel/GmView.java"
G2="$SRC/hal/Gmpf2Client.java"
bad=0
fail() { echo "FAIL $1"; bad=1; }
has() { grep -qF -- "$2" "$1" || fail "$3"; }

# ---- gear long press (KeyReceiver): the game menu while game mode runs, Android settings otherwise
K="$SRC/KeyReceiver.java"
n=$(grep -c 'GameMenu.onGearLong(ctx, () -> {' "$K" || true)
[ "$n" = 1 ] || fail "KeyReceiver: $n gear long press hooks (want 1)"
grep -A3 'GameMenu.onGearLong(ctx, () -> {' "$K" | grep -qF 'if (setupDone(ctx, "gear long press")) openTvSettings(ctx);' \
  || fail "KeyReceiver: Android settings no longer the fallback of the long press"
grep -B4 'GameMenu.onGearLong(ctx' "$K" | grep -qF '} else if (!sGearLongFired && ev.getDownTime() == sGearDownTime) {' \
  || fail "KeyReceiver: game menu not on the first repeated DOWN of the gear"
has "$GM" 'if (port != 1 && port != 2) { fallback.run(); return; }' "onGearLong: shows off HDMI"
has "$GM" 'if (Boolean.TRUE.equals(running) && GameProfile.activePort() == port) show(app, port, false);' \
  "onGearLong: shows without game mode running (55) on the HDMI on screen"
has "$GM" 'return p != null && p.length >= 1 && p[0] == 0;' "game mode running is not 55 state 0"
has "$GM" 'OverlayHost.get(app).dismissAll(true);' "a second long press does not close the menu"
# ---- auto show once on the first game mode entry, auto hide 30 s then, 10 s by the key
has "$GM" 'if (!prefs.getBoolean(K_FIRST_SHOWN, false)) firstPending = true;' "auto show not once"
has "$GM" 'prefs.edit().putBoolean(K_FIRST_SHOWN, true).apply();' "auto show not remembered"
has "$GM" 'if (firstPending && OverlayHost.get(app).current() == null) {' "auto show over another panel"
has "$GM" 'show(app, port, true);' "auto show not marked first"
has "$GM" '@Override protected long autoHideMs() { return GmModel.autoHideMs(ctl.first); }' "auto hide not from GmModel"
has "$GM" 'if ((port != 1 && port != 2) || !screen) {' "watcher reads the HAL with no HDMI session or the screen off"
has "$SRC/App.java" 'org.lumen.projector.panel.GameMenu.install(this);' "App does not install the game menu"

# ---- HAL: the new IGmpf2 calls only from GameMenu, with the stock ranges, each write read back
for m in getVrrState setVrrState getGamePicture setGamePicture getDarkScenes setDarkScenes getGameSound setGameSound; do
  users=$(grep -rlE "\\.$m\\(" "$SRC" --include=*.java | grep -v '/hal/' | sed "s|$SRC/||" | sort | tr '\n' ' ')
  [ "$users" = "panel/GameMenu.java " ] || fail "$m called from: $users(want panel/GameMenu.java only)"
done
has "$G2" 'private static final int GET_GAMING_SOUND_EFFECT_MODE = 1;' "IGmpf2 1 code"
has "$G2" 'private static final int SET_GAMING_SOUND_EFFECT_MODE = 2;' "IGmpf2 2 code"
has "$G2" 'private static final int GET_DARK_BOOST_STATE = 296;' "IGmpf2 296 code"
has "$G2" 'private static final int SET_DARK_BOOST_ONOFF = 297;' "IGmpf2 297 code"
has "$G2" 'private static final int GET_GAME_PICTURE_MODE = 298;' "IGmpf2 298 code"
has "$G2" 'private static final int SET_GAME_PICTURE_MODE = 299;' "IGmpf2 299 code"
has "$G2" 'private static final int GET_VRR_ENABLE_STATE = 300;' "IGmpf2 300 code"
has "$G2" 'private static final int SET_VRR_MODE_ENABLE = 301;' "IGmpf2 301 code"
n=$(grep -c 'checkRange("hdmi source", hdmiSource, 1, 2)' "$G2" || true)
[ "$n" = 7 ] || fail "Gmpf2Client: $n HDMI source range checks (want 7: 310 and the new 296..301)"
n=$(grep -c 'checkRange("game picture", \(mode\|gamePicture\), 0, 4)' "$G2" || true)
[ "$n" = 3 ] || fail "Gmpf2Client: $n game picture range checks (want 3: 296, 297, 299)"
has "$G2" 'return callI32RetBool(SET_GAMING_SOUND_EFFECT_MODE, checkRange("game sound", mode, 190, 192));' "IGmpf2 2 outside 190..192"
has "$G2" 'return callI32I32RetI32(SET_VRR_MODE_ENABLE, on ? 1 : 0, checkRange("hdmi source", hdmiSource, 1, 2));' "IGmpf2 301 state not 0/1"
has "$GM" 'int back = poll(() -> g2.getVrrState(port), GmModel.VRR, value);' "VRR write not read back"
has "$GM" 'int back = poll(() -> g2.getGamePicture(port), GmModel.PIC, value);' "game picture write not read back"
has "$GM" 'int back = poll(() -> g2.getDarkScenes(p, port) ? 1 : 0, GmModel.DARK, value);' "dark scenes write not read back"
has "$GM" 'int back = poll(g2::getGameSound, GmModel.SND, value);' "game sound write not read back"
has "$GM" 'if (g.getCurrentInputSource() != port) throw new IllegalStateException("696 is not HDMI " + port);' \
  "write without the 696 check"
n=$(grep -c 'if (g.getCurrentInputSource() != port) throw' "$GM" || true)
[ "$n" = 2 ] || fail "GameMenu: $n write paths with the 696 check (want 2: speed and the others)"
has "$GM" 'if (back == value && o != null && o.isTopSpeed()) {' "207 not only after 647 reads top speed"
grep -q 'SystemClock.sleep(100)' "$GM" && fail "GameMenu: the speed read back sleeps on the HAL thread again"
# only the HAL thread talks to the vendor: every client call in panel/Gm* sits inside Hal.query lambdas
grep -nE 'Gmpf(2)?Client\.(get|peek)|HalController' "$SRC"/panel/Gm*.java "$GM" && fail "game menu reaches the HAL outside Hal.query"
# ---- capability gating: a gated item is shown after a read in range, hidden on no answer or a bad read back
has "$CTL" 'c.onNoAnswer(k);' "no answer does not hide a gated item"
has "$CTL" 'c.onRead(k, raw);' "gated read not fed to the capability check"
has "$CTL" 'ok = back != GmModel.BAD && GameMenu.CAPS.onReadBack(key, value, back);' "gated write not checked by its read back"
has "$CTL" 'List<String> tiles() { return GmModel.visibleOrder(GameMenu.CAPS); }' "dock not from the visible items"
has "$VIEW" 'List<String[]> groups = GmModel.visibleGroups(GameMenu.CAPS);' "dock groups not from the visible items"
grep -A1 -F 'if (port == 1 || port == 2) {' "$GM" | grep -qF 'try { s.vrr = g2.getVrrState(port); }' \
  || fail "gated reads without an HDMI port"

# ---- frame player: frames by GmModel.frameAt, consoles only; computers get a flat icon
has "$VIEW" 'int f = GmModel.frameAt(now - t0);' "frame player not on GmModel.frameAt"
has "$VIEW" 'try (InputStream in = am.open(GmModel.framePath(theme, f))) {' "frames not from GmModel.framePath"
has "$VIEW" 'Bitmap b = decodeFrame(am, theme, f);' "frame loader not on decodeFrame (the one the harness checks)"
has "$VIEW" 'if (!GmModel.isConsole(theme)) return;' "frames loaded for a computer"
has "$VIEW" 'QpdStyle.Icon i = icon("pc:" + ctl.theme);' "computer icon missing"
has "$VIEW" 'if (sLoader == null) sLoader = SafeHandler.newThread("z9x-gm-frames");' "one frame loader thread per menu"
has "$VIEW" 'String name = ctl.cecName != null ? ctl.cecName : "HDMI " + ctl.port;' "card name not the shown name"
for f in apple windows laptop; do [ -f "$HERE/res/drawable/gm_ic_$f.xml" ] || fail "no gm_ic_$f icon"; done
grep -q '<target' "$HERE/res/drawable/gm_ic_apple.xml" "$HERE/res/drawable/gm_ic_windows.xml" "$HERE/res/drawable/gm_ic_laptop.xml" \
  && fail "computer icons are animated (owner: flat, no turntable)"
grep -qF 'static final String[] FRAME_THEMES = {TH_PS, TH_XBOX, TH_SWITCH, TH_PAD};' "$SRC/panel/GmModel.java" \
  || fail "GmModel.FRAME_THEMES is not ps, xbox, switch, pad"
[ "$(ls "$HERE/assets/gamemenu" | sort | tr '\n' ' ')" = "pad ps switch xbox " ] \
  || fail "assets/gamemenu holds $(ls "$HERE/assets/gamemenu" | tr '\n' ' ')(want pad ps switch xbox)"
# every frame of every theme: 30 turn + 24 sway, a static WebP (VP8X with alpha, no animation) of 600 x 360, and
# decoded (Pillow, when installed) with a visible controller in it; the same check runs on a built APK (apk_frames.py)
python3 "$HERE/test/gamemenu/apk_frames.py" --assets "$HERE/assets/gamemenu" || fail "controller frames"
if [ -n "${GM_APK:-}" ]; then python3 "$HERE/test/gamemenu/apk_frames.py" --assets "$HERE/assets/gamemenu" --apk "$GM_APK" || fail "frames in $GM_APK"; fi
# ---- the device picture follows the CEC list while the menu is open (PS5: placeholder first, its name later)
has "$GM" 'if (w != null) w.refreshCec(port);' "show does not read the CEC list again"
has "$GM" 'if (p != null && p.isShowing() && p.ctl.port == port) p.ctl.onSource(s);' "a CEC read does not reach the open menu"
has "$GM" 'if (now - cecAt > (isShowing() ? CEC_MENU_MS : CEC_EVERY_MS)) {' "CEC list not re-read while the menu is open"
has "$GM" 'marked = GameProfile.isConsolePort(app, port);' "the user's console mark is not a console hint"
has "$CTL" 'boolean hint = consolePort || (snap != null && Boolean.TRUE.equals(snap.allm));' "ALLM is not a console hint"

# ---- licences (CC BY 4.0 credit of the frames) in Projector settings, Help
has "$SRC/SettingsActivity.java" 'actionRow(gc, R.string.gm_licences, R.string.gm_licences_d, v -> org.lumen.projector.panel.GmLicences.show(this));' \
  "no Licences row"
L="$SRC/panel/GmLicences.java"
for w in '"PS5 Controller\" by Taohid Animation' '"Xbox One Controller Free\" by paxillop' '"Nintendo Switch OLED\" by Wille Evensson' \
         'https://creativecommons.org/licenses/by/4.0/' 'ps5-controller-b7bb9c5102a04cb0b1966c6d02bad7d6' \
         'xbox-one-controller-free-97fb54c001f84a6896b6ce8eb7a1814d' 'nintendo-switch-oled-aa2c7d815a104accae7709eb39ac2184'; do
  grep -qF -- "$w" "$L" || fail "licences: missing $w"
done

# ---- strings: 17 locales + base, same keys, plain words (no em or en dash, no semicolon), same placeholders
python3 - "$HERE/res" <<'EOF' || bad=1
import os, re, sys, xml.etree.ElementTree as ET
res = sys.argv[1]
def load(p, all_=False):
    return {e.get('name'): ''.join(e.itertext()) for e in ET.parse(p).getroot().iter('string')
            if all_ or e.get('translatable') != 'false'}
base = load(os.path.join(res, 'values/strings_gm.xml'))
dirs = sorted(d for d in os.listdir(res) if d.startswith('values-') and os.path.isdir(os.path.join(res, d))
              and os.path.exists(os.path.join(res, d, 'strings.xml')))
bad = 0
if len(dirs) != 17:
    print('FAIL strings: %d locale dirs (want 17): %s' % (len(dirs), dirs)); bad = 1
for d in ['values'] + dirs:
    p = os.path.join(res, d, 'strings_gm.xml')
    if not os.path.exists(p):
        print('FAIL strings: no %s' % p); bad = 1; continue
    s = load(p, d == 'values')
    if d != 'values' and set(s) != set(base):
        print('FAIL strings: %s keys differ: %s' % (d, sorted(set(s) ^ set(base)))); bad = 1
    for k, v in s.items():
        if re.search('[\u2013\u2014;]', v) or ' - ' in v:
            print('FAIL strings: %s %s has a dash or a semicolon: %r' % (d, k, v)); bad = 1
        if k in base and sorted(re.findall(r'%\d\$[sd]', v)) != sorted(re.findall(r'%\d\$[sd]', base[k])):
            print('FAIL strings: %s %s placeholders differ' % (d, k)); bad = 1
        if not v.strip():
            print('FAIL strings: %s %s empty' % (d, k)); bad = 1
sys.exit(bad)
EOF

# ---- HB: the Z9X game menu never reaches the HB app (its own GameBar)
HB="$HERE/../../devices/goat_elegant/app/HbProjector.layers"
for l in 'drop      src/org/lumen/projector/panel/Gm*.java' 'drop      src/org/lumen/projector/panel/GameMenu.java' \
         'drop      res/drawable/gm_ic_*.xml' 'drop      res/values*/strings_gm.xml' 'drop      assets/gamemenu/' \
         'drop      tools/gm_icons.py' 'drop      test/gamemenu/'; do
  grep -qxF -- "$l" "$HB" || fail "HB recipe: missing '$l'"
done
[ $bad = 0 ] && echo "game menu wiring: ok"
exit $bad
