#!/usr/bin/env python3
"""Mutants of the Lumen game menu (GmModel logic + the wiring run.sh pins): each must turn test/gamemenu red.
python3 test/gamemenu/mutate.py"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.normpath(os.path.join(HERE, '..', '..'))
ROOT = os.path.normpath(os.path.join(APP, '..', '..'))
PJ = 'src/org/lumen/projector/'
MOD = PJ + 'panel/GmModel.java'
GM = PJ + 'panel/GameMenu.java'
CTL = PJ + 'panel/GmController.java'
VIEW = PJ + 'panel/GmView.java'
LIC = PJ + 'panel/GmLicences.java'
KEY = PJ + 'KeyReceiver.java'
G2 = PJ + 'hal/Gmpf2Client.java'
SET = PJ + 'SettingsActivity.java'
APPJ = PJ + 'App.java'
RU = 'res/values-ru/strings_gm.xml'
DE = 'res/values-de/strings_gm.xml'
HB = '../../devices/goat_elegant/app/HbProjector.layers'

MUTANTS = [
    # ---- capability gating
    ('gated item shown before any read', MOD, 'for (String g : GATED) cap.put(g, CAP_UNKNOWN);', ''),
    ('read outside the stock range shows the item', MOD, "cap.put(k, plain(k, raw) == BAD ? CAP_UNKNOWN : CAP_OK);",
     'cap.put(k, CAP_OK);'),
    ('a failed item comes back on a good read', MOD, 'if (!cap.containsKey(k) || get(k) == CAP_FAILED) return;',
     'if (!cap.containsKey(k)) return;'),
    ('no answer keeps the item', MOD, 'if (cap.containsKey(k) && get(k) != CAP_FAILED) cap.put(k, CAP_UNKNOWN);', ''),
    ('a bad read back keeps the item', MOD, 'if (!ok && cap.containsKey(k)) cap.put(k, CAP_FAILED);', ''),
    ('unknown counts as visible', MOD, 'return !cap.containsKey(k) || get(k) == CAP_OK;', 'return !cap.containsKey(k) || get(k) != CAP_FAILED;'),
    ('VRR proven without a check', MOD, 'static final String[] GATED = {VRR, PIC, DARK, SND};', 'static final String[] GATED = {PIC, DARK, SND};'),
    ('empty group kept', MOD, '            if (ks.isEmpty()) continue;\n', ''),
    # ---- raw values
    ('VRR -1 not read as off', MOD, 'case VRR: return raw == -1 ? 0 :', 'case VRR: return raw == -1 ? BAD :'),
    ('game picture 5 accepted', MOD, '(raw >= 0 && raw <= 4) ? raw : BAD;', '(raw >= 0 && raw <= 5) ? raw : BAD;'),
    ('sound 0 not off', MOD, 'case SND: return (raw == 0 || raw == 190) ? 0 :', 'case SND: return (raw == 190) ? 0 :'),
    ('sound wire shifted', MOD, 'return 190 + v;', 'return 191 + v;'),
    ('sound 3 sent', MOD, 'if (v < 0 || v > 2) throw', 'if (v < 0 || v > 3) throw'),
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
    ('game menu off HDMI', GM, 'if (port != 1 && port != 2) { fallback.run(); return; }', ''),
    ('game menu without game mode', GM, 'if (Boolean.TRUE.equals(running) && GameProfile.activePort() == port) show(app, port, false);',
     'if (true) show(app, port, false);'),
    ('auto show every time', GM, 'if (!prefs.getBoolean(K_FIRST_SHOWN, false)) firstPending = true;', 'firstPending = true;'),
    ('auto show over another panel', GM, 'if (firstPending && OverlayHost.get(app).current() == null) {', 'if (firstPending) {'),
    ('auto hide fixed', GM, '@Override protected long autoHideMs() { return GmModel.autoHideMs(ctl.first); }',
     '@Override protected long autoHideMs() { return 10_000; }'),
    ('watcher polls without an HDMI session', GM, 'if ((port != 1 && port != 2) || !screen) {', 'if (!screen) {'),
    ('not installed', APPJ, 'try { org.lumen.projector.panel.GameMenu.install(this); }', 'try { }'),
    # ---- wiring: HAL
    ('VRR source 3 allowed', G2, 'return callI32RetI32(GET_VRR_ENABLE_STATE, checkRange("hdmi source", hdmiSource, 1, 2));',
     'return callI32RetI32(GET_VRR_ENABLE_STATE, checkRange("hdmi source", hdmiSource, 1, 3));'),
    ('game sound 193 allowed', G2, 'checkRange("game sound", mode, 190, 192)', 'checkRange("game sound", mode, 190, 193)'),
    ('game picture 5 sent', G2, 'return callI32I32RetI32(SET_GAME_PICTURE_MODE, checkRange("game picture", mode, 0, 4),',
     'return callI32I32RetI32(SET_GAME_PICTURE_MODE, checkRange("game picture", mode, 0, 5),'),
    ('VRR write not read back', GM, 'int back = poll(() -> g2.getVrrState(port), GmModel.VRR, value);', 'int back = value;'),
    ('sound write not read back', GM, 'int back = poll(g2::getGameSound, GmModel.SND, value);', 'int back = GmModel.sndWire(value);'),
    ('write on another input', GM, '        if (g.getCurrentInputSource() != port) throw new IllegalStateException("696 is not HDMI " + port);\n        switch (key) {',
     '        switch (key) {'),
    ('207 before 647 agrees', GM, 'if (back == value && o != null && o.isTopSpeed()) {', 'if (o != null && o.isTopSpeed()) {'),
    ('speed read back sleeps on the HAL thread', GM, 'GmpfClient.GameModeOption back = g.getGameModeOption();\n',
     'SystemClock.sleep(100); GmpfClient.GameModeOption back = g.getGameModeOption();\n'),
    ('gated read without a port', GM, '        if (port == 1 || port == 2) {\n            try { s.vrr =', '        {\n            try { s.vrr ='),
    ('no answer not fed to the check', CTL, '            c.onNoAnswer(k);\n', ''),
    ('gated write trusted', CTL, 'ok = back != GmModel.BAD && GameMenu.CAPS.onReadBack(key, value, back);', 'ok = true;'),
    ('dock shows every item', CTL, 'List<String> tiles() { return GmModel.visibleOrder(GameMenu.CAPS); }',
     'List<String> tiles() { return java.util.Arrays.asList(GmModel.ORDER); }'),
    # ---- licences, strings, HB
    ('no Licences row', SET, '        actionRow(gc, R.string.gm_licences, R.string.gm_licences_d, v -> org.lumen.projector.panel.GmLicences.show(this));\n', ''),
    ('Xbox credit missing', LIC, '"\\"Xbox One Controller Free\\" by paxillop, ', '"\\"Xbox One Controller\\", '),
    ('an em dash in Russian', RU, 'Обычная задержка. Трапеция', 'Обычная задержка \u2014 трапеция'),
    ('a semicolon in German', DE, '<string name="gm_off">', '<string name="gm_off">Aus; '),
    ('a German string missing', DE, '<string name="gm_on">', '<string name="gm_on_x">'),
    ('HB gets the game menu', HB, 'drop      src/org/lumen/projector/panel/GameMenu.java\n', ''),
]

COPY_DIRS = ['src', 'res', 'test/gamemenu']


def tree(tmp):
    root = os.path.join(tmp, 'r')
    app = os.path.join(root, 'apps', 'Z9xProjector')
    for d in COPY_DIRS:
        shutil.copytree(os.path.join(APP, d), os.path.join(app, d))
    os.makedirs(os.path.join(app, 'assets'))
    os.symlink(os.path.join(APP, 'assets', 'gamemenu'), os.path.join(app, 'assets', 'gamemenu'))
    hb = os.path.join(root, 'devices', 'goat_elegant', 'app')
    os.makedirs(hb)
    shutil.copy(os.path.join(ROOT, 'devices', 'goat_elegant', 'app', 'HbProjector.layers'), hb)
    return app


def run(app):
    r = subprocess.run(['sh', os.path.join(app, 'test/gamemenu/run.sh')], capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def main():
    tmp = tempfile.mkdtemp(prefix='gm_mut_')
    try:
        rc, out = run(tree(tmp))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if rc != 0:
        print('BASELINE RED: ' + out[-800:])
        sys.exit(1)
    killed = 0
    for name, rel, old, new in MUTANTS:
        tmp = tempfile.mkdtemp(prefix='gm_mut_')
        try:
            app = tree(tmp)
            target = os.path.normpath(os.path.join(app, rel))
            s = open(target, encoding='utf-8').read()
            if s.count(old) != 1:
                print('ANCHOR %d: %s' % (s.count(old), name))
                continue
            open(target, 'w', encoding='utf-8').write(s.replace(old, new))
            rc, out = run(app)
            if rc != 0:
                killed += 1
                print('killed   ' + name)
            else:
                print('SURVIVED ' + name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print('mutants: %d/%d killed' % (killed, len(MUTANTS)))
    sys.exit(0 if killed == len(MUTANTS) else 1)


if __name__ == '__main__':
    main()
