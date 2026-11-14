package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.FeatureKind;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.projector.Hal;
import org.lumen.projector.KeyReceiver;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.audio.AiSoundEngine;
import org.lumen.projector.audio.AudioOutputChoice;
import org.lumen.projector.audio.AudioPathReporter;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.HbFeatures;
import org.lumen.projector.hal.PictureMode;
import org.lumen.projector.source.SourceOverlay;
import org.lumen.projector.source.TifHdmiState;
import org.lumen.projector.ui.Notify;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Z9X HB (goat_elegant): the device adapter of the shared projector menu (apps/core/lumen-menu;
 * profile devices/goat_elegant/menu.json). Every read and write goes through the HB AIDL clients (GmpfClient /
 * Gmpf2Client over AidlCaller: the allow-list of devices/goat_elegant/research/allowed.tsv, no new call) on the HAL
 * thread (Hal.query), and every rule the HB quick panel kept is here, not in the menu:
 *  - presence: implemented() = HbMenuGates.GATES read against HbFeatures (G0148 / unknown model: no Boost, no colour
 *    temperature; a feature.xml key that says off hides its rows);
 *  - Boost (DISPLAY 151 / 152): refused without a call while Performance / Office holds it, every write judged by the
 *    152 read-back (BoostRules, stock ignores the boolean);
 *  - HDMI-only setters (game rows, aspect, HDMI range / EDID) re-check the input right before the write;
 *  - top-speed game refuses Auto keystone, zoom and the picture restore (stock common.json gameModeFast);
 *  - the vendor turns dynamic black off after a light / lumens mode / Boost change: the stock notice says so;
 *  - sound: AI sound engine, equalizer repair after a mode change, eARC through EarcLink, outputs through
 *    AudioOutputChoice + CEC System Audio (HbSystemAudio), formats as stock.
 * Values cross as the profile's tokens (HbMenuGates tables); the menu never sees a code.
 */
public final class HbMenuFeatures implements ProjectorFeatures {
    static final String TAG = "HbMenu";
    private static final String HOME_PKG = "org.lumen.home", HOME_FILES = "org.lumen.home.FilesLauncher";

    final Context app;
    final SharedPreferences prefs;
    /** Last snapshot read (main thread), for the synchronous open() checks. */
    private volatile Snapshot last;
    private final HbGeneralRows general;
    private final HbPictureRows picture;
    private final HbSoundRows sound;
    private final HbGameRows game;
    /** The sections a write or an action goes to; each answers {@link HbRows#OTHER} / false for another's feature. */
    private final HbRows[] sections;

    public HbMenuFeatures(Context ctx) {
        app = ctx.getApplicationContext();
        prefs = app.getSharedPreferences(QuickPanel.PREFS, Context.MODE_PRIVATE);
        general = new HbGeneralRows(this);
        picture = new HbPictureRows(this);
        sound = new HbSoundRows(this);
        game = new HbGameRows(this);
        sections = new HbRows[]{general, new HbKeystoneRows(this), new HbFocusRows(this), picture, new HbHdmiRows(this),
                sound, game, new HbProjectionRows(this)};
    }

    Snapshot last() { return last; }

    // ================================================================== presence

    @Override
    public Set<FeatureId> implemented() {
        return HbMenuGates.implemented(f -> HbFeatures.on(HbFeatures.F.valueOf(f)));
    }

    /** Game menu items on this unit (GameMenu.Io side): GAME_MODE + GAME_BAR and each item's own switch. */
    public static Set<String> gameItems() {
        return HbMenuGates.gameItems(f -> HbFeatures.on(HbFeatures.F.valueOf(f)));
    }

    @Override
    public boolean ready() { return Hal.isReady(); }

    // ================================================================== read

    @Override
    public void read(Consumer<MenuState> cb) {
        Hal.query(this::snapshot, sn -> {
            last = sn;
            cb.accept(state(sn));
        });
    }

    /** HAL thread. */
    private Snapshot snapshot(GmpfClient g, Gmpf2Client g2) {
        Snapshot sn = Snapshot.read(g, g2);
        sn.tifHdmi = TifHdmiState.read(app);
        sn.soundPathActive = AudioPathReporter.isActive(g);
        sn.soundPath = AudioPathReporter.confirmedPath();
        return sn;
    }

    /** Main thread: the menu state of a snapshot (null = nothing read: an empty state). */
    MenuState state(Snapshot sn) {
        MenuState.Builder b = MenuState.builder();
        if (sn == null) return b.build();
        boolean hdmi = sn.hdmiConfirmed();
        b.put(FeatureId.INPUT, sn.source == null ? (Boolean.TRUE.equals(sn.tifHdmi) ? "hdmi" : null)
                : sn.source == GmpfClient.SOURCE_HDMI1 ? "hdmi1" : sn.source == GmpfClient.SOURCE_HDMI2 ? "hdmi2" : "android");
        b.on(FeatureId.EYE, sn.eye);
        b.on(FeatureId.KEYSTONE_ON_POWER, sn.powerOnAk);
        b.on(FeatureId.KEYSTONE_CURTAIN, sn.curtain);
        b.on(FeatureId.KEYSTONE_OBSTACLE, sn.obstacle);
        b.on(FeatureId.FOCUS_SMART, sn.smartAf);
        // light and Boost: the slider shows Boost at its own step while 152 reads it
        b.number(FeatureId.LAMP, sn.lamp);
        if (HbFeatures.on(HbFeatures.F.BOOST)) b.on(FeatureId.BOOST, sn.boost);
        // picture
        PictureMode pm = sn.pictureMode == null ? null : PictureMode.fromWire(sn.pictureMode);
        b.put(FeatureId.PICTURE_MODE, pm == null ? null : HbMenuGates.PICTURE_MODE.token(pm.wire));
        String[] items = {"brightness", "contrast", "saturation", "sharpness"};    // PqItem order
        for (int i = 0; i < items.length && i < sn.pq.length; i++) b.number(FeatureId.pq(items[i]), sn.pq[i]);
        if (HbFeatures.on(HbFeatures.F.COLOR_TEMP)) b.put(FeatureId.PICTURE_COLOR_TEMP, HbMenuGates.COLOR_TEMP.token(sn.colorTemp));
        b.put(FeatureId.PICTURE_MEMC, HbMenuGates.MEMC.token(sn.memc));
        // HDMI rows: values only while an HDMI input is on screen (they belong to that input): without a value the
        // menu shows the row without a choice (a runtime hide is a module request, REQUESTS-hb.md)
        if (hdmi) {
            b.put(FeatureId.HDMI_RANGE, HbMenuGates.HDMI_RANGE.token(sn.hdmiRange));
            b.put(FeatureId.HDMI_EDID, HbMenuGates.EDID.token(sn.edid));
            b.put(FeatureId.PICTURE_ASPECT, HbMenuGates.ASPECT.token(sn.aspect));
        }
        // sound
        b.put(FeatureId.SOUND_MODE, HbMenuGates.SOUND_MODE.token(sn.soundEffect));
        if (sn.soundEffect != null) AiSoundEngine.noteMode(sn.soundEffect, "menu read-back");
        b.on(FeatureId.SOUND_EARC, sn.earc);
        outputs(b);
        game.read(b, sn, hdmi);
        // projection
        b.put(FeatureId.PROJECTION_MODE, HbMenuGates.projectionToken(sn.putMode, sn.autoReverse));
        b.on(FeatureId.PROJECTION_REAR, sn.putMode == null ? null : (Boolean) (sn.putMode == 2 || sn.putMode == 3));
        // settings
        try {
            if (org.lumen.projector.home.LauncherSwitcher.choiceAvailable(app)) {
                boolean classic = org.lumen.projector.home.LauncherSwitcher.MODE_CLASSIC.equals(
                        org.lumen.projector.home.LauncherSwitcher.currentMode(app));
                b.put(FeatureId.SETTINGS_LAUNCHER, classic ? "classic" : "lumen");
            }
        } catch (Throwable t) {
            Log.w(TAG, "launcher: " + t);
        }
        return b.build();
    }

    /** Main thread: the chosen output and format (HbOutputOptions / AudioOutputChoice, as the HB Sound page). */
    private void outputs(MenuState.Builder b) {
        try {
            b.put(FeatureId.SOUND_OUTPUT, HbMenuGates.outputToken(AudioOutputChoice.kind(app).name()));
            b.put(FeatureId.SOUND_FORMAT, HbMenuGates.FORMAT.token(AudioOutputChoice.formatIndex(app)));
        } catch (Throwable t) {
            Log.w(TAG, "outputs: " + t);
        }
    }

    // ================================================================== set

    @Override
    public void set(FeatureId feature, String value, Consumer<Result> cb) {
        if (!implemented().contains(feature)) { refuse(cb, null); return; }
        switch (feature) {
            case SOUND_OUTPUT: sound.setOutput(value, cb); return;
            case SOUND_FORMAT: sound.setFormat(value, cb); return;
            case SETTINGS_LAUNCHER: general.setLauncher(value, cb); return;
            default: break;
        }
        if (feature.parent == FeatureId.PICTURE_PQ) { picture.setPq(feature.item, value, cb); return; }
        write(feature + "=" + value, cb, (g, g2) -> writeHal(feature, value, g, g2));
    }

    /** A refusal the adapter already explained with its own notice (the menu then shows nothing more). */
    static final String EXPLAINED = "";

    /** A write: null = done, else the refusal note (string resource name, or EXPLAINED). HAL thread. */
    interface Write {
        String run(GmpfClient g, Gmpf2Client g2) throws Exception;
    }

    /** Runs {@code w} on the HAL thread, then reads everything back; the result on the main thread. */
    void write(String what, Consumer<Result> cb, Write w) {
        if (!Hal.isReady()) { Ui.main().post(() -> cb.accept(Result.notReady())); return; }
        Hal.query((g, g2) -> {
            Object[] out = new Object[2];
            try {
                out[0] = w.run(g, g2);
                Log.i(TAG, what + (out[0] == null ? "" : ": refused " + out[0]));
            } catch (Throwable t) {
                Log.w(TAG, what + ": " + t);
                out[0] = t;
            }
            out[1] = snapshot(g, g2);
            return out;
        }, out -> {
            if (out == null) { cb.accept(Result.notReady()); return; }
            last = (Snapshot) out[1];
            MenuState after = state(last);
            if (out[0] == null) cb.accept(Result.done(after));
            else if (EXPLAINED.equals(out[0])) cb.accept(Result.refused(null, after));
            else if (out[0] instanceof String) cb.accept(Result.refused((String) out[0], after));
            else cb.accept(Result.failed(after));
        });
    }

    static void refuse(Consumer<Result> cb, String reason) {
        Ui.main().post(() -> cb.accept(Result.refused(reason, null)));
    }

    /** HAL thread: one feature write, by the section whose feature it is. */
    private String writeHal(FeatureId feature, String value, GmpfClient g, Gmpf2Client g2) throws Exception {
        boolean on = "on".equals(value);
        if (feature.kind == FeatureKind.TOGGLE
                && !on && !"off".equals(value)) throw new IllegalArgumentException(feature + " " + value);
        for (HbRows s : sections) {
            String r = s.writeHal(feature, value, on, g, g2);
            if (r != HbRows.OTHER) return r;
        }
        throw new IllegalArgumentException("no writer for " + feature);
    }

    // ================================================================== run

    @Override
    public void run(FeatureId action, Consumer<Result> cb) {
        if (!implemented().contains(action)) { refuse(cb, null); return; }
        for (HbRows s : sections) if (s.run(action, cb)) return;
        refuse(cb, null);
    }

    // ================================================================== open

    @Override
    public boolean open(FeatureId screen, Runnable back) {
        if (!implemented().contains(screen)) return false;
        Snapshot sn = last;
        boolean topSpeed = sn != null && sn.isTopSpeedGame();
        switch (screen) {
            case KEYSTONE_MANUAL:
                ManualKeystonePanel.open(app, back);
                return true;
            case INPUT:
                if (!setupDone("input")) return false;
                SourceOverlay.showFromQuickPanel(app);                      // the HB input list (HDMI 1 / 2, Android)
                return true;
            case ZOOM:
                if (topSpeed) { Notify.show(app, app.getString(R.string.panel_kst_game_mode)); return false; }
                OpticalZoomPanel.open(app, back);
                return true;
            case FOCUS_MANUAL:
                Hal.openManualFocus(app);
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
                return setupDone("usb files") && start(usbFilesIntent());
            case PICTURE_PRO:
            case SETTINGS_CEC:
                // panel pages, not screens: ProPicture (expert picture pages) and CecPage go into the menu's popup
                // through the page hook the module gives device pages (REQUESTS-hb.md); nothing opens from here
                Log.i(TAG, "open " + screen + ": a page of the menu, not a screen");
                return false;
            default:
                Log.i(TAG, "open " + screen + ": not a screen of the HB adapter");
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
            if (i.resolveActivity(app.getPackageManager()) == null) i = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(i);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "start " + i + ": " + t);
            Ui.toast(app, R.string.toast_failed);
            return false;
        }
    }

    private static Intent usbFilesIntent() {
        return new Intent(Intent.ACTION_MAIN).setClassName(HOME_PKG, HOME_FILES);
    }

}
