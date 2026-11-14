package org.lumen.projector.panel;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import org.lumen.projector.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * State and keys of one shown game menu (GameMenu.MenuPanel); GmView draws it, GameMenu.Io talks to the projector.
 * Main thread only. Every value on screen comes from a HAL read (or the read-back after a write); a chosen value is
 * shown at once and replaced by the read-back when it arrives.
 */
final class GmController {
    final Context app;
    final GameMenu.Io io;
    final int port;
    final boolean first;
    /** What the CEC list says about the device behind the port; replaced while the menu is open (onSource). */
    GmModel.Source source;
    /** The user marked this input as a console (GameProfile, Projector settings). */
    final boolean consolePort;
    /** Name on the card (GmModel.shownName, null = the port) and picture theme, from source and the ALLM read. */
    String cecName, theme;
    GmOverlays overlays;

    final GmModel.State state = new GmModel.State();
    GameMenu.Snap snap;
    boolean loaded;
    int arRaw = GmModel.BAD;

    // ---- focus
    int zone = GmModel.Z_DOCK, idx, lastDock, preset;
    String popup;               // item whose popup is open, null = none
    int prow;                   // focused row of the popup (index into GmModel.options or AIM_ROW_*)
    int aimRow;
    boolean moving;             // crosshair move mode
    Runnable confirmOk;         // Turbo question open
    int confirmIdx;             // 0 = switch on, 1 = cancel
    final Set<String> pending = new HashSet<>();
    String toast;
    long toastUntil;
    long shownAt;

    private GmView view;
    /** The window content: the stage with the real-time model, or the view alone (GmStage.wrap). */
    private View root;

    GmController(Context app, GameMenu.Io io, int port, boolean first, GmModel.Source source, boolean consolePort) {
        this.app = app;
        this.io = io;
        this.port = port;
        this.first = first;
        this.source = source == null ? GmModel.Source.NONE : source;
        this.consolePort = consolePort;
        updateTheme();
    }

    /**
     * Theme and card name from the CEC source, the user's console mark and ALLM (a device that asks for game mode
     * itself is a console even when it names nothing). Called on every source or HAL update while the menu is open,
     * so a name that arrives after the menu opened (PS5: "Playback_1" first) still turns the picture into its pad.
     */
    private void updateTheme() {
        boolean hint = consolePort || (snap != null && Boolean.TRUE.equals(snap.allm));
        String t = GmModel.theme(source, hint);
        if (!t.equals(theme)) Log.i(GameMenu.TAG, "HDMI " + port + " device " + source + (hint ? " (console hint)" : "") + ": " + t);
        theme = t;
        cecName = GmModel.shownName(source.name, theme);
    }

    /** A fresh CEC read of the port (GameMenu.Watch). Main thread. */
    void onSource(GmModel.Source s) {
        if (s == null || s.same(source)) return;
        source = s;
        updateTheme();
        invalidate();
    }

    View view() {
        if (root == null) {
            view = new GmView(app, this);
            root = GmStage.wrap(app, view);
        }
        return root;
    }

    GmView gmView() {
        view();
        return view;
    }

    List<String> tiles() { return GmModel.visibleOrder(GameMenu.CAPS); }

    String focusedTile() {
        List<String> t = tiles();
        return t.isEmpty() ? null : t.get(Math.max(0, Math.min(idx, t.size() - 1)));
    }

    // ================================================================== lifecycle
    void onShown() {
        shownAt = SystemClock.uptimeMillis();
        gmView().enter();
        refresh();
    }

    void refresh() {
        io.read(port, s -> {
            applySnap(s);
            loaded = true;
            invalidate();
        });
    }

    void animateOut(Runnable end) { gmView().exit(end); }

    void onDismissed() {
        moving = false;
        if (overlays != null) overlays.setMoving(false);
        gmView().release();
    }

    void invalidate() { if (view != null) view.invalidate(); }

    // ================================================================== reads
    void applySnap(GameMenu.Snap s) {
        if (s == null) return;
        snap = s;
        GmModel.Caps c = GameMenu.CAPS;
        state.set(GmModel.LAT, GmModel.latOf(s.opt));
        state.set(GmModel.HFR, GmModel.hfrOf(s.opt));
        state.set(GmModel.SR, s.sr == 0 || s.sr == 1 ? s.sr : GmModel.BAD);
        if (c.cap.containsKey(GmModel.SR)) {                   // the profile gates it (X50: 121 must answer 0 / 1 first)
            if (s.sr == 0 || s.sr == 1) c.onRead(GmModel.SR, s.sr);
            else c.onNoAnswer(GmModel.SR);
        }
        arRaw = s.ar;
        state.set(GmModel.AR, s.ar == GmModel.BAD ? GmModel.BAD : GmModel.arOf(s.ar));
        gated(c, GmModel.VRR, s.vrr);
        gated(c, GmModel.PIC, s.pic);
        gated(c, GmModel.DARK, s.dark);
        gated(c, GmModel.SND, s.snd);
        gated(c, GmModel.CS, s.cs);
        gated(c, GmModel.HDR, s.hdrMode);
        state.width = s.width;
        state.height = s.height;
        updateTheme();
        if (overlays != null) {
            state.set(GmModel.AIM, overlays.aimOn() ? 1 : 0);
            state.set(GmModel.FPS, overlays.fpsPos());
        }
        clampFocus();
    }

    private void gated(GmModel.Caps c, String k, Integer raw) {
        if (raw == null) {
            c.onNoAnswer(k);
            state.set(k, GmModel.BAD);
            return;
        }
        c.onRead(k, raw);
        state.set(k, GmModel.plain(k, raw));
    }

    void clampFocus() {
        int n = tiles().size();
        if (idx >= n) idx = Math.max(0, n - 1);
        if (lastDock >= n) lastDock = Math.max(0, n - 1);
        if (popup != null && !GameMenu.CAPS.visible(popup)) popup = null;
        if (view != null) gmView().syncFocus(focusId());
    }

    // ================================================================== keys
    private static int dir(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return GmModel.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return GmModel.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return GmModel.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return GmModel.RIGHT;
            default: return -1;
        }
    }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    /** Every key before the views; true = consumed. BACK goes to back() through the panel. */
    boolean key(KeyEvent ev) {
        int code = ev.getKeyCode();
        int d = dir(code);
        boolean ok = isOk(code);
        if (d < 0 && !ok) return false;
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (ev.getRepeatCount() > 0 && !moving && popup == null && d < 0) return true;
        try {
            if (confirmOk != null) confirmKey(d, ok);
            else if (popup != null) popupKey(d, ok);
            else dockKey(d, ok);
        } catch (Throwable t) {
            Log.w(GameMenu.TAG, "key " + code + ": " + t);
        }
        // every handled key redraws: nothing else repaints a popup on a computer or a console without ALLM (the
        // focused row stayed where it was on a test Z9X while UP / DOWN moved the hidden focus)
        invalidate();
        return true;
    }

    /** BACK: question, move mode, popup, then the menu (false = close). */
    boolean back() {
        if (confirmOk != null) { confirmOk = null; invalidate(); return true; }
        if (moving) { setMoving(false); invalidate(); return true; }
        if (popup != null) { closePopup(); return true; }
        return false;
    }

    private void dockKey(int d, boolean ok) {
        int nTiles = tiles().size();
        if (ok) {
            if (zone == GmModel.Z_PRESETS) applyPreset(preset);
            else if (focusedTile() != null) openPopup(focusedTile());
            return;
        }
        int[] m = GmModel.move(zone, zone == GmModel.Z_DOCK ? idx : preset, lastDock, d, nTiles, GmModel.PRESETS);
        int oldZone = zone;
        String oldFocus = focusId();
        zone = m[0];
        if (zone == GmModel.Z_DOCK) idx = m[1]; else preset = m[1];
        lastDock = m[2];
        if (oldZone == GmModel.Z_DOCK && zone == GmModel.Z_DOCK) lastDock = idx;
        String now = focusId();
        if (!now.equals(oldFocus)) gmView().onFocusMoved(oldFocus, now);
    }

    /** Id of the focused dock element: "t:<item>" or "p:<preset>". */
    String focusId() {
        if (zone == GmModel.Z_PRESETS) return "p:" + preset;
        String t = focusedTile();
        return t == null ? "none" : "t:" + t;
    }

    // ---- popups
    void openPopup(String k) {
        popup = k;
        if (GmModel.AIM.equals(k)) {
            aimRow = GmModel.AIM_ROW_TOGGLE;
            prow = 0;
        } else {
            List<Integer> o = GmModel.options(k);
            int cur = state.get(k);
            int at = o.indexOf(cur);
            prow = at < 0 ? 0 : at;
        }
        gmView().onPopup(true);
        invalidate();
    }

    void closePopup() {
        setMoving(false);
        popup = null;
        gmView().onPopup(false);
        invalidate();
    }

    private void popupKey(int d, boolean ok) {
        String k = popup;
        if (GmModel.AIM.equals(k)) { aimKey(d, ok); return; }
        List<Integer> o = GmModel.options(k);
        if (ok) {
            if (prow >= 0 && prow < o.size()) choose(k, o.get(prow));
            return;
        }
        if (d == GmModel.UP || d == GmModel.DOWN) {
            int was = prow;
            prow = GmModel.popupMove(prow, d, o.size());
            if (prow != was) gmView().onPopupRow(was, prow);
        }
    }

    private void aimKey(int d, boolean ok) {
        GmOverlays ov = overlays;
        if (ov == null) return;
        if (moving) {
            if (ok) { setMoving(false); return; }
            if (d >= 0) {
                int[] p = GmModel.aimNudge(ov.aimX(), ov.aimY(), d);
                ov.setAimPos(p[0], p[1]);
            }
            return;
        }
        if (ok) {
            switch (aimRow) {
                case GmModel.AIM_ROW_TOGGLE: ov.setAimOn(!ov.aimOn()); break;
                case GmModel.AIM_ROW_MOVE: ov.setAimOn(true); setMoving(true); break;
                default: ov.setAimOn(true); break;
            }
            state.set(GmModel.AIM, ov.aimOn() ? 1 : 0);
            return;
        }
        if (d < 0) return;
        int[] m = GmModel.aimMove(aimRow, ov.aimStyle(), ov.aimColour(), d);
        boolean changed = m[1] != ov.aimStyle() || m[2] != ov.aimColour();
        aimRow = m[0];
        if (changed) {
            ov.setAimStyle(m[1], m[2]);
            ov.setAimOn(true);
            state.set(GmModel.AIM, 1);
        }
    }

    private void setMoving(boolean on) {
        moving = on;
        if (overlays != null) overlays.setMoving(on);
    }

    // ---- the Turbo question
    void confirm(Runnable ok) {
        confirmOk = ok;
        confirmIdx = 0;
        invalidate();
    }

    private void confirmKey(int d, boolean ok) {
        if (d == GmModel.LEFT || d == GmModel.RIGHT) confirmIdx = 1 - confirmIdx;
        else if (ok) {
            Runnable r = confirmOk;
            confirmOk = null;
            if (confirmIdx == 0 && r != null) r.run();
        }
        invalidate();
    }

    void toast(int res) { if (res != 0) toast(app.getString(res)); }

    void toast(String t) {
        toast = t;
        toastUntil = SystemClock.uptimeMillis() + 2_400;
        invalidate();
    }

    // ================================================================== writes
    /** A value picked in a popup: stock rules, the Turbo question, then the write and its read-back. */
    void choose(String k, int v) {
        if (GmModel.FPS.equals(k)) {
            if (overlays != null) overlays.setFps(v);
            state.set(k, v);
            return;
        }
        int r = GmModel.refuse(k, v, state);
        if (r != GmModel.OK) { toast(ruleText(r)); return; }
        if (v == state.get(k) || pending.contains(k)) return;
        if (GmModel.LAT.equals(k) || GmModel.HFR.equals(k)) {
            // speed and HFR are one 648 option: never two of them in flight
            if (pending.contains(GmModel.LAT) || pending.contains(GmModel.HFR)) return;
            int lat = GmModel.LAT.equals(k) ? v : state.get(GmModel.LAT);
            int hfr = GmModel.HFR.equals(k) ? v : state.get(GmModel.HFR);
            if (lat == GmModel.BAD || hfr == GmModel.BAD) { toast(R.string.lumen_gm_failed_soft); return; }
            final int opt = GmModel.option(lat, hfr);
            final boolean toTurbo = lat == 1 && state.get(GmModel.LAT) != 1;
            // the quick panel's note after top speed (207 reset the keystone to the full frame)
            Runnable go = () -> write(GmModel.SPEED, opt, k, v,
                    ok -> toast(!ok ? R.string.lumen_gm_failed_soft : toTurbo ? R.string.lumen_m_panel_speed_top_note : 0));
            int curLat = state.get(GmModel.LAT);
            if (GmModel.LAT.equals(k) && GmModel.askTurbo(curLat, v, null)) {
                io.fullFrame(full -> {
                    if (GmModel.askTurbo(curLat, v, full)) confirm(go); else go.run();
                });
            } else {
                go.run();
            }
            return;
        }
        write(k, v, k, v, null);
    }

    /** One write; {@code after} (may be null) runs with true when the read-back agreed. */
    void write(String key, int value, String item, int itemValue, java.util.function.Consumer<Boolean> after) {
        final int before = state.get(item);
        state.set(item, itemValue);
        pending.add(item);
        invalidate();
        io.apply(key, value, port, snap, (back, s) -> {
            pending.remove(item);
            boolean ok;
            boolean hidden = false;
            if (GmModel.SPEED.equals(key)) ok = back == value;
            else if (GmModel.AR.equals(key)) ok = back != GmModel.BAD && GmModel.arOf(back) == value;
            else if (GmModel.isGated(key)) {
                ok = back != GmModel.BAD && GameMenu.CAPS.onReadBack(key, value, back);
                hidden = !GameMenu.CAPS.visible(key);
                if (back == GmModel.BAD) { GameMenu.CAPS.onReadBack(key, value, Integer.MIN_VALUE + 1); hidden = true; }
            } else ok = back == value;
            applySnap(s);
            if (!ok) {
                Log.w(GameMenu.TAG, key + "=" + value + " not confirmed (read back " + back + "), was " + before
                        + (hidden ? ": item hidden this boot" : ""));
                if (after == null) toast(hidden ? R.string.lumen_gm_failed : R.string.lumen_gm_failed_soft);
                if (hidden && item.equals(popup)) closePopup();
            }
            if (after != null) after.accept(ok);
            invalidate();
        });
    }

    String ruleText(int r) {
        switch (r) {
            case GmModel.R_VRR_WIDE: return app.getString(R.string.lumen_gm_rule_vrr_wide);
            case GmModel.R_AR_VRR: return app.getString(R.string.lumen_gm_rule_ar_vrr);
            case GmModel.R_AR_4K: return app.getString(R.string.lumen_gm_rule_ar_4k);
            case GmModel.R_HFR_VRR: return app.getString(R.string.lumen_gm_rule_hfr_vrr);
            default: return app.getString(R.string.lumen_gm_failed_soft);
        }
    }

    // ---- presets
    void applyPreset(int p) {
        final List<GmModel.Step> plan = GmModel.plan(p, state, GameMenu.CAPS);
        final String name = presetName(p);
        if (plan.isEmpty()) {
            toast(app.getString(R.string.lumen_gm_preset_on, name));
            return;
        }
        boolean turbo = false;
        for (GmModel.Step st : plan) {
            if (GmModel.SPEED.equals(st.key) && GmModel.latOf(st.value) == 1) turbo = true;
        }
        final int curLat = state.get(GmModel.LAT);
        Runnable go = () -> runSteps(plan, 0, name, 0);
        if (turbo && GmModel.askTurbo(curLat, 1, null)) {
            io.fullFrame(full -> {
                if (GmModel.askTurbo(curLat, 1, full)) confirm(go); else go.run();
            });
        } else {
            go.run();
        }
    }

    private void runSteps(List<GmModel.Step> plan, int i, String name, int failed) {
        if (i >= plan.size()) {
            toast(failed == 0 ? app.getString(R.string.lumen_gm_preset_on, name) : app.getString(R.string.lumen_gm_preset_partly, name));
            return;
        }
        GmModel.Step st = plan.get(i);
        if (!GmModel.SPEED.equals(st.key) && GmModel.isGated(st.key) && !GameMenu.CAPS.visible(st.key)) {
            runSteps(plan, i + 1, name, failed);
            return;
        }
        String item = GmModel.SPEED.equals(st.key) ? GmModel.LAT : st.key;
        int itemValue = GmModel.SPEED.equals(st.key) ? GmModel.latOf(st.value) : st.value;
        write(st.key, st.value, item, itemValue, ok -> runSteps(plan, i + 1, name, failed + (ok ? 0 : 1)));
    }

    String presetName(int p) {
        return app.getString(p == GmModel.P_COMPETE ? R.string.lumen_gm_preset_compete
                : p == GmModel.P_BALANCE ? R.string.lumen_gm_preset_balance : R.string.lumen_gm_preset_story);
    }
}
