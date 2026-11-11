package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import org.lumen.menu.Feature;
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
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Gmpf2;
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
    private static final String TAG = "P6Menu";
    /** Every part the menu shows (KEYSTONE adds only the zoom / rotation probes: their screens check themselves). */
    private static final int PARTS = Play6Snapshot.ROOT | Play6Snapshot.FOCUS | Play6Snapshot.LAMP
            | Play6Snapshot.PICTURE | Play6Snapshot.SOUND | Play6Snapshot.PROJECTION | Play6Snapshot.GAME
            | Play6Snapshot.EYE;
    /** boolean: the user confirmed turning power saving on (off) once; stock asks the first time only. */
    private static final String KEY_ECO_ON_CONFIRMED = "eco_on_confirmed", KEY_ECO_OFF_CONFIRMED = "eco_off_confirmed";
    private static final String HOME_PKG = "org.lumen.home", HOME_FILES = "org.lumen.home.FilesLauncher";

    private final Context app;
    private final SharedPreferences prefs;
    private final boolean eyeReady;
    /** Last read (main thread). */
    private volatile Play6MenuGates.Read last;

    public Play6Features(Context ctx, boolean eyeReady) {
        app = ctx.getApplicationContext();
        prefs = app.getSharedPreferences(QuickPanel.PREFS, Context.MODE_PRIVATE);
        this.eyeReady = eyeReady;
    }

    // ================================================================== presence and the v3 hooks

    @Override
    public Set<String> implemented() { return Play6MenuGates.implemented(); }

    @Override
    public boolean ready() { return Hal.isReady(); }

    @Override
    public boolean available(String feature) {
        boolean launcher = false;
        if (Feature.SETTINGS_LAUNCHER.equals(feature)) {
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
    public Note blocked(String feature, String value, MenuState now) { return Play6MenuGates.blocked(feature, value, last); }

    @Override
    public Confirm confirm(String feature, String value, MenuState now) {
        return Play6MenuGates.confirm(feature, value, last, prefs.getBoolean(KEY_ECO_ON_CONFIRMED, false),
                prefs.getBoolean(KEY_ECO_OFF_CONFIRMED, false));
    }

    @Override
    public void setConfirmed(String feature, String value, Consumer<Result> cb) {
        // power saving: stock asks the first time each way only
        if (Feature.LAMP_ECO.equals(feature)) {
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

    private MenuState state() { return Play6MenuGates.state(last, this::text); }

    /** A string of the app by resource name (the name itself when it does not exist: never a crash). */
    private String text(String name) {
        int id = app.getResources().getIdentifier(name, "string", app.getPackageName());
        return id == 0 ? name : app.getString(id);
    }

    // ================================================================== set

    @Override
    public void set(String feature, String value, Consumer<Result> cb) {
        if (!implemented().contains(feature) && !feature.startsWith(Feature.PICTURE_PQ + ".")) { refuse(cb, null); return; }
        if (!Hal.isReady()) { Ui.main().post(() -> cb.accept(Result.notReady())); return; }
        Play6MenuGates.Read r = last;
        if (r == null) { refuse(cb, "toast_not_ready"); return; }
        String no = Play6MenuGates.refusal(feature, r);
        if (no != null) { refuse(cb, no.isEmpty() ? null : no); return; }
        boolean on = "on".equals(value);
        switch (feature) {
            case Feature.SOUND_OUTPUT: setOutput(value, cb); return;
            case Feature.SETTINGS_LAUNCHER: setLauncher(value, cb); return;
            case Feature.LAMP_ECO: setEco(on, cb); return;
            case Feature.PICTURE_MODE: setPictureMode(value, r, cb); return;
            case Feature.PROJECTION_MODE: setProjection(value, r, cb); return;
            case Feature.PROJECTION_REAR: setRear(on, r, cb); return;
            case Feature.HDMI_EDID: setEdid(value, r, cb); return;
            default: break;
        }
        write(feature + "=" + value, cb, (g, g2) -> writeHal(feature, value, on, g, g2));
    }

    /** A write on p6-hal: null = done, else the refusal note (string resource name, "" = no note). */
    private interface Write {
        String run(Play6Gmpf g, Play6Gmpf2 g2) throws Exception;
    }

    /** Runs {@code w} on p6-hal, then reads everything back; the result on the main thread. */
    private void write(String what, Consumer<Result> cb, Write w) {
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

    private void refuse(Consumer<Result> cb, String reason) {
        Ui.main().post(() -> cb.accept(Result.refused(reason, state())));
    }

    /** p6-hal: one feature write with the Play 6 rules. */
    private String writeHal(String feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2) throws Exception {
        if (Feature.def(feature) != null && Feature.def(feature).kind == Feature.Kind.TOGGLE
                && !on && !"off".equals(value)) throw new IllegalArgumentException(feature + " " + value);
        if (feature.startsWith(Feature.PICTURE_PQ + ".")) return setPq(feature.substring(Feature.PICTURE_PQ.length() + 1), value, g);
        switch (feature) {
            case Feature.EYE: {
                if (!"on".equals(value) && !"off".equals(value)) throw new IllegalArgumentException("eye " + value);
                if (!Device.hasEyeProtection()) return "";                         // no NUI reader: nothing sent
                if (!g.setEyeProtection(on)) throw new IllegalStateException("152 returned false");
                return null;
            }
            case Feature.KEYSTONE_REALTIME:
                g.setMoveAk(on);                                                     // 585
                return null;
            case Feature.KEYSTONE_ON_POWER:
                g.setPowerOnAk(on);                                                  // 552
                return null;
            case Feature.FOCUS_SMART:
                if (!g2.isIdentityOpen()) return "toast_not_ready";
                Hal.setSmartAutofocus(g, g2, on);                                    // IGmpf2 64, then 587
                return null;
            case Feature.LAMP: {
                int v = Integer.parseInt(value.trim());
                if (!Play6MenuGates.lampValue(v)) throw new IllegalArgumentException("lamp " + v);
                g.setLampLevel(v);                                                   // 176
                return null;
            }
            case Feature.PICTURE_COLOR_TEMP: {
                Integer w = Play6MenuGates.COLOR_TEMP.wire(value);
                if (w == null) throw new IllegalArgumentException("colour temperature " + value);
                if (!Play6MenuGates.custom(g.getPictureMode())) return "";
                g.setColorTemperature(w);                                            // 685
                return null;
            }
            case Feature.PICTURE_MEMC: {
                Integer w = Play6MenuGates.MEMC.wire(value);
                if (w == null) throw new IllegalArgumentException("memc " + value);
                if (!Play6MenuGates.memcMode(g.getPictureMode())) return "";
                if (!g.setMemcLevel(w)) throw new IllegalStateException("669 returned false");
                return null;
            }
            case Feature.PICTURE_DYNAMIC_CONTRAST:
                if (!g2.isIdentityOpen() || !Play6MenuGates.custom(g.getPictureMode())) return "";
                g2.setDynamicContrast(on);                                           // IGmpf2 231
                return null;
            case Feature.HDMI_RANGE: {
                Integer w = Play6MenuGates.HDMI_RANGE.wire(value);
                if (w == null) throw new IllegalArgumentException("range " + value);
                if (!hdmiNow(g, "635")) return "panel_game_hdmi_only";
                if (!g.setHdmiRange(w)) throw new IllegalStateException("635 returned false");
                return null;
            }
            case Feature.PICTURE_ASPECT: {
                Integer w = Play6MenuGates.ASPECT.wire(value);
                if (w == null) throw new IllegalArgumentException("aspect " + value);
                if (!hdmiNow(g, "665")) return "panel_game_hdmi_only";
                if (w == Play6MenuGates.ASPECT_ORIGINAL && !Play6MenuGates.originalFits(g.getHdmiInfo())) return "";
                if (!g.setAspectRatio(w)) throw new IllegalStateException("665 returned false");
                return null;
            }
            case Feature.SOUND_MODE: {
                Integer w = Play6MenuGates.SOUND_MODE.wire(value);
                if (w == null) throw new IllegalArgumentException("sound mode " + value);
                g.setSoundEffect(w);                                                 // 61
                org.lumen.projector.audio.P6SoundEq.afterSoundModeChange(app);
                return null;
            }
            case Feature.SOUND_EARC:
                if (!g.setEarc(on)) throw new IllegalStateException("633 returned false");
                return null;
            case Feature.GAME_MODE: {
                Integer w = Play6MenuGates.GAME_MODE.wire(value);
                if (w == null) throw new IllegalArgumentException("game mode " + value);
                if (!hdmiNow(g, "game mode")) return "panel_game_hdmi_only";
                return GameMode.set(app, g, g2, w) ? null : "toast_not_ready";     // IGmpf2 34 / 35 (+ 648)
            }
            default:
                throw new IllegalArgumentException("no writer for " + feature);
        }
    }

    /** p6-hal: a Custom picture item (0..100), Custom only (stock image_mode_custom). */
    private String setPq(String item, String value, Play6Gmpf g) throws Exception {
        int v = Integer.parseInt(value.trim());
        if (v < 0 || v > 100) throw new IllegalArgumentException("pq " + item + " " + v);
        if (!Play6MenuGates.custom(g.getPictureMode())) return "";
        boolean r;
        switch (item) {
            case "brightness": r = g.setBrightness(v); break;                       // 693
            case "contrast": r = g.setContrast(v); break;                           // 695
            case "saturation": r = g.setSaturation(v); break;                       // 691
            case "sharpness": r = g.setSharpness(v); break;                         // 689
            default: throw new IllegalArgumentException("pq item " + item);
        }
        if (!r) throw new IllegalStateException("pq " + item + " returned false");
        return null;
    }

    /** Picture mode: locked in Game (refusal; Performance was asked by the menu, Play6MenuGates.confirm), else 667(0, m). */
    private void setPictureMode(String value, Play6MenuGates.Read r, Consumer<Result> cb) {
        Integer m = Play6MenuGates.PICTURE_MODE.wire(value);
        if (m == null) { refuse(cb, null); return; }
        if (m.equals(Play6MenuGates.shownPictureMode(r.pictureMode))) { refuse(cb, null); return; }
        write("667 picture mode " + m, cb, (g, g2) -> {
            if (Play6MenuGates.gamePicture(g.getPictureMode())) return "p6_pic_game_hint";
            if (!g.setPictureMode(m)) throw new IllegalStateException("667 returned false");
            return null;
        });
    }

    /** Stock sequence: Auto = 580(true); table / ceiling = 580(false) then 172 (a flip was asked by the menu). */
    private void setProjection(String value, Play6MenuGates.Read r, Consumer<Result> cb) {
        final int cur = r.putMode;
        final boolean auto = r.autoReverse;
        if ("auto".equals(value)) {
            if (auto) { refuse(cb, null); return; }
            write("580 auto projection on", cb, (g, g2) -> { g.setAutoProjection(true); return null; });
            return;
        }
        final int target = Play6MenuGates.putModeFor(value, cur);
        if (target < 0) { refuse(cb, null); return; }
        if (target == cur) {
            if (auto) write("580 auto projection off", cb, (g, g2) -> { g.setAutoProjection(false); return null; });
            else refuse(cb, null);
            return;
        }
        write("172 put mode " + target, cb, (g, g2) -> {
            g.setAutoProjection(false);                                              // 580(false)
            g.setPutMode(target);                                                    // 172
            return null;
        });
    }

    /** Rear switch: 172 alone, auto flip kept (stock ProjectionRepository.setRearProjection). */
    private void setRear(boolean on, Play6MenuGates.Read r, Consumer<Result> cb) {
        final int target = Play6MenuGates.putModeForRear(on, r.putMode);
        if (target == r.putMode) { refuse(cb, null); return; }
        write("172 put mode " + target, cb, (g, g2) -> { g.setPutMode(target); return null; });
    }

    /** HDMI mode (asked by the menu: the source reconnects): 660, HDMI on screen only. */
    private void setEdid(String value, Play6MenuGates.Read r, Consumer<Result> cb) {
        Integer v = Play6MenuGates.EDID.wire(value);
        if (v == null || v.equals(r.edid)) { refuse(cb, null); return; }
        write("660 edid " + v, cb, (g, g2) -> {
            if (!hdmiNow(g, "660")) return "panel_game_hdmi_only";
            if (!g.setEdidVersion(v)) throw new IllegalStateException("660 returned false");
            return null;
        });
    }

    /** Power saving (stock eco_mode_switch, IGmpf 327 / 328), battery model only; the vendor changes the light with it. */
    private void setEco(boolean on, Consumer<Result> cb) {
        if (!Device.hasBattery()) { refuse(cb, null); return; }
        write("328 power saving " + on, cb, (g, g2) -> { g.setEcoState(on); return null; });
    }

    /** Sound output: only the outputs connected now (AudioOutputChoice, no HAL). Main thread. */
    private void setOutput(String value, Consumer<Result> cb) {
        String kind = Play6MenuGates.outputKind(value);
        if (kind == null) { refuse(cb, null); return; }
        try {
            AudioOutputChoice.Option pick = null;
            for (AudioOutputChoice.Option o : AudioOutputChoice.options(app)) if (o.kind.name().equals(kind)) pick = o;
            if (pick == null) {
                Log.i(TAG, "output " + value + ": not connected now");
                refuse(cb, null);
                return;
            }
            AudioOutputChoice.choose(app, pick.kind, pick.addr);
        } catch (Throwable t) {
            Log.w(TAG, "output " + value + ": " + t);
            Ui.main().post(() -> cb.accept(Result.failed(state())));
            return;
        }
        read(st -> cb.accept(Result.done(st)));
    }

    /** Home screen (asked, the menu closed first): LauncherSwitcher, after the first-run setup only. */
    private void setLauncher(String value, Consumer<Result> cb) {
        Integer w = Play6MenuGates.LAUNCHER.wire(value);
        if (w == null || !KeyReceiver.isSetupComplete(app)) { refuse(cb, w == null ? null : "toast_setup_running"); return; }
        final String mode = w == 1 ? LauncherSwitcher.MODE_CLASSIC : LauncherSwitcher.MODE_LUMEN;
        if (mode.equals(LauncherSwitcher.currentMode(app))) { refuse(cb, null); return; }
        LauncherSwitcher.apply(app, mode, true, "quick menu",
                (ok, holder, err) -> Log.i(TAG, "home screen " + mode + " -> " + ok + " " + holder));
        Ui.main().post(() -> cb.accept(Result.done(state())));
    }

    // ================================================================== run

    @Override
    public void run(String action, Consumer<Result> cb) {
        if (!implemented().contains(action)) { refuse(cb, null); return; }
        switch (action) {
            case Feature.KEYSTONE_AK_NOW:                                            // the menu closed first
                Hal.requestKeystone(app);                                            // 326 on p6-long
                Ui.main().post(() -> cb.accept(Result.done(state())));
                return;
            case Feature.FOCUS_AF_NOW:                                               // the menu closed first
                Hal.requestAutofocus(app);                                           // 307(44)
                Ui.main().post(() -> cb.accept(Result.done(state())));
                return;
            case Feature.KEYSTONE_RESTORE:                                           // asked by the menu
                write("207 keystone reset", r -> cb.accept(r.kind == Result.Kind.DONE
                        ? Result.done(r.after, Note.of("panel_reset_keystone_done")) : r), (g, g2) -> {
                    Log.i(TAG, "keystone reset: 207(0), 130 -> " + g.resetKeystone());
                    return null;
                });
                return;
            case Feature.PICTURE_RESET: {                                            // asked by the menu
                Play6MenuGates.Read r = last;
                if (r == null || !Play6MenuGates.custom(r.pictureMode)) { refuse(cb, null); return; }
                write("658 reset custom picture", cb, (g, g2) -> {
                    if (!Play6MenuGates.custom(g.getPictureMode())) return "";
                    g.resetCustomPicture();                                          // 658
                    return null;
                });
                return;
            }
            default:
                refuse(cb, null);
        }
    }

    // ================================================================== open

    @Override
    public boolean open(String screen, Runnable back) {
        if (!implemented().contains(screen)) return false;
        // BACK from a device screen: the module's way back to the row that opened it; Play6Menu keeps the menu while
        // such a screen is up (it is dropped when no menu window is left otherwise)
        switch (screen) {
            case Feature.KEYSTONE_MANUAL:
                ManualKeystonePanel.open(app, Play6Menu.holdFor(app, back));             // checks everything on p6-hal itself
                return true;
            case Feature.KEYSTONE_ROTATION:
                Play6RotatePanel.open(app, Play6Menu.holdFor(app, back));
                return true;
            case Feature.ZOOM_DIGITAL:
                Play6ZoomShiftPanel.open(app, Play6Menu.holdFor(app, back));
                return true;
            case Feature.FOCUS_MANUAL:
                Hal.openManualFocus(app);
                return true;
            case Feature.INPUT:
                if (!setupDone("input")) return false;
                SourceOverlay.showFromQuickPanel(app);                               // BACK there opens the card again
                return true;
            case Feature.SETTINGS_SYSTEM:
                return setupDone("system settings") && start(new Intent(Settings.ACTION_SETTINGS));
            case Feature.SETTINGS_PROJECTOR:
                if (!setupDone("projector settings")) return false;
                Hal.openProjectorSettings(app, null);
                return true;
            case Feature.SETTINGS_RECENTS:
                if (!setupDone("recent apps")) return false;
                org.lumen.projector.recents.RecentsActivity.open(app);
                return true;
            case Feature.BLUETOOTH:
                return setupDone("bluetooth") && start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            case Feature.NETWORK:
                return setupDone("network") && start(new Intent(Settings.ACTION_WIFI_SETTINGS));
            case Feature.USB_FILES:
                return setupDone("usb files") && start(new Intent(Intent.ACTION_MAIN).setClassName(HOME_PKG, HOME_FILES));
            case Feature.PICTURE_PRO:
            case Feature.SOUND_EQ:
            case Feature.SETTINGS_AMBIENT_SPEAKER:
                // pages inside the menu, not screens: Play6MenuPages gives them through MenuController.Pages
                Log.i(TAG, "open " + screen + ": a page of the menu, not a screen");
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

    /** p6-hal: the HDMI port is the current source right now (IGmpf 696, the vendor keeps these per source). */
    private static boolean hdmiNow(Play6Gmpf g, String what) {
        int src = -1;
        try { src = g.getCurrentInputSource(); } catch (Throwable t) { Log.w(TAG, what + ": 696 " + t); }
        if (src == Play6MenuGates.SOURCE_HDMI1) return true;
        Log.i(TAG, what + " refused: HDMI is not the current source (696=" + src + ")");
        return false;
    }
}
