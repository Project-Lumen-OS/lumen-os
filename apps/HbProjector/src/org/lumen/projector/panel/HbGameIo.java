package org.lumen.projector.panel;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.lumen.projector.Hal;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.game.GameProfile;
import org.lumen.projector.hal.AspectRatio;
import org.lumen.projector.hal.GameModeOption;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.HbFeatures;
import org.lumen.projector.hal.HdmiSignal;
import org.lumen.projector.ui.Notify;

import java.util.function.Consumer;

import static org.lumen.projector.utils.KstFrames.isFullFrame;

/**
 * Z9X HB (goat_elegant): the HB side of the shared game menu (apps/core/lumen-menu GameMenu.Io).
 * The calls the HB game bar sent, as the stock HB GameModeApi / OtherViewModel sends them (devices/goat_elegant/research
 * the design notes), all on the HAL thread through Hal.query:
 *  - speed (lat / hfr): DISPLAY 76, read back by 77 (a false 76 counts only when 77 reads it within 6 s); VRR off first
 *    when leaving top speed with VRR on (stock GameModeFactory); 200 ms later VRR and the game picture of the input sent
 *    again (stock afterGameModeApi); top speed then DISPLAY 38(0) once 77 reads it (stock switchGameModeSpeed);
 *  - vrr: TV 145 / 146 (src 1 | 2); pic: TV 147 / 148; dark: TV 149 / 150 for the game picture of the input;
 *  - ar: TV 35 / 36 (Auto 2, 16:9 1); allm: TV 131; hdr: TV 78; signal: TV 38;
 *  - running: DISPLAY 148 state 0 (stock GameModeApi.isGameModeRunning);
 *  - fps: DISPLAY 179 while VRR is on, else the TV 38 frame rate (stock 2 s poll of the HB frame counter).
 * Super resolution (321 / 322 on the Z9X) and game sound (IGmpf2 1 / 2) do not exist on the HB: their fields stay
 * empty and a write is refused (module request: hide them by profile game_menu.items, REQUESTS-hb.md).
 */
public final class HbGameIo implements GameMenu.Io {
    private static final String TAG = "HbGameIo";
    private static final long SPEED_WAIT_MS = 6_000, SPEED_POLL_MS = 100, AFTER_SPEED_MS = 200, TOP_SPEED_WAIT_MS = 10_000;
    private static final long READ_BACK_MS = 1_500, READ_BACK_POLL_MS = 150;
    private final Context app;

    public HbGameIo(Context c) { app = c.getApplicationContext(); }

    @Override public void read(int port, Consumer<GameMenu.Snap> cb) {
        Hal.query((g, g2) -> readOnHal(g, g2, port), s -> cb.accept(s == null ? new GameMenu.Snap() : s));
    }

    @Override public void fullFrame(Consumer<Boolean> cb) {
        Hal.query((g, g2) -> isFullFrame(g.getKeystonePoints()), cb::accept);
    }

    @Override public void apply(String key, int value, int port, GameMenu.Snap now, GameMenu.ApplyResult cb) {
        final int[] back = {GmModel.BAD};
        Hal.query((g, g2) -> {
            try {
                back[0] = GmModel.SPEED.equals(key) ? writeSpeed(g, g2, value, port) : applyOnHal(g, g2, key, value, port, now);
            } catch (Throwable t) {
                Log.w(TAG, "apply " + key + "=" + value + ": " + t);
            }
            return readOnHal(g, g2, port);
        }, s -> cb.done(back[0], s == null ? new GameMenu.Snap() : s));
    }

    /** HAL thread: one read of everything the menu shows; a failed call leaves its field at the default. */
    static GameMenu.Snap readOnHal(GmpfClient g, Gmpf2Client g2, int port) {
        GameMenu.Snap s = new GameMenu.Snap();
        try {
            GameModeOption o = g.getGameModeOption();
            if (o != null) s.opt = o.wire;
        } catch (Throwable t) { Log.d(TAG, "77: " + t); }
        try {
            int[] p = g2.getGameModeProp();
            s.running = p != null && p.length >= 1 && p[0] == 0;
        } catch (Throwable t) { Log.d(TAG, "148: " + t); }
        try {
            HdmiSignal sig = g.getHdmiInfo();
            if (sig != null && sig.plausible()) { s.width = sig.width; s.height = sig.height; s.frameCenti = sig.frameRateCenti; }
        } catch (Throwable t) { Log.d(TAG, "TV 38: " + t); }
        try { s.hdr = g.getHdrType(); } catch (Throwable t) { Log.d(TAG, "78: " + t); }
        try { s.ar = g.getAspectRatioRaw(); } catch (Throwable t) { Log.d(TAG, "36: " + t); }
        if (port == 1 || port == 2) {
            try { s.allm = g2.getAllmStatus(port); } catch (Throwable t) { Log.d(TAG, "131: " + t); }
            if (HbFeatures.on(HbFeatures.F.GAME_VRR)) {
                try { s.vrr = g2.getVrr(port) ? 1 : 0; } catch (Throwable t) { Log.d(TAG, "146: " + t); }
            }
            if (HbFeatures.on(HbFeatures.F.GAME_PICTURE) || HbFeatures.on(HbFeatures.F.GAME_DARK_BOOST)) {
                try { s.pic = g2.getGamePicture(port); } catch (Throwable t) { Log.d(TAG, "148 picture: " + t); }
            }
            int pic = s.pic == null ? GmModel.BAD : GmModel.plain(GmModel.PIC, s.pic);
            if (pic != GmModel.BAD && HbFeatures.on(HbFeatures.F.GAME_DARK_BOOST)) {
                try { s.dark = g2.getDarkBoost(pic, port) ? 1 : 0; } catch (Throwable t) { Log.d(TAG, "150: " + t); }
            }
            if (!HbFeatures.on(HbFeatures.F.GAME_PICTURE)) s.pic = null;     // read only for the dark scenes call
        }
        return s;
    }

    /** HAL thread: the input on screen must be HDMI {@code port} (TV 4). */
    private static void onPort(GmpfClient g, int port) throws Exception {
        if (port != 1 && port != 2) throw new IllegalStateException("no HDMI port");
        if (g.getCurrentInputSource() != port) throw new IllegalStateException("TV 4 is not HDMI " + port);
    }

    /** HAL thread: DISPLAY 76 with the stock HB order; the raw 77 read back (BAD when none). */
    private int writeSpeed(GmpfClient g, Gmpf2Client g2, int value, int port) throws Exception {
        onPort(g, port);
        GameModeOption o = GameModeOption.fromWire(value);
        if (o == null) throw new IllegalArgumentException("option " + value);
        if (!o.isTopSpeed() && HbFeatures.on(HbFeatures.F.GAME_VRR)) {
            try {
                if (g2.getVrr(port)) Log.i(TAG, "145(0) before leaving top speed -> " + g2.setVrr(false, port));
            } catch (Throwable t) { Log.w(TAG, "145 before 76: " + t); }
        }
        boolean r = g.setGameModeOption(o);
        GameModeOption now = g.getGameModeOption();
        long end = SystemClock.uptimeMillis() + SPEED_WAIT_MS;
        while (!r && now != o && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(SPEED_POLL_MS);
            try { now = g.getGameModeOption(); } catch (Throwable t) { Log.w(TAG, "77: " + t); }
        }
        Log.i(TAG, "76(" + o + ") -> " + r + ", 77 reads " + now);
        if (now == o) {
            afterSpeed(port);
            if (o.isTopSpeed()) topSpeedKeystone(o, SystemClock.uptimeMillis() + TOP_SPEED_WAIT_MS);
        }
        return now == null ? GmModel.BAD : now.wire;
    }

    /** Stock afterGameModeApi: 200 ms after a speed change, VRR and the game picture of the input are sent again. */
    private void afterSpeed(int port) {
        Hal.runDelayed((g, g2) -> {
            if (HbFeatures.on(HbFeatures.F.GAME_VRR)) {
                boolean v = g2.getVrr(port);
                Log.i(TAG, "after speed: 145(" + v + ") -> " + g2.setVrr(v, port));
            }
            if (HbFeatures.on(HbFeatures.F.GAME_PICTURE)) {
                int p = g2.getGamePicture(port);
                Log.i(TAG, "after speed: 147(" + p + ") -> " + g2.setGamePicture(p, port));
            }
        }, AFTER_SPEED_MS);
    }

    /** DISPLAY 38(0) for top speed once 77 reads it (re-polled every 300 ms, never holding the HAL thread). */
    private void topSpeedKeystone(GameModeOption o, long deadline) {
        Hal.runDelayed((g, g2) -> {
            GameModeOption now = g.getGameModeOption();
            if (now == o) {
                g.resetKeystoneForTopSpeedGame();
                GameProfile.noteKeystoneFullFrame(app, "game menu top speed");
                Log.i(TAG, "38 after 77 reads " + o);
                Ui.main().post(() -> Notify.show(app, app.getString(R.string.panel_speed_top_note)));
            } else if (SystemClock.uptimeMillis() < deadline) {
                topSpeedKeystone(o, deadline);
            } else {
                Log.w(TAG, "38 skipped: 77 still reads " + now + " instead of " + o);
            }
        }, 300);
    }

    /** HAL thread: one item write (not the speed) and its raw read-back. */
    static int applyOnHal(GmpfClient g, Gmpf2Client g2, String key, int value, int port, GameMenu.Snap now) throws Exception {
        onPort(g, port);
        switch (key) {
            case GmModel.AR: {
                int wire = GmModel.arWire(value);
                AspectRatio a = wire == 2 ? AspectRatio.AUTO : wire == 1 ? AspectRatio.R16_9 : null;
                if (a == null) throw new IllegalArgumentException("aspect " + value);
                boolean r = g.setAspectRatio(a);
                int back = g.getAspectRatioRaw();
                Log.i(TAG, "35(" + wire + ") -> " + r + ", 36 reads " + back);
                return back;
            }
            case GmModel.VRR: {
                if (!HbFeatures.on(HbFeatures.F.GAME_VRR)) throw new IllegalStateException("GAME_VRR off");
                int r = g2.setVrr(value == 1, port);
                int back = poll(() -> g2.getVrr(port) ? 1 : 0, GmModel.VRR, value);
                Log.i(TAG, "145(" + value + ", " + port + ") -> " + r + ", 146 reads " + back);
                return back;
            }
            case GmModel.PIC: {
                if (!HbFeatures.on(HbFeatures.F.GAME_PICTURE)) throw new IllegalStateException("GAME_PICTURE off");
                int r = g2.setGamePicture(value, port);
                int back = poll(() -> g2.getGamePicture(port), GmModel.PIC, value);
                Log.i(TAG, "147(" + value + ", " + port + ") -> " + r + ", 148 reads " + back);
                return back;
            }
            case GmModel.DARK: {
                if (!HbFeatures.on(HbFeatures.F.GAME_DARK_BOOST)) throw new IllegalStateException("GAME_DARK_BOOST off");
                int pic = now == null || now.pic == null ? GmModel.BAD : GmModel.plain(GmModel.PIC, now.pic);
                if (pic == GmModel.BAD) pic = GmModel.plain(GmModel.PIC, g2.getGamePicture(port));
                if (pic == GmModel.BAD) throw new IllegalStateException("game picture unknown");
                final int p = pic;
                int r = g2.setDarkBoost(value == 1, p, port);
                int back = poll(() -> g2.getDarkBoost(p, port) ? 1 : 0, GmModel.DARK, value);
                Log.i(TAG, "149(" + value + ", " + p + ", " + port + ") -> " + r + ", 150 reads " + back);
                return back;
            }
            default:
                // sr / snd: no HB vendor call (super resolution and game sound are not on the HB)
                throw new IllegalArgumentException("item " + key + " is not on the HB");
        }
    }

    interface IntRead { int read() throws Exception; }

    /** Reads until the menu value equals {@code want} or READ_BACK_MS passed; the last raw value. */
    private static int poll(IntRead r, String key, int want) throws Exception {
        long end = SystemClock.uptimeMillis() + READ_BACK_MS;
        int raw = r.read();
        while (GmModel.plain(key, raw) != want && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(READ_BACK_POLL_MS);
            raw = r.read();
        }
        return raw;
    }

    @Override public int activePort() { return GameProfile.activePort(); }

    @Override public boolean isConsolePort(int port) { return GameProfile.isConsolePort(app, port); }

    @Override public void gameRunning(Consumer<Boolean> cb) {
        Hal.query((g, g2) -> {
            int[] p = g2.getGameModeProp();
            return p != null && p.length >= 1 && p[0] == 0;
        }, cb::accept);
    }

    @Override public void fps(Consumer<Integer> cb) {
        final int port = GameProfile.activePort();
        Hal.query((g, g2) -> {
            if ((port == 1 || port == 2) && HbFeatures.on(HbFeatures.F.GAME_VRR) && g2.getVrr(port)) {
                int f = g2.getOutputFps();                                  // 179 while VRR runs (stock)
                return f > 0 ? f : -1;
            }
            HdmiSignal s = g.getHdmiInfo();
            return s != null && s.plausible() ? GmModel.fpsShown(s.frameRateCenti) : -1;
        }, cb::accept);
    }
}
