#!/usr/bin/env python3
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
ZJ = 'apps/Z9xProjector/src/org/lumen/projector/'
KEY = ZJ + 'KeyReceiver.java'
G2 = ZJ + 'hal/Gmpf2Client.java'
B2 = 'apps/core/lumen-gmpf/src/org/lumen/projector/hal/Gmpf2Base.java'   # the shared IGmpf2 body
SET = ZJ + 'SettingsActivity.java'
APPJ = ZJ + 'App.java'
IO = ZJ + 'panel/Z9xGameIo.java'   # the Z9X side of the shared game menu: its reads, writes and read-backs
RU = M + 'res/values-ru/lumen_strings_gm.xml'
DE = M + 'res/values-de/lumen_strings_gm.xml'
HB = 'devices/goat_elegant/app/HbProjector.layers'

MUTANTS = [
    # ---- capability gating
    ('gated item shown before any read', MOD, 'for (String g : GATED) cap.put(g, CAP_UNKNOWN);', ''),
    ('read outside the stock range shows the item', MOD, "cap.put(k, plain(k, raw) == BAD ? CAP_UNKNOWN : CAP_OK);",
     'cap.put(k, CAP_OK);'),
    ('a failed item comes back on a good read', MOD, 'if (!cap.containsKey(k) || get(k) == CAP_FAILED) return;',
     'if (!cap.containsKey(k)) return;'),
    ('no answer keeps the item', MOD, 'if (cap.containsKey(k) && get(k) != CAP_FAILED) cap.put(k, CAP_UNKNOWN);', ''),
    ('a bad read back keeps the item', MOD, 'if (!ok && cap.containsKey(k)) cap.put(k, CAP_FAILED);', ''),
    ('unknown counts as visible', MOD, '&& (!cap.containsKey(k) || get(k) == CAP_OK);', '&& (!cap.containsKey(k) || get(k) != CAP_FAILED);'),
    ('VRR proven without a check', MOD, 'static final String[] GATED = {VRR, PIC, DARK, SND, CS, HDR};',
     'static final String[] GATED = {PIC, DARK, SND, CS, HDR};'),
    ('empty group kept', MOD, '            if (ks.isEmpty()) continue;\n', ''),
    # ---- raw values
    ('VRR -1 not read as off', MOD, 'case VRR: return raw == -1 ? 0 :', 'case VRR: return raw == -1 ? BAD :'),
    ('game picture 5 accepted', MOD, '(raw >= 0 && raw <= 4) ? raw : BAD;', '(raw >= 0 && raw <= 5) ? raw : BAD;'),
    ('sound 0 not off', MOD, 'case SND: return (raw == 0 || raw == SND_OFF) ? 0 :', 'case SND: return (raw == SND_OFF) ? 0 :'),
    ('sound wire shifted', MOD, 'return SND_OFF + v;', 'return SND_OFF + 1 + v;'),
    ('sound past the profile sent', MOD, 'if (v < 0 || v >= sounds) throw', 'if (v < 0 || v > sounds) throw'),
    ('turbo option swapped', MOD, 'if (lat == 1) return hfr == 1 ? 2 : 3;', 'if (lat == 1) return hfr == 1 ? 3 : 2;'),
    ('HFR bit of 647 wrong', MOD, 'static int hfrOf(int opt) { return (opt == 1 || opt == 2) ? 1', 'static int hfrOf(int opt) { return (opt == 1 || opt == 3) ? 1'),
    ('21:9 offered without the live check', MOD, 'static final int[] WIDE_WIRE = {};', 'static final int[] WIDE_WIRE = {5, 6};'),
    # ---- stock mutex rules
    ('VRR allowed with a wide format', MOD, 'if (VRR.equals(k) && v == 1 && ar >= 2) return R_VRR_WIDE;', ''),
    ('aspect changes while VRR is on', MOD, 'if (AR.equals(k) && vrr == 1 && v != ar) return R_AR_VRR;', ''),
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
    ('HFR off while a hidden VRR stays on', MOD, 'if (hfrW == 0 && vrrStaysOn) hfrW = 1;', ''),
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
    ('Turbo question keys do not redraw', CTL, '            if (confirmOk != null) confirmKey(d, ok);\n',
     '            if (confirmOk != null) { if (ok) { confirmOk = null; } return true; }\n'),
    ('popup row change not told to the view', CTL, '            if (prow != was) gmView().onPopupRow(was, prow);\n', ''),
    ('popup focus starts on the first row', CTL, '            prow = at < 0 ? 0 : at;', '            prow = 0;'),
    ('ALLM is no console hint', CTL, 'boolean hint = consolePort || (snap != null && Boolean.TRUE.equals(snap.allm));', 'boolean hint = consolePort;'),
    ('a new CEC answer ignored by the open menu', CTL, '        source = s;\n        updateTheme();\n', ''),
    ('theme not refreshed by a HAL read', CTL, '        state.height = s.height;\n        updateTheme();\n', '        state.height = s.height;\n'),
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
    ('gear long press always Android settings', KEY, 'org.lumen.projector.panel.GameMenu.onGearLong(ctx, () -> {',
     'android.util.Log.d("x", "long"); ((Runnable) () -> {'),
    ('game menu off HDMI', GM, 'if (!hdmiPort(port)) { fallback.run(); return; }', ''),
    ('game menu without game mode', GM, 'if (Boolean.TRUE.equals(running) && sIo.activePort() == port) show(app, port, false);',
     'if (true) show(app, port, false);'),
    ('auto show every time', GM, 'if (!prefs.getBoolean(K_FIRST_SHOWN, false)) firstPending = true;', 'firstPending = true;'),
    ('auto show over another panel', GM, 'if (firstPending && sHost.current() == null) {', 'if (firstPending) {'),
    ('auto hide fixed', GM, '@Override protected long autoHideMs() { return GmModel.autoHideMs(ctl.first); }',
     '@Override protected long autoHideMs() { return 10_000; }'),
    ('watcher polls without an HDMI session', GM, 'if (!hdmiPort(port) || !screen) {', 'if (!screen) {'),
    ('not installed', APPJ, 'try { org.lumen.projector.panel.GameMenu.install(this, new org.lumen.projector.panel.Z9xGameIo(this)); }',
     'try { }'),
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
    ('no Licences row', SET, '        actionRow(gc, R.string.lumen_gm_licences, R.string.lumen_gm_licences_d, v -> org.lumen.projector.panel.GmLicences.show(this));\n', ''),
    ('licences screen stays after its dialog', PJ + 'ui/LicencesActivity.java', '        d.setOnDismissListener(x -> finish());\n', ''),
    ('licences screen without the credits', PJ + 'ui/LicencesActivity.java', 'AlertDialog d = GmLicences.open(this);', 'AlertDialog d = null;'),
    ('Xbox credit missing', LIC, '"\\"Xbox One Controller Free\\" by paxillop, ', '"\\"Xbox One Controller\\", '),
    ('an em dash in Russian', RU, 'Обычная задержка. Трапеция', 'Обычная задержка \u2014 трапеция'),
    ('a semicolon in German', DE, '<string name="lumen_gm_off">', '<string name="lumen_gm_off">Aus; '),
    ('a German string missing', DE, '<string name="lumen_gm_on">', '<string name="lumen_gm_on_x">'),
    # (the HB runs the shared game menu since core-menu: the old 'HB gets the game menu' recipe mutant is gone)
]

if __name__ == '__main__':
    scratch.main(M + 'test/gamemenu/run.sh', MUTANTS)
