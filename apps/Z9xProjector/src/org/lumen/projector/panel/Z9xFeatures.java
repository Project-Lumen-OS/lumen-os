// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import org.lumen.menu.Feature;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.projector.Hal;
import org.lumen.projector.KeyReceiver;
import org.lumen.projector.Ui;
import org.lumen.projector.ak.AkOverlay;
import org.lumen.projector.audio.AiSoundEngine;
import org.lumen.projector.audio.AudioOutputChoice;
import org.lumen.projector.audio.AudioPathReporter;
import org.lumen.projector.audio.SoundEq;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.KstPoint;
import org.lumen.projector.source.TifHdmiState;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Z9X adapter of the shared menu: every feature of
 * devices/horse_popular/menu.json with the same z9x-hal calls, side effects and stock notes as the Z9X 1.0.10 quick
 * panel (QuickPanelController of release/z9x-1.0.10, cited QPC:line). What the rows show, the refusals before a
 * press and the stock questions are the plain-Java {@link Z9xMenuRules}; this class reads the projector into
 * {@link Z9xMenuRules.In} and writes. Device pages (input list, expert picture, HDMI-CEC, fit, equalizer) are
 * {@link Z9xMenuPages}.
 */
public final class Z9xFeatures implements ProjectorFeatures {
    private static final String TAG = "Z9xFeatures";
    static final String KEY_GAME_MODE = QuickPanel.KEY_GAME_MODE;
    static final String KEY_ULTRA_120 = QuickPanelController.KEY_ULTRA_120;
    private static final long GAME_OPTION_WAIT_MS = 6_000, GAME_OPTION_POLL_MS = 100, PENDING_SPEED_MS = 10_000;
    private static final int FULL_FRAME_TOLERANCE = 4;

    /** Every feature of devices/horse_popular/menu.json; the 1.0.10 quick panel had each of them. */
    static final Set<String> FEATURES = Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(
            Feature.EYE, Feature.KEYSTONE_AK_NOW, Feature.KEYSTONE_FIT_NOW, Feature.KEYSTONE_MANUAL,
            Feature.ZOOM, Feature.KEYSTONE_FIT_SCREEN, Feature.KEYSTONE_ROTATION, Feature.KEYSTONE_REALTIME,
            Feature.KEYSTONE_ON_POWER, Feature.KEYSTONE_CURTAIN, Feature.KEYSTONE_OBSTACLE, Feature.KEYSTONE_RESTORE,
            Feature.FOCUS_AF_NOW, Feature.FOCUS_MANUAL, Feature.FOCUS_SMART, Feature.FOCUS_ON_MOVE,
            Feature.FOCUS_ON_POWER, Feature.LAMP, Feature.BOOST, Feature.PICTURE_MODE,
            Feature.PICTURE_PQ, Feature.PICTURE_COLOR_TEMP, Feature.PICTURE_MEMC, Feature.PICTURE_AI_CONTRAST,
            Feature.PICTURE_SUPER_RES, Feature.PICTURE_ULTRA120, Feature.PICTURE_PRO, Feature.PICTURE_RESET,
            Feature.PICTURE_RESET_ALL, Feature.HDMI_RANGE, Feature.HDMI_EDID, Feature.PICTURE_ASPECT,
            Feature.SOUND_ENHANCEMENT, Feature.SOUND_MODE, Feature.SOUND_EQ, Feature.SOUND_LEVELING,
            Feature.SOUND_EARC, Feature.SOUND_OUTPUT, Feature.SOUND_FORMAT, Feature.INPUT,
            Feature.BLUETOOTH, Feature.NETWORK, Feature.MIRRORING, Feature.USB_FILES,
            Feature.GAME_MODE, Feature.GAME_SPEED, Feature.GAME_ALLM, Feature.PROJECTION_MODE,
            Feature.PROJECTION_REAR, Feature.SETTINGS_SYSTEM, Feature.SETTINGS_PROJECTOR, Feature.SETTINGS_CEC,
            Feature.SETTINGS_RECENTS, Feature.SETTINGS_LAUNCHER)));

    private final Context app;
    private final SharedPreferences prefs;
    private final Set<String> implemented;
    private volatile Z9xMenuRules.In last;
    /** The equalizer page (Z9xMenuPages) follows every read. */
    private Consumer<Z9xMenuRules.In> eqListener;
    // game mode / speed just set (QPC:1898): shown until the read agrees or the wait is over
    private volatile int pendingGame = -1;
    private volatile long pendingGameUntil, pendingSpeedUntil;
    private volatile GmpfClient.GameModeOption pendingSpeed;
    /** The output list the last read offered (tokens match Z9xMenuRules.In.outTokens). */
    private volatile List<AudioOutputChoice.Option> outOptions;

    public Z9xFeatures(Context c) {
        app = c.getApplicationContext();
        prefs = app.getSharedPreferences(QuickPanel.PREFS, Context.MODE_PRIVATE);
        implemented = FEATURES;
    }

    void setEqListener(Consumer<Z9xMenuRules.In> l) { eqListener = l; }

    Z9xMenuRules.In last() { return last; }

    @Override public Set<String> implemented() { return implemented; }

    @Override public boolean ready() { return Hal.isReady(); }

    @Override public boolean available(String f) {
        switch (f) {
            case Feature.SETTINGS_LAUNCHER: return org.lumen.projector.home.LauncherSwitcher.choiceAvailable(app);
            case Feature.KEYSTONE_FIT_SCREEN: return org.lumen.projector.fit.FitDevices.supported(app);  // QPC:963
            default: return true;
        }
    }

    @Override public boolean setupDone() { return KeyReceiver.isSetupComplete(app); }

    @Override public String footer(String section) {
        return org.lumen.menu.MenuSpec.GENERAL.equals(section) ? Z9xMenuRules.FOOTER_GENERAL : null;   // QPC:1484
    }

    // ================================================================== read

    @Override public void read(Consumer<MenuState> cb) {
        Hal.query(this::readNow, in -> cb.accept(stateOf(in)));
    }

    /** HAL thread: one Snapshot plus the tvinput session and the audio path (QPC:788 refresh). */
    private Z9xMenuRules.In readNow(GmpfClient g, Gmpf2Client g2) {
        Snapshot sn = Snapshot.read(g, g2);
        if (sn == null) return null;
        sn.tifHdmi = TifHdmiState.read(app);
        sn.soundPathActive = AudioPathReporter.isActive(g);
        sn.soundPath = AudioPathReporter.confirmedPath();
        return in(sn);
    }

    /** Main thread: the app stores on top of the read, the state the rows show. */
    private MenuState stateOf(Z9xMenuRules.In in) {
        boolean ok = in != null;
        if (!ok) {                                                   // QPC apply(null): the app's stores are still known
            in = new Z9xMenuRules.In();
            in.failed = true;
        }
        addStores(in);
        last = ok ? in : null;
        if (eqListener != null) {
            try { eqListener.accept(last); } catch (Throwable t) { Log.e(TAG, "equalizer", t); }
        }
        if (ok) AiSoundEngine.noteMode(in.soundEffect, "panel read-back");
        return Z9xMenuRules.state(in, this::text);
    }

    String text(String name, Object... args) {
        int id = app.getResources().getIdentifier(name, "string", app.getPackageName());
        if (id == 0) { Log.w(TAG, "no string " + name); return ""; }
        return args.length == 0 ? app.getString(id) : app.getString(id, args);
    }

    static Z9xMenuRules.In in(Snapshot sn) {
        Z9xMenuRules.In in = new Z9xMenuRules.In();
        in.source = sn.source;
        in.hdr = sn.hdr;
        in.pictureMode = sn.pictureMode;
        in.lamp = sn.lamp;
        in.lumensMode = sn.lumensMode;
        in.memc = sn.memc;
        in.fmt3d = sn.fmt3d;
        in.gameOption = sn.gameOption;
        in.aspect = sn.aspect;
        in.soundEffect = sn.soundEffect;
        in.soundProcess = sn.soundProcess;
        in.putMode = sn.putMode;
        in.hdmiRange = sn.hdmiRange;
        in.edid = sn.edid;
        in.gameMode55 = sn.gameMode55;
        System.arraycopy(sn.pq, 0, in.pq, 0, Math.min(sn.pq.length, in.pq.length));
        in.is3dTo2d = sn.is3dTo2d;
        in.dtsOn = sn.dtsOn;
        in.eye = sn.eye;
        in.curtain = sn.curtain;
        in.obstacle = sn.obstacle;
        in.powerOnAk = sn.powerOnAk;
        in.moveAk = sn.moveAk;
        in.moveAf = sn.moveAf;
        in.powerOnAf = sn.powerOnAf;
        in.smartAf = sn.smartAf;
        in.autoReverse = sn.autoReverse;
        in.boost = sn.boost;
        in.aiContrast = sn.aiContrast;
        in.superRes = sn.superRes;
        in.ultra120 = sn.ultra120;
        in.nativeFps = sn.nativeFps;
        in.volumeBalance = sn.volumeBalance;
        in.earc = sn.earc;
        in.allm = sn.allm;
        in.gameActive55 = sn.gameActive55;
        in.tifHdmi = sn.tifHdmi;
        in.hdmiFpsCenti = sn.hdmiSignal != null && sn.hdmiSignal.plausible() ? sn.hdmiSignal.frameRateCenti : null;
        in.soundPathActive = sn.soundPathActive;
        in.soundPath = sn.soundPath;
        return in;
    }

    /** The app's own stores the 1.0.10 panel read next to the HAL (prefs, pending changes, audio, home screen). */
    private void addStores(Z9xMenuRules.In in) {
        in.storedGameMode = prefs.getInt(KEY_GAME_MODE, -1);
        boolean u120 = prefs.getBoolean(KEY_ULTRA_120, false);
        if (in.ultra120 != null && in.ultra120 != u120) prefs.edit().putBoolean(KEY_ULTRA_120, in.ultra120).apply();   // QPC:2126
        in.storedUltra120 = u120;
        long now = SystemClock.uptimeMillis();
        int pg = pendingGame;
        if (in.hdmiConfirmed() && in.gameMode55 != null) {
            if (pg >= 0 && pg != in.gameMode55 && now < pendingGameUntil) in.pendingGame = pg;
            else pendingGame = -1;
        }
        GmpfClient.GameModeOption ps = pendingSpeed;
        if (in.gameOption != null) {
            if (ps != null && in.gameOption != ps.wire && now < pendingSpeedUntil) in.pendingSpeed = ps.wire;
            else pendingSpeed = null;
        }
        try {
            List<AudioOutputChoice.Option> o = AudioOutputChoice.options(app);
            outOptions = o;
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < o.size(); i++) {
                String tok = outToken(o.get(i), i, seen);
                in.outTokens.add(tok);
                in.outLabels.add(String.valueOf(o.get(i).label));
            }
            in.outSelected = AudioOutputChoice.selectedIndex(app, o);
            in.arcConnected = AudioOutputChoice.arcConnected(app);
            in.formatIndex = in.arcConnected ? AudioOutputChoice.formatIndex(app) : -1;
        } catch (Throwable t) {
            Log.w(TAG, "sound outputs: " + t);
        }
        in.launcherClassic = org.lumen.projector.home.LauncherSwitcher.MODE_CLASSIC.equals(
                org.lumen.projector.home.LauncherSwitcher.currentMode(app));
        GmpfClient.SoundEffect sub = AiSoundEngine.shownSubMode();
        in.aiSubWire = sub == null ? 1 : sub.wire;
        in.eqPresetLabel = Z9xMenuPages.eqPresetLabel(app, in);
    }

    /** Output tokens: auto, speaker, then the kind of each connected device (arc, bt, wired, usb), unique. */
    static String outToken(AudioOutputChoice.Option o, int i, Set<String> seen) {
        String t = o.kind == null ? "out" : o.kind.name().toLowerCase(java.util.Locale.ROOT);   // auto speaker arc bt wired usb
        if (seen.contains(t)) t = t + "_" + i;
        seen.add(t);
        return t;
    }

    // ================================================================== rules

    @Override public Note blocked(String f, String value, MenuState now) {
        if (Feature.EYE.equals(f) && !GmpfClient.nuiReaderReady()) {             // QPC:1935: no NUI fifo reader, no 152
            Log.w(TAG, "152 eye protection refused: " + GmpfClient.NUI_READY_PROP + " != ready");
            return Note.of("eye_unavailable");
        }
        if (Feature.KEYSTONE_RESTORE.equals(f) && (Hal.isKeystoneRunning() || AkOverlay.isActive())) {
            return Note.toast("toast_kst_busy");                                  // QPC:1970
        }
        return Z9xMenuRules.blocked(f, value, last);
    }

    @Override public Confirm confirm(String f, String value, MenuState now) { return Z9xMenuRules.confirm(f, value, last); }

    // ================================================================== write

    /** One HAL task; the result carries the read-back of every value. */
    private interface Write { Result run(GmpfClient g, Gmpf2Client g2) throws Exception; }

    /** QPC:751 change(): the write on the HAL thread, then one read; a throw is "Could not do that". */
    private void write(String what, Write w, Consumer<Result> cb) {
        Hal.query((g, g2) -> {
            Result r;
            try {
                r = w.run(g, g2);
                Log.i(TAG, what + (r == null ? "" : " -> " + r));
            } catch (Throwable t) {
                Log.w(TAG, what + ": " + t);
                r = Result.failed(null);
            }
            return new Object[]{r, readNow(g, g2)};
        }, o -> {
            Object[] a = (Object[]) o;
            Result r = (Result) a[0];
            MenuState after = stateOf((Z9xMenuRules.In) a[1]);
            cb.accept(r == null ? Result.done(after) : r.with(after));
        });
    }

    /** QPC:771 hdmiNow: HDMI 1/2 on screen (696) or an open HDMI TIF session, checked right before the write. */
    private boolean hdmiNow(GmpfClient g, String what) {
        int src = -1;
        try { src = g.getCurrentInputSource(); } catch (Throwable t) { Log.w(TAG, what + ": 696 " + t); }
        if (src == GmpfClient.SOURCE_HDMI1 || src == GmpfClient.SOURCE_HDMI2) return true;
        Boolean tif = TifHdmiState.read(app);
        if (Boolean.TRUE.equals(tif)) return true;
        Log.i(TAG, what + " refused: not on HDMI (696=" + src + ", tvinput session=" + tif + ")");
        return false;
    }

    private static final Result HDMI_ONLY = Result.refusedWith(Note.of("panel_game_hdmi_only"), null);

    private static boolean dolbyVision(GmpfClient g) {
        try { return g.getHdrType() == GmpfClient.HDR_DOLBY_VISION; } catch (Throwable t) { return false; }
    }

    private static boolean threeD(GmpfClient g) throws Exception {
        return g.getCurrent3DFormat() != 1 || g.is3DTo2DEnabled();
    }

    @Override public void set(String f, String v, Consumer<Result> cb) { set(f, v, false, cb); }

    @Override public void setConfirmed(String f, String v, Consumer<Result> cb) { set(f, v, true, cb); }

    private void set(String f, String v, boolean confirmed, Consumer<Result> cb) {
        final boolean on = "on".equals(v);
        if (f.startsWith(Feature.PICTURE_PQ + ".")) {
            GmpfClient.PqItem item = pqItem(f.substring(Feature.PICTURE_PQ.length() + 1));
            final int n = Integer.parseInt(v);
            if (item == null) { cb.accept(Result.failed(null)); return; }
            write("pq " + item + " " + n, (g, g2) -> { g.setPq(item, n); return null; }, cb);
            return;
        }
        switch (f) {
            case Feature.EYE:                                                     // QPC:1944
                write("152 eye protection " + on, (g, g2) -> {
                    if (!g.setEyeProtection(on)) throw new IllegalStateException("152 returned false");
                    return null;
                }, cb);
                return;
            case Feature.KEYSTONE_REALTIME:                                       // QPC:980
                write("585 real-time keystone " + on, (g, g2) ->
                        Hal.setMoveAkChecked(g, on) ? null : Result.done(null, Note.of("toast_rt_kst_ceiling")), cb);
                return;
            case Feature.KEYSTONE_ON_POWER:
                write("552 power-on keystone " + on, (g, g2) -> { g.setPowerOnAk(on); return null; }, cb);
                return;
            case Feature.KEYSTONE_CURTAIN:                                        // fit ON also turns obstacle avoidance on
                write("290 fit to screen " + on, (g, g2) -> { Hal.setCurtainFitCoupled(g, on); return null; }, cb);
                return;
            case Feature.KEYSTONE_OBSTACLE:
                write("292 obstacle avoidance " + on, (g, g2) -> { Hal.setObstacleAvoidCoupled(g, on); return null; }, cb);
                return;
            case Feature.FOCUS_SMART:                                             // QPC:929: 91 and 587 (stock one switch)
                org.lumen.projector.power.StandbyController.noteUserMoveAf();
                write("IGmpf2 91 + 587 smart AF " + on, (g, g2) -> { g2.setSmartAutofocus(on); g.setMoveAf(on); return null; }, cb);
                return;
            case Feature.FOCUS_ON_MOVE:
                org.lumen.projector.power.StandbyController.noteUserMoveAf();
                write("587 AF after moving " + on, (g, g2) -> { g.setMoveAf(on); return null; }, cb);
                return;
            case Feature.FOCUS_ON_POWER:
                write("302 AF at power-on " + on, (g, g2) -> { g.setPowerOnAf(on); return null; }, cb);
                return;
            case Feature.LAMP: {
                final int n = Integer.parseInt(v);
                write("176 lamp " + n, (g, g2) -> { g.setLampLevel(n); return null; }, cb);
                return;
            }
            case Feature.BOOST:                                                   // QPC:1708
                write("IGmpf2 52 boost " + on, (g, g2) -> {
                    if (on && threeD(g)) return Result.refusedWith(Note.of("panel_boost_3d"), null);   // stock: Boost blocked in 3D
                    if (!g2.setBoost(on)) throw new IllegalStateException("52 returned false");
                    return null;
                }, cb);
                return;
            case Feature.PICTURE_MODE: {                                          // QPC:1682
                int w = Z9xMenuRules.pmWire(v);
                GmpfClient.PictureMode m = w < 0 ? null : GmpfClient.PictureMode.fromWire(w);
                if (m == null) break;
                write("667 picture mode " + m, (g, g2) -> {
                    if (!g.setPictureMode(m)) throw new IllegalStateException("667 returned false");
                    if (m == GmpfClient.PictureMode.AI && !dolbyVision(g)) g.setAiScene(GmpfClient.AiScene.FILM);
                    return null;
                }, cb);
                return;
            }
            case Feature.PICTURE_COLOR_TEMP: {                                    // QPC:1058
                int i = Z9xMenuRules.indexOf(Z9xMenuRules.CT, v);
                if (i < 0) break;
                final GmpfClient.ColorTemp c = GmpfClient.ColorTemp.values()[i];
                write("174 color temp " + c, (g, g2) -> { g.setColorTemp(c); return null; }, cb);
                return;
            }
            case Feature.PICTURE_MEMC: {
                int i = Z9xMenuRules.indexOf(Z9xMenuRules.MEMC, v);
                if (i < 0) break;
                final GmpfClient.MemcLevel l = GmpfClient.MemcLevel.values()[i];
                write("669 memc " + l, (g, g2) -> { g.setMemcLevel(l); return null; }, cb);
                return;
            }
            case Feature.PICTURE_AI_CONTRAST:
                write("IGmpf2 288 ai contrast " + on, (g, g2) -> { g2.setAiContrast(on); return null; }, cb);
                return;
            case Feature.PICTURE_SUPER_RES:
                write("IGmpf2 322 super resolution " + on, (g, g2) -> { g2.setSuperResolution(on); return null; }, cb);
                return;
            case Feature.PICTURE_ULTRA120:
                setUltra120(on, cb);
                return;
            case Feature.HDMI_RANGE: {                                            // QPC:1126
                int i = Z9xMenuRules.indexOf(Z9xMenuRules.RANGE, v);
                if (i < 0) break;
                final GmpfClient.HdmiRange r = GmpfClient.HdmiRange.values()[i];
                write("635 hdmi range " + r, (g, g2) -> {
                    if (!hdmiNow(g, "635")) return HDMI_ONLY;
                    if (!g.setHdmiRange(r)) throw new IllegalStateException("635 returned false");
                    return null;
                }, cb);
                return;
            }
            case Feature.HDMI_EDID: {                                             // QPC:1140
                final int wire = PanelRules.edidWire("enhanced".equals(v) ? 1 : 0);
                write("660 edid " + wire, (g, g2) -> {
                    if (!hdmiNow(g, "660")) return HDMI_ONLY;
                    if (!g.setEdidVersion(wire)) throw new IllegalStateException("660 returned false");
                    return Result.done(null, Note.titled("hdmi_mode_note"));
                }, cb);
                return;
            }
            case Feature.PICTURE_ASPECT: {                                        // QPC:1160
                int w = Z9xMenuRules.wireOf(Z9xMenuRules.ASPECT, Z9xMenuRules.ASPECT_WIRE, v);
                GmpfClient.AspectRatio a = null;
                for (GmpfClient.AspectRatio x : GmpfClient.AspectRatio.values()) if (x.wire == w) a = x;
                if (a == null) break;
                final GmpfClient.AspectRatio fa = a;
                write("665 aspect " + a, (g, g2) -> {
                    if (!hdmiNow(g, "665")) return HDMI_ONLY;
                    g.setAspectRatio(fa);
                    return null;
                }, cb);
                return;
            }
            case Feature.SOUND_ENHANCEMENT:
                setSoundProfile(v, cb);
                return;
            case Feature.SOUND_MODE:
                setSoundMode(v, cb);
                return;
            case Feature.SOUND_LEVELING:                                          // QPC:1212
                prefs.edit().putBoolean(SoundProfiles.KEY_VOLUME_LEVELING, on).apply();
                write("42 volume leveling " + on, (g, g2) -> {
                    boolean ok = g.setVolumeBalance(on);
                    SoundEq.afterVendorCall(g, "42(" + on + ")");
                    if (!ok) throw new IllegalStateException("42 returned false");
                    return null;
                }, cb);
                return;
            case Feature.SOUND_EARC:                                              // QPC:1222
                write("633 eARC " + on, (g, g2) -> {
                    if (!org.lumen.projector.cec.EarcLink.setUser(g, on)) throw new IllegalStateException("633 returned false");
                    return null;
                }, cb);
                return;
            case Feature.SOUND_OUTPUT:
                setOutput(v, cb);
                return;
            case Feature.SOUND_FORMAT: {                                          // QPC:1244
                int i = Z9xMenuRules.indexOf(Z9xMenuRules.FORMAT, v);
                AudioOutputChoice.Format[] all = AudioOutputChoice.Format.values();
                if (i < 0 || i >= all.length) break;
                AudioOutputChoice.chooseFormat(app, all[i]);
                Ui.main().post(() -> read(st -> cb.accept(Result.done(st, Note.titled("sound_format_" + v)))));
                return;
            }
            case Feature.GAME_MODE:
                setGameMode(v, cb);
                return;
            case Feature.GAME_SPEED:
                setGameSpeed(v, confirmed, cb);
                return;
            case Feature.GAME_ALLM: {                                             // QPC:1430
                final boolean auto = "signal".equals(v);
                write("IGmpf2 312 allm " + auto, (g, g2) -> {
                    if (!hdmiNow(g, "312")) return HDMI_ONLY;
                    g2.setAllmAutoSwitch(auto);
                    return null;
                }, cb);
                return;
            }
            case Feature.PROJECTION_MODE:
                setProjection(v, cb);
                return;
            case Feature.PROJECTION_REAR: {                                       // QPC:2042
                Z9xMenuRules.In in = last;
                if (in == null || in.putMode == null) { cb.accept(Result.done(null)); return; }
                int target = Z9xMenuRules.rearTarget(in.putMode, on);
                if (target == in.putMode) { cb.accept(Result.done(null)); return; }
                putMode(in.putMode, target, cb);
                return;
            }
            case Feature.SETTINGS_LAUNCHER: {                                     // QPC:1488 (the menu closed first)
                final String mode = "classic".equals(v) ? org.lumen.projector.home.LauncherSwitcher.MODE_CLASSIC
                        : org.lumen.projector.home.LauncherSwitcher.MODE_LUMEN;
                org.lumen.projector.home.LauncherSwitcher.apply(app, mode, true, "quick panel",
                        (ok, holder, err) -> Log.i(TAG, "home screen " + mode + " -> " + ok + " " + holder));
                cb.accept(Result.done(null));
                return;
            }
            default:
                break;
        }
        Log.w(TAG, "set " + f + "=" + v + ": not a Z9X feature or token");
        cb.accept(Result.failed(null));
    }

    private static GmpfClient.PqItem pqItem(String token) {
        for (GmpfClient.PqItem i : GmpfClient.PqItem.values()) if (i.name().toLowerCase(java.util.Locale.ROOT).equals(token)) return i;
        return null;
    }

    /** Ultra 120 Hz, IGmpf 161 setOutputTiming(4 on / 6 off) with the stock checks on the HAL thread (QPC:1747). */
    private void setUltra120(boolean on, Consumer<Result> cb) {
        write("161 output timing " + (on ? 4 : 6), (g, g2) -> {
            if (on) {
                if (threeD(g)) return Result.refusedWith(Note.of("panel_ultra120_3d"), null);
                int src = g.getCurrentInputSource();
                Boolean tif = TifHdmiState.read(app);
                if (src != GmpfClient.SOURCE_MEDIA || !Boolean.FALSE.equals(tif)) {
                    GmpfClient.HdmiSignal sig = null;
                    try { sig = g.getHdmiInfo(); } catch (Throwable t) { Log.d(TAG, "662: " + t); }
                    if (sig == null || !sig.plausible() || sig.frameRateCenti >= 6200) {
                        Log.i(TAG, "161(4) refused: 696=" + src + " tvinput session=" + tif + " 662=" + sig);
                        return Result.refusedWith(Note.of(sig != null && sig.plausible() && sig.frameRateCenti >= 6200
                                ? "panel_ultra120_refused" : "panel_ultra120_hdmi"), null);
                    }
                }
            }
            boolean ok = g.setOutputTiming(on ? GmpfClient.OutputTiming.ULTRA_120_ON : GmpfClient.OutputTiming.ULTRA_120_OFF);
            if (!ok) return Result.refusedWith(Note.of("panel_ultra120_refused"), null);
            boolean now = on;
            try {
                GmpfClient.OutputTimingInfo t = g.getOutputTimingInfo();
                if (t != null) now = t.isUltra120();
            } catch (Throwable t) {
                Log.d(TAG, "157: " + t);
            }
            prefs.edit().putBoolean(KEY_ULTRA_120, now).apply();
            return null;
        }, cb);
    }

    /** QPC:1179 sound enhancement: SoundProfiles.apply and the stock note of what happened. */
    private void setSoundProfile(String v, Consumer<Result> cb) {
        int i = Z9xMenuRules.indexOf(Z9xMenuRules.ENH, v);
        if (i < 0 || i >= SoundProfiles.ORDER.length) { cb.accept(Result.failed(null)); return; }
        final SoundProfiles.Profile prof = SoundProfiles.ORDER[i];
        final String label = text(i == 0 ? "panel_harman" : "panel_dts_vx");
        write("sound profile " + prof, (g, g2) -> {
            SoundProfiles.Result r = SoundProfiles.apply(app, g, prof);
            if (r == SoundProfiles.Result.APPLIED) return Result.done(null, Note.text(label, true));
            if (r == SoundProfiles.Result.SPEAKER_ONLY) return Result.done(null, Note.text(text("sound_speaker_only", label), true));
            if (r == SoundProfiles.Result.OUTPUT_NOT_READY) return Result.done(null, Note.text(text("sound_output_not_ready", label), true));
            Log.w(TAG, "sound profile " + prof + ": read-back differs");
            return Result.done(null, Note.titled("sound_not_confirmed"));
        }, cb);
    }

    /** QPC:1386 sound mode: AI follows the foreground app, the others are the amp tables; the curve is rewritten. */
    private void setSoundMode(String v, Consumer<Result> cb) {
        int w = Z9xMenuRules.soundWire(v);
        final GmpfClient.SoundEffect e = w < 0 ? null : GmpfClient.SoundEffect.fromWire(w);
        if (e == null) { cb.accept(Result.failed(null)); return; }
        final String title = text("panel_sound_mode");
        write("61 sound " + e, (g, g2) -> {
            if (e == GmpfClient.SoundEffect.AI) {
                AiSoundEngine.userChoseAi(g);
            } else {
                g.setSoundEffect(e);
                AiSoundEngine.noteMode(e.wire, "user");
            }
            SoundProfiles.rememberMode(app, e);
            SoundEq.afterUserModeChange(g, "61(" + e.wire + ")");
            String label = e == GmpfClient.SoundEffect.AI
                    ? text("sound_ai_sub", text(Z9xMenuRules.soundLabelRes(AiSoundEngine.shownSubMode() == null ? 1
                    : AiSoundEngine.shownSubMode().wire))) : text(Z9xMenuRules.soundLabelRes(e.wire));
            return Result.done(null, Note.text(title + ": " + label, false));
        }, cb);
    }

    /** QPC:1230 sound output: the option of the list that was shown, a preferred device of the media strategy. */
    private void setOutput(String token, Consumer<Result> cb) {
        Z9xMenuRules.In in = last;
        List<AudioOutputChoice.Option> o = outOptions;
        int i = in == null ? -1 : in.outTokens.indexOf(token);
        if (o == null || i < 0 || i >= o.size()) { cb.accept(Result.done(null)); return; }
        AudioOutputChoice.Option opt = o.get(i);
        AudioOutputChoice.choose(app, opt.kind, opt.addr);
        final CharSequence label = opt.label;
        Ui.main().post(() -> read(st -> cb.accept(Result.done(st, Note.text(label, true)))));
    }

    /** QPC:1802 game mode: 0 off = 56(0) + 57(1); 1 on = 648(current) + 56(0) + 57(0); 2 auto = 56(1). */
    private void setGameMode(String v, Consumer<Result> cb) {
        final int i = Z9xMenuRules.indexOf(Z9xMenuRules.GAME, v);
        if (i < 0) { cb.accept(Result.failed(null)); return; }
        write("game mode " + i, (g, g2) -> {
            if (i != 0) {                                                         // stock order: Dolby Vision, 3D, then HDMI
                if (g.getHdrType() == GmpfClient.HDR_DOLBY_VISION) return Result.refusedWith(Note.titledBy("panel_game_mode", "game_blocked_dv"), null);
                if (threeD(g)) return Result.refusedWith(Note.titledBy("panel_game_mode", "panel_boost_3d"), null);
            }
            if (!hdmiNow(g, "game mode")) return HDMI_ONLY;
            boolean ok;
            if (i == 0) {
                ok = g2.setGameModeType(Gmpf2Client.GameModeType.MANUAL);
                ok &= g2.setGameModeState(false);
            } else if (i == 1) {
                GmpfClient.GameModeOption o = g.getGameModeOption();
                if (o != null) g.setGameModeOption(o);
                ok = g2.setGameModeType(Gmpf2Client.GameModeType.MANUAL);
                ok &= g2.setGameModeState(true);
            } else {
                ok = g2.setGameModeType(Gmpf2Client.GameModeType.AUTO);
            }
            if (!ok) throw new IllegalStateException("56 / 57 returned false");
            prefs.edit().putInt(KEY_GAME_MODE, i).apply();
            pendingGame = i;
            pendingGameUntil = SystemClock.uptimeMillis() + PENDING_SPEED_MS;
            return null;
        }, cb);
    }

    /**
     * QPC:1847 game speed: top speed asks first when the keystone is not the full frame (stock), then 648 and, for top
     * speed, 207 once 647 reads it.
     */
    private void setGameSpeed(String v, boolean confirmed, Consumer<Result> cb) {
        int w = Z9xMenuRules.wireOf(Z9xMenuRules.SPEED, Z9xMenuRules.SPEED_WIRE, v);
        final GmpfClient.GameModeOption o = w < 0 ? null : GmpfClient.GameModeOption.fromWire(w);
        Z9xMenuRules.In in = last;
        if (o == null || in == null) { cb.accept(Result.done(null)); return; }
        if (confirmed || !o.isTopSpeed() || in.isTopSpeedGame()) {
            applyGameSpeed(o, cb);
            return;
        }
        Hal.query((g, g2) -> isFullFrame(g.getKeystonePoints()), full -> {
            if (Boolean.TRUE.equals(full)) applyGameSpeed(o, cb);
            else cb.accept(Result.ask(new Confirm("panel_speed_top", "game_top_speed_confirm", "panel_switch"), null));
        });
    }

    private void applyGameSpeed(GmpfClient.GameModeOption o, Consumer<Result> cb) {
        Z9xMenuRules.In in = last;
        final boolean modeOn = in != null && Z9xMenuRules.shownGameMode(in) == 1;  // shown mode On (QPC:1870)
        write("648 game option " + o, (g, g2) -> {
            if (!hdmiNow(g, "648")) return HDMI_ONLY;                            // 207 below would reset the normal picture
            boolean r = g.setGameModeOption(o);
            if (PanelRules.resendGameState(r, modeOn)) {
                try { g2.getGameModeProp(); } catch (Throwable t) { Log.d(TAG, "g2 55 before 57: " + t); }
                boolean r57 = false;
                try { r57 = g2.setGameModeState(true); } catch (Throwable t) { Log.w(TAG, "g2 57(0): " + t); }
                Log.i(TAG, "57(0) after 648 -> " + r57);
            }
            GmpfClient.GameModeOption now = g.getGameModeOption();
            Log.i(TAG, "648(" + o + ") -> " + r + ", 647 reads " + now);
            if (!r && now != o) {
                now = waitGameOption(g, o);
                if (now != o) throw new IllegalStateException("648 -> false, 647 reads " + now);
            }
            pendingSpeed = o;
            pendingSpeedUntil = SystemClock.uptimeMillis() + PENDING_SPEED_MS;
            if (o.isTopSpeed()) topSpeedKeystone(o, SystemClock.uptimeMillis() + PENDING_SPEED_MS);
            return null;
        }, cb);
    }

    private static GmpfClient.GameModeOption waitGameOption(GmpfClient g, GmpfClient.GameModeOption want) {
        long end = SystemClock.uptimeMillis() + GAME_OPTION_WAIT_MS;
        GmpfClient.GameModeOption now = null;
        while (true) {
            try { now = g.getGameModeOption(); } catch (Throwable t) { Log.w(TAG, "647: " + t); }
            if (now == want || SystemClock.uptimeMillis() >= end) return now;
            SystemClock.sleep(GAME_OPTION_POLL_MS);
        }
    }

    /** 207 for top speed once 647 reads it (re-polled every 300 ms on the HAL thread, never blocking it). */
    private void topSpeedKeystone(GmpfClient.GameModeOption o, long deadline) {
        Hal.runDelayed((g, g2) -> {
            GmpfClient.GameModeOption now = g.getGameModeOption();
            if (now == o) {
                g.resetKeystoneForTopSpeedGame();
                Log.i(TAG, "207 after 647 reads " + o);
                Ui.main().post(() -> org.lumen.projector.ui.Notify.show(app, text("panel_speed_top_note")));
            } else if (SystemClock.uptimeMillis() < deadline) {
                topSpeedKeystone(o, deadline);
            } else {
                Log.w(TAG, "207 skipped: 647 still reads " + now + " instead of " + o);
            }
        }, 300);
    }

    /** 186 corners within a few panel px of the full frame. */
    static boolean isFullFrame(KstPoint p) {
        if (p == null) return false;
        KstPoint f = KstPoint.fullFrame();
        for (int c = KstPoint.TL; c <= KstPoint.BL; c++) {
            if (Math.abs(p.x(c) - f.x(c)) > FULL_FRAME_TOLERANCE || Math.abs(p.y(c) - f.y(c)) > FULL_FRAME_TOLERANCE) return false;
        }
        return true;
    }

    /** QPC:2022 projection: Auto = 580 on; Table / Ceiling keep front / rear; the same put mode only turns Auto off. */
    private void setProjection(String v, Consumer<Result> cb) {
        Z9xMenuRules.In in = last;
        if (in == null || in.putMode == null || in.autoReverse == null) { cb.accept(Result.done(null)); return; }
        if ("auto".equals(v)) {
            if (in.autoReverse) { cb.accept(Result.done(null)); return; }
            write("580 auto flip on", (g, g2) -> { g.setAutoReverse(true); return null; }, cb);
            return;
        }
        int target = Z9xMenuRules.projectionTarget(in.putMode, v);
        if (target == in.putMode) {
            if (in.autoReverse) write("580 auto flip off", (g, g2) -> { g.setAutoReverse(false); return null; }, cb);
            else cb.accept(Result.done(null));
            return;
        }
        putMode(in.putMode, target, cb);
    }

    /** QPC:2058: 580 off, 172, and to ceiling the push-pull screen off (stock). */
    private void putMode(int cur, int target, Consumer<Result> cb) {
        final GmpfClient.PutMode m = GmpfClient.PutMode.fromWire(target);
        if (m == null) { cb.accept(Result.failed(null)); return; }
        final boolean toCeiling = Z9xMenuRules.toCeiling(cur, target);
        write("172 put mode " + m, (g, g2) -> {
            g.setAutoReverse(false);
            g.setPutMode(m);
            if (toCeiling) {
                try { g2.disablePushPullScreenForCeiling(); } catch (Throwable t) { Log.w(TAG, "IGmpf2 201: " + t); }
            }
            return null;
        }, cb);
    }

    // ================================================================== actions and screens

    @Override public void run(String a, Consumer<Result> cb) {
        switch (a) {
            case Feature.KEYSTONE_AK_NOW: Hal.requestKeystone(app, false); cb.accept(Result.done(null)); return;
            case Feature.KEYSTONE_FIT_NOW: Hal.requestKeystone(app, true); cb.accept(Result.done(null)); return;
            case Feature.FOCUS_AF_NOW: Hal.requestAutofocus(app); cb.accept(Result.done(null)); return;
            case Feature.PICTURE_RESET:                                           // QPC:1107
                write("IGmpf2 278 reset basic", (g, g2) -> { g2.resetPicture(Gmpf2Client.PictureReset.BASIC); return null; }, cb);
                return;
            case Feature.PICTURE_RESET_ALL:                                       // QPC:1115
                write("IGmpf2 278 reset all", (g, g2) -> {
                    if (!g2.resetPicture(Gmpf2Client.PictureReset.ALL)) throw new IllegalStateException("278 returned false");
                    return null;
                }, cb);
                return;
            case Feature.KEYSTONE_RESTORE:
                resetKeystone(cb);
                return;
            default:
                Log.w(TAG, "run " + a + ": not a Z9X action");
                cb.accept(Result.failed(null));
        }
    }

    /** QPC:1969 keystone reset, the stock sequence with the lens home and the full-frame safety net. */
    private void resetKeystone(Consumer<Result> cb) {
        write("keystone reset (stock)", (g, g2) -> {
            if (Hal.isKeystoneRunning() || AkOverlay.isActive()) return Result.refusedWith(Note.of("toast_kst_busy"), null);
            boolean paused = ManualKeystonePanel.pauseRealtime(app, g, "panel reset");
            try {
                int r181 = g.resetKeystoneStock();                                // 207, 130 (129), 181(50)
                boolean r46 = false;
                int r153 = Integer.MIN_VALUE;
                try {
                    r153 = g.zoomHome();                                          // 153(5,10), stock order: before 46
                    OpticalZoomPanel.onLensHome(app);
                } catch (Throwable t) { Log.w(TAG, "153(5,10): " + t); }
                try { r46 = g2.resetKeystoneRatio(); } catch (Throwable t) { Log.w(TAG, "IGmpf2 46: " + t); }
                Log.i(TAG, "reset: 207/130/181(50) -> " + r181 + ", 153(5,10) -> " + r153 + ", 46(3) -> " + r46);
                KstPoint after = null;
                try { after = g.getKeystonePoints(); } catch (Throwable t) { Log.w(TAG, "186 after reset: " + t); }
                if (after == null || !isFullFrame(after)) {
                    Log.w(TAG, "reset: 186 reads " + after + " after the stock sequence: full frame through 150 -> 185");
                    KstPoint full = KstPoint.fullFrame();
                    if (!g.checkKeystonePoints(full)) throw new IllegalStateException("150 rejected the full frame");
                    Log.i(TAG, "185 full frame -> " + g.applyKeystonePoints(full));
                }
                org.lumen.projector.game.GameProfile.noteKeystoneFullFrame(app, "quick panel reset");
                try { g.regenerateBootLogo(); } catch (Throwable t) { Log.w(TAG, "158: " + t); }
                return Result.done(null, Note.of("kst_restore_done"));
            } finally {
                if (paused) ManualKeystonePanel.resumeRealtime(app, g, "panel reset");
            }
        }, cb);
    }

    @Override public boolean open(String s, Runnable back) {
        try {
            switch (s) {
                case Feature.KEYSTONE_MANUAL: ManualKeystonePanel.open(app, back); return true;
                case Feature.ZOOM: OpticalZoomPanel.open(app, back); return true;
                case Feature.KEYSTONE_ROTATION: RotatePanel.open(app, back); return true;
                case Feature.FOCUS_MANUAL: Hal.openManualFocus(app); return true;
                case Feature.SETTINGS_PROJECTOR: Hal.openProjectorSettings(app, null); return true;
                case Feature.SETTINGS_RECENTS: org.lumen.projector.recents.RecentsActivity.open(app); return true;
                case Feature.SETTINGS_SYSTEM:
                    Ui.wakeFromDream(app);
                    app.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return true;
                default:
                    return false;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "open " + s + ": " + e);
            Ui.toast(app, org.lumen.projector.R.string.lumen_m_toast_failed);
            return false;
        }
    }
}
