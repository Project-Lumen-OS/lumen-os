// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import org.lumen.projector.Hal;
import org.lumen.projector.SafeHandler;
import org.lumen.projector.Ui;
import org.lumen.projector.game.GameProfile;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.KstPoint;
import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.Panel;
import java.util.List;
import java.util.function.Consumer;
import org.lumen.projector.Hal;
import org.lumen.projector.game.GameProfile;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.KstPoint;

/**
 * The Z9X side of the shared game menu (apps/core/lumen-menu, GameMenu.Io): every read and write on z9x-hal via
 * Hal.query, moved here unchanged from GameMenu.HalIo when the menu became one module for every projector.
 */
public final class Z9xGameIo implements GameMenu.Io {
    private static final String TAG = GameMenu.TAG;
    private final Context app;

    public Z9xGameIo(Context c) { app = c.getApplicationContext(); }

    @Override public void read(int port, Consumer<GameMenu.Snap> cb) {
        Hal.query((g, g2) -> readOnHal(g, g2, port), s -> cb.accept(s == null ? new GameMenu.Snap() : s));
    }

    @Override public void fullFrame(Consumer<Boolean> cb) {
        Hal.query((g, g2) -> isFullFrame(g.getKeystonePoints()), cb::accept);
    }

    @Override public void apply(String key, int value, int port, GameMenu.Snap now, GameMenu.ApplyResult cb) {
        if (GmModel.SPEED.equals(key)) { applySpeed(value, port, cb); return; }
        final int[] back = {GmModel.BAD};
        Hal.query((g, g2) -> {
            try {
                back[0] = applyOnHal(app, g, g2, key, value, port, now);
            } catch (Throwable t) {
                Log.w(TAG, "apply " + key + "=" + value + ": " + t);
            }
            return readOnHal(g, g2, port);
        }, s -> cb.done(back[0], s == null ? new GameMenu.Snap() : s));
    }

    /**
     * 648 and its read-back without holding the z9x-hal thread: the vendor stores the option at once but 647 reads
     * the old one until the picture is applied again (~4.3 s, QuickPanelController.onGameSpeed), so 647 is read
     * again every SPEED_POLL_MS from the main thread, each read its own short HAL task, until it agrees or
     * SPEED_WAIT_MS passed. 207 (stock switchGameModeSpeed) only after 647 reads the new top speed option.
     */
    private void applySpeed(int value, int port, GameMenu.ApplyResult cb) {
        final long deadline = SystemClock.uptimeMillis() + SPEED_WAIT_MS;
        Hal.query((g, g2) -> {
            try {
                return writeSpeedOnHal(g, g2, value, port);
            } catch (Throwable t) {
                Log.w(TAG, "apply speed=" + value + ": " + t);
                return null;
            }
        }, back -> {
            if (back == null) finishSpeed(value, port, GmModel.BAD, cb);
            else speedPoll(value, port, deadline, back, cb);
        });
    }

    private void speedPoll(int value, int port, long deadline, int back, GameMenu.ApplyResult cb) {
        if (back == value || SystemClock.uptimeMillis() >= deadline) {
            finishSpeed(value, port, back, cb);
            return;
        }
        Ui.main().postDelayed(() -> Hal.query((g, g2) -> {
            GmpfClient.GameModeOption o = g.getGameModeOption();
            return o == null ? GmModel.BAD : o.wire;
        }, b -> speedPoll(value, port, deadline, b == null ? GmModel.BAD : b, cb)), SPEED_POLL_MS);
    }

    private void finishSpeed(int value, int port, int back, GameMenu.ApplyResult cb) {
        Hal.query((g, g2) -> {
            Log.i(TAG, "648(" + value + ") read back " + back);
            GmpfClient.GameModeOption o = GmpfClient.GameModeOption.fromWire(value);
            if (back == value && o != null && o.isTopSpeed()) {
                try {
                    g.resetKeystoneForTopSpeedGame();             // stock switchGameModeSpeed: 207 after 647 reads it
                    GameProfile.noteKeystoneFullFrame(app, "game menu turbo");
                    Log.i(TAG, "207 after 647 reads " + o);
                } catch (Throwable t) {
                    Log.w(TAG, "207: " + t);
                }
            }
            return readOnHal(g, g2, port);
        }, s -> cb.done(back, s == null ? new GameMenu.Snap() : s));
    }

    private static final long SPEED_WAIT_MS = 6_000, SPEED_POLL_MS = 300;

    /** z9x-hal: 648 (and 57 as the quick panel), then one 647 read; the raw option read (BAD when none). */
    static int writeSpeedOnHal(GmpfClient g, Gmpf2Client g2, int value, int port) throws Exception {
        if (port != 1 && port != 2) throw new IllegalStateException("no HDMI port");
        if (g.getCurrentInputSource() != port) throw new IllegalStateException("696 is not HDMI " + port);
        GmpfClient.GameModeOption o = GmpfClient.GameModeOption.fromWire(value);
        if (o == null) throw new IllegalArgumentException("option " + value);
        boolean r = g.setGameModeOption(o);
        if (PanelRules.resendGameState(r, true)) {           // game mode runs (55), as the quick panel with mode On
            try { g2.getGameModeProp(); } catch (Throwable t) { Log.d(TAG, "55 before 57: " + t); }
            try { Log.i(TAG, "57(0) after 648 -> " + g2.setGameModeState(true)); } catch (Throwable t) { Log.w(TAG, "57: " + t); }
        }
        GmpfClient.GameModeOption back = g.getGameModeOption();
        Log.i(TAG, "648(" + o + ") -> " + r + ", 647 reads " + back);
        return back == null ? GmModel.BAD : back.wire;
    }

    static GameMenu.Snap readOnHal(GmpfClient g, Gmpf2Client g2, int port) {
        GameMenu.Snap s = new GameMenu.Snap();
        try {
            GmpfClient.GameModeOption o = g.getGameModeOption();
            if (o != null) s.opt = o.wire;
        } catch (Throwable t) { Log.d(TAG, "647: " + t); }
        try {
            int[] p = g2.getGameModeProp();
            s.running = p != null && p.length >= 1 && p[0] == 0;
        } catch (Throwable t) { Log.d(TAG, "55: " + t); }
        try {
            GmpfClient.HdmiSignal sig = g.getHdmiInfo();
            if (sig != null && sig.plausible()) { s.width = sig.width; s.height = sig.height; s.frameCenti = sig.frameRateCenti; }
        } catch (Throwable t) { Log.d(TAG, "662: " + t); }
        try { s.hdr = g.getHdrType(); } catch (Throwable t) { Log.d(TAG, "620: " + t); }
        if (port == 1 || port == 2) {
            try { s.allm = g2.getAllmStatus(port); } catch (Throwable t) { Log.d(TAG, "310: " + t); }
        }
        try { s.sr = g2.getSuperResolution() ? 1 : 0; } catch (Throwable t) { Log.d(TAG, "321: " + t); }
        try { s.ar = g.getAspectRatioRaw(); } catch (Throwable t) { Log.d(TAG, "664: " + t); }
        if (port == 1 || port == 2) {
            try { s.vrr = g2.getVrrState(port); } catch (Throwable t) { Log.d(TAG, "300: " + t); }
            try { s.pic = g2.getGamePicture(port); } catch (Throwable t) { Log.d(TAG, "298: " + t); }
            int pic = s.pic == null ? GmModel.BAD : GmModel.plain(GmModel.PIC, s.pic);
            if (pic != GmModel.BAD) {
                try { s.dark = g2.getDarkScenes(pic, port) ? 1 : 0; } catch (Throwable t) { Log.d(TAG, "296: " + t); }
            }
        }
        try { s.snd = g2.getGameSound(); } catch (Throwable t) { Log.d(TAG, "1: " + t); }
        return s;
    }

    private static final long READ_BACK_MS = 1_500, READ_BACK_POLL_MS = 150;

    /** z9x-hal: one write (not the speed, see HalIo.applySpeed) and its read-back (raw getter value). */
    static int applyOnHal(Context app, GmpfClient g, Gmpf2Client g2, String key, int value, int port, GameMenu.Snap now)
            throws Exception {
        if (port != 1 && port != 2) throw new IllegalStateException("no HDMI port");
        if (g.getCurrentInputSource() != port) throw new IllegalStateException("696 is not HDMI " + port);
        switch (key) {
            case GmModel.SR: {
                boolean r = g2.setSuperResolution(value == 1);
                int back = g2.getSuperResolution() ? 1 : 0;
                Log.i(TAG, "322(" + value + ") -> " + r + ", 321 reads " + back);
                return back;
            }
            case GmModel.AR: {
                int wire = GmModel.arWire(value);
                GmpfClient.AspectRatio a = wire == 2 ? GmpfClient.AspectRatio.AUTO
                        : wire == 1 ? GmpfClient.AspectRatio.R16_9 : null;
                if (a == null) throw new IllegalArgumentException("aspect " + value);
                boolean r = g.setAspectRatio(a);
                int back = g.getAspectRatioRaw();
                Log.i(TAG, "665(" + wire + ") -> " + r + ", 664 reads " + back);
                return back;
            }
            case GmModel.VRR: {
                int r = g2.setVrrState(value == 1, port);
                int back = poll(() -> g2.getVrrState(port), GmModel.VRR, value);
                Log.i(TAG, "301(" + value + ", " + port + ") -> " + r + ", 300 reads " + back);
                return back;
            }
            case GmModel.PIC: {
                int r = g2.setGamePicture(value, port);
                int back = poll(() -> g2.getGamePicture(port), GmModel.PIC, value);
                Log.i(TAG, "299(" + value + ", " + port + ") -> " + r + ", 298 reads " + back);
                return back;
            }
            case GmModel.DARK: {
                int pic = now == null || now.pic == null ? GmModel.BAD : GmModel.plain(GmModel.PIC, now.pic);
                if (pic == GmModel.BAD) pic = GmModel.plain(GmModel.PIC, g2.getGamePicture(port));
                if (pic == GmModel.BAD) throw new IllegalStateException("game picture unknown");
                final int p = pic;
                int r = g2.setDarkScenes(value == 1, p, port);
                int back = poll(() -> g2.getDarkScenes(p, port) ? 1 : 0, GmModel.DARK, value);
                Log.i(TAG, "297(" + value + ", " + p + ", " + port + ") -> " + r + ", 296 reads " + back);
                return back;
            }
            case GmModel.SND: {
                boolean r = g2.setGameSound(GmModel.sndWire(value));
                int back = poll(g2::getGameSound, GmModel.SND, value);
                Log.i(TAG, "2(" + GmModel.sndWire(value) + ") -> " + r + ", 1 reads " + back);
                return back;
            }
            default:
                throw new IllegalArgumentException("item " + key);
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

    private static boolean isFullFrame(KstPoint p) {
        if (p == null) return false;
        KstPoint f = KstPoint.fullFrame();
        for (int c = KstPoint.TL; c <= KstPoint.BL; c++) {
            if (Math.abs(p.x(c) - f.x(c)) > 4 || Math.abs(p.y(c) - f.y(c)) > 4) return false;
        }
        return true;
    }

    // ================================================================== the panel

    @Override public int activePort() { return GameProfile.activePort(); }

    @Override public boolean isConsolePort(int port) { return GameProfile.isConsolePort(app, port); }

    @Override public void gameRunning(Consumer<Boolean> cb) {
        Hal.query((g, g2) -> {
            int[] p = g2.getGameModeProp();
            return p != null && p.length >= 1 && p[0] == 0;
        }, cb::accept);
    }

    @Override public void fps(Consumer<Integer> cb) {
        Hal.query((g, g2) -> {
            GmpfClient.HdmiSignal s = g.getHdmiInfo();
            return s != null && s.plausible() ? GmModel.fpsShown(s.frameRateCenti) : -1;
        }, cb::accept);
    }
}
