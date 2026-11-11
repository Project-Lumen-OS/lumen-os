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

import org.lumen.projector.SafeHandler;
import org.lumen.projector.Ui;
import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.OverlayPanelHost;
import org.lumen.projector.ui.Panel;
import org.lumen.projector.ui.PanelHost;

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
 * session (Io.activePort). The watcher polls 55 every 3 s only while an HDMI session is on screen and the
 * screen is on; it also keeps our own crosshair and frame counter windows (GmOverlays) up only during a game.
 *
 * HAL: only through Hal.query on the z9x-hal thread (the device's Io, Z9X: Z9xGameIo); proven calls (647/648, 207, 321/322, 664/665, 662,
 * 620, 310, 55/57) as the quick panel sends them, the new game calls (IGmpf2 300/301, 298/299, 296/297, 1/2) with the
 * stock arguments, each item behind the capability check of GmModel.Caps and every write read back.
 *
 * <p>Windows: through the PanelHost given to {@link #install}, the overlay host unless the
 * device gives its activity host; the crosshair and frame counter layers need a host with static layers (the
 * activity host has none, so there they are not drawn).
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
        Integer cs, hdrMode;                 // menu values (GmModel.CS / HDR), null = no answer or not offered now
    }

    /** The menu's access to the projector (real: HalIo; the render harness gives a fake). Callbacks on main. */
    interface Io {
        void read(int port, Consumer<Snap> cb);

        /** Writes item {@code key} (or GmModel.SPEED) and returns the raw read-back (BAD = none) and a fresh read. */
        void apply(String key, int value, int port, Snap now, ApplyResult cb);

        /** Keystone is the full frame (186 within 4 px), null = unknown. */
        void fullFrame(Consumer<Boolean> cb);

        /** The HDMI port (1, 2) of the open game-mode session, or -1 (stock: the TIF session of org.lumen.tvinput). */
        int activePort();

        /** True when the user marked the device on this HDMI port as a console. */
        boolean isConsolePort(int port);

        /** Game mode runs right now (stock 55 state 0); cb on the main thread, null = no answer. */
        void gameRunning(Consumer<Boolean> cb);

        /** The frame rate the overlay shows (GmModel.fpsShown of the HDMI signal), -1 = none; cb on the main thread. */
        void fps(Consumer<Integer> cb);

        /** HDMI ports 1..n of this projector (Z9X, HB: 2; X50: 3, stock TV.inputToInt HDMI1..3). */
        default int hdmiPorts() { return 2; }
    }

    /** The device's game menu access, given by the projector app at start (install). */
    static Io sIo;
    /** Where the menu's windows live (install; the overlay host unless the device gives another). */
    static PanelHost sHost;

    /** {@code port} is an HDMI port of this projector (1..Io.hdmiPorts). */
    static boolean hdmiPort(int port) { return port >= 1 && port <= (sIo == null ? 2 : sIo.hdmiPorts()); }

    interface ApplyResult {
        void done(int rawBack, Snap after);
    }

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
        try { marked = sIo.isConsolePort(port); } catch (Throwable t) { Log.d(TAG, "console mark: " + t); }
        GmController ctl = new GmController(app, sIo, port, first, src, marked);
        ctl.overlays = GmOverlays.get(app);
        MenuPanel p = new MenuPanel(ctl);
        sPanel = p;
        Log.i(TAG, "show on HDMI " + port + (first ? " (first game mode entry)" : "") + ", device " + src + ": " + ctl.theme);
        if (!sHost.show(p)) { sPanel = null; return; }
        // the device list may be older than this menu (or empty: the first show comes in the tick that reads it):
        // read it now, the answer reaches the open menu (Watch.readCec -> GmController.onSource)
        if (w != null) w.refreshCec(port);
    }

    /**
     * Gear held (KeyReceiver, first repeated DOWN). While game mode runs on the HDMI input on screen: the game menu
     * (a second long press closes it); otherwise {@code fallback} (Android settings, as before). Main thread.
     */
    public static void onGearLong(Context c, Runnable fallback) {
        if (sIo == null) { fallback.run(); return; }
        try {
            Context app = c.getApplicationContext();
            if (sPanel != null && sPanel.isShowing()) {
                sHost.dismissAll();
                return;
            }
            final int port = sIo.activePort();
            if (!hdmiPort(port)) { fallback.run(); return; }
            sIo.gameRunning(running -> {
                if (Boolean.TRUE.equals(running) && sIo.activePort() == port) show(app, port, false);
                else fallback.run();
            });
        } catch (Throwable t) {
            Log.e(TAG, "gear long press", t);
            fallback.run();
        }
    }

    /** App.onCreate: the game mode watcher (no HAL call while no HDMI session is on screen), overlay host. */
    public static void install(Context c, Io io) {
        install(c, io, null);
    }

    /** As {@link #install(Context, Io)} with the menu's windows in {@code host} (null: the overlay host). */
    public static void install(Context c, Io io, PanelHost host) {
        try {
            if (io == null) return;                          // no game menu on this device
            sHost = host != null ? host : OverlayPanelHost.get(c);
            sIo = io;
            org.lumen.menu.MenuProfile p = MenuController.profile(c);
            CAPS.allow(items(p.gameItems(), sHost.hasStaticLayers())); // only the items of devices/<c>/menu.json
            CAPS.gate(p.gameGated());          // and those its game_menu.gated names only once their getter answers
            GmModel.setSounds(p.gameSounds().size());       // the stock game sound values of this model
            if (Watch.sInstance == null) Watch.sInstance = new Watch(c.getApplicationContext());
        } catch (Throwable t) {
            Log.e(TAG, "install", t);
        }
    }

    /**
     * The tiles of the profile this host can serve: the crosshair and the frame counter are layers over the game
     * (PanelHost.addStatic), so a host without such layers (the X50 activity host) never shows those two tiles.
     */
    static java.util.List<String> items(java.util.List<String> profile, boolean staticLayers) {
        if (staticLayers) return profile;
        java.util.List<String> out = new java.util.ArrayList<>(profile);
        if (out.remove(GmModel.AIM) | out.remove(GmModel.FPS)) {
            Log.i(TAG, "no layers over the game in this host: crosshair and frame counter not offered");
        }
        return out;
    }

    /** Game mode is running on the HDMI input on screen (last watcher read, at most a few seconds old). Main. */
    static boolean gameRunning() {
        Watch w = Watch.sInstance;
        return w != null && w.running && sIo.activePort() == w.runningPort;
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
        private final GmModel.Source[] sources = {GmModel.Source.NONE, GmModel.Source.NONE, GmModel.Source.NONE,
                GmModel.Source.NONE};                  // index = HDMI port 1..3
        private final String[] logged = new String[sources.length];   // index = HDMI port 1..3
        private final Runnable tick = this::tick;

        Watch(Context app) {
            this.app = app;
            prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            main.postDelayed(tick, TICK_MS);
        }

        GmModel.Source source(int port) { return hdmiPort(port) && port < sources.length ? sources[port] : GmModel.Source.NONE; }

        /** A CEC read of {@code port} now (the menu just opened). Main thread. */
        void refreshCec(int port) {
            if (!hdmiPort(port) || port >= sources.length) return;
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
            final int port = sIo.activePort();
            boolean screen = true;
            try {
                PowerManager pm = app.getSystemService(PowerManager.class);
                screen = pm == null || pm.isInteractive();
            } catch (Throwable ignored) { }
            if (!hdmiPort(port) || !screen) {
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
            sIo.gameRunning(r -> {
                boolean was = running;
                running = Boolean.TRUE.equals(r) && sIo.activePort() == port;
                runningPort = port;
                if (running && !was) {
                    Log.i(TAG, "game mode runs on HDMI " + port);
                    if (!prefs.getBoolean(K_FIRST_SHOWN, false)) firstPending = true;
                }
                if (!running) firstPending = false;
                if (firstPending && sHost.current() == null) {
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
