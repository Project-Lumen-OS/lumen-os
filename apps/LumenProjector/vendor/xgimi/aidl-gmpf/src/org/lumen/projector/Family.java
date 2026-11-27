// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioManager;
import android.os.Handler;
import android.util.Log;

import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.HbFeatures;

/**
 * What the AIDL gmpf family (the Z9X HB, its three model codes) answers where the one projector core asks its family
 *. Every HAL family adapter has this class with the same members:
 * the core is one class per function, the family's stock is the values and the HAL reads here.
 */
public final class Family {
    private Family() {}

    /**
     * power.VendorSleep, HAL thread: the stock prepare's vendor calls (power/StrPrepare, power/GmpfStrVendor) on
     * the HB (G0118, G0130 6.16.24, G0148 6.17.12): IPowerManager 6, whose type 0 releases gmpf_wake_lock
     * (PowerManagerImpl.goToSleep, Ghidra on both gmpf-service builds); without it only the first sleep after a cold
     * boot reached STR (GitHub issue 5); null when the HAL has no goToSleep.
     */
    public static org.lumen.projector.power.StrPrepare.Vendor strVendor(GmpfClient g) {
        return org.lumen.projector.power.GmpfStrVendor.of(g);
    }

    /**
     * power.StandbyActivity (hb-t5): after a wake by OK the window keeps the focus until the waking press is released
     * and this long has passed, so Lumen Home never acts on the OK UP of the wake (the lamp needs ~300 ms anyway).
     */
    public static final long WAKE_HOLD_MS = 600;
    /** power.StandbyActivity: the hold never lasts longer, also with the waking key held down. */
    public static final long WAKE_HOLD_MAX_MS = 1500;

    /**
     * dream.DreamSettings (hb-t8c fix2): the stock HB screensaver timer 1:1 (newsettings res/raw/screen.json node
     * screen_waiting_time_close): Never, 1, 5, 10, 30 min, 1 h in that order (DreamSettings.DELAY_NEVER = Never).
     */
    public static final int[] DREAM_DELAYS = {-1, 1, 5, 10, 30, 60};
    /** dream.DreamSettings: the stock default is Never (def_screen_off_timeout 2147483647), applied once. */
    public static final boolean DREAM_DEFAULT_NEVER = true;
    /** lumen-hdmi InactiveSleep: sleep on the watched device's <Inactive Source> when it is not back in 5 s and no key
     *  was pressed. Off until the owner decides (2026-11-26); RelinkHost.inactiveSourceSleep reads it. */
    public static final boolean INACTIVE_SOURCE_SLEEP = false;

    /** ManualFocusActivity: not ported to the HB (its stock wheel path is not read yet). */
    public static final boolean FOCUS_WHEEL = false;

    /** audio.SoundEq: {@link #eqMode} when the vendor owns the PEQ (nothing is sent). */
    public static final int EQ_VENDOR = Integer.MIN_VALUE;

    /**
     * audio.SoundEq, HAL thread: the sound mode (62) whose curve Lumen applies, or {@link #EQ_VENDOR}. The HB has no
     * Harman / DTS:X process (26 / 43 are not sent there): the feature
     * gate HbFeatures.F.EQUALIZER decides, PENDING until a device check of the HB speaker path turns it ON.
     */
    public static int eqMode(GmpfClient g, String why, String tag) {
        if (!HbFeatures.on(HbFeatures.F.EQUALIZER)) {
            Log.i(tag, why + ": equalizer is " + HbFeatures.state(HbFeatures.F.EQUALIZER) + " on the HB, nothing sent");
            return EQ_VENDOR;
        }
        int mode = -1;
        try { mode = g.getSoundEffectRaw(); } catch (Throwable t) { Log.w(tag, why + ": 62 " + t); }
        return mode;
    }

    /** audio.AudioPathReporter: the audio device callback, registered at once (an HB trial review is pending). */
    public static void registerAudioDevices(Context app, AudioDeviceCallback cb, Handler h, String tag) {
        AudioManager am = app.getSystemService(AudioManager.class);
        if (am != null) am.registerAudioDeviceCallback(cb, h);
    }

    /**
     * setup.SetupBridgeProvider: whether the HB stock has the Setup action or toggle {@code what}: fit needs
     * CURTAIN_FIT ON (whatever 290 reads), the manual keystone panel MANUAL_KEYSTONE ON, a toggle its feature ON
     *; Setup hides a row on "unsupported".
     */
    public static boolean setupHas(String what) {
        switch (what) {
            case "kst_fit":
            case "curtain_fit_state":
            case "CURTAIN_FIT": return HbFeatures.on(HbFeatures.F.CURTAIN_FIT);
            case "kst_manual": return HbFeatures.on(HbFeatures.F.MANUAL_KEYSTONE);
            case "OBSTACLE": return HbFeatures.on(HbFeatures.F.OBSTACLE_AVOID);
            case "MOVE_AK": return HbFeatures.on(HbFeatures.F.MOVE_AK);
            default: return true;
        }
    }

    /** audio.AudioOutputChoice: the HB kernel senses no plug, the wired output is never an Auto candidate (AutoOutput). */
    public static final boolean JACK_SENSED = false;

    /** audio.AudioOutputChoice: start of an evaluation (HbWiredJack.keep); true when it connected the output now. */
    public static boolean jackKeep(android.media.AudioManager am, android.media.audiopolicy.AudioProductStrategy media,
                                   org.lumen.projector.audio.AudioOutputKind chosen, String why) {
        return org.lumen.projector.audio.HbWiredJack.keep(am, media, chosen, why);
    }

    /** audio.AudioOutputChoice: end of an evaluation, after the preferred device (HbWiredJack.settle). */
    public static void jackSettle(android.media.AudioManager am, android.media.audiopolicy.AudioProductStrategy media,
                                  org.lumen.projector.audio.AudioOutputKind chosen, String why) {
        org.lumen.projector.audio.HbWiredJack.settle(am, media, chosen, why);
    }

    /** panel.ManualKeystonePanel: the stock title (newsettings KeyStoneWind.resetView :1733, keystone_txt_title). */
    public static final int KST_TITLE = R.string.kst_precise_hb;
    /** panel.ManualKeystonePanel: setKstPrepare(x, 0) is the refused REVIEW row 9.3 (type 0): HW warp only (hb-t8c). */
    public static final boolean KST_PREPARE = false;

    /** panel.ManualKeystonePanel, HAL thread, 647 is top speed: the HDMI input alone locks the screen (no 55 / 666 read). */
    public static boolean kstGameLocked(GmpfClient g, Gmpf2Client g2, int wire647, boolean hdmi, StringBuilder st, String tag) {
        return hdmi;
    }

    /** panel.ManualKeystonePanel: no 3D on the HB stock (support_3D=0): never refused, nothing read. */
    public static int kst3dReason(GmpfClient g, StringBuilder st, String tag) {
        return 0;
    }

    /** panel.ManualKeystonePanel: no screen state call on the HB stock screen. */
    public static void kstScreenOpen(Gmpf2Client g2, boolean open, String tag) {
    }

    /**
     * panel.ManualKeystonePanel, HAL thread, hold OK (hb-t8b): the stock "precise correction" reset KeyStoneWind.onKey
     * :1203-1212 -> setIChipReset = 38(0), 84, 85(0,0) only after 84 returned 6 (GmpfClient.resetKeystoneStock), the
     * corners only. Null with KEYSTONE_RESET off (the full frame path runs alone); refused in top-speed game mode by
     * resetKeystoneStock itself (it throws: nothing else runs).
     */
    public static String kstStockReset(GmpfClient g, String tag) throws android.os.RemoteException {
        if (!HbFeatures.on(HbFeatures.F.KEYSTONE_RESET)) return null;
        int mode = g.resetKeystoneStock();                    // 38(0), 84, 85(0,0) only if 84 returned 6
        return "38(0) sent, 84 -> " + mode;
    }

    /** panel.ProjectorMenu: the shared menu's adapter of this family. */
    public static org.lumen.menu.ProjectorFeatures menuFeatures(Context app) {
        return new org.lumen.projector.panel.HbMenuFeatures(app);
    }

    /** panel.ProjectorMenu: the family's own pages of the shared menu. */
    public static org.lumen.projector.panel.HbMenuPages menuPages(Context app, org.lumen.menu.ProjectorFeatures f) {
        return new org.lumen.projector.panel.HbMenuPages(app);
    }

    /** panel.ProjectorMenu: the menu's windows in the menu's own overlay host. */
    public static org.lumen.projector.ui.PanelHost menuHost(Context app) {
        return null;
    }

    /** panel.ProjectorMenu: the owner builds a new menu on a locale change itself (QuickPanelController, ProjectorMenu.reset). */
    public static final boolean MENU_LOCALE_REBUILD = false;

    /** panel.ProjectorMenu: the game menu io, only when this unit has game items (HbFeatures GAME_MODE + GAME_BAR). */
    public static org.lumen.projector.panel.HbGameIo gameIo(Context c) {
        return org.lumen.projector.panel.HbMenuFeatures.gameItems().isEmpty() ? null : new org.lumen.projector.panel.HbGameIo(c);
    }

    /** panel.QuickPanelController: no 3D on the stock (support_3D=0): a 3D popup request opens the card. */
    public static final boolean HAS_3D = false;
    /** panel.QuickPanelController: BACK of the manual keystone goes to the keystone page of the menu (stock HB). */
    public static final boolean KST_BACK_TO_MENU = true;
    /** KeyReceiver (HB beta6): the power menu opening while deep sleep is blocked says why (power.StrNotice). */
    public static final boolean STR_NOTICE = true;

    /** App: direct boot aware, the modules start once the user is unlocked. */
    public static final boolean DIRECT_BOOT = true;
    /** App.onCreate, before HalController: the feature table (HbFeatures) of the unit's model code (UnitModel). */
    public static void init(Context app, String model) {
        HbFeatures.init(app, model);
    }

    /** App.startModules: CEC volume control / System Audio follow the sound output choice (start, choice, listener). */
    public static void installCec(Context app) {
        org.lumen.projector.cec.HbSystemAudio.install(app);
    }

    /** App screen receiver: nothing at SCREEN_ON on this family (no CEC call in the wake window, cec-fix-1127). */
    public static void onScreenOn() {
    }

    /** App: the audio policy of this family needs no wait, {@code r} runs now. */
    public static void whenAudioUp(Context app, String tag, Runnable r) {
        r.run();
    }

    /** App: no stock rule set to choose on this family (no 3D, the default AF card keys). */
    public static void useStockRules() {
    }

    /** App: the console profile writes game settings by itself: only with HbFeatures.F.GAME_MODE ON. */
    public static boolean gameMode() {
        return HbFeatures.on(HbFeatures.F.GAME_MODE);
    }

    /** App: the signal panel's tokens of this family's gmpf values. */
    public static org.lumen.projector.panel.HbSignalTokens signalTokens() {
        return new org.lumen.projector.panel.HbSignalTokens();
    }

    /** App: the interface resolution row only with HbFeatures.F.UI_RESOLUTION ON (PENDING). */
    public static boolean uiResolution() {
        return HbFeatures.on(HbFeatures.F.UI_RESOLUTION);
    }

    // ---- panel.ProPicture: the stock HB professional settings (hb-t8b, newsettings settings_tree_product_dapengse.json
    // :208-354, picture.json; seen on the stock HB 2026-10-30)
    /** White balance opens the 2-point and 11-point pages under the source title, each page with its reset row. */
    public static final boolean PRO_WB_PAGES = true;
    /** The IGmpf2 278 parts of the reset rows of the 2-point, 11-point and colour correction pages (stock
     *  PictureViewModel.postAction :3282-3284, :3557-3559, :3543-3545). */
    public static final org.lumen.projector.hal.PictureReset[] PRO_PAGE_RESETS = {org.lumen.projector.hal.PictureReset.WB_2_POINT,
            org.lumen.projector.hal.PictureReset.WB_11_POINT, org.lumen.projector.hal.PictureReset.COLOR_CORRECTION};
    /** Colour optimisation, local contrast and dynamic contrast hide while an HDR signal plays. */
    public static final boolean PRO_HDR_HIDES = true;
    /** An HLG signal offers HLG and ST.2084 (PictureViewModel.freshGammaValue :4914). */
    public static final boolean PRO_HLG_PQ = true;
    /** The picture sliders coalesce (4PDA report 2026-11-10: the picture flickered on fast slides). */
    public static final boolean SLIDE_COALESCE = true;
    /** The stock labels: 2-point / 11-point pages, Сегментация, Контраст, Гамма-индекс, Динамическое улучшение уровня
     *  черного, Четкость, Снижение шума, Сбросить до значений по умолчанию under the title Больше. */
    public static final int PRO_WB_2 = R.string.pro_wb_2pt_hb, PRO_WB_11 = R.string.pro_wb_11pt_hb,
            PRO_WB_POINT = R.string.pro_segmentation_hb, PRO_CONTRAST = R.string.pro_contrast_hb,
            PRO_GAMMA_VALUE = R.string.pro_gamma_index_hb, PRO_DYN_BLACK = R.string.pro_dyn_black_hb,
            PRO_CLARITY = R.string.pro_clarity_hb, PRO_NOISE = R.string.pro_noise_hb,
            PRO_RESET = R.string.pro_reset_default_hb, PRO_MORE = R.string.more_hb;
    /** Выкл, Слабый, Средний, Сильный, Авто. */
    public static final int[] PRO_LEVELS = {R.string.pro_off_hb, R.string.pro_weak_hb, R.string.pro_medium_hb,
            R.string.pro_strong_hb, R.string.pro_auto_hb};

    /** panel.ProPicture: original frame rate is not in the HB tree (HbFeatures). */
    public static boolean proNativeFps() {
        return HbFeatures.on(HbFeatures.F.NATIVE_FRAME_RATE);
    }

    /** panel.ProPicture: hb-t6, hidden in the AI picture modes the vendor refuses 112 in (ColorSpaceRule). */
    public static boolean proColorSpaceShown(Integer pictureMode, Integer colorSpace) {
        return org.lumen.projector.panel.ColorSpaceRule.shown(pictureMode, colorSpace);
    }

    /**
     * panel.ProPicture: the white balance page title, stock image_mode_two_title (hb-t8b): the source (Android =
     * online_local_video, else HDMI 1 / HDMI 2) and the signal (SDR, HDR10, HLG, HDR10+, Dolby Vision, HDR Vivid).
     * Empty when unknown.
     */
    public static String proSourceTitle(Context app, Integer source, Integer hdr) {
        if (source == null) return "";
        String src = source == GmpfClient.SOURCE_HDMI1 ? "HDMI 1" : source == GmpfClient.SOURCE_HDMI2 ? "HDMI 2"
                : app.getString(R.string.pro_src_media_hb);
        if (hdr == null) return src;
        String sig = hdr == GmpfClient.HDR_SDR ? "SDR" : hdr == GmpfClient.HDR_HDR10 ? "HDR10"
                : hdr == GmpfClient.HDR_HLG ? "HLG" : hdr == GmpfClient.HDR_HDR10_PLUS ? "HDR10+"
                : hdr == GmpfClient.HDR_DOLBY_VISION ? "Dolby Vision" : hdr == GmpfClient.HDR_VIVID ? "HDR Vivid" : null;
        return sig == null ? src : src + "  " + sig;
    }

    /**
     * panel.ProPicture, HAL thread, a colour space choice. hb-t7 (Z9X 1.0.8 parity brightness item 10): stock
     * DisplayHelper.mutexColorAdjustmentBoost (newsettings Mutex_release_838): Boost keeps its colour space unless
     * feature support_boost_color_space says otherwise; the G0130 feature.xml has no such key (default "0" = mutex)
     * and dispcomm.ini LensDispSupportColorSpace = {1, 0}. After 112 the HDR note when the read-back disagrees
     * (ColorSpaceRule), and stock re-reads dynamic black (PictureViewModel :4623 refreshBlackEnhance): SystemUI shows
     * "Colour space disables dynamic black enhancement" for the vendor's DB_OFF:switchCS (DynamicBlackToastHelper).
     */
    public static void proColorSpace(Context app, CharSequence title, GmpfClient g, Gmpf2Client g2,
                                     org.lumen.projector.hal.ColorSpace cs, String tag) throws Exception {
        if (g2.isBoost()) {
            Log.i(tag, "112 refused: Boost is on");
            org.lumen.projector.ui.Notify.show(app, title, app.getString(R.string.boost_blocks_change));
            return;
        }
        Boolean db = opt("g2 29", g2::getDynamicBlack, tag);
        g.setColorSpace(cs);
        Integer hdr = opt("620", g::getHdrType, tag);
        Integer src = opt("696", g::getCurrentInputSource, tag);
        Integer pm = opt("666", g::getPictureModeRaw, tag);
        // the per-input picture tables: HDMI 1 / 2 as is, every other input 0 (stock PictureManager, as ProPicture.read)
        Integer back = src == null || pm == null ? null : opt("111", () -> g.getColorSpaceRaw(
                src == GmpfClient.SOURCE_HDMI1 || src == GmpfClient.SOURCE_HDMI2 ? src : 0, pm), tag);
        if (org.lumen.projector.panel.ColorSpaceRule.hdrRefused(hdr != null && hdr != GmpfClient.HDR_SDR, cs.wire, back)) {
            Log.i(tag, "112 not kept under HDR " + hdr + ": reads " + back);
            org.lumen.projector.ui.Notify.show(app, title, app.getString(R.string.pro_cs_hdr_hb));
        }
        if (Boolean.TRUE.equals(db) && Boolean.FALSE.equals(opt("g2 29", g2::getDynamicBlack, tag))) {
            org.lumen.projector.ui.Notify.show(app, app.getString(R.string.pro_dynamic_black_off_hb,
                    app.getString(R.string.pro_color_space)));
        }
    }

    /** panel.ProPicture: the HB stock refuses no dynamic black change (nothing read). */
    public static boolean proDynamicBlackRefused(Context app, CharSequence title, GmpfClient g, String tag) {
        return false;
    }

    /** panel.ProPicture: no original frame rate row on the HB (proNativeFps), nothing refused. */
    public static boolean proNativeFpsRefused(Context app, CharSequence title, GmpfClient g, String tag) {
        return false;
    }

    // ---- panel.OpticalZoomPanel: the HB lens (hb-t5 / hb-t6, stock G0130 newsettings ZoomStateView)
    /** OK does nothing on the zoom screen (no picture position there on the HB stock). */
    public static final boolean ZOOM_SHIFT = false;
    /** No jump stop: the stock dial reads the lens again after every move. */
    public static final boolean ZOOM_JUMP_STOP = false;
    /** A move the lens refuses at the last position of an end drops that position from the dial (ZoomScale). */
    public static final boolean ZOOM_END_SHRINK = true;
    /** The log names of a limit position move (189) and an optical one (95). */
    public static final String ZOOM_LIMIT_CALL = "189", ZOOM_OPTICAL_CALL = "95";

    /** panel.OpticalZoomPanel, main thread: no refusal rule before the screen on the HB stock, {@code open} now. */
    public static void zoomOpen(Context app, String tag, Runnable open) {
        open.run();
    }

    /** panel.OpticalZoomPanel, HAL thread: the session start, 185 setKstPrepare(true, 2) and 93 zoomStart(0). */
    public static String zoomEnter(GmpfClient g, Gmpf2Client g2) throws Exception {
        return "185(true,2) -> " + g2.setKstPrepareForZoom(true) + ", 93(0) -> " + g.zoomSessionStart();
    }

    /** panel.OpticalZoomPanel: the limit positions of 63(12,10) count (zoom.ini zoomLimitStepEn). */
    public static boolean zoomHasLimit(Gmpf2Client g2) {
        return true;
    }

    /** panel.OpticalZoomPanel, HAL thread: the limit position, 190. */
    public static int zoomLimitStep(GmpfClient g, Gmpf2Client g2) throws Exception {
        return g.getZoomLimitStep()[1];
    }

    /** panel.OpticalZoomPanel, HAL thread: one limit position move, 189. */
    public static int zoomLimitMove(GmpfClient g, Gmpf2Client g2, int step) throws Exception {
        return g.setOpticalZoomLimitStep(step);
    }

    /** panel.OpticalZoomPanel, HAL thread: 94(0), 185(false, 2); false when one failed. */
    public static boolean zoomClose(GmpfClient g, Gmpf2Client g2, String tag) {
        boolean ok = true;
        try { Log.i(tag, "94(0) -> " + g.zoomSessionFinish()); } catch (Throwable t) { ok = false; Log.w(tag, "94: " + t); }
        try { Log.i(tag, "185(false,2) -> " + g2.setKstPrepareForZoom(false)); } catch (Throwable t) { ok = false; Log.w(tag, "185(false,2): " + t); }
        return ok;
    }

    private interface Read<T> { T get() throws Exception; }

    /** A read whose failure only leaves a value unknown (null), as the page's own reads. */
    private static <T> T opt(String what, Read<T> r, String tag) {
        try {
            return r.get();
        } catch (Throwable t) {
            Log.d(tag, "read " + what + ": " + t);
            return null;
        }
    }

    /** cec.EarcLink: the log tag and the thread of the eARC link watch. */
    public static final String EARC_TAG = "Z9xEarc", EARC_THREAD = "z9x-earc";

    /** cec.EarcLink: one getEARCStatus read (HB TV 79) on the HAL thread, its value to status there. */
    public static void earcStatus(java.util.function.IntConsumer status) {
        Hal.run((g, g2) -> {
            int s = g.getEarcStatus();
            status.accept(s);
        });
    }

    /** cec.EarcLink: r after every HAL (re)connect. */
    public static void whenHalConnected(Runnable r) {
        Hal.addConnectedListener(r);
    }

    /** cec.EarcLink: the HAL is connected now. */
    public static boolean halReady() {
        return Hal.isReady();
    }

    /** cec.EarcLink: this boot's id (the output it connected is stored with it). */
    public static String bootId() {
        return Hal.bootId();
    }

    /** cec.EarcLink: the link moved to or from eARC, the output format is sent again. */
    public static void earcFormatAgain(String why) {
        org.lumen.projector.audio.AudioOutputChoice.resendFormat(why);
    }

    // ---- the log tags and thread names of the core classes that name their family (BootSource, ManualFocusActivity)
    /** The prefix of the core classes' log tags on this family. */
    public static final String TAG_PREFIX = "Z9x";
    /** The prefix of the core classes' thread names and preference files on this family. */
    public static final String THREAD_PREFIX = "z9x";

    // ---- voice.AssistantHost
    /** voice.AssistantHost: the assistant keeps its full animations (no weak GPU on this family). */
    public static final boolean ASSIST_LOW_POWER = false;

    /** voice.AssistantHost: the keystone the assistant asks for (no fit to the wall). */
    public static void assistKeystone(Context c) {
        Hal.requestKeystone(c, false);
    }

    // ---- ManualFocusActivity: the HAL state before the window opens or comes back, its focus motor
    /** ManualFocusActivity.open: true (and the notice shown) when the HAL feature is off or not connected. */
    static boolean focusOpenRefused(Context ctx) {
        if (!HalController.featureEnabled()) { Ui.featureOffNotice(ctx); return true; }
        if (!HalController.get(ctx).isConnected()) { Ui.toast(ctx, R.string.toast_not_ready); return true; }
        return false;
    }

    /** ManualFocusActivity.onResume: true (and the notice shown) when the HAL feature is off. */
    static boolean focusResumeRefused(Context ctx) {
        if (!HalController.featureEnabled()) { Ui.featureOffNotice(ctx); return true; }
        return false;
    }

    /** ManualFocusActivity: the focus motor of this family's HAL. */
    static MotorController focusMotor(Context ctx) {
        return HalController.get(ctx).motor();
    }

    // ---- power.StandbyStateProvider (apps/common/projector): the lamp-only standby state and the CEC wake request
    /** power.StandbyStateProvider: the lamp-only standby is on (also while a standby of this boot waits to re-enter). */
    public static boolean standbyActive() {
        return org.lumen.projector.power.StandbyController.isActive();
    }

    /** remote/ProjectorRemoteHost: a power off from the lamp-only standby path is under way. */
    public static boolean standbyShutdownInFlight() {
        return org.lumen.projector.power.StandbyController.shutdownInFlight();
    }

    /** power.StandbyStateProvider "cec_wake": the CEC wake request of org.lumen.tvinput, answered by the cec module. */
    public static android.os.Bundle cecWake(Context ctx, android.os.Bundle extras) {
        return org.lumen.projector.cec.CecPolicy.onWakeRequest(ctx, extras);
    }

    /** The NUI fifo reader of the gmpf vendor service: 148 / 152 / 114 only with it up (eye/EyeGuard). */
    public static final String NUI_READY_PROP = org.lumen.projector.hal.GmpfClient.NUI_READY_PROP;

    public static boolean nuiReaderReady() {
        return org.lumen.projector.hal.GmpfClient.nuiReaderReady();
    }

    /** The vendor UI watchdog while the eye mask is up (114 / 115, eye/EyeWatchdog). Main thread. */
    public static void eyeWatchdog(boolean on) {
        org.lumen.projector.eye.EyeWatchdog.arm(on);
    }

    /** power.SleepTimer: the lamp steps down with the timer fade (power/TimerLamp, IGmpf 176). */
    public static final boolean TIMER_LAMP = true;

    public static void timerLampDown(Context app, int level, long ms) {
        org.lumen.projector.power.TimerLamp.fadeDown(app, level, ms);
    }

    /** power.SleepTimer at the end or abort of the fade: the user's lamp level back (IGmpf 177). */
    public static void timerLampRestore() {
        org.lumen.projector.power.TimerLamp.restore();
    }

    /** KeyReceiver: stop a manual focus move (posts; sends 2 only if the motor moves). */
    static void motorStop(Context ctx) {
        HalController.get(ctx).motor().stop();
    }

    /** KeyReceiver: the focus motor of the focus step keys. */
    static MotorController motor(Context ctx) {
        return HalController.get(ctx).motor();
    }

    /** KeyReceiver: a focus step key while the projector features are off: the notice, true (the key does nothing). */
    static boolean focusStepRefused(Context ctx, boolean firstDown) {
        if (HalController.featureEnabled()) return false;
        if (firstDown) Ui.featureOffNotice(ctx);
        return true;
    }

    /** KeyReceiver: autofocus on the Focus key (1 s debounce inside). */
    static void requestAutofocus(Context ctx) {
        HalController.get(ctx).requestAutofocus();
    }

    /** KeyReceiver: Lumen OS installs (sys.InstallGuard): every global key is ignored but the eye mask's. */
    static boolean installing() {
        return org.lumen.projector.sys.InstallGuard.active();
    }

    /** KeyReceiver: the power menu opening while deep sleep is blocked says why (StrNotice, STR_NOTICE families). */
    static void strNoticeKey(Context ctx, android.view.KeyEvent ev) {
        if (STR_NOTICE) org.lumen.projector.power.StrNotice.onGlobalKey(ctx, ev);
    }

    /** KeyReceiver: the remote power key (power/PowerUi: lamp-only standby, fade + sleep, power menu). */
    static boolean powerKey(Context ctx, android.content.Intent intent, android.view.KeyEvent ev) {
        return org.lumen.projector.power.PowerUi.onGlobalKey(ctx, intent, ev);
    }

    /** KeyReceiver: no own use of the remote's shortcut key on this family. */
    static boolean isShortcutKey(android.view.KeyEvent ev) {
        return false;
    }

    static void shortcutKey(Context ctx) {
    }

    /** KeyReceiver: the gear long press of the menu (the game menu while game mode runs on HDMI, else the fallback). */
    static void gearLong(Context ctx, Runnable fallback) {
        org.lumen.projector.panel.ProjectorMenu.gearLong(ctx, fallback);
    }

    /** KeyReceiver: the Ambient key starts the screensaver only when the user's switch is on (dream.DreamSettings). */
    static boolean dreamEnabled(Context ctx) {
        return org.lumen.projector.dream.DreamSettings.isEnabled(ctx);
    }

    /** audio.AudioOutputChoice: the digital output format (digital_output_format, the ARC / eARC format row). */
    public static final boolean DIGITAL_FORMAT = true;
    /** audio.AudioOutputChoice: the selected row is the stored output only while media is really routed to it. */
    public static final boolean SELECTION_ROUTED = true;
    /** audio.AudioOutputChoice: no timed re-apply, the boot receiver re-applies at BOOT_COMPLETED. */
    public static final long AUDIO_REAPPLY_MS = 0;

    /** audio.AudioOutputChoice: the vendor reports the eARC link (cec/EarcLink): the ARC row says eARC. */
    public static boolean earcLinked() {
        return org.lumen.projector.cec.EarcLink.linked();
    }

    /** The auto-keystone overlay (ak/AkOverlay): this family's stock draws the AK screens, ak/AkScreen. */
    public static void akInstall(Context ctx) {
        org.lumen.projector.ak.AkScreen.install(ctx);
    }

    public static void akLocaleChanged() {
        org.lumen.projector.ak.AkScreen.onLocaleChanged();
    }

    public static boolean akActive() {
        return org.lumen.projector.ak.AkScreen.isActive();
    }

    public static boolean akEvent(int type, String value) {
        return org.lumen.projector.ak.AkScreen.onFocusEvent(type, value);
    }

    /** setup/SetupBridgeProvider: the device names of the ping reply (none: Setup offers its own), the remote module
     *  starts with the app (remote_state never says "starting"). */
    public static String[] setupNames() {
        return null;
    }

    public static final boolean REMOTE_STARTS_LATE = false;

    /** game/HdmiEventReceiver: the standby, the HDMI source, the relink and the game profile (game/HdmiEvents). */
    public static void hdmiEvent(Context ctx, android.content.Intent intent) {
        org.lumen.projector.game.HdmiEvents.onEvent(ctx, intent);
    }

    /** remote/ProjectorRemoteHost: this wake came from an HDMI device over CEC (cec/CecPolicy). */
    public static boolean cecWakeActive() {
        return org.lumen.projector.cec.CecPolicy.cecWakeActive();
    }

    // ---- the quick menu behind core QuickPanel: the shared menu through QuickPanelOwner / QuickPanelController
    public static void menuToggle(Context c) { org.lumen.projector.panel.QuickPanelOwner.toggle(c); }

    public static void menuShow(Context c, String section) { org.lumen.projector.panel.QuickPanelOwner.show(c, section); }

    public static void menuRefresh() { org.lumen.projector.panel.QuickPanelOwner.refreshIfShowing(); }

    public static Runnable menuRestorer() { return org.lumen.projector.panel.QuickPanelOwner.restorer(); }

    public static void menuExtension(String section, org.lumen.projector.panel.QuickPanel.Extension ext) {
        org.lumen.projector.panel.QuickPanelOwner.addExtension(section, ext);
    }

    public static void menuLocaleChanged(Context c) { org.lumen.projector.panel.QuickPanelOwner.onLocaleChanged(c); }

    // ---- the power actions of the shared power menu, sleep timer and Home tile (PowerKey.PowerActions)
    public static final long POWER_FADE_SHORT_MS = org.lumen.projector.power.PowerKey.PowerActions.FADE_SHORT_MS;

    public static boolean powerBusy() { return org.lumen.projector.power.PowerKey.PowerActions.isBusy(); }

    public static void powerSleepNow(Context c, String why) { org.lumen.projector.power.PowerKey.PowerActions.sleepNow(c, why); }

    public static void powerSleepWithFade(Context c, String why) { org.lumen.projector.power.PowerKey.PowerActions.sleepWithFade(c, why); }

    public static void powerOff(Context c) { org.lumen.projector.power.PowerKey.PowerActions.powerOff(c); }

    public static void powerRestart(Context c) { org.lumen.projector.power.PowerKey.PowerActions.restart(c); }

    // ---- panel.RotatePanel (and the other adjustment cards): the family's HAL steps and how keys become steps
    /** Keys are queued and sent as few steps as possible (false: one step at a time between two fresh reads). */
    public static final boolean ADJUST_COALESCE = false;
    /** An adjustment card whose HAL open did not run within this time fails (0: no watchdog). */
    public static final long ADJUST_WATCHDOG_MS = 0;

    public static boolean adjustHalReady() { return Hal.isReady(); }

    public static boolean adjustOpen() {
        return org.lumen.projector.panel.RotatePanel.isOpen() || org.lumen.projector.panel.ZoomShiftPanel.isOpen();
    }

    public static org.lumen.projector.panel.RotateIo rotateIo(Context app, GmpfClient g, Gmpf2Client g2) {
        return new org.lumen.projector.panel.GmpfRotateIo(app, g, g2);
    }

    public static void rotateInstall(Context app) { org.lumen.projector.panel.GmpfRotateIo.install(app); }
}
