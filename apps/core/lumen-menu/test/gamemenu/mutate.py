#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the Lumen game menu (GmModel logic + the wiring run.sh pins): each must turn test/gamemenu red.
python3 test/gamemenu/mutate.py [-k substring]"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import scratch  # noqa: E402

# paths relative to gsi/: the menu lives in the module, the hooks in the device app the suite assembles (the Z9X)
M = 'apps/core/lumen-menu/'
PJ = M + 'src/org/lumen/projector/'
MOD = PJ + 'panel/GmModel.java'
GM = PJ + 'panel/GameMenu.java'
CTL = PJ + 'panel/GmController.java'
VIEW = PJ + 'panel/GmView.java'
LIC = PJ + 'panel/GmLicences.java'
ZJ = 'apps/LumenProjector/core/src/org/lumen/projector/'
KEY = ZJ + 'KeyReceiver.java'
G2 = 'apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/hal/Gmpf2Client.java'
B2 = 'apps/core/lumen-gmpf/src/org/lumen/projector/hal/Gmpf2Base.java'   # the shared IGmpf2 body
Z9F = 'apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/panel/Z9xFeatures.java'
APPJ = ZJ + 'App.java'
IO = 'apps/LumenProjector/vendor/xgimi/hidl-gmpf/src/org/lumen/projector/panel/Z9xGameIo.java'   # the Z9X side of the shared game menu: its reads, writes and read-backs
RU = M + 'res/values-ru/lumen_strings_gm.xml'
DE = M + 'res/values-de/lumen_strings_gm.xml'
HB = 'devices/goat_elegant/app/HbProjector.layers'

MUTANTS = [
    # ---- x50-game3-1128: layers over the game off under the device's cover (X50 eye guard)
    ('layers: an unknown cover shows them', 'apps/core/lumen-menu/src/org/lumen/projector/ui/LayerSet.java', 'return Boolean.FALSE.equals(coverUp);', 'return !Boolean.TRUE.equals(coverUp);'),
    ('layers: a cover check that throws shows them', 'apps/core/lumen-menu/src/org/lumen/projector/ui/LayerSet.java', '            up = null;\n', '            up = false;\n'),
    ('layers: kept on screen under the cover', 'apps/core/lumen-menu/src/org/lumen/projector/ui/LayerSet.java', '            } else if (!on && shown.remove(v)) {\n                drop(v);\n', '            } else if (!on && shown.contains(v)) {\n'),
    ('layers: added again while shown', 'apps/core/lumen-menu/src/org/lumen/projector/ui/LayerSet.java', 'if (on && !shown.contains(v)) {', 'if (on) {'),
    ('layers: a refused add counted as shown', 'apps/core/lumen-menu/src/org/lumen/projector/ui/LayerSet.java', '                    windows.add(v, e.getValue());\n                    shown.add(v);\n', '                    shown.add(v);\n                    windows.add(v, e.getValue());\n'),
    ('layers: a removed layer comes back', 'apps/core/lumen-menu/src/org/lumen/projector/ui/LayerSet.java', '        layers.remove(v);\n        if (shown.remove(v)) drop(v);', '        if (shown.remove(v)) drop(v);'),
    ('layers: add does not check the cover', 'apps/core/lumen-menu/src/org/lumen/projector/ui/LayerSet.java', '        layers.put(v, lp);\n        sync();\n', '        layers.put(v, lp);\n'),
    # ---- x50-game3-1128: the stock screen shift (SCREEN_OFFSET, ScreenOffsetDialog)
    ('so: refusal dropped', MOD, 'if (SO.equals(k) && s.get(AR) < 2) return R_SO_RATIO;', ''),
    ('so: refused with 21:9 too', MOD, 's.get(AR) < 2) return R_SO_RATIO;', 's.get(AR) < 3) return R_SO_RATIO;'),
    ('so: shown without the aspect item', MOD, 'if (SO.equals(k) && !visible(AR)) return false;', ''),
    ('so: 6 lines per key', MOD, 'SHIFT_STEP = 3,', 'SHIFT_STEP = 6,'),
    ('so: the band leaves the screen at the bottom', MOD, 'Math.min(UI_LINES - height, top + SHIFT_STEP)', 'top + SHIFT_STEP'),
    ('so: the band leaves the screen at the top', MOD, 'if (d == UP) return Math.max(0, top - SHIFT_STEP);', 'if (d == UP) return top - SHIFT_STEP;'),
    ('so: panel line multiplied', MOD, 'return (int) (top / shiftScale(panel));', 'return (int) (top * shiftScale(panel));'),
    ('so: band rounded instead of truncated', MOD, 'return new int[] {(int) (a[1] * k), (int) (a[3] * k)};', 'return new int[] {Math.round(a[1] * k), Math.round(a[3] * k)};'),
    ('so: unknown panel scale 0.5', MOD, 'return panel > 0 ? UI_LINES / (float) panel : 1f;', 'return panel > 0 ? UI_LINES / (float) panel : 0.5f;'),
    ('so: reset on a short OK', CTL, 'if (longOk && !shiftResetHeld) {', 'if (!shiftResetHeld) {'),
    ('so: reset on every repeat', CTL, '                shiftResetHeld = true;\n', ''),
    ('so: the OK release does not re-arm the reset', CTL, '            if (ok) shiftResetHeld = false;\n', ''),
    ('so: BACK closes the menu from the band', CTL, '        if (shifting) { shifting = false; invalidate(); return true; }\n', ''),
    ('so: refusal text missing', CTL, 'case GmModel.R_SO_RATIO: return app.getString(R.string.lumen_gm_rule_so_ratio);', ''),
    ('so: move sent as a read', CTL, 'io.shift(GmModel.SHIFT_MOVE, port, GmModel.shiftWire(top, shiftArea[4]), a -> {', 'io.shift(GmModel.SHIFT_READ, port, GmModel.shiftWire(top, shiftArea[4]), a -> {'),
    ('so: an unanswered read keeps the band', CTL, '            shifting = false;\n            toast(R.string.lumen_gm_failed_soft);\n            return;', '            toast(R.string.lumen_gm_failed_soft);\n            return;'),
    ('so: repeats dropped while shifting', CTL, '!moving && !shifting && popup == null', '!moving && popup == null'),
    ('so: closing the menu keeps the band', CTL, '        moving = false;\n        shifting = false;\n', '        moving = false;\n'),
    ('so: OK opens a popup instead of the band', CTL, '        if (GmModel.SO.equals(k)) { startShift(); return; }\n', ''),
    # ---- rs30-menu: VRR only at top speed where the profile says so (stock support_vrr_only_at_top_speed)
    ('VRR speed rule never applied', MOD, 'if (vrrTopSpeed && VRR.equals(k) && v == 1 && s.get(LAT) != 1) return R_VRR_SPEED;', ''),
    ('VRR refused at top speed too', MOD, '&& v == 1 && s.get(LAT) != 1) return R_VRR_SPEED;', '&& v == 1) return R_VRR_SPEED;'),
    ('VRR off refused below top speed', MOD, 'VRR.equals(k) && v == 1 && s.get(LAT) != 1) return R_VRR_SPEED;',
     'VRR.equals(k) && s.get(LAT) != 1) return R_VRR_SPEED;'),
    ('VRR speed rule on for every device', MOD, 'static void setVrrTopSpeed(boolean on) { vrrTopSpeed = on; }',
     'static void setVrrTopSpeed(boolean on) { vrrTopSpeed = true; }'),
    ('profile VRR rule not installed', GM, '            GmModel.setVrrTopSpeed(p.gameVrrTopSpeed());', '            GmModel.setVrrTopSpeed(false);'),
    ('VRR speed refusal without its text', CTL, 'case GmModel.R_VRR_SPEED: return app.getString(R.string.lumen_gm_rule_vrr_speed);', ''),
    # ---- capability gating
    ('gated item shown before any read', MOD, 'for (String g : GATED) cap.put(g, CAP_UNKNOWN);', ''),
    ('read outside the stock range shows the item', MOD, "cap.put(k, plain(k, raw) == BAD ? CAP_UNKNOWN : CAP_OK);",
     'cap.put(k, CAP_OK);'),
    ('a failed item comes back on a good read', MOD, 'if (!cap.containsKey(k) || get(k) == CAP_FAILED) return;',
     'if (!cap.containsKey(k)) return;'),
    ('no answer keeps the item', MOD, 'if (cap.containsKey(k) && get(k) != CAP_FAILED) cap.put(k, CAP_UNKNOWN);', ''),
    ('a bad read back keeps the item', MOD, 'if (!ok && cap.containsKey(k)) cap.put(k, CAP_FAILED);', ''),
    ('unknown counts as visible', MOD, '&& (!cap.containsKey(k) || get(k) == CAP_OK);', '&& (!cap.containsKey(k) || get(k) != CAP_FAILED);'),
    ('VRR proven without a check', MOD, 'static final String[] GATED = {VRR, PIC, DARK, SND, CS, HDR, FS};',
     'static final String[] GATED = {PIC, DARK, SND, CS, HDR, FS};'),
    ('empty group kept', MOD, '            if (ks.isEmpty()) continue;\n', ''),
    # ---- raw values
    ('VRR -1 not read as off', MOD, 'case VRR: return raw == -1 ? 0 :', 'case VRR: return raw == -1 ? BAD :'),
    ('game picture 5 accepted', MOD, '(raw >= 0 && raw <= 4) ? raw : BAD;', '(raw >= 0 && raw <= 5) ? raw : BAD;'),
    ('sound 0 not off', MOD, 'case SND: return (raw == 0 || raw == SND_OFF) ? 0 :', 'case SND: return (raw == SND_OFF) ? 0 :'),
    ('sound wire shifted', MOD, 'return SND_OFF + v;', 'return SND_OFF + 1 + v;'),
    ('sound past the profile sent', MOD, 'if (v < 0 || v >= sounds) throw', 'if (v < 0 || v > sounds) throw'),
    ('turbo option swapped', MOD, 'if (lat == 1) return hfr == 1 ? 2 : 3;', 'if (lat == 1) return hfr == 1 ? 3 : 2;'),
    ('HFR bit of 647 wrong', MOD, 'static int hfrOf(int opt) { return (opt == 1 || opt == 2) ? 1', 'static int hfrOf(int opt) { return (opt == 1 || opt == 3) ? 1'),
    ('21:9 offered without the profile', MOD, 'private static int[] wideWire = {};', 'private static int[] wideWire = {5, 6};'),
    ('aspect item opens while VRR is on', MOD, '        if (AR.equals(k) && s.get(VRR) == 1) return R_AR_VRR;       // stock toast_close_vrr_retry on picking the item\n', ''),
    ('wide wire values swapped', MOD, 'wideWire = new int[] {w.get(0), w.get(1)};', 'wideWire = new int[] {w.get(1), w.get(0)};'),
    ('aspect gate never opened', CTL, '            if (s.ar != GmModel.BAD) c.onRead(GmModel.AR, s.ar);\n', ''),
    # ---- stock mutex rules
    ('VRR allowed with a wide format', MOD, 'if (VRR.equals(k) && v == 1 && ar >= 2) return R_VRR_WIDE;', ''),
    ('HFR off while VRR is on', MOD, 'if (HFR.equals(k) && v == 0 && vrr == 1) return R_HFR_VRR;', ''),
    ('values not offered accepted', MOD, "if (!optionOffered(k, v) && !AIM.equals(k)) return R_NOT_OFFERED;", ''),
    ('turbo never asks', MOD, 'return newLat == 1 && curLat != 1 && !Boolean.TRUE.equals(fullFrame);', 'return false;'),
    ('turbo asks at the full frame', MOD, 'return newLat == 1 && curLat != 1 && !Boolean.TRUE.equals(fullFrame);',
     'return newLat == 1 && curLat != 1;'),
    # ---- presets
    ('competitive without dark scenes', MOD, 'm.put(PIC, 1); m.put(DARK, 1); m.put(SND, 1);', 'm.put(PIC, 1); m.put(SND, 1);'),
    ('story picture is FPS', MOD, 'm.put(HFR, 0); m.put(PIC, 2);', 'm.put(HFR, 0); m.put(PIC, 1);'),
    ('wide format not to Auto before VRR', MOD, 'if (vrrWant != null && vrrWant == 1 && s.get(AR) >= 2) out.add(new Step(AR, 0));', ''),
    ('VRR off after HFR off', MOD, 'if (vrrWant != null && vrrWant == 0 && vrrNow == 1) out.add(new Step(VRR, 0));', ''),
    ('HFR off while a hidden VRR stays on', MOD, 'if (hfrW == 0 && (vrrStaysOn || s.get(FS) == 1)) hfrW = 1;',
     'if (hfrW == 0 && s.get(FS) == 1) hfrW = 1;'),
    ('dark scenes not re-sent after the picture', MOD, '|| (DARK.equals(k) && hasStep(out, PIC))', ''),
    ('preset writes hidden items', MOD, 'if (w == null || !c.visible(k)) continue;', 'if (w == null) continue;'),
    ('preset match counts hidden items', MOD, '                if (!c.visible(e.getKey())) continue;\n', ''),
    # ---- focus map
    ('dock wraps at the right', MOD, 'case RIGHT: return new int[] {Z_DOCK, Math.min(tiles - 1, idx + 1), Math.min(tiles - 1, idx + 1)};',
     'case RIGHT: return new int[] {Z_DOCK, (idx + 1) % tiles, (idx + 1) % tiles};'),
    ('UP always to the first preset', MOD, 'case UP: return new int[] {Z_PRESETS, presetBelow(idx, tiles, presets), idx};',
     'case UP: return new int[] {Z_PRESETS, 0, idx};'),
    ('DOWN forgets the tile', MOD, 'case DOWN: return new int[] {Z_DOCK, Math.max(0, Math.min(tiles - 1, lastDock)), lastDock};',
     'case DOWN: return new int[] {Z_DOCK, 0, lastDock};'),
    ('popup DOWN past the last row', MOD, 'if (key == DOWN) return Math.min(rows - 1, row + 1);', 'if (key == DOWN) return row + 1;'),
    ('crosshair style does not wrap', MOD, 'if (key == RIGHT) return new int[] {row, (style + 1) % AIM_STYLES, colour};',
     'if (key == RIGHT) return new int[] {row, Math.min(AIM_STYLES - 1, style + 1), colour};'),
    ('crosshair leaves the screen', MOD, 'Math.max(AIM_MIN_X, Math.min(AIM_MAX_X, x + dx))', 'x + dx'),
    # ---- console mapping
    ('PS5 not a PlayStation', MOD, 'n.matches(".*\\\\bps[2-9]\\\\b.*")', 'n.matches(".*\\\\bps[2-4]\\\\b.*")'),
    ('nameless Sony is a computer', MOD, '            if (vendorId == VENDOR_SONY && playback) return TH_PS;\n', ''),
    ('placeholder name counts as a name', MOD, 'return n.isEmpty() || PLACEHOLDER.matcher(n).matches() ? null : n;',
     'return n.isEmpty() ? null : n;'),
    ('nameless Microsoft block not Xbox', MOD, '            if (in(vendorId, VENDORS_MICROSOFT)) return TH_XBOX;\n', ''),
    ('vendor beats a real name', MOD, '        if (n.isEmpty()) {\n            if (vendorId == VENDOR_SONY', '        if (true) {\n            if (vendorId == VENDOR_SONY'),
    ('no generic pad for a nameless console', MOD, 'return consoleHint ? TH_PAD : TH_COMPUTER;', 'return TH_COMPUTER;'),
    ('the placeholder on the card', MOD, '        if (real != null) return real;\n', '        if (cecName != null) return cecName;\n'),
    ('Mac any substring', MOD, 'n.matches(".*\\\\bmac\\\\b.*")', 'n.contains("mac")'),
    ('Microsoft before Xbox', MOD, '        if (n.contains("xbox")) return TH_XBOX;\n', ''),
    ('a computer gets a gamepad', MOD, 'return TH_PS.equals(theme) || TH_XBOX.equals(theme) || TH_SWITCH.equals(theme) || TH_PAD.equals(theme);',
     'return !TH_COMPUTER.equals(theme);'),
    ('the generic pad plays no frames', MOD, ' || TH_SWITCH.equals(theme) || TH_PAD.equals(theme);', ' || TH_SWITCH.equals(theme);'),
    # ---- keys redraw, the picture follows the device while the menu is open
    ('popup keys do not redraw (owner Z9X bug)', CTL, '            else if (popup != null) popupKey(d, ok);\n',
     '            else if (popup != null) { popupKey(d, ok); return true; }\n'),
    ('Turbo question keys do not redraw', CTL, '            else if (confirmOk != null) confirmKey(d, ok);\n',
     '            else if (confirmOk != null) { if (ok) { confirmOk = null; } return true; }\n'),
    ('popup row change not told to the view', CTL, '            if (prow != was) gmView().onPopupRow(was, prow);\n', ''),
    ('popup focus starts on the first row', CTL, '            prow = at < 0 ? 0 : at;', '            prow = 0;'),
    ('ALLM is no console hint', CTL, 'boolean hint = consolePort || (snap != null && Boolean.TRUE.equals(snap.allm));', 'boolean hint = consolePort;'),
    ('a new CEC answer ignored by the open menu', CTL, '        source = s;\n        updateTheme();\n', ''),
    ('theme not refreshed by a HAL read', CTL, '        state.hdr = s.hdr;\n        updateTheme();\n', '        state.hdr = s.hdr;\n'),
    ('CEC read not passed to the open menu', GM, 'if (p != null && p.isShowing() && p.ctl.port == port) p.ctl.onSource(s);', ''),
    ('show does not re-read the CEC list', GM, '        if (w != null) w.refreshCec(port);\n', ''),
    # ---- frame player
    ('turn without the ease', MOD, 'double e = 1 - Math.pow(1 - k, 2.2);', 'double e = k;'),
    ('turn does not end on the hero frame', MOD, '(int) Math.floor(e * TURN_FRAMES)) + 1) % TURN_FRAMES;',
     '(int) Math.floor(e * TURN_FRAMES)));'),
    ('sway at 100 ms', MOD, 'TURN_MS = 1300, IDLE_STEP_MS = 140;', 'TURN_MS = 1300, IDLE_STEP_MS = 100;'),
    ('idle frame path', MOD, '"gamemenu/%s/idle_%02d.webp"', '"gamemenu/%s/idle_%d.webp"'),
    ('frame player off frameAt', VIEW, 'int f = GmModel.frameAt(now - t0);', 'int f = (int) ((now - t0) / 40 % 30);'),
    ('frames loaded for computers', VIEW, '            if (!GmModel.isConsole(theme)) return;\n', ''),
    ('one loader thread per menu', VIEW, 'if (sLoader == null) sLoader = SafeHandler.newThread("z9x-gm-frames");',
     'sLoader = SafeHandler.newThread("z9x-gm-frames");'),
    # ---- timers
    ('first auto hide 10 s', MOD, 'return first ? 30_000 : 10_000;', 'return 10_000;'),
    ('frame counter shows 0 Hz', MOD, 'if (frameRateCenti <= 0 || frameRateCenti > 50000) return -1;', ''),
    # ---- wiring: keys, auto show
    ('gear long press always Android settings', KEY, 'Family.gearLong(ctx, () -> {',
     'android.util.Log.d("x", "long"); ((Runnable) () -> {'),
    ('game menu off HDMI', GM, 'if (!hdmiPort(port)) { fallback.run(); return; }', ''),
    ('game menu without game mode', GM, 'if (Boolean.TRUE.equals(running) && sIo.activePort() == port) show(app, port, false);',
     'if (true) show(app, port, false);'),
    ('auto show every time', GM, 'if (!prefs.getBoolean(K_FIRST_SHOWN, false)) firstPending = true;', 'firstPending = true;'),
    ('auto show over another panel', GM, 'if (firstPending && sHost.current() == null) {', 'if (firstPending) {'),
    ('auto hide fixed', GM, '@Override protected long autoHideMs() { return GmModel.autoHideMs(ctl.first); }',
     '@Override protected long autoHideMs() { return 10_000; }'),
    ('watcher polls without an HDMI session', GM, 'if (!hdmiPort(port) || !screen) {', 'if (!screen) {'),
    ('not installed', APPJ, 'try { org.lumen.projector.panel.ProjectorMenu.installGameMenu(this); }', 'try { }'),
    # ---- wiring: HAL
    ('VRR source 3 allowed', B2, 'private static final int HDMI_1 = 1, HDMI_2 = 2;', 'private static final int HDMI_1 = 1, HDMI_2 = 3;'),
    ('VRR read without a source check', B2, 'return wire.i32RetI32(key(GET_VRR_ENABLE_STATE), hdmi(hdmi));',
     'return wire.i32RetI32(key(GET_VRR_ENABLE_STATE), hdmi);'),
    ('game sound 193 allowed', G2, 'GAME_SOUND_OFF = 190, GAME_SOUND_STORY = 192;', 'GAME_SOUND_OFF = 190, GAME_SOUND_STORY = 193;'),
    ('game picture 5 sent', B2, 'public static final int GAME_PICTURE_MAX = 4;', 'public static final int GAME_PICTURE_MAX = 5;'),
    ('game picture write without a check', B2, 'wire.i32I32RetI32(key(SET_GAME_PICTURE_MODE), gamePicture(mode), hdmi(hdmi));',
     'wire.i32I32RetI32(key(SET_GAME_PICTURE_MODE), mode, hdmi(hdmi));'),
    ('VRR state 2 sent', B2, 'private static final int ON = 1, OFF = 0;', 'private static final int ON = 2, OFF = 0;'),
    ('VRR write not read back', IO, 'int back = poll(() -> g2.getVrrState(port), GmModel.VRR, value);', 'int back = value;'),
    ('sound write not read back', IO, 'int back = poll(g2::getGameSound, GmModel.SND, value);', 'int back = GmModel.sndWire(value);'),
    ('write on another input', IO, '        if (g.getCurrentInputSource() != port) throw new IllegalStateException("696 is not HDMI " + port);\n        switch (key) {',
     '        switch (key) {'),
    ('207 before 647 agrees', IO, 'if (back == value && o != null && o.isTopSpeed()) {', 'if (o != null && o.isTopSpeed()) {'),
    ('speed read back sleeps on the HAL thread', IO, 'GameModeOption back = g.getGameModeOption();\n',
     'SystemClock.sleep(100); GameModeOption back = g.getGameModeOption();\n'),
    ('gated read without a port', IO, '        if (port == 1 || port == 2) {\n            try { s.vrr =', '        {\n            try { s.vrr ='),
    ('no answer not fed to the check', CTL, '            c.onNoAnswer(k);\n', ''),
    ('gated write trusted', CTL, 'ok = back != GmModel.BAD && GameMenu.CAPS.onReadBack(key, value, back);', 'ok = true;'),
    ('dock shows every item', CTL, 'List<String> tiles() { return GmModel.visibleOrder(GameMenu.CAPS); }',
     'List<String> tiles() { return java.util.Arrays.asList(GmModel.ORDER); }'),
    # ---- licences, strings, HB
    ('no Licences page', PJ + 'panel/MenuController.java',
     '        if (FeatureId.SETTINGS_LICENCES == f) { push(LicencesPage.build(app)); return; }\n', ''),
    ('licences not readable', PJ + 'panel/LicencesPage.java', 'new TextRow(app, part).readable()', 'new TextRow(app, part)'),
    ('own licences screen back', 'apps/LumenProjector/core/AndroidManifest.xml', '    </application>',
     '        <activity android:name="org.lumen.projector.ui.LicencesActivity" />\n    </application>'),
    ('Xbox credit missing', LIC, '"\\"Xbox One Controller Free\\" by paxillop, ', '"\\"Xbox One Controller\\", '),
    ('an em dash in Russian', RU, 'Обычная задержка. Трапеция', 'Обычная задержка \u2014 трапеция'),
    ('a semicolon in German', DE, '<string name="lumen_gm_off">', '<string name="lumen_gm_off">Aus; '),
    ('a German string missing', DE, '<string name="lumen_gm_on">', '<string name="lumen_gm_on_x">'),
    # (the HB runs the shared game menu since core-menu: the old 'HB gets the game menu' recipe mutant is gone)
    # ---- x50-game-1128: the stock HDR item only with an HDR10 signal (xhplayer MutexManager, toast hdr_settings_mutex)
    ('HDR item open without HDR10', MOD, '        if (HDR.equals(k) && s.hdr != HDR_HDR10) return R_HDR_SIGNAL;\n', ''),
    ('HDR item open with any HDR', MOD, 'if (HDR.equals(k) && s.hdr != HDR_HDR10) return R_HDR_SIGNAL;',
     'if (HDR.equals(k) && s.hdr < 0) return R_HDR_SIGNAL;'),
    ('HDR rule refuses every item', MOD, 'if (HDR.equals(k) && s.hdr != HDR_HDR10) return R_HDR_SIGNAL;',
     'if (s.hdr != HDR_HDR10) return R_HDR_SIGNAL;'),
    # ---- x50-game2-1128: the stock FreeSync item (MutexManager on picking it, HFR off refused while it is on) and the
    #      colour space list per model (COLOR_SPACE_V2 without Adobe RGB)
    ('FreeSync opens below top speed', MOD, '            if (vrrTopSpeed && s.get(LAT) != 1) return R_FS_SPEED;\n', ''),
    ('FreeSync opens with a wide format', MOD, '            if (s.get(AR) >= 2) return R_FS_WIDE;\n', ''),
    ('FreeSync wide rule on 21:9 only', MOD, 'if (s.get(AR) >= 2) return R_FS_WIDE;', 'if (s.get(AR) == 2) return R_FS_WIDE;'),
    ('FreeSync speed rule without the model flag', MOD, 'if (vrrTopSpeed && s.get(LAT) != 1) return R_FS_SPEED;',
     'if (s.get(LAT) != 1) return R_FS_SPEED;'),
    ('HFR off allowed while FreeSync is on', MOD, '        if (HFR.equals(k) && v == 0 && s.get(FS) == 1) return R_HFR_FS;\n', ''),
    ('FreeSync HFR rule after the VRR rule', MOD,
     '        if (HFR.equals(k) && v == 0 && s.get(FS) == 1) return R_HFR_FS;\n        if (HFR.equals(k) && v == 0 && vrr == 1) return R_HFR_VRR;\n',
     '        if (HFR.equals(k) && v == 0 && vrr == 1) return R_HFR_VRR;\n        if (HFR.equals(k) && v == 0 && s.get(FS) == 1) return R_HFR_FS;\n'),
    ('preset turns HFR off under FreeSync', MOD, 'if (hfrW == 0 && (vrrStaysOn || s.get(FS) == 1)) hfrW = 1;',
     'if (hfrW == 0 && vrrStaysOn) hfrW = 1;'),
    ('FreeSync not gated', MOD, 'static final String[] GATED = {VRR, PIC, DARK, SND, CS, HDR, FS};',
     'static final String[] GATED = {VRR, PIC, DARK, SND, CS, HDR};'),
    ('colour space list ignored', MOD, '        if (CS.equals(k)) return i >= 0 && i < csOffered.length && csOffered[i];\n', ''),
    ('controller never reads FreeSync', CTL, '        gated(c, GmModel.FS, s.fs);\n', ''),
    ('FreeSync HFR refusal without its text', CTL, 'case GmModel.R_HFR_FS: return app.getString(R.string.lumen_gm_rule_hfr_fs);', ''),
    ('FreeSync wide refusal without its text', CTL, 'case GmModel.R_FS_WIDE: return app.getString(R.string.lumen_gm_rule_vrr_wide);', ''),
    ('HDR rule not in refuse', MOD, '        int p = popupRefused(k, s);\n        if (p != OK) return p;\n', ''),
    ('controller opens the HDR popup without the rule', CTL, 'int r = GmModel.popupRefused(k, state);', 'int r = GmModel.OK;'),
    ('controller never learns the HDR type', CTL, '        state.hdr = s.hdr;\n', ''),
    ('HDR refusal without its text', CTL, 'case GmModel.R_HDR_SIGNAL: return app.getString(R.string.lumen_gm_rule_hdr_signal);', ''),
]

if __name__ == '__main__':
    scratch.main(M + 'test/gamemenu/run.sh', MUTANTS)
