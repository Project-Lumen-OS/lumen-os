package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.FeatureKind;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.projector.home.LauncherSwitcher;
import org.lumen.projector.Device;
import org.lumen.projector.Hal;
import org.lumen.projector.KeyReceiver;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.audio.AudioOutputChoice;
import org.lumen.projector.game.GameMode;
import org.lumen.projector.hal.Play6Gmpf2;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Values;
import org.lumen.projector.hal.PqItem;
import org.lumen.projector.source.SourceOverlay;

import java.util.Set;
import java.util.function.Consumer;

/**
 * XGIMI Play 6 (dragon_healthy): the device adapter of the shared projector menu (apps/core/lumen-menu
 * sections 4 and 10; profile devices/dragon_healthy/menu.json; facts the design notes). It
 * replaces the old Play 6 quick panel (Play6PanelController) and keeps its calls and rules: every read and write
 * goes through the Play 6 HIDL facade (Hal.query on p6-hal: IGmpf / IGmpf2 through Play6Gmpf / Play6Gmpf2, the start
 * gate, the IGmpf2 identity guard all or nothing), no HAL code the old panel did not send.
 *  - values: Play6Snapshot (one read of every part the menu shows), mapped by Play6MenuGates.state: a row the
 *    current picture mode or input does not have is hidden (rule: never a value of another mode);
 *  - before a press: Play6MenuGates.blocked (the game page off HDMI, the picture mode in Game) and .confirm (the
 *    stock questions: Performance heat warning, picture flip, HDMI mode, keystone restore, picture reset, home
 *    screen, power saving the first time); the module asks them inside the menu;
 *  - writes: refused without a call where the stock tree has no such row now (Play6MenuGates.refusal), every mode
 *    or input bound setter reads the mode or IGmpf 696 again on p6-hal right before the call, then everything is
 *    read back.
 * Values cross as the profile's tokens (Play6MenuGates tables); the menu never sees a code.
 */
public final class Play6Features implements ProjectorFeatures {
    static final String TAG = "P6Menu";
    /** Every part the menu shows (KEYSTONE adds only the zoom / rotation probes: their screens check themselves). */
    private static final int PARTS = Play6Snapshot.ROOT | Play6Snapshot.FOCUS | Play6Snapshot.LAMP
            | Play6Snapshot.PICTURE | Play6Snapshot.SOUND | Play6Snapshot.PROJECTION | Play6Snapshot.GAME
            | Play6Snapshot.EYE;
    /** boolean: the user confirmed turning power saving on (off) once; stock asks the first time only. */
    private static final String KEY_ECO_ON_CONFIRMED = "eco_on_confirmed", KEY_ECO_OFF_CONFIRMED = "eco_off_confirmed";
    private static final String HOME_PKG = "org.lumen.home", HOME_FILES = "org.lumen.home.FilesLauncher";

    final Context app;
    final SharedPreferences prefs;
    private final boolean eyeReady;
    /** Last read (main thread). */
    private volatile Play6MenuGates.Read last;
    private final Play6GeneralRows general;
    private final Play6PictureRows picture;
    private final Play6HdmiRows hdmi;
    private final Play6SoundRows sound;
    private final Play6ProjectionRows projection;
    /** The sections a write or an action goes to; each answers {@link Play6Rows#OTHER} / false for another's feature. */
    private final Play6Rows[] sections;

    public Play6Features(Context ctx, boolean eyeReady) {
        app = ctx.getApplicationContext();
        prefs = app.getSharedPreferences(QuickPanel.PREFS, Context.MODE_PRIVATE);
        this.eyeReady = eyeReady;
        general = new Play6GeneralRows(this);
        picture = new Play6PictureRows(this);
        hdmi = new Play6HdmiRows(this);
        sound = new Play6SoundRows(this);
        projection = new Play6ProjectionRows(this);
        sections = new Play6Rows[]{general, new Play6KeystoneRows(this), new Play6FocusRows(this), picture, hdmi, sound,
                new Play6GameRows(this), projection};
    }

    Play6MenuGates.Read last() { return last; }

    // ================================================================== presence and the v3 hooks

    @Override
    public Set<FeatureId> implemented() { return Play6MenuGates.implemented(); }

    @Override
    public boolean ready() { return Hal.isReady(); }

    @Override
    public boolean available(FeatureId feature) {
        boolean launcher = false;
        if (FeatureId.SETTINGS_LAUNCHER == feature) {
            try {
                launcher = LauncherSwitcher.choiceAvailable(app);
            } catch (Throwable t) {
                Log.w(TAG, "launcher: " + t);
            }
        }
        return Play6MenuGates.available(feature, eyeReady, launcher, Device.hasBattery());
    }

    @Override
    public boolean setupDone() { return KeyReceiver.isSetupComplete(app); }

    @Override
    public Note blocked(FeatureId feature, String value, MenuState now) { return Play6MenuGates.blocked(feature, value, last); }

    @Override
    public Note blockedPage(String section, MenuState now) {
        return MenuSpec.GAME.equals(section) ? Play6MenuGates.gamePage(last) : ProjectorFeatures.super.blockedPage(section, now);
    }

    @Override
    public Confirm confirm(FeatureId feature, String value, MenuState now) {
        return Play6MenuGates.confirm(feature, value, last, prefs.getBoolean(KEY_ECO_ON_CONFIRMED, false),
                prefs.getBoolean(KEY_ECO_OFF_CONFIRMED, false));
    }

    @Override
    public void setConfirmed(FeatureId feature, String value, Consumer<Result> cb) {
        // power saving: stock asks the first time each way only
        if (FeatureId.LAMP_ECO == feature) {
            prefs.edit().putBoolean("on".equals(value) ? KEY_ECO_ON_CONFIRMED : KEY_ECO_OFF_CONFIRMED, true).apply();
        }
        set(feature, value, cb);
    }

    // ================================================================== read

    @Override
    public void read(Consumer<MenuState> cb) {
        Hal.query((g, g2) -> Play6Snapshot.read(g, g2, PARTS), sn -> {
            Play6MenuGates.Read r = sn == null || !Hal.isReady() ? null : convert(sn);
            last = r;
            cb.accept(state());
        });
    }

    /** Main thread: the plain values of a snapshot, plus the Android-side ones (outputs, home screen). */
    private Play6MenuGates.Read convert(Play6Snapshot sn) {
        Play6MenuGates.Read r = new Play6MenuGates.Read();
        r.source = sn.source;
        r.pictureMode = sn.pictureMode;
        r.lamp = sn.lamp;
        r.eco = sn.eco;
        r.soundEffect = sn.soundEffect;
        r.putMode = sn.putMode;
        r.autoReverse = sn.autoReverse;
        r.eye = sn.eye;
        r.powerOnAk = sn.powerOnAk;
        r.moveAk = sn.moveAk;
        r.smartAf = sn.smartAf;
        r.memc = sn.memc;
        r.colorTemp = sn.colorTemp;
        r.dynContrast = sn.dynContrast;
        System.arraycopy(sn.pq, 0, r.pq, 0, r.pq.length);
        r.aspect = sn.aspect;
        r.hdmiInfo = sn.hdmiInfo;
        r.hdmiRange = sn.hdmiRange;
        r.edid = sn.edid;
        r.earc = sn.earc;
        r.gmpf2 = sn.gmpf2 && Hal.hasGmpf2();
        r.gameMode = sn.gameProp == null ? -1 : GameMode.modeOf(sn.gameProp);
        r.eqOk = sn.eqOk;
        try {
            r.outputKind = AudioOutputChoice.kind(app).name();
            java.util.List<String> kinds = new java.util.ArrayList<>();
            for (AudioOutputChoice.Option o : AudioOutputChoice.options(app)) {
                kinds.add(o.kind.name());
                if (o.label != null && o.label.length() > 0) r.outputLabels.put(o.kind.name(), o.label.toString());
            }
            r.outputKinds = kinds;
        } catch (Throwable t) {
            Log.w(TAG, "output: " + t);
        }
        try {
            if (LauncherSwitcher.choiceAvailable(app)) {
                r.launcher = LauncherSwitcher.MODE_CLASSIC.equals(LauncherSwitcher.currentMode(app)) ? "classic" : "lumen";
            }
        } catch (Throwable t) {
            Log.w(TAG, "launcher: " + t);
        }
        return r;
    }

    MenuState state() { return Play6MenuGates.state(last, this::text); }

    /** A string of the app by resource name (the name itself when it does not exist: never a crash). */
    private String text(String name) {
        int id = app.getResources().getIdentifier(name, "string", app.getPackageName());
        return id == 0 ? name : app.getString(id);
    }

    // ================================================================== set

    @Override
    public void set(FeatureId feature, String value, Consumer<Result> cb) {
        if (!implemented().contains(feature) && feature.parent != FeatureId.PICTURE_PQ) { refuse(cb, null); return; }
        if (!Hal.isReady()) { Ui.main().post(() -> cb.accept(Result.notReady())); return; }
        Play6MenuGates.Read r = last;
        if (r == null) { refuse(cb, "toast_not_ready"); return; }
        String no = Play6MenuGates.refusal(feature, r);
        if (no != null) { refuse(cb, no.isEmpty() ? null : no); return; }
        boolean on = "on".equals(value);
        switch (feature) {
            case SOUND_OUTPUT: sound.setOutput(value, cb); return;
            case SETTINGS_LAUNCHER: general.setLauncher(value, cb); return;
            case LAMP_ECO: general.setEco(on, cb); return;
            case PICTURE_MODE: picture.setPictureMode(value, r, cb); return;
            case PROJECTION_MODE: projection.setProjection(value, r, cb); return;
            case PROJECTION_REAR: projection.setRear(on, r, cb); return;
            case HDMI_EDID: hdmi.setEdid(value, r, cb); return;
            default: break;
        }
        write(feature + "=" + value, cb, (g, g2) -> writeHal(feature, value, on, g, g2));
    }

    /** A write on p6-hal: null = done, else the refusal note (string resource name, "" = no note). */
    interface Write {
        String run(Play6Gmpf g, Play6Gmpf2 g2) throws Exception;
    }

    /** Runs {@code w} on p6-hal, then reads everything back; the result on the main thread. */
    void write(String what, Consumer<Result> cb, Write w) {
        Hal.query((g, g2) -> {
            Object[] out = new Object[2];
            try {
                out[0] = w.run(g, g2);
                Log.i(TAG, what + (out[0] == null ? "" : ": refused " + out[0]));
            } catch (Throwable t) {
                Log.w(TAG, what + ": " + t);
                out[0] = t;
            }
            out[1] = Play6Snapshot.read(g, g2, PARTS);
            return out;
        }, out -> {
            if (out == null) { cb.accept(Result.notReady()); return; }
            last = convert((Play6Snapshot) out[1]);
            MenuState after = state();
            if (out[0] == null) cb.accept(Result.done(after));
            else if ("".equals(out[0])) cb.accept(Result.refused(null, after));
            else if (out[0] instanceof String) cb.accept(Result.refused((String) out[0], after));
            else cb.accept(Result.failed(after));
        });
    }

    void refuse(Consumer<Result> cb, String reason) {
        Ui.main().post(() -> cb.accept(Result.refused(reason, state())));
    }

    /** p6-hal: one feature write, by the section whose feature it is. */
    private String writeHal(FeatureId feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2) throws Exception {
        if (feature.kind == FeatureKind.TOGGLE
                && !on && !"off".equals(value)) throw new IllegalArgumentException(feature + " " + value);
        if (feature.parent == FeatureId.PICTURE_PQ) return picture.setPq(feature.item, value, g);
        for (Play6Rows s : sections) {
            String r = s.writeHal(feature, value, on, g, g2);
            if (r != Play6Rows.OTHER) return r;
        }
        throw new IllegalArgumentException("no writer for " + feature);
    }

    // ================================================================== run

    @Override
    public void run(FeatureId action, Consumer<Result> cb) {
        if (!implemented().contains(action)) { refuse(cb, null); return; }
        for (Play6Rows s : sections) if (s.run(action, cb)) return;
        refuse(cb, null);
    }

    // ================================================================== open

    @Override
    public boolean open(FeatureId screen, Runnable back) {
        if (!implemented().contains(screen)) return false;
        // BACK from a device screen: the module's way back to the row that opened it; Play6Menu keeps the menu while
        // such a screen is up (it is dropped when no menu window is left otherwise)
        switch (screen) {
            case KEYSTONE_MANUAL:
                ManualKeystonePanel.open(app, Play6Menu.holdFor(app, back));             // checks everything on p6-hal itself
                return true;
            case KEYSTONE_ROTATION:
                Play6RotatePanel.open(app, Play6Menu.holdFor(app, back));
                return true;
            case ZOOM_DIGITAL:
                Play6ZoomShiftPanel.open(app, Play6Menu.holdFor(app, back));
                return true;
            case FOCUS_MANUAL:
                Hal.openManualFocus(app);
                return true;
            case INPUT:
                if (!setupDone("input")) return false;
                SourceOverlay.showFromQuickPanel(app);                               // BACK there opens the card again
                return true;
            case SETTINGS_SYSTEM:
                return setupDone("system settings") && start(new Intent(Settings.ACTION_SETTINGS));
            case SETTINGS_PROJECTOR:
                if (!setupDone("projector settings")) return false;
                Hal.openProjectorSettings(app, null);
                return true;
            case SETTINGS_RECENTS:
                if (!setupDone("recent apps")) return false;
                org.lumen.projector.recents.RecentsActivity.open(app);
                return true;
            case BLUETOOTH:
                return setupDone("bluetooth") && start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            case NETWORK:
                return setupDone("network") && start(new Intent(Settings.ACTION_WIFI_SETTINGS));
            case USB_FILES:
                return setupDone("usb files") && start(new Intent(Intent.ACTION_MAIN).setClassName(HOME_PKG, HOME_FILES));
            case PICTURE_PRO:
            case SOUND_EQ:
            case SETTINGS_AMBIENT_SPEAKER:
                // pages inside the menu, not screens: Play6MenuPages gives them through MenuController.Pages
                Log.i(TAG, "open " + screen + ": a page of the menu, not a screen");
                return false;
            case MIRRORING:
                // the menu's own popup over the receiver (lumen-menu QpdMirror, org.lumen.miracast), shown only where the
                // receiver's control provider resolves, i.e. this unit has Wi-Fi Direct (LumenMiracast ControlGate)
                Log.i(TAG, "open " + screen + ": the menu's screen mirroring popup, not a screen");
                return false;
            default:
                Log.i(TAG, "open " + screen + ": not a screen of the Play 6 adapter");
                return false;
        }
    }

    // ================================================================== helpers

    private boolean setupDone(String what) {
        if (KeyReceiver.isSetupComplete(app)) return true;
        Log.i(TAG, "setup not complete: " + what + " ignored");
        Ui.toast(app, R.string.toast_setup_running);
        return false;
    }

    private boolean start(Intent i) {
        try {
            Ui.wakeFromDream(app);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(app.getPackageManager()) == null) {
                Log.w(TAG, "nothing handles " + i);
                Ui.toast(app, R.string.toast_failed);
                return false;
            }
            app.startActivity(i);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "start " + i + ": " + t);
            Ui.toast(app, R.string.toast_failed);
            return false;
        }
    }
}
