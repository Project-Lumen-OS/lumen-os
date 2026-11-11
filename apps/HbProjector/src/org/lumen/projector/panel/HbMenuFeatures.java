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
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.ak.AkOverlay;
import org.lumen.projector.audio.AiSoundEngine;
import org.lumen.projector.audio.AudioOutputChoice;
import org.lumen.projector.audio.AudioPathReporter;
import org.lumen.projector.audio.HbOutputOptions;
import org.lumen.projector.audio.SoundEq;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.HbFeatures;
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
    private static final String TAG = "HbMenu";
    /** 647 follows 648 after the picture is applied again (~4.3 s seen); the state shows the choice meanwhile. */
    private static final long PENDING_MS = 10_000, GAME_OPTION_WAIT_MS = 6_000, GAME_OPTION_POLL_MS = 100;
    /** IGmpf 666 / TV 34 value of the vendor's own game picture mode (stock DisplayHelper.isGameModeBasic). */
    private static final int PICTURE_MODE_GAME = 4;
    private static final String HOME_PKG = "org.lumen.home", HOME_FILES = "org.lumen.home.FilesLauncher";

    private final Context app;
    private final SharedPreferences prefs;
    /** Last snapshot read (main thread), for the synchronous open() checks. */
    private volatile Snapshot last;
    private volatile int pendingGame = -1;
    private volatile long pendingGameUntil;
    private volatile Integer pendingSpeed;
    private volatile long pendingSpeedUntil;

    public HbMenuFeatures(Context ctx) {
        app = ctx.getApplicationContext();
        prefs = app.getSharedPreferences(QuickPanel.PREFS, Context.MODE_PRIVATE);
    }

    // ================================================================== presence

    @Override
    public Set<String> implemented() {
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
        b.put(Feature.INPUT, sn.source == null ? (Boolean.TRUE.equals(sn.tifHdmi) ? "hdmi" : null)
                : sn.source == GmpfClient.SOURCE_HDMI1 ? "hdmi1" : sn.source == GmpfClient.SOURCE_HDMI2 ? "hdmi2" : "android");
        b.on(Feature.EYE, sn.eye);
        b.on(Feature.KEYSTONE_ON_POWER, sn.powerOnAk);
        b.on(Feature.KEYSTONE_CURTAIN, sn.curtain);
        b.on(Feature.KEYSTONE_OBSTACLE, sn.obstacle);
        b.on(Feature.FOCUS_SMART, sn.smartAf);
        // light and Boost: the slider shows Boost at its own step while 152 reads it
        b.number(Feature.LAMP, sn.lamp);
        if (HbFeatures.on(HbFeatures.F.BOOST)) b.on(Feature.BOOST, sn.boost);
        // picture
        GmpfClient.PictureMode pm = sn.pictureMode == null ? null : GmpfClient.PictureMode.fromWire(sn.pictureMode);
        b.put(Feature.PICTURE_MODE, pm == null ? null : HbMenuGates.PICTURE_MODE.token(pm.wire));
        String[] items = {"brightness", "contrast", "saturation", "sharpness"};    // GmpfClient.PqItem order
        for (int i = 0; i < items.length && i < sn.pq.length; i++) b.number(Feature.PICTURE_PQ + "." + items[i], sn.pq[i]);
        if (HbFeatures.on(HbFeatures.F.COLOR_TEMP)) b.put(Feature.PICTURE_COLOR_TEMP, HbMenuGates.COLOR_TEMP.token(sn.colorTemp));
        b.put(Feature.PICTURE_MEMC, HbMenuGates.MEMC.token(sn.memc));
        // HDMI rows: values only while an HDMI input is on screen (they belong to that input): without a value the
        // menu shows the row without a choice (a runtime hide is a module request, REQUESTS-hb.md)
        if (hdmi) {
            b.put(Feature.HDMI_RANGE, HbMenuGates.HDMI_RANGE.token(sn.hdmiRange));
            b.put(Feature.HDMI_EDID, HbMenuGates.EDID.token(sn.edid));
            b.put(Feature.PICTURE_ASPECT, HbMenuGates.ASPECT.token(sn.aspect));
        }
        // sound
        b.put(Feature.SOUND_MODE, HbMenuGates.SOUND_MODE.token(sn.soundEffect));
        if (sn.soundEffect != null) AiSoundEngine.noteMode(sn.soundEffect, "menu read-back");
        b.on(Feature.SOUND_EARC, sn.earc);
        outputs(b);
        // game: the input's mode from 148 when it fits 77, else the last value set; a pending change shows meanwhile
        int gm = prefs.getInt(QuickPanel.KEY_GAME_MODE, -1);
        if (hdmi && sn.gameMode55 != null) {
            int pg = pendingGame;
            if (pg >= 0 && pg != sn.gameMode55 && SystemClock.uptimeMillis() < pendingGameUntil) gm = pg;
            else { pendingGame = -1; gm = sn.gameMode55; }
        }
        b.put(Feature.GAME_MODE, gm < 0 ? null : HbMenuGates.GAME_MODE.token(gm));
        Integer opt = sn.gameOption;
        Integer pend = pendingSpeed;
        if (opt != null && pend != null && !opt.equals(pend) && SystemClock.uptimeMillis() < pendingSpeedUntil) opt = pend;
        else pendingSpeed = null;
        b.put(Feature.GAME_SPEED, HbMenuGates.gameSpeedToken(opt));
        b.put(Feature.GAME_ALLM, sn.allm == null ? null : HbMenuGates.ALLM.token(sn.allm ? 1 : 0));
        // projection
        b.put(Feature.PROJECTION_MODE, HbMenuGates.projectionToken(sn.putMode, sn.autoReverse));
        b.on(Feature.PROJECTION_REAR, sn.putMode == null ? null : (Boolean) (sn.putMode == 2 || sn.putMode == 3));
        // settings
        try {
            if (org.lumen.projector.home.LauncherSwitcher.choiceAvailable(app)) {
                boolean classic = org.lumen.projector.home.LauncherSwitcher.MODE_CLASSIC.equals(
                        org.lumen.projector.home.LauncherSwitcher.currentMode(app));
                b.put(Feature.SETTINGS_LAUNCHER, classic ? "classic" : "lumen");
            }
        } catch (Throwable t) {
            Log.w(TAG, "launcher: " + t);
        }
        return b.build();
    }

    /**
     * Stock visibility of a picture mode for the content on screen (dapengse support lists): HDR Vivid only with HDR
     * Vivid content, colour accuracy for SDR, HDR10, HLG and HDR10+; every other mode always. Unknown content = SDR.
     */
    static boolean offered(int pictureModeWire, Integer hdrType) {
        int hdr = hdrType == null ? GmpfClient.HDR_SDR : hdrType;
        if (pictureModeWire == GmpfClient.PictureMode.HDR_VIVID.wire) return hdr == GmpfClient.HDR_VIVID;
        if (pictureModeWire == GmpfClient.PictureMode.COLOR_ACCURACY.wire) {
            return hdr == GmpfClient.HDR_SDR || hdr == GmpfClient.HDR_HDR10 || hdr == GmpfClient.HDR_HLG
                    || hdr == GmpfClient.HDR_HDR10_PLUS;
        }
        return true;
    }

    /** Main thread: the chosen output and format (HbOutputOptions / AudioOutputChoice, as the HB Sound page). */
    private void outputs(MenuState.Builder b) {
        try {
            b.put(Feature.SOUND_OUTPUT, HbMenuGates.outputToken(AudioOutputChoice.kind(app).name()));
            b.put(Feature.SOUND_FORMAT, HbMenuGates.FORMAT.token(AudioOutputChoice.formatIndex(app)));
        } catch (Throwable t) {
            Log.w(TAG, "outputs: " + t);
        }
    }

    // ================================================================== set

    @Override
    public void set(String feature, String value, Consumer<Result> cb) {
        if (!implemented().contains(feature)) { refuse(cb, null); return; }
        switch (feature) {
            case Feature.SOUND_OUTPUT: setOutput(value, cb); return;
            case Feature.SOUND_FORMAT: setFormat(value, cb); return;
            case Feature.SETTINGS_LAUNCHER: setLauncher(value, cb); return;
            default: break;
        }
        if (feature.startsWith(Feature.PICTURE_PQ + ".")) { setPq(feature.substring(Feature.PICTURE_PQ.length() + 1), value, cb); return; }
        write(feature + "=" + value, cb, (g, g2) -> writeHal(feature, value, g, g2));
    }

    /** A refusal the adapter already explained with its own notice (the menu then shows nothing more). */
    static final String EXPLAINED = "";

    /** A write: null = done, else the refusal note (string resource name, or EXPLAINED). HAL thread. */
    private interface Write {
        String run(GmpfClient g, Gmpf2Client g2) throws Exception;
    }

    /** Runs {@code w} on the HAL thread, then reads everything back; the result on the main thread. */
    private void write(String what, Consumer<Result> cb, Write w) {
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

    private static void refuse(Consumer<Result> cb, String reason) {
        Ui.main().post(() -> cb.accept(Result.refused(reason, null)));
    }

    /** HAL thread: one feature write with the HB rules. */
    private String writeHal(String feature, String value, GmpfClient g, Gmpf2Client g2) throws Exception {
        boolean on = "on".equals(value);
        if (Feature.def(feature) != null && Feature.def(feature).kind == Feature.Kind.TOGGLE
                && !on && !"off".equals(value)) throw new IllegalArgumentException(feature + " " + value);
        switch (feature) {
            case Feature.EYE: {
                if (!"on".equals(value) && !"off".equals(value)) throw new IllegalArgumentException("eye " + value);
                if (!GmpfClient.nuiReaderReady()) return "eye_unavailable";   // no NUI fifo reader: nothing sent
                if (!g.setEyeProtection(on)) throw new IllegalStateException("61 returned false");
                return null;
            }
            case Feature.KEYSTONE_ON_POWER:
                g.setPowerOnAk(on);                                             // FACT 45
                return null;
            case Feature.KEYSTONE_CURTAIN:
                Hal.setCurtainFitCoupled(g, on);                                // FOCUS 35 (+ 33 on)
                return null;
            case Feature.KEYSTONE_OBSTACLE:
                Hal.setObstacleAvoidCoupled(g, on);                             // FOCUS 33 (+ 35 off)
                return null;
            case Feature.FOCUS_SMART:
                // stock FocusRepository.setSmartAutoFocus: FOCUS 85, then MOTION 4 (AF after moving); the user set it
                org.lumen.projector.power.StandbyController.noteUserMoveAf();
                g2.setSmartAutofocus(on);
                g.setMoveAf(on);
                return null;
            case Feature.LAMP: {
                int v = Integer.parseInt(value.trim());
                if (!HbMenuGates.lampValue(v)) throw new IllegalArgumentException("lamp " + v);
                if (Boolean.TRUE.equals(safeBoost(g2))) {
                    // a light step in Boost leaves Boost first (BoostRules: never against a picture mode that holds it)
                    String r = setBoost(false, g, g2);
                    if (r != null) return r;
                }
                Boolean db = dynamicBlack(g2);
                g.setLampLevel(v);                                              // DISPLAY 6
                dynamicBlackOff(g2, db, R.string.panel_lamp);
                return null;
            }
            case Feature.BOOST:
                return setBoost(on, g, g2);
            case Feature.PICTURE_MODE:
                return setPictureMode(value, g, g2);
            case Feature.PICTURE_COLOR_TEMP: {
                Integer w = HbMenuGates.COLOR_TEMP.wire(value);
                if (w == null) throw new IllegalArgumentException("colour temperature " + value);
                Boolean db = dynamicBlack(g2);
                g.setColorTemp(w == 0 ? GmpfClient.ColorTemp.STANDARD : GmpfClient.ColorTemp.MOVIE);   // DISPLAY 4
                dynamicBlackOff(g2, db, R.string.panel_color_temp);
                return null;
            }
            case Feature.PICTURE_MEMC: {
                Integer w = HbMenuGates.MEMC.wire(value);
                if (w == null) throw new IllegalArgumentException("memc " + value);
                if (!g.setMemcLevel(GmpfClient.MemcLevel.values()[w])) throw new IllegalStateException("TV 31 false");
                return null;
            }
            case Feature.HDMI_RANGE: {
                Integer w = HbMenuGates.HDMI_RANGE.wire(value);
                if (w == null) throw new IllegalArgumentException("range " + value);
                if (!hdmiNow(g)) return "panel_game_hdmi_only";
                if (!g.setHdmiRange(GmpfClient.HdmiRange.values()[w])) throw new IllegalStateException("TV 63 false");
                return null;
            }
            case Feature.HDMI_EDID: {
                Integer w = HbMenuGates.EDID.wire(value);
                if (w == null) throw new IllegalArgumentException("edid " + value);
                if (!hdmiNow(g)) return "panel_game_hdmi_only";
                if (!g.setEdidVersion(w)) throw new IllegalStateException("TV 40 false");
                Ui.main().post(() -> Notify.show(app, app.getString(R.string.hdmi_mode), app.getString(R.string.hdmi_mode_note)));
                return null;
            }
            case Feature.PICTURE_ASPECT: {
                Integer w = HbMenuGates.ASPECT.wire(value);
                if (w == null) throw new IllegalArgumentException("aspect " + value);
                if (!hdmiNow(g)) return "panel_game_hdmi_only";
                GmpfClient.AspectRatio a = null;
                for (GmpfClient.AspectRatio r : GmpfClient.AspectRatio.values()) if (r.wire == w) a = r;
                if (a == null || !g.setAspectRatio(a)) throw new IllegalStateException("TV 35 " + w);
                return null;
            }
            case Feature.SOUND_MODE:
                return setSoundMode(value, g);
            case Feature.SOUND_EARC:
                if (!org.lumen.projector.cec.EarcLink.setUser(g, on)) throw new IllegalStateException("TV 65 false");
                return null;
            case Feature.GAME_MODE:
                return setGameMode(value, g, g2);
            case Feature.GAME_SPEED:
                return setGameSpeed(value, g);
            case Feature.GAME_ALLM: {
                Integer w = HbMenuGates.ALLM.wire(value);
                if (w == null) throw new IllegalArgumentException("allm " + value);
                if (!hdmiNow(g)) return "panel_game_hdmi_only";
                g2.setAllmAutoSwitch(w == 1);                                   // TV 129
                return null;
            }
            case Feature.PROJECTION_MODE:
                return setProjection(value, g, g2);
            case Feature.PROJECTION_REAR: {
                GmpfClient.PutMode cur = g.getPutMode();
                if (cur == null) throw new IllegalStateException("put mode unreadable");
                GmpfClient.PutMode m = GmpfClient.PutMode.fromWire(HbMenuGates.putModeForRear(on, cur.wire));
                if (m != null && m != cur) g.setPutMode(m);                     // DISPLAY 2 only, auto flip kept
                return null;
            }
            default:
                throw new IllegalArgumentException("no writer for " + feature);
        }
    }

    /** HAL thread. Boost by the 152 read-back (BoostRules); refused without a call where the picture mode holds it. */
    private String setBoost(boolean on, GmpfClient g, Gmpf2Client g2) throws Exception {
        Integer pm;
        try { pm = g.getPictureModeRaw(); } catch (Throwable t) { pm = null; }
        Boolean cur = safeBoost(g2);
        if (!on && BoostRules.onLeavePress(cur, pm) == BoostRules.Leave.EXPLAIN_PICTURE_MODE) return boostLocked(pm);
        Boolean db = dynamicBlack(g2);
        boolean ret = g2.setBoost(on);                                          // DISPLAY 151
        int lensAfter;
        try { lensAfter = g2.isBoost() ? 1 : 0; } catch (Throwable t) { lensAfter = -1; }
        Integer pmAfter;
        try { pmAfter = g.getPictureModeRaw(); } catch (Throwable t) { pmAfter = null; }
        BoostRules.Outcome o = BoostRules.judge(on, ret, lensAfter, pmAfter);
        Log.i(TAG, "151(" + (on ? 1 : 0) + ") -> " + ret + ", 152 -> " + lensAfter + ", picture mode " + pmAfter + ": " + o);
        if (o == BoostRules.Outcome.LOCKED_BY_PICTURE_MODE) return boostLocked(pmAfter);
        if (o == BoostRules.Outcome.FAILED) throw new IllegalStateException("151 returned " + ret + ", 152 reads " + lensAfter);
        dynamicBlackOff(g2, db, R.string.panel_boost);
        return null;
    }

    /** "Boost belongs to <picture mode>: choose another picture mode" (Performance / Office), stock HB text. */
    private String boostLocked(Integer pictureModeWire) {
        String tok = pictureModeWire == null ? null : HbMenuGates.PICTURE_MODE.token(pictureModeWire);
        int label = tok == null ? 0 : MenuLabels.option(Feature.PICTURE_MODE, tok);
        String mode = app.getString(label != 0 ? label : R.string.panel_picture_mode);
        String text = app.getString(R.string.hb_boost_locked, mode);
        Ui.main().post(() -> Notify.show(app, app.getString(R.string.panel_boost), text));
        return EXPLAINED;
    }

    /** HAL thread: picture mode with the stock refusals (game mode, Colour accuracy in the user's Boost). */
    private String setPictureMode(String value, GmpfClient g, Gmpf2Client g2) throws Exception {
        Integer w = HbMenuGates.PICTURE_MODE.wire(value);
        if (w == null) throw new IllegalArgumentException("picture mode " + value);
        GmpfClient.PictureMode m = GmpfClient.PictureMode.fromWire(w);
        Integer raw = g.getPictureModeRaw();
        if (raw != null && GmpfClient.PictureMode.fromWire(raw) == m) return null;
        Integer hdr;
        try { hdr = g.getHdrType(); } catch (Throwable t) { hdr = null; }
        if (!offered(w, hdr)) return EXPLAINED;                         // stock hides it for this content
        if (gameModeOn(g, raw)) return "blocked_game";
        if (m == GmpfClient.PictureMode.COLOR_ACCURACY && BoostRules.blocksColorAccuracy(safeBoost(g2), raw)) {
            return "boost_blocks_change";
        }
        if (!g.setPictureMode(m)) throw new IllegalStateException("TV 33 false");
        if (m == GmpfClient.PictureMode.AI) g.setAiScene(GmpfClient.AiScene.FILM);
        return null;
    }

    /** HAL thread: sound mode, AI through the engine, the equalizer curve rewritten after a user change. */
    private String setSoundMode(String value, GmpfClient g) throws Exception {
        Integer w = HbMenuGates.SOUND_MODE.wire(value);
        if (w == null) throw new IllegalArgumentException("sound mode " + value);
        GmpfClient.SoundEffect e = GmpfClient.SoundEffect.fromWire(w);
        if (e == GmpfClient.SoundEffect.AI) {
            AiSoundEngine.userChoseAi(g);
        } else {
            g.setSoundEffect(e);                                                // AUDIO 6
            AiSoundEngine.noteMode(e.wire, "user");
        }
        SoundProfiles.rememberMode(app, e);
        SoundEq.afterUserModeChange(g, "6(" + e.wire + ")");
        return null;
    }

    /** HAL thread: stock gmuiapi setGameMode: 0 = 147 manual + 146 off, 1 = 76 again + 147 manual + 146 on, 2 = 147 auto. */
    private String setGameMode(String value, GmpfClient g, Gmpf2Client g2) throws Exception {
        Integer i = HbMenuGates.GAME_MODE.wire(value);
        if (i == null) throw new IllegalArgumentException("game mode " + value);
        if (!hdmiNow(g)) return "panel_game_hdmi_only";
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
        if (!ok) throw new IllegalStateException("147 / 146 returned false");
        prefs.edit().putInt(QuickPanel.KEY_GAME_MODE, i).apply();
        pendingGame = i;
        pendingGameUntil = SystemClock.uptimeMillis() + PENDING_MS;
        return null;
    }

    /** HAL thread: DISPLAY 76; false is a success only when 77 reads it within 6 s; top speed then sends 38(0). */
    private String setGameSpeed(String value, GmpfClient g) throws Exception {
        Integer w = HbMenuGates.GAME_SPEED.wire(value);
        if (w == null) throw new IllegalArgumentException("game speed " + value);
        GmpfClient.GameModeOption o = GmpfClient.GameModeOption.fromWire(w);
        if (!hdmiNow(g)) return "panel_game_hdmi_only";
        boolean r = g.setGameModeOption(o);
        GmpfClient.GameModeOption now = g.getGameModeOption();
        if (!r && now != o) {
            long end = SystemClock.uptimeMillis() + GAME_OPTION_WAIT_MS;
            while (now != o && SystemClock.uptimeMillis() < end) {
                SystemClock.sleep(GAME_OPTION_POLL_MS);
                try { now = g.getGameModeOption(); } catch (Throwable t) { Log.w(TAG, "77: " + t); }
            }
            if (now != o) throw new IllegalStateException("76 -> false, 77 reads " + now);
        }
        pendingSpeed = o.wire;
        pendingSpeedUntil = SystemClock.uptimeMillis() + PENDING_MS;
        if (o.isTopSpeed()) topSpeedKeystone(o, SystemClock.uptimeMillis() + PENDING_MS);
        return null;
    }

    /** DISPLAY 38(0) for top speed once 77 reads it (re-polled every 300 ms, never blocking the HAL thread). */
    private void topSpeedKeystone(GmpfClient.GameModeOption o, long deadline) {
        Hal.runDelayed((g, g2) -> {
            GmpfClient.GameModeOption now = g.getGameModeOption();
            if (now == o) {
                g.resetKeystoneForTopSpeedGame();
                Ui.main().post(() -> Notify.show(app, app.getString(R.string.panel_speed_top_note)));
            } else if (SystemClock.uptimeMillis() < deadline) {
                topSpeedKeystone(o, deadline);
            } else {
                Log.w(TAG, "38(0) skipped: 77 still reads " + now + " instead of " + o);
            }
        }, 300);
    }

    /**
     * HAL thread, stock ProjectionRepository: Auto = MOTION 8 on; Table = 8 off + put mode 0 / 2; Ceiling = 8 off +
     * put mode 1 / 3 + SYS 140 (the stock hoist dialog is the menu's confirmation of this choice).
     */
    private String setProjection(String value, GmpfClient g, Gmpf2Client g2) throws Exception {
        if (HbMenuGates.PROJECTION.wire(value) == null) throw new IllegalArgumentException("projection " + value);
        GmpfClient.PutMode cur = g.getPutMode();
        Boolean auto = g.getAutoReverse();
        if (cur == null || auto == null) throw new IllegalStateException("put mode / auto flip unreadable");
        if ("auto".equals(value)) {
            if (!auto) g.setAutoReverse(true);
            return null;
        }
        GmpfClient.PutMode m = GmpfClient.PutMode.fromWire(HbMenuGates.putModeFor(value, cur.wire));
        if (m == null) throw new IllegalStateException("no put mode for " + value);
        if (m == cur) {
            if (auto) g.setAutoReverse(false);
            return null;
        }
        g.setAutoReverse(false);
        g.setPutMode(m);
        if ("ceiling".equals(value) && cur.wire != 1 && cur.wire != 3) {
            try { g2.disablePushPullScreenForCeiling(); } catch (Throwable t) { Log.w(TAG, "140(false): " + t); }
        }
        return null;
    }

    /** Picture sliders: through the HB slide coalescer (newest value, one write in flight), as the HB panel did. */
    private void setPq(String item, String value, Consumer<Result> cb) {
        GmpfClient.PqItem it;
        switch (item) {
            case "brightness": it = GmpfClient.PqItem.BRIGHTNESS; break;
            case "contrast": it = GmpfClient.PqItem.CONTRAST; break;
            case "saturation": it = GmpfClient.PqItem.SATURATION; break;
            case "sharpness": it = GmpfClient.PqItem.SHARPNESS; break;
            default: refuse(cb, null); return;
        }
        final int v;
        try { v = Integer.parseInt(value.trim()); } catch (NumberFormatException e) { refuse(cb, null); return; }
        if (v < 0 || v > 100) { refuse(cb, null); return; }
        write("pq " + it + " " + v, cb, (g, g2) -> {
            if (!g.setPq(it, v)) throw new IllegalStateException("pq " + it + " false");
            return null;
        });
    }

    /** Sound output: AudioOutputChoice + CEC System Audio (off for the speaker, on for the soundbar / Auto). Main thread. */
    private void setOutput(String value, Consumer<Result> cb) {
        String kind = HbMenuGates.outputKind(value);
        if (kind == null) { refuse(cb, null); return; }
        try {
            AudioOutputChoice.Option pick = null;
            for (AudioOutputChoice.Option o : HbOutputOptions.rows(app).options) if (o.kind.name().equals(kind)) pick = o;
            if (pick == null) {                                         // not connected now: the HB Sound page lists none
                Log.i(TAG, "output " + value + ": not offered now");
                Ui.main().post(() -> cb.accept(Result.refused(null, state(last))));
                return;
            }
            AudioOutputChoice.choose(app, pick.kind, pick.addr);
            org.lumen.projector.cec.HbSystemAudio.onChoice(app, kind);
        } catch (Throwable t) {
            Log.w(TAG, "output " + value + ": " + t);
            Ui.main().post(() -> cb.accept(Result.failed(state(last))));
            return;
        }
        Ui.main().post(() -> cb.accept(Result.done(state(last))));
    }

    private void setFormat(String value, Consumer<Result> cb) {
        Integer ord = HbMenuGates.FORMAT.wire(value);
        if (ord == null) { refuse(cb, null); return; }
        AudioOutputChoice.chooseFormat(app, AudioOutputChoice.Format.values()[ord]);
        Ui.main().post(() -> cb.accept(Result.done(state(last))));
    }

    /** Home screen: the menu confirmed it; LauncherSwitcher applies (after the first-run setup only). */
    private void setLauncher(String value, Consumer<Result> cb) {
        Integer w = HbMenuGates.LAUNCHER.wire(value);
        if (w == null || !KeyReceiver.isSetupComplete(app)) { refuse(cb, w == null ? null : "toast_setup_running"); return; }
        String mode = w == 1 ? org.lumen.projector.home.LauncherSwitcher.MODE_CLASSIC
                : org.lumen.projector.home.LauncherSwitcher.MODE_LUMEN;
        org.lumen.projector.home.LauncherSwitcher.apply(app, mode, true, "quick menu",
                (ok, holder, err) -> Log.i(TAG, "home screen " + mode + " -> " + ok + " " + holder));
        Ui.main().post(() -> cb.accept(Result.done(state(last))));
    }

    // ================================================================== run

    @Override
    public void run(String action, Consumer<Result> cb) {
        if (!implemented().contains(action)) { refuse(cb, null); return; }
        switch (action) {
            case Feature.KEYSTONE_AK_NOW:
                write("auto keystone", cb, (g, g2) -> {
                    if (topSpeedGame(g, g2)) return "panel_kst_game_mode";     // stock autoKeystone: gameModeFast
                    Ui.main().post(() -> Hal.requestKeystone(app, false));     // FOCUS 27
                    return null;
                });
                return;
            case Feature.KEYSTONE_FIT_NOW:
                Hal.requestKeystone(app, true);                                 // FOCUS 53(11)
                Ui.main().post(() -> cb.accept(Result.done(state(last))));
                return;
            case Feature.KEYSTONE_RESTORE:
                if (Hal.isKeystoneRunning() || AkOverlay.isActive()) { refuse(cb, "toast_kst_busy"); return; }
                write("restore default picture", cb, this::restore);
                return;
            case Feature.FOCUS_AF_NOW:
                Hal.requestAutofocus(app);
                Ui.main().post(() -> cb.accept(Result.done(state(last))));
                return;
            case Feature.PICTURE_RESET:
                write("TV 164 reset basic + professional", cb, (g, g2) -> {
                    if (!g2.resetPicture(Gmpf2Client.PictureReset.BASIC)) throw new IllegalStateException("164 basic false");
                    if (!g2.resetPicture(Gmpf2Client.PictureReset.PROFESSIONAL)) throw new IllegalStateException("164 professional false");
                    return null;
                });
                return;
            case Feature.PICTURE_RESET_ALL:
                write("TV 164 reset all picture modes", cb, (g, g2) -> {
                    if (!g2.resetPicture(Gmpf2Client.PictureReset.ALL)) throw new IllegalStateException("164 all false");
                    return null;
                });
                return;
            default:
                refuse(cb, null);
        }
    }

    /**
     * HAL thread, stock HB KeyStoneManager.resetKeyStone: 38(0), 84, 85(0,0) only if 84 read 6, then 63(5,10) lens home
     * with the optical zoom; real-time keystone paused around it; refused in top speed and while an AK runs.
     */
    private String restore(GmpfClient g, Gmpf2Client g2) throws Exception {
        GmpfClient.GameModeOption o = g.getGameModeOption();
        if (o != null && o.isTopSpeed()) return "panel_kst_game_mode";
        if (Hal.isKeystoneRunning() || AkOverlay.isActive()) return "toast_kst_busy";
        boolean paused = ManualKeystonePanel.pauseRealtime(app, g, "menu reset");
        try {
            int mode = g.resetKeystoneStock();
            Log.i(TAG, "restore: 38(0) sent, 84 -> " + mode);
            if (HbFeatures.on(HbFeatures.F.OPTICAL_ZOOM)) {
                try { Log.i(TAG, "restore: 63(5,10) lens home -> " + g.zoomHome()); } catch (Throwable t) { Log.w(TAG, "63: " + t); }
            }
            org.lumen.projector.game.GameProfile.noteKeystoneFullFrame(app, "quick menu restore");
            Ui.main().post(() -> Notify.show(app, app.getString(R.string.kst_restore_done_hb)));
        } finally {
            if (paused) ManualKeystonePanel.resumeRealtime(app, g, "menu reset");
        }
        return null;
    }

    // ================================================================== open

    @Override
    public boolean open(String screen, Runnable back) {
        if (!implemented().contains(screen)) return false;
        Snapshot sn = last;
        boolean topSpeed = sn != null && sn.isTopSpeedGame();
        switch (screen) {
            case Feature.KEYSTONE_MANUAL:
                ManualKeystonePanel.open(app, back);
                return true;
            case Feature.INPUT:
                if (!setupDone("input")) return false;
                SourceOverlay.showFromQuickPanel(app);                      // the HB input list (HDMI 1 / 2, Android)
                return true;
            case Feature.ZOOM:
                if (topSpeed) { Notify.show(app, app.getString(R.string.panel_kst_game_mode)); return false; }
                OpticalZoomPanel.open(app, back);
                return true;
            case Feature.FOCUS_MANUAL:
                Hal.openManualFocus(app);
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
                return setupDone("usb files") && start(usbFilesIntent());
            case Feature.PICTURE_PRO:
            case Feature.SETTINGS_CEC:
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


    /** HAL thread: HDMI 1 / 2 on screen now (TV 4), or an open org.lumen.tvinput HDMI session. */
    private boolean hdmiNow(GmpfClient g) {
        int src = -1;
        try { src = g.getCurrentInputSource(); } catch (Throwable t) { Log.w(TAG, "TV 4: " + t); }
        if (src == GmpfClient.SOURCE_HDMI1 || src == GmpfClient.SOURCE_HDMI2) return true;
        return Boolean.TRUE.equals(TifHdmiState.read(app));
    }

    /** HAL thread: top-speed game mode running (77 top speed and 148 state on), stock gameModeFast. */
    private static boolean topSpeedGame(GmpfClient g, Gmpf2Client g2) {
        try {
            GmpfClient.GameModeOption o = g.getGameModeOption();
            if (o == null || !o.isTopSpeed()) return false;
            int[] p = g2.getGameModeProp();
            return p == null || Gmpf2Client.gameModeOf(p, o.wire) < 0 || p[0] == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** HAL thread: game mode on for the picture rules (stock isGameModeBasic): picture mode 4, or 148 state on HDMI. */
    private boolean gameModeOn(GmpfClient g, Integer pictureMode) {
        if (pictureMode != null && pictureMode == PICTURE_MODE_GAME) return true;
        Snapshot sn = last;
        if (sn != null && sn.gameActive55 != null) return sn.gameActive55 && hdmiNow(g);
        return prefs.getInt(QuickPanel.KEY_GAME_MODE, -1) == 1 && hdmiNow(g);
    }

    private static Boolean safeBoost(Gmpf2Client g2) {
        try { return g2.isBoost(); } catch (Throwable t) { return null; }
    }

    /** HAL thread. DISPLAY 174 getDynamicBlackType() == 1, null when unreadable. */
    private static Boolean dynamicBlack(Gmpf2Client g2) {
        try { return g2.getDynamicBlack(); } catch (Throwable t) { return null; }
    }

    /** HAL thread: the stock notice when the vendor turned dynamic black off after our setter (event 19 DB_OFF). */
    private void dynamicBlackOff(Gmpf2Client g2, Boolean before, int causeRes) {
        if (!Boolean.TRUE.equals(before)) return;
        if (!Boolean.FALSE.equals(dynamicBlack(g2))) return;
        Log.i(TAG, "dynamic black turned off by the vendor after " + app.getString(causeRes));
        Ui.main().post(() -> Notify.show(app, app.getString(R.string.pro_dynamic_black_off_hb, app.getString(causeRes))));
    }
}
