// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.os.Handler;
import android.util.Log;

import org.lumen.projector.audio.AudioPorts;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.SoundProcess;
import org.lumen.projector.panel.PanelRules;
import org.lumen.projector.panel.RefusalReason;
import org.lumen.projector.panel.RuleNotes;

/**
 * What the HIDL gmpf family (the Z9X and the RS 20 line) answers where the one projector core asks its family
 *. Every HAL family adapter has this class with the same members:
 * the core is one class per function, the family's stock is the values and the HAL reads here.
 */
public final class Family {
    private Family() {}

    /** power.StandbyActivity: how long the window keeps the focus after a wake by OK (0: it closes at once). */
    public static final long WAKE_HOLD_MS = 0;
    /** power.StandbyActivity: the hold never lasts longer, also with the waking key held down. */
    public static final long WAKE_HOLD_MAX_MS = 0;

    /** dream.DreamSettings: the stock "Start after" choices in minutes (DreamSettings.DELAY_NEVER = Never). */
    public static final int[] DREAM_DELAYS = {5, 15, 30, 60, 120};
    /** dream.DreamSettings: the stock default of "Start after" is Never (the untouched Lumen default becomes it once). */
    public static final boolean DREAM_DEFAULT_NEVER = false;
    /** lumen-hdmi InactiveSleep: sleep on the watched device's <Inactive Source> when it is not back in 5 s and no key
     *  was pressed. Off until the owner decides (2026-11-26); RelinkHost.inactiveSourceSleep reads it. */
    public static final boolean INACTIVE_SOURCE_SLEEP = false;

    /** ManualFocusActivity: the stock window is the full-screen focus chart (FocusPattern, the HB stock SystemUI). */
    public static final boolean FOCUS_CHART = false;
    /** ManualFocusActivity: the Z9X stock systemui FocusAdjust.java:586-603: a mouse wheel moves the lens for 200 ms per notch. */
    public static final boolean FOCUS_WHEEL = true;

    /** audio.SoundEq: {@link #eqMode} when the vendor owns the PEQ (nothing is sent). */
    public static final int EQ_VENDOR = Integer.MIN_VALUE;

    /**
     * audio.SoundEq, HAL thread: the sound mode (62) whose curve Lumen applies, or {@link #EQ_VENDOR}. On this family
     * Lumen owns the PEQ only in the AMP (Harman) process: 43 false and 26 = Harman.
     */
    public static int eqMode(GmpfClient g, String why, String tag) {
        Boolean dts = null;
        Integer proc = null;
        int mode = -1;
        try { dts = g.getDtsEffects(); } catch (Throwable t) { Log.w(tag, why + ": 43 " + t); }
        try { proc = g.getSoundProcessRaw(); } catch (Throwable t) { Log.w(tag, why + ": 26 " + t); }
        try { mode = g.getSoundEffectRaw(); } catch (Throwable t) { Log.w(tag, why + ": 62 " + t); }
        if (!Boolean.FALSE.equals(dts) || proc == null || proc != SoundProcess.HARMAN.wire) {
            Log.i(tag, why + ": not the AMP (Harman) process (26=" + proc + " 43=" + dts + "): the vendor owns the PEQ, nothing sent");
            return EQ_VENDOR;
        }
        return mode;
    }

    /** audio.AudioPathReporter: the audio device callback, only while the audio policy answers (AudioPorts). */
    public static void registerAudioDevices(Context app, AudioDeviceCallback cb, Handler h, String tag) {
        AudioPorts.registerDeviceCallback(app, cb, h, tag);
    }

    /**
     * setup.SetupBridgeProvider: whether this family's stock has the Setup action or toggle {@code what}
     * (kst_fit, kst_manual, curtain_fit_state, or a toggle name). Every one is there on this family.
     */
    public static boolean setupHas(String what) {
        return true;
    }

    /** audio.AudioOutputChoice: the kernel reports a plug of the 3.5 mm jack (the wired output is an Auto candidate). */
    public static final boolean JACK_SENSED = true;

    /** audio.AudioOutputChoice: start of an evaluation; the jack needs no keeping on this family. */
    public static boolean jackKeep(android.media.AudioManager am, android.media.audiopolicy.AudioProductStrategy media,
                                   org.lumen.projector.audio.AudioOutputKind chosen, String why) {
        return false;
    }

    /** audio.AudioOutputChoice: end of an evaluation (nothing on this family). */
    public static void jackSettle(android.media.AudioManager am, android.media.audiopolicy.AudioProductStrategy media,
                                  org.lumen.projector.audio.AudioOutputKind chosen, String why) {
    }

    /** panel.ManualKeystonePanel: the title of the screen. */
    public static final int KST_TITLE = R.string.panel_kst_manual;
    /** panel.ManualKeystonePanel: 18 setKstPrepare(true, 0) at the first move (persist.z9x.kst_prepare can turn it off). */
    public static final boolean KST_PREPARE = true;

    /**
     * panel.ManualKeystonePanel, HAL thread, 647 is top speed: whether a top-speed game really runs on HDMI (stock
     * isGameKeystoneMutex: 55 state on or 666 == 4, and 647 = 2 / 3, KstGameLock; 55 unknown = 647 alone, as 1.0.7).
     * Appends the reads to {@code st}.
     */
    public static boolean kstGameLocked(GmpfClient g, Gmpf2Client g2, int wire647, boolean hdmi, StringBuilder st, String tag) {
        int[] p = null;
        Boolean game55 = null;                       // 55 state on; null = unread / does not fit 647
        Integer pic = null;                          // 666, read only when 55 says the game is off
        if (hdmi) {
            try { p = g2.getGameModeProp(); } catch (Throwable t) { Log.w(tag, "g2 55: " + t); }
            if (Gmpf2Client.gameModeOf(p, wire647) >= 0) game55 = p[0] == 0;
            if (Boolean.FALSE.equals(game55)) {
                try { pic = g.getPictureModeRaw(); } catch (Throwable t) { Log.w(tag, "666: " + t); }
            }
        }
        st.append(" 55=").append(java.util.Arrays.toString(p)).append(" game=").append(game55).append(" 666=").append(pic);
        return org.lumen.projector.panel.KstGameLock.refuse(wire647, hdmi, game55, pic);
    }

    /** panel.ManualKeystonePanel, HAL thread: the refusal text when 3D is on (192 / 193, the Z9X stock support_Trapezoid_3D=0), else 0. */
    public static int kst3dReason(GmpfClient g, StringBuilder st, String tag) {
        org.lumen.projector.panel.PanelRules.In in3 = new org.lumen.projector.panel.PanelRules.In();
        try { in3.fmt192 = g.getCurrent3DFormat(); } catch (Throwable t) { Log.w(tag, "192: " + t); }
        try { in3.to2d193 = g.is3DTo2DEnabled(); } catch (Throwable t) { Log.w(tag, "193: " + t); }
        st.append("192=").append(in3.fmt192).append(" 193=").append(in3.to2d193);
        return org.lumen.projector.panel.PanelRules.is3d(in3) ? R.string.panel_ultra120_3d : 0;
    }

    /** panel.ManualKeystonePanel, HAL thread: stock KeyStoneWind.java:708 / :1034, 194 setPushPullScreenState while open. */
    public static void kstScreenOpen(Gmpf2Client g2, boolean open, String tag) {
        int v = open ? 1 : 0;
        try { Log.i(tag, "194(" + v + ") -> " + g2.setAdjustScreenOpen(open)); } catch (Throwable t) { Log.w(tag, "194(" + v + "): " + t); }
    }

    /**
     * panel.ManualKeystonePanel, HAL thread, hold OK: the stock screen reset KeyStoneWind MENU > Reset ->
     * setCorrectKeystoneReset = setIChipReset, 207(0) and 129(0,0) if 130 reads 6 (GmpfClient.resetKeystoneCorners):
     * the corners only, no lens move. The log text of what was sent, or null when it failed (logged).
     */
    public static String kstStockReset(GmpfClient g, String tag) {
        try {
            g.resetKeystoneCorners();                         // 207(0), 129(0,0) only if 130 read 6
            return "207(0) sent";
        } catch (Throwable t) {
            Log.w(tag, "reset: 207 corners reset: " + t);
            return null;
        }
    }

    /** panel.ProjectorMenu: the shared menu's adapter of this family. */
    public static org.lumen.menu.ProjectorFeatures menuFeatures(Context app) {
        return new org.lumen.projector.panel.Z9xFeatures(app);
    }

    /** panel.ProjectorMenu: the family's own pages of the shared menu. */
    public static org.lumen.projector.panel.Z9xMenuPages menuPages(Context app, org.lumen.menu.ProjectorFeatures f) {
        return new org.lumen.projector.panel.Z9xMenuPages(app, (org.lumen.projector.panel.Z9xFeatures) f);
    }

    /** panel.ProjectorMenu: the menu's windows in the menu's own overlay host. */
    public static org.lumen.projector.ui.PanelHost menuHost(Context app) {
        return null;
    }

    /** panel.ProjectorMenu: the owner builds a new menu on a locale change itself (QuickPanelController, ProjectorMenu.reset). */
    public static final boolean MENU_LOCALE_REBUILD = false;

    /** panel.ProjectorMenu: the game menu io of this family (as 1.0.10: always). */
    public static org.lumen.projector.panel.Z9xGameIo gameIo(Context c) {
        return new org.lumen.projector.panel.Z9xGameIo(c);
    }

    /** panel.QuickPanelController: the stock has 3D (the 3D popup request opens it). */
    public static final boolean HAS_3D = true;
    /** panel.QuickPanelController: BACK of the manual keystone opened from outside the menu just closes it. */
    public static final boolean KST_BACK_TO_MENU = false;
    /** KeyReceiver: no deep sleep blocked notice (power.StrNotice) on this family. */
    public static final boolean STR_NOTICE = false;

    /** App: the process is not direct boot aware (it starts after the unlock): the modules start in onCreate. */
    public static final boolean DIRECT_BOOT = false;
    /** App.onCreate, before HalController (the unit's model code from the core, unused here): the family's tables (nothing on this family). */
    public static void init(Context app, String model) {
    }

    /** App.startModules: the family's own CEC modules (none on this family). */
    public static void installCec(Context app) {
    }

    /** App screen receiver: SCREEN_ON for the family's own modules (none on this family). */
    public static void onScreenOn() {
    }

    /** App: run {@code r} once the audio policy answers (AudioPorts: a trial without the user's audio files). */
    public static void whenAudioUp(Context app, String tag, Runnable r) {
        AudioPorts.whenUp(app, new Handler(android.os.Looper.getMainLooper()), tag, r);
    }

    /** App: the stock rule sets of this family: the 3D rule order of the shared menu's picture.3d, the stock AF card keys. */
    public static void useStockRules() {
        org.lumen.projector.display.ThreeD.useStockRules();
        AfCardOverlay.useStockKeys();
    }

    /** App: the console profile, the game menu, the signal and sensor cards and the wall colour run on this family. */
    public static boolean gameMode() {
        return true;
    }

    /** App: the signal panel's tokens of this family's gmpf values. */
    public static org.lumen.projector.panel.Z9xSignalTokens signalTokens() {
        return new org.lumen.projector.panel.Z9xSignalTokens();
    }

    /** App: the interface resolution row (display.UiResolution) runs on this family. */
    public static boolean uiResolution() {
        return true;
    }

    // ---- panel.ProPicture: the professional settings pages of the 1.0.8 stock (one white balance page)
    /** White balance is one page, its 2-point and 11-point parts under their headers (no own pages, no reset rows). */
    public static final boolean PRO_WB_PAGES = false;
    /** No reset rows on the white balance and colour correction pages. */
    public static final org.lumen.projector.hal.PictureReset[] PRO_PAGE_RESETS = null;
    /** Colour optimisation, local contrast and dynamic contrast stay under an HDR signal. */
    public static final boolean PRO_HDR_HIDES = false;
    /** An HLG signal offers the HLG curve only. */
    public static final boolean PRO_HLG_PQ = false;
    /** One write and one page read per slider commit. */
    public static final boolean SLIDE_COALESCE = false;
    /** The labels: the white balance parts, the 11-point level, the contrast title, gamma value, dynamic black, clarity,
     *  noise reduction, the reset (no title above it). */
    public static final int PRO_WB_2 = R.string.pro_wb_2, PRO_WB_11 = R.string.pro_wb_11, PRO_WB_POINT = R.string.pro_wb_point,
            PRO_CONTRAST = R.string.panel_pq_contrast, PRO_GAMMA_VALUE = R.string.pro_gamma_value,
            PRO_DYN_BLACK = R.string.pro_dynamic_black, PRO_CLARITY = R.string.pro_clarity, PRO_NOISE = R.string.pro_noise,
            PRO_RESET = R.string.pro_reset, PRO_MORE = 0;
    /** Off, low, medium, high, auto. */
    public static final int[] PRO_LEVELS = {R.string.panel_off, R.string.panel_low, R.string.panel_medium,
            R.string.panel_high, R.string.panel_auto};

    /** panel.ProPicture: the original frame rate row is on the page. */
    public static boolean proNativeFps() {
        return true;
    }

    /** panel.ProPicture: the colour space row whenever 111 answered. */
    public static boolean proColorSpaceShown(Integer pictureMode, Integer colorSpace) {
        return colorSpace != null;
    }

    /** panel.ProPicture: no source title on the white balance page (PRO_WB_PAGES false). */
    public static String proSourceTitle(Context app, Integer source, Integer hdr) {
        return "";
    }

    /**
     * panel.ProPicture, HAL thread, a colour space choice: 1.0.8 (parity brightness item 10) stock
     * colorAdjustmentMutexBoost, the 5-segment Boost mode keeps its colour space (G0082 has no support_boost_color_space;
     * dispcomm.ini LensDispSupportColorSpace {1, 0}); 2026-11-04 (stock picture_gamut_athenaultra): the Office mode
     * (666 == 25) first, then Boost. Then 112.
     */
    public static void proColorSpace(Context app, CharSequence title, GmpfClient g, Gmpf2Client g2,
                                     org.lumen.projector.hal.ColorSpace cs, String tag) throws Exception {
        PanelRules.In in = new PanelRules.In();
        in.pic666 = opt("666", g::getPictureModeRaw, tag);
        in.boost51 = opt("g2 51", g2::isBoost, tag);
        RefusalReason w = PanelRules.colorSpace(in);
        if (w != RefusalReason.NONE) {
            Log.i(tag, "112 refused: " + w);
            RuleNotes.show(app, title, w, true);
            return;
        }
        g.setColorSpace(cs);
    }

    /** panel.ProPicture, HAL thread: 2026-11-04 (stock image_dynamic_black_bit_enhancement): refused in Office (666 6 or 25). */
    public static boolean proDynamicBlackRefused(Context app, CharSequence title, GmpfClient g, String tag) {
        PanelRules.In in = new PanelRules.In();
        in.pic666 = opt("666", g::getPictureModeRaw, tag);
        return RuleNotes.show(app, title, PanelRules.dynamicBlack(in), true);
    }

    /**
     * panel.ProPicture, HAL thread, original frame rate on: 2026-11-04 (stock image_original_needle_display): refused
     * while Ultra 120 is on (157), then in 3D; the 161(6) after it is kept for what the rule lets through.
     */
    public static boolean proNativeFpsRefused(Context app, CharSequence title, GmpfClient g, String tag) {
        PanelRules.In in = new PanelRules.In();
        in.ultra120 = opt("157", () -> {
            org.lumen.projector.hal.OutputTimingInfo t = g.getOutputTimingInfo();
            return t == null ? null : t.isUltra120();
        }, tag);
        in.fmt192 = opt("192", g::getCurrent3DFormat, tag);
        in.to2d193 = opt("193", g::is3DTo2DEnabled, tag);
        return RuleNotes.show(app, title, PanelRules.nativeFpsOn(in), false);
    }

    // ---- panel.OpticalZoomPanel: the Z9X lens (stock V6.15.62 ZoomStateView)
    /** OK switches the arrows to the picture position (141 shift) and back (Lumen OS 1.0.8, parity keystone item 10). */
    public static final boolean ZOOM_SHIFT = true;
    /** A move whose read-back jumps more than one position stops the screen (zoom audit F4). */
    public static final boolean ZOOM_JUMP_STOP = true;
    /** A refused end position stays on the dial. */
    public static final boolean ZOOM_END_SHRINK = false;
    /** The log names of a limit position move and an optical one. */
    public static final String ZOOM_LIMIT_CALL = "IGmpf2 14", ZOOM_OPTICAL_CALL = "119";

    /** panel.OpticalZoomPanel, main thread: the 1.0.8 keystone refusal rules (fresh reads), then {@code open}. */
    public static void zoomOpen(Context app, String tag, Runnable open) {
        Hal.query((g, g2) -> PanelRules.keystone(RuleNotes.readKeystone(app, g, g2)), w -> {
            if (w != null && w != RefusalReason.NONE) {
                Log.i(tag, "open refused: " + w);
                RuleNotes.show(app, app.getString(R.string.panel_optical_zoom), w, false);
                return;
            }
            open.run();
        });
    }

    /**
     * panel.OpticalZoomPanel, HAL thread: the session start, its log text. Stock OpticsZoomShiftWind.java:350 / :670:
     * 194 setPushPullScreenState(1) around the screen, then IGmpf2 18(true, 2) and 121 zoomStart(0).
     */
    public static String zoomEnter(GmpfClient g, Gmpf2Client g2) throws Exception {
        return "194(1) -> " + g2.setAdjustScreenOpen(true) + ", 18(true,2) -> " + g2.setKstPrepareForZoom(true)
                + ", 121(0) -> " + g.zoomSessionStart();
    }

    /** panel.OpticalZoomPanel: the limit positions count only where the HAL has the limit calls (IGmpf2 13 / 14). */
    public static boolean zoomHasLimit(Gmpf2Client g2) {
        return g2.hasZoomLimit();
    }

    /** panel.OpticalZoomPanel, HAL thread: the limit position, IGmpf2 13 (0 or -1..-limit). */
    public static int zoomLimitStep(GmpfClient g, Gmpf2Client g2) throws Exception {
        return g2.getZoomLimitStep()[1];
    }

    /** panel.OpticalZoomPanel, HAL thread: one limit position move, IGmpf2 14. */
    public static int zoomLimitMove(GmpfClient g, Gmpf2Client g2, int step) throws Exception {
        return g2.setOpticalZoomLimitStep(step);
    }

    /** panel.OpticalZoomPanel, HAL thread: 120(0), 18(false, 2), 194(0); false when one of the first two failed. */
    public static boolean zoomClose(GmpfClient g, Gmpf2Client g2, String tag) {
        boolean ok = true;
        try { Log.i(tag, "120(0) -> " + g.zoomSessionFinish()); } catch (Throwable t) { ok = false; Log.w(tag, "120: " + t); }
        try { Log.i(tag, "18(false,2) -> " + g2.setKstPrepareForZoom(false)); } catch (Throwable t) { ok = false; Log.w(tag, "18(false,2): " + t); }
        try { Log.i(tag, "194(0) -> " + g2.setAdjustScreenOpen(false)); } catch (Throwable t) { Log.w(tag, "194: " + t); }
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
    /** cec.EarcLink: the ARC restart after a lost link (IHdmiControlService.setArcMode, which stock never sends). */
    public static final boolean ARC_RESTART = true;

    /** cec.EarcLink: one getEARCStatus read (Z9X IGmpf 619) on the HAL thread, its value to status there. */
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

    /** cec.EarcLink ARC restart: the stored sound output (an AudioOutputKind name, null = none stored). */
    public static String audioOutputKind(Context app) {
        org.lumen.projector.audio.AudioOutputKind k = org.lumen.projector.audio.AudioOutputChoice.kind(app);
        return k == null ? null : k.name();
    }

    /**
     * cec.EarcLink ARC restart: IHdmiControlService.setArcMode(true) through the framework's service behind
     * HdmiTvClient (HdmiClient.mService): setArcMode has no manager method, and the HAL gate keeps ServiceManager out
     * of this app. False without a TV client.
     */
    public static boolean arcModeOn(Context app) throws Exception {
        android.hardware.hdmi.HdmiControlManager m;
        try {
            m = app.getApplicationContext().getSystemService(android.hardware.hdmi.HdmiControlManager.class);
        } catch (Throwable t) {
            m = null;
        }
        android.hardware.hdmi.HdmiTvClient tv = m == null ? null : m.getTvClient();
        if (tv == null) return false;
        java.lang.reflect.Field f = tv.getClass().getSuperclass().getDeclaredField("mService");
        f.setAccessible(true);
        android.hardware.hdmi.IHdmiControlService s = (android.hardware.hdmi.IHdmiControlService) f.get(tv);
        if (s == null) return false;
        s.setArcMode(true);
        return true;
    }

    // ---- the log tags and thread names of the core classes that name their family (BootSource, ManualFocusActivity)
    /** The prefix of the core classes' log tags on this family. */
    public static final String TAG_PREFIX = "Z9x";
    /** The prefix of the core classes' thread names and preference files on this family. */
    public static final String THREAD_PREFIX = "z9x";

    // ---- voice.AssistantHost
    /** voice.AssistantHost: the assistant keeps its full animations (no weak GPU on this family). */
    public static final boolean ASSIST_LOW_POWER = false;

    /** voice.AssistantHost: the keystone the assistant asks for (272 / 326 on "z9x-kst", no fit to the wall). */
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
