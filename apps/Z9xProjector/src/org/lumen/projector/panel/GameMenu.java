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

/**
 * Lumen game menu, design A (decisions 2026-11-11): a bottom dock over the game with the live status card, the
 * three presets and the ten tiles in four groups (GmModel / GmView), opened by a LONG press of the gear while game mode
 * runs on HDMI (instead of Android settings, as the stock xhplayer: gear long press = KEYCODE_MENU -> GameMenuActivity),
 * and once by itself on the first entry into game mode (stock isFirstShowGameMenu), then it hides 30 s after the last
 * key; opened by the key it hides after 10 s.
 *
 * Game mode running = IGmpf2 55 state 0 (stock GameModeApi.isGameModeRunning) while org.lumen.tvinput shows an HDMI
 * session (GameProfile.activePort). The watcher polls 55 every 3 s only while an HDMI session is on screen and the
 * screen is on; it also keeps our own crosshair and frame counter windows (GmOverlays) up only during a game.
 *
 * HAL: only through Hal.query on the z9x-hal thread ({@link HalIo}); proven calls (647/648, 207, 321/322, 664/665, 662,
 * 620, 310, 55/57) as the quick panel sends them, the new game calls (IGmpf2 300/301, 298/299, 296/297, 1/2) with the
 * stock arguments, each item behind the capability check of GmModel.Caps and every write read back.
 */
public final class GameMenu {
    static final String TAG = "Z9xGameMenu";
    static final String PREFS = "z9x_gamemenu";
    static final String K_FIRST_SHOWN = "first_shown";
    /** Vendor id our own TV device reports (ConsoleMatcher.VENDOR_SELF_MTK, "mtk"). */
    static final int VENDOR_SELF_MTK = 0x6D746B;

    private GameMenu() {}

    /** Capabilities of the gated items on this unit, this boot (shared by every show). */
    static final GmModel.Caps CAPS = new GmModel.Caps();

    // ================================================================== HAL side
    /** What one read of the HAL found (z9x-hal thread); fields stay at their defaults when a call failed. */
    static final class Snap {
        int opt = GmModel.BAD;               // 647
        boolean running;                     // 55 state 0
        int width, height, frameCenti;       // 662 when plausible
        int hdr = -1;                        // 620
        Boolean allm;                        // 310
        int sr = GmModel.BAD;                // 321
        int ar = GmModel.BAD;                // 664 raw
        Integer vrr, pic, dark, snd;         // raw replies of the gated getters, null = no answer
    }

    /** The menu's access to the projector (real: HalIo; the render harness gives a fake). Callbacks on main. */
    interface Io {
        void read(int port, Consumer<Snap> cb);

        /** Writes item {@code key} (or GmModel.SPEED) and returns the raw read-back (BAD = none) and a fresh read. */
        void apply(String key, int value, int port, Snap now, ApplyResult cb);

        /** Keystone is the full frame (186 within 4 px), null = unknown. */
        void fullFrame(Consumer<Boolean> cb);
    }

    interface ApplyResult {
        void done(int rawBack, Snap after);
    }

    /** The real projector: every call on z9x-hal via Hal.query. */
    static final class HalIo implements Io {
        private final Context app;

        HalIo(Context c) { app = c.getApplicationContext(); }

        @Override public void read(int port, Consumer<Snap> cb) {
            Hal.query((g, g2) -> readOnHal(g, g2, port), s -> cb.accept(s == null ? new Snap() : s));
        }

        @Override public void fullFrame(Consumer<Boolean> cb) {
            Hal.query((g, g2) -> isFullFrame(g.getKeystonePoints()), cb::accept);
        }

        @Override public void apply(String key, int value, int port, Snap now, ApplyResult cb) {
            if (GmModel.SPEED.equals(key)) { applySpeed(value, port, cb); return; }
            final int[] back = {GmModel.BAD};
            Hal.query((g, g2) -> {
                try {
                    back[0] = applyOnHal(app, g, g2, key, value, port, now);
                } catch (Throwable t) {
                    Log.w(TAG, "apply " + key + "=" + value + ": " + t);
                }
                return readOnHal(g, g2, port);
            }, s -> cb.done(back[0], s == null ? new Snap() : s));
        }

        /**
         * 648 and its read-back without holding the z9x-hal thread: the vendor stores the option at once but 647 reads
         * the old one until the picture is applied again (~4.3 s, QuickPanelController.onGameSpeed), so 647 is read
         * again every SPEED_POLL_MS from the main thread, each read its own short HAL task, until it agrees or
         * SPEED_WAIT_MS passed. 207 (stock switchGameModeSpeed) only after 647 reads the new top speed option.
         */
        private void applySpeed(int value, int port, ApplyResult cb) {
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

        private void speedPoll(int value, int port, long deadline, int back, ApplyResult cb) {
            if (back == value || SystemClock.uptimeMillis() >= deadline) {
                finishSpeed(value, port, back, cb);
                return;
            }
            Ui.main().postDelayed(() -> Hal.query((g, g2) -> {
                GmpfClient.GameModeOption o = g.getGameModeOption();
                return o == null ? GmModel.BAD : o.wire;
            }, b -> speedPoll(value, port, deadline, b == null ? GmModel.BAD : b, cb)), SPEED_POLL_MS);
        }

        private void finishSpeed(int value, int port, int back, ApplyResult cb) {
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
            }, s -> cb.done(back, s == null ? new Snap() : s));
        }
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

    static Snap readOnHal(GmpfClient g, Gmpf2Client g2, int port) {
        Snap s = new Snap();
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
    static int applyOnHal(Context app, GmpfClient g, Gmpf2Client g2, String key, int value, int port, Snap now)
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
    private static MenuPanel sPanel;

    /** The overlay window of the menu: full screen, transparent, keys go to the controller. */
    static final class MenuPanel extends Panel {
        final GmController ctl;

        MenuPanel(GmController ctl) { this.ctl = ctl; }

        @Override protected View onCreateView(Context c) { return ctl.view(); }

        @Override protected WindowManager.LayoutParams onCreateLayoutParams(Context c) {
            return OverlayHost.params(true, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.TOP | Gravity.START);
        }

        @Override protected long autoHideMs() { return GmModel.autoHideMs(ctl.first); }

        @Override protected boolean onBack() { return ctl.back(); }

        @Override protected boolean onKeyEvent(KeyEvent ev) { return ctl.key(ev); }

        @Override protected void onShown(View root) {
            ctl.onShown();
            if (ctl.overlays != null) ctl.overlays.menuChanged();
        }

        @Override protected void animateOut(View root, Runnable end) { ctl.animateOut(end); }

        @Override protected void onDismissed() {
            ctl.onDismissed();
            if (sPanel == this) sPanel = null;
            if (ctl.overlays != null) ctl.overlays.menuChanged();
        }
    }

    /** The menu is on screen. Main thread. */
    static boolean isShowing() { return sPanel != null && sPanel.isShowing(); }

    /** Shows the menu on HDMI {@code port}. Main thread. */
    static void show(Context c, int port, boolean first) {
        Context app = c.getApplicationContext();
        if (sPanel != null && sPanel.isShowing()) return;
        Watch w = Watch.sInstance;
        GmModel.Source src = w == null ? GmModel.Source.NONE : w.source(port);
        boolean marked = false;
        try { marked = GameProfile.isConsolePort(app, port); } catch (Throwable t) { Log.d(TAG, "console mark: " + t); }
        GmController ctl = new GmController(app, new HalIo(app), port, first, src, marked);
        ctl.overlays = GmOverlays.get(app);
        MenuPanel p = new MenuPanel(ctl);
        sPanel = p;
        Log.i(TAG, "show on HDMI " + port + (first ? " (first game mode entry)" : "") + ", device " + src + ": " + ctl.theme);
        if (!OverlayHost.get(app).show(p)) { sPanel = null; return; }
        // the device list may be older than this menu (or empty: the first show comes in the tick that reads it):
        // read it now, the answer reaches the open menu (Watch.readCec -> GmController.onSource)
        if (w != null) w.refreshCec(port);
    }

    /**
     * Gear held (KeyReceiver, first repeated DOWN). While game mode runs on the HDMI input on screen: the game menu
     * (a second long press closes it); otherwise {@code fallback} (Android settings, as before). Main thread.
     */
    public static void onGearLong(Context c, Runnable fallback) {
        try {
            Context app = c.getApplicationContext();
            if (sPanel != null && sPanel.isShowing()) {
                OverlayHost.get(app).dismissAll(true);
                return;
            }
            final int port = GameProfile.activePort();
            if (port != 1 && port != 2) { fallback.run(); return; }
            Hal.query((g, g2) -> {
                int[] p = g2.getGameModeProp();
                return p != null && p.length >= 1 && p[0] == 0;
            }, running -> {
                if (Boolean.TRUE.equals(running) && GameProfile.activePort() == port) show(app, port, false);
                else fallback.run();
            });
        } catch (Throwable t) {
            Log.e(TAG, "gear long press", t);
            fallback.run();
        }
    }

    /** App.onCreate: the game mode watcher (no HAL call while no HDMI session is on screen). */
    public static void install(Context c) {
        try {
            if (Watch.sInstance == null) Watch.sInstance = new Watch(c.getApplicationContext());
        } catch (Throwable t) {
            Log.e(TAG, "install", t);
        }
    }

    /** Game mode is running on the HDMI input on screen (last watcher read, at most a few seconds old). Main. */
    static boolean gameRunning() {
        Watch w = Watch.sInstance;
        return w != null && w.running && GameProfile.activePort() == w.runningPort;
    }

    // ================================================================== watcher
    static final class Watch {
        static Watch sInstance;
        private static final long TICK_MS = 3_000, CEC_EVERY_MS = 12_000, CEC_MENU_MS = 2_500;

        private final Context app;
        private final SafeHandler main = Ui.main();
        private final SafeHandler worker = SafeHandler.newThread("z9x-gm");
        private final SharedPreferences prefs;
        volatile boolean running;
        volatile int runningPort;
        private boolean firstPending;
        private long cecAt;
        private final GmModel.Source[] sources = {GmModel.Source.NONE, GmModel.Source.NONE, GmModel.Source.NONE};
        private final String[] logged = new String[3];
        private final Runnable tick = this::tick;

        Watch(Context app) {
            this.app = app;
            prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            main.postDelayed(tick, TICK_MS);
        }

        GmModel.Source source(int port) { return port == 1 || port == 2 ? sources[port] : GmModel.Source.NONE; }

        /** A CEC read of {@code port} now (the menu just opened). Main thread. */
        void refreshCec(int port) {
            if (port != 1 && port != 2) return;
            cecAt = SystemClock.uptimeMillis();
            worker.post(() -> readCec(port));
        }

        private void tick() {
            try {
                tickInner();
            } catch (Throwable t) {
                Log.w(TAG, "watch: " + t);
            }
            main.removeCallbacks(tick);
            main.postDelayed(tick, TICK_MS);
        }

        private void tickInner() {
            final int port = GameProfile.activePort();
            boolean screen = true;
            try {
                PowerManager pm = app.getSystemService(PowerManager.class);
                screen = pm == null || pm.isInteractive();
            } catch (Throwable ignored) { }
            if ((port != 1 && port != 2) || !screen) {
                if (running) Log.i(TAG, "game mode left (no HDMI session or screen off)");
                running = false;
                firstPending = false;
                GmOverlays.get(app).setGame(false);
                return;
            }
            long now = SystemClock.uptimeMillis();
            // while the menu is open every tick: a console's name arrives after its placeholder (PS5: Playback_1)
            if (now - cecAt > (isShowing() ? CEC_MENU_MS : CEC_EVERY_MS)) {
                cecAt = now;
                worker.post(() -> readCec(port));
            }
            Hal.query((g, g2) -> {
                int[] p = g2.getGameModeProp();
                return p != null && p.length >= 1 && p[0] == 0;
            }, r -> {
                boolean was = running;
                running = Boolean.TRUE.equals(r) && GameProfile.activePort() == port;
                runningPort = port;
                if (running && !was) {
                    Log.i(TAG, "game mode runs on HDMI " + port);
                    if (!prefs.getBoolean(K_FIRST_SHOWN, false)) firstPending = true;
                }
                if (!running) firstPending = false;
                if (firstPending && OverlayHost.get(app).current() == null) {
                    firstPending = false;
                    prefs.edit().putBoolean(K_FIRST_SHOWN, true).apply();
                    show(app, port, true);
                }
                GmOverlays.get(app).setGame(running);
            });
        }

        /**
         * z9x-gm thread: the first CEC source device behind {@code port} (never ourselves: logical address 0, vendor
         * mtk, type TV). The result goes to the open menu of that port at once.
         */
        private void readCec(int port) {
            GmModel.Source src = GmModel.Source.NONE;
            StringBuilder all = new StringBuilder();
            try {
                HdmiControlManager m = app.getSystemService(HdmiControlManager.class);
                List<HdmiDeviceInfo> list = m == null ? null : m.getConnectedDevices();
                if (list != null) {
                    for (HdmiDeviceInfo d : list) {
                        if (d == null || !d.isCecDevice()) continue;
                        all.append(" [port ").append(d.getPortId()).append(" la ").append(d.getLogicalAddress())
                                .append(" type ").append(d.getDeviceType()).append(" vendor 0x")
                                .append(Integer.toHexString(d.getVendorId())).append(" '").append(d.getDisplayName()).append("']");
                        if (src != GmModel.Source.NONE || d.getPortId() != port) continue;
                        if (d.getLogicalAddress() == 0 || d.getVendorId() == VENDOR_SELF_MTK
                                || d.getDeviceType() == HdmiDeviceInfo.DEVICE_TV) continue;   // ourselves
                        if (!d.isSourceType() && d.getDeviceType() != HdmiDeviceInfo.DEVICE_PLAYBACK) continue;
                        src = new GmModel.Source(d.getDisplayName(), d.getVendorId(),
                                d.getDeviceType() == HdmiDeviceInfo.DEVICE_PLAYBACK);
                    }
                } else {
                    all.append(m == null ? " no HdmiControlManager" : " no list");
                }
            } catch (Throwable t) {
                all.append(' ').append(t);
            }
            // one line per change: what the CEC list really says on this unit (names and vendors are unverified)
            String line = "HDMI " + port + " CEC:" + all + " -> " + src;
            if (!line.equals(logged[port])) {
                logged[port] = line;
                Log.i(TAG, line);
            }
            final GmModel.Source s = src;
            main.post(() -> {
                sources[port] = s;
                MenuPanel p = sPanel;
                if (p != null && p.isShowing() && p.ctl.port == port) p.ctl.onSource(s);
            });
        }
    }
}
