// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.projector.Hal;
import org.lumen.projector.KeyReceiver;
import org.lumen.projector.Ui;
import org.lumen.projector.ak.AkOverlay;
import org.lumen.projector.audio.AiSoundEngine;
import org.lumen.projector.audio.AudioOutputChoice;
import org.lumen.projector.audio.AudioPathReporter;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.SoundEffect;
import org.lumen.projector.source.TifHdmiState;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.lumen.menu.utils.MenuValues.uniqueToken;

/**
 * The Z9X adapter of the shared menu: every feature of
 * devices/horse_popular/menu.json with the same z9x-hal calls, side effects and stock notes as the Z9X 1.0.10 quick
 * panel (QuickPanelController of release/z9x-1.0.10, cited QPC:line). What the rows show, the refusals before a
 * press and the stock questions are the plain-Java {@link Z9xMenuRules}; this class reads the projector into
 * {@link Z9xMenuRules.In} and writes. Device pages (input list, expert picture, HDMI-CEC, fit, equalizer) are
 * {@link Z9xMenuPages}.
 */
public final class Z9xFeatures implements ProjectorFeatures {
    static final String TAG = "Z9xFeatures";
    static final String KEY_GAME_MODE = QuickPanel.KEY_GAME_MODE;
    static final String KEY_ULTRA_120 = QuickPanelController.KEY_ULTRA_120;

    /** Every feature of devices/horse_popular/menu.json; the 1.0.10 quick panel had each of them. */
    static final Set<FeatureId> FEATURES = Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(
            FeatureId.EYE, FeatureId.KEYSTONE_AK_NOW, FeatureId.KEYSTONE_FIT_NOW, FeatureId.KEYSTONE_MANUAL,
            FeatureId.ZOOM, FeatureId.KEYSTONE_FIT_SCREEN, FeatureId.KEYSTONE_ROTATION, FeatureId.KEYSTONE_REALTIME,
            FeatureId.KEYSTONE_ON_POWER, FeatureId.KEYSTONE_CURTAIN, FeatureId.KEYSTONE_OBSTACLE, FeatureId.KEYSTONE_RESTORE,
            FeatureId.FOCUS_AF_NOW, FeatureId.FOCUS_MANUAL, FeatureId.FOCUS_SMART, FeatureId.FOCUS_ON_MOVE,
            FeatureId.FOCUS_ON_POWER, FeatureId.LAMP, FeatureId.BOOST, FeatureId.PICTURE_MODE,
            FeatureId.PICTURE_PQ, FeatureId.PICTURE_COLOR_TEMP, FeatureId.PICTURE_MEMC, FeatureId.PICTURE_AI_CONTRAST,
            FeatureId.PICTURE_SUPER_RES, FeatureId.PICTURE_ULTRA120, FeatureId.PICTURE_PRO, FeatureId.PICTURE_RESET,
            FeatureId.PICTURE_RESET_ALL, FeatureId.HDMI_RANGE, FeatureId.HDMI_EDID, FeatureId.PICTURE_ASPECT,
            FeatureId.SOUND_ENHANCEMENT, FeatureId.SOUND_MODE, FeatureId.SOUND_EQ, FeatureId.SOUND_LEVELING,
            FeatureId.SOUND_EARC, FeatureId.SOUND_OUTPUT, FeatureId.SOUND_FORMAT, FeatureId.INPUT,
            FeatureId.BLUETOOTH, FeatureId.NETWORK, FeatureId.MIRRORING, FeatureId.USB_FILES,
            FeatureId.GAME_MODE, FeatureId.GAME_SPEED, FeatureId.GAME_ALLM, FeatureId.PROJECTION_MODE,
            FeatureId.PROJECTION_REAR, FeatureId.SETTINGS_SYSTEM, FeatureId.SETTINGS_PROJECTOR, FeatureId.SETTINGS_CEC,
            FeatureId.SETTINGS_RECENTS, FeatureId.SETTINGS_LAUNCHER, FeatureId.LAMP_OFF)));

    final Context app;
    final SharedPreferences prefs;
    private final Set<FeatureId> implemented;
    private volatile Z9xMenuRules.In last;
    /** The equalizer page (Z9xMenuPages) follows every read. */
    private Consumer<Z9xMenuRules.In> eqListener;
    /** The output list the last read offered (tokens match Z9xMenuRules.In.outTokens). */
    private volatile List<AudioOutputChoice.Option> outOptions;
    private final Z9xGameRows game;
    /** The sections a write or an action goes to; each answers false for a feature that is not its own. */
    private final Z9xRows[] sections;

    public Z9xFeatures(Context c) {
        app = c.getApplicationContext();
        prefs = app.getSharedPreferences(QuickPanel.PREFS, Context.MODE_PRIVATE);
        implemented = FEATURES;
        game = new Z9xGameRows(this);
        sections = new Z9xRows[]{new Z9xGeneralRows(this), new Z9xKeystoneRows(this), new Z9xFocusRows(this),
                new Z9xPictureRows(this), new Z9xHdmiRows(this), new Z9xSoundRows(this), game, new Z9xProjectionRows(this)};
    }

    void setEqListener(Consumer<Z9xMenuRules.In> l) { eqListener = l; }

    Z9xMenuRules.In last() { return last; }

    List<AudioOutputChoice.Option> outOptions() { return outOptions; }

    @Override public Set<FeatureId> implemented() { return implemented; }

    @Override public boolean ready() { return Hal.isReady(); }

    @Override public boolean available(FeatureId f) {
        switch (f) {
            case SETTINGS_LAUNCHER: return org.lumen.projector.home.LauncherSwitcher.choiceAvailable(app);
            case KEYSTONE_FIT_SCREEN: return org.lumen.projector.fit.FitDevices.supported(app);  // QPC:963
            default: return true;
        }
    }

    @Override public boolean setupDone() { return KeyReceiver.isSetupComplete(app); }

    @Override public String footer(String section) {
        return MenuSpec.GENERAL.equals(section) ? Z9xMenuRules.FOOTER_GENERAL : null;   // QPC:1484
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
        game.addPending(in);
        try {
            List<AudioOutputChoice.Option> o = AudioOutputChoice.options(app);
            outOptions = o;
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < o.size(); i++) {
                String tok = uniqueToken(outKind(o.get(i)), i, seen);
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
        SoundEffect sub = AiSoundEngine.shownSubMode();
        in.aiSubWire = sub == null ? 1 : sub.wire;
        in.eqPresetLabel = Z9xMenuPages.eqPresetLabel(app, in);
    }

    /** Output tokens: auto, speaker, then the kind of each connected device (arc, bt, wired, usb), unique. */
    private static String outKind(AudioOutputChoice.Option o) {
        return o.kind == null ? "out" : o.kind.name().toLowerCase(java.util.Locale.ROOT);   // auto speaker arc bt wired usb
    }

    // ================================================================== rules

    @Override public Note blocked(FeatureId f, String value, MenuState now) {
        if (FeatureId.EYE == f && !GmpfClient.nuiReaderReady()) {             // QPC:1935: no NUI fifo reader, no 152
            Log.w(TAG, "152 eye protection refused: " + GmpfClient.NUI_READY_PROP + " != ready");
            return Note.of("eye_unavailable");
        }
        if (FeatureId.KEYSTONE_RESTORE == f && (Hal.isKeystoneRunning() || AkOverlay.isActive())) {
            return Note.toast("toast_kst_busy");                                  // QPC:1970
        }
        return Z9xMenuRules.blocked(f, value, last);
    }

    @Override public Note blockedPage(String section, MenuState now) {
        return MenuSpec.GAME.equals(section) ? Z9xMenuRules.gamePage(last) : ProjectorFeatures.super.blockedPage(section, now);
    }

    @Override public Confirm confirm(FeatureId f, String value, MenuState now) { return Z9xMenuRules.confirm(f, value, last); }

    // ================================================================== write

    /** One HAL task; the result carries the read-back of every value. */
    interface Write { Result run(GmpfClient g, Gmpf2Client g2) throws Exception; }

    /** QPC:751 change(): the write on the HAL thread, then one read; a throw is "Could not do that". */
    void write(String what, Write w, Consumer<Result> cb) {
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

    @Override public void set(FeatureId f, String v, Consumer<Result> cb) { set(f, v, false, cb); }

    @Override public void setConfirmed(FeatureId f, String v, Consumer<Result> cb) { set(f, v, true, cb); }

    private void set(FeatureId f, String v, boolean confirmed, Consumer<Result> cb) {
        for (Z9xRows s : sections) if (s.set(f, v, confirmed, cb)) return;
        Log.w(TAG, "set " + f + "=" + v + ": not a Z9X feature or token");
        cb.accept(Result.failed(null));
    }

    // ================================================================== actions and screens

    @Override public void run(FeatureId a, Consumer<Result> cb) {
        for (Z9xRows s : sections) if (s.run(a, cb)) return;
        Log.w(TAG, "run " + a + ": not a Z9X action");
        cb.accept(Result.failed(null));
    }

    @Override public boolean open(FeatureId s, Runnable back) {
        try {
            switch (s) {
                case KEYSTONE_MANUAL: ManualKeystonePanel.open(app, back); return true;
                case ZOOM: OpticalZoomPanel.open(app, back); return true;
                case KEYSTONE_ROTATION: RotatePanel.open(app, back); return true;
                case FOCUS_MANUAL: Hal.openManualFocus(app); return true;
                case SETTINGS_PROJECTOR: Hal.openProjectorSettings(app, null); return true;
                case SETTINGS_RECENTS: org.lumen.projector.recents.RecentsActivity.open(app); return true;
                case SETTINGS_SYSTEM:
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