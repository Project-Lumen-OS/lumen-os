// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector;

import android.content.Context;

/**
 * What the Play 6 HIDL family answers where a core class its recipe takes asks its family
 * (the core list devices/dragon_healthy/app/Play6Projector.aipq). It has
 * the members of those core classes; the other members of the gmpf families' Family join with the next folds.
 */
public final class Family {
    private Family() {}

    /** The prefix of the core classes' log tags on this family. */
    public static final String TAG_PREFIX = "P6";
    /** The prefix of the core classes' thread names and preference files on this family. */
    public static final String THREAD_PREFIX = "p6";

    /** voice.AssistantHost: the weak GPU of the Play 6, the assistant runs without its heavy animations. */
    public static final boolean ASSIST_LOW_POWER = true;
    /** lumen-hdmi InactiveSleep: sleep on the watched device's <Inactive Source> when it is not back in 5 s and no key
     *  was pressed. Off until the owner decides (2026-11-26); RelinkHost.inactiveSourceSleep reads it. */
    public static final boolean INACTIVE_SOURCE_SLEEP = false;

    /** voice.AssistantHost: the keystone the assistant asks for (326 autoKst through the Hal facade). */
    public static void assistKeystone(Context c) {
        Hal.requestKeystone(c);
    }

    /** ManualFocusActivity: the stock window is the bottom panel only (no focus chart). */
    public static final boolean FOCUS_CHART = false;
    /** ManualFocusActivity: no mouse wheel on the lens (the Play 6 stock FocusAdjust has none). */
    public static final boolean FOCUS_WHEEL = false;

    /** ManualFocusActivity.open: true (and the notice shown) while the Hal facade is not ready (start gate, IGmpf). */
    static boolean focusOpenRefused(Context ctx) {
        if (!Hal.isReady()) { Ui.toast(ctx, R.string.toast_not_ready); return true; }
        return false;
    }

    /** ManualFocusActivity.onResume: the same readiness check as {@link #focusOpenRefused}. */
    static boolean focusResumeRefused(Context ctx) {
        return focusOpenRefused(ctx);
    }

    /** ManualFocusActivity: the focus motor (thread p6-motor). */
    static MotorController focusMotor(Context ctx) {
        return MotorController.get(ctx);
    }

    // ---- power.StandbyStateProvider (apps/common/projector): the lamp-only standby state and the CEC wake request
    /** power.StandbyStateProvider: the lamp-only standby is on (also while a standby of this boot waits to re-enter). */
    public static boolean standbyActive() {
        return org.lumen.projector.power.LampStandby.isActive();
    }

    /** remote/ProjectorRemoteHost: a power off from the lamp-only standby path is under way. */
    public static boolean standbyShutdownInFlight() {
        return org.lumen.projector.power.LampStandby.shutdownInFlight();
    }

    /** power.StandbyStateProvider "cec_wake": the CEC wake request of org.lumen.tvinput, answered by the cec module. */
    public static android.os.Bundle cecWake(Context ctx, android.os.Bundle extras) {
        return org.lumen.projector.cec.CecFrameworkWake.onWakeRequest(ctx, extras);
    }

    /** The NUI fifo reader of the Play 6 vendor service: 148 / 152 only with it up (eye/EyeGuard). */
    public static final String NUI_READY_PROP = org.lumen.projector.hal.Play6Gmpf.NUI_READY_PROP;

    public static boolean nuiReaderReady() {
        return org.lumen.projector.hal.Play6Gmpf.nuiReaderReady();
    }

    /** No vendor UI watchdog on the Play 6: 114 / 115 are banned there until its own review (Note A). */
    public static void eyeWatchdog(boolean on) {
    }

    /** power.SleepTimer: no lamp step during the timer fade on the Play 6 (the Z9X TimerLamp is plan P2). */
    public static final boolean TIMER_LAMP = false;

    public static void timerLampDown(Context app, int level, long ms) {
    }

    public static void timerLampRestore() {
    }

    /** KeyReceiver: stop a manual focus move (no-op unless something moves). */
    static void motorStop(Context ctx) {
        MotorController.stopIfAny();
    }

    /** KeyReceiver: the focus motor of the focus step keys (p6-motor; nothing moves before the start gate). */
    static MotorController motor(Context ctx) {
        return MotorController.get(ctx);
    }

    /** KeyReceiver: a focus step key while the start gate is closed or the HAL is not connected: "not ready", true. */
    static boolean focusStepRefused(Context ctx, boolean firstDown) {
        if (Hal.isReady()) return false;
        if (firstDown) Ui.toast(ctx, R.string.toast_not_ready);
        return true;
    }

    /** KeyReceiver: autofocus on the Focus key (1 s debounce inside). */
    static void requestAutofocus(Context ctx) {
        Hal.requestAutofocus(ctx);
    }

    /** KeyReceiver: the Play 6 installs from a PC only, no install guard. */
    static boolean installing() {
        return false;
    }

    /** KeyReceiver: no deep sleep notice on the Play 6. */
    static void strNoticeKey(Context ctx, android.view.KeyEvent ev) {
    }

    /** KeyReceiver: the remote power key (power/PowerKey: Android sleep only, no lamp-only standby). */
    static boolean powerKey(Context ctx, android.content.Intent intent, android.view.KeyEvent ev) {
        return org.lumen.projector.power.Play6PowerKey.onGlobalKey(ctx, intent, ev);
    }

    /**
     * KeyReceiver: the Play 6 key layout sends the remote's shortcut key (scancode 184) as the Ambient code; stock's
     * default for it on the Play 6 is "ambient light switch": the ambient speaker, then the next colour (Lumen 1.0.6).
     */
    static boolean isShortcutKey(android.view.KeyEvent ev) {
        return org.lumen.projector.speaker.SpeakerMode.isShortcutKey(ev);
    }

    static void shortcutKey(Context ctx) {
        org.lumen.projector.speaker.SpeakerMode.shortcutKey(ctx);
    }

    /** KeyReceiver: the gear long press (the menu profile's keys.gear_long; no game menu: the fallback). */
    static void gearLong(Context ctx, Runnable fallback) {
        org.lumen.projector.panel.Play6Menu.gearLong(ctx, fallback);
    }

    /** KeyReceiver: Android's screensaver switch (Settings.Secure.SCREENSAVER_ENABLED, default on as in the framework). */
    static boolean dreamEnabled(Context ctx) {
        return android.provider.Settings.Secure.getInt(ctx.getContentResolver(), "screensaver_enabled", 1) == 1;
    }

    /** audio.AudioOutputChoice: a plug of the 3.5 mm jack is reported by the kernel (an Auto candidate). */
    public static final boolean JACK_SENSED = true;
    /** audio.AudioOutputChoice: no digital output format on the Play 6 (no ARC output, no format row). */
    public static final boolean DIGITAL_FORMAT = false;
    /** audio.AudioOutputChoice: the selected row is the stored output while it is listed. */
    public static final boolean SELECTION_ROUTED = false;
    /** audio.AudioOutputChoice: the Play 6 boot receiver is frozen (check_handshake), so the boot re-apply is timed. */
    public static final long AUDIO_REAPPLY_MS = 30_000L;

    public static void registerAudioDevices(Context app, android.media.AudioDeviceCallback cb, android.os.Handler h,
            String tag) {
        app.getSystemService(android.media.AudioManager.class).registerAudioDeviceCallback(cb, h);
    }

    /** audio.AudioOutputChoice: nothing to wait for on the jack (the kernel reports its plug). */
    public static boolean jackKeep(android.media.AudioManager am, android.media.audiopolicy.AudioProductStrategy media, org.lumen.projector.audio.AudioOutputKind chosen, String why) {
        return false;
    }

    public static void jackSettle(android.media.AudioManager am, android.media.audiopolicy.AudioProductStrategy media, org.lumen.projector.audio.AudioOutputKind chosen, String why) {
    }

    public static boolean earcLinked() {
        return false;
    }

    /**
     * The auto-keystone overlay (ak/AkOverlay): the Play 6 vendor runs its real-time AK with no app, the overlay is
     * never up and the AK events (105-118, 120) are only logged with the time since the previous one of the same run
     * (a pause of {@value #AK_RUN_GAP_MS} ms starts a new run), so the log shows which events arrive and how far apart.
     */
    public static void akInstall(Context ctx) {}

    public static void akLocaleChanged() {}

    public static boolean akActive() {
        return false;
    }

    private static final long AK_RUN_GAP_MS = 10_000;
    /** Main thread state. */
    private static long sAkLastAt = -1;
    private static int sAkRunEvents;

    public static boolean akEvent(int type, String value) {
        long now = android.os.SystemClock.elapsedRealtime();
        boolean newRun = sAkLastAt < 0 || now - sAkLastAt > AK_RUN_GAP_MS;
        sAkRunEvents = newRun ? 1 : sAkRunEvents + 1;
        android.util.Log.i("P6Focus", "AK event type=" + type + (value == null || value.isEmpty() ? "" : " value=" + value)
                + (newRun ? " (new run)" : " (+" + (now - sAkLastAt) + " ms, #" + sAkRunEvents + ")"));
        sAkLastAt = now;
        return false;
    }

    /**
     * setup/SetupBridgeProvider: what the Play 6 Setup may ask (manual keystone, the real-time keystone toggle; no fit
     * to screen, no curtain state), the device names of its ping reply (the product model, else the stock names) and
     * its remote module, which starts after the start gate (remote_state says "starting" until then).
     */
    public static boolean setupHas(String what) {
        return "kst_manual".equals(what) || Hal.T_MOVE_AK.equals(what);
    }

    private static final String[] SETUP_NAMES = {"XGIMI Play 6", "Play 6"};
    private static final String SETUP_BRAND = "XGIMI ";

    public static String[] setupNames() {
        String model = setupName(android.os.SystemProperties.get("ro.product.model", ""));
        if (model == null || model.contains("Z9X")) return SETUP_NAMES.clone();
        String shortName = model.startsWith(SETUP_BRAND) ? setupName(model.substring(SETUP_BRAND.length())) : null;
        return shortName == null || shortName.equals(model) ? new String[] {model} : new String[] {model, shortName};
    }

    private static String setupName(String raw) {
        if (raw == null) return null;
        String n = raw.trim().replaceAll("\\s+", " ");
        return n.isEmpty() || n.length() > 32 ? null : n;
    }

    public static final boolean REMOTE_STARTS_LATE = true;

    /**
     * game/HdmiEventReceiver: on the Play 6 every HDMI event of org.lumen.tvinput goes to its source/HdmiSource
     * ("session": IGmpf 698 connects HDMI to the video window or gives it back; "cec_added" / "cec_removed" run the
     * game mode console check again while HDMI is shown). Main thread, nothing here calls the HAL.
     */
    public static void hdmiEvent(Context ctx, android.content.Intent intent) {
        org.lumen.projector.source.HdmiSource.onHdmiEvent(ctx, intent);
    }

    /** remote/ProjectorRemoteHost: this wake came from an HDMI device over CEC (cec/CecFrameworkWake). */
    public static boolean cecWakeActive() {
        return org.lumen.projector.cec.CecFrameworkWake.cecWakeActive();
    }

    /**
     * Play6App: the device names the stock firmware and the base image leave (never a user's own name), and the
     * stock Bluetooth names (its "my device" default 我的Play 6, INUI_TV, XGIMI TV): they become the model name once.
     */
    public static final java.util.Set<String> STOCK_DEVICE_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
            "", "XGIMI TV", "AOSP TV on ARM", "INUI_TV"));
    public static final java.util.Set<String> STOCK_BT_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
            "我的Play 6", "我的Play6", "INUI_TV", "XGIMI TV", "AOSP TV on ARM"));

    // ---- the quick menu behind core QuickPanel: the shared menu through Play6Menu (no restorer, no locale hook)
    public static void menuToggle(Context c) { org.lumen.projector.panel.Play6Menu.quickToggle(c); }

    public static void menuShow(Context c, String section) { org.lumen.projector.panel.Play6Menu.quickShow(c, section); }

    public static void menuRefresh() { org.lumen.projector.panel.Play6Menu.quickRefresh(); }

    public static Runnable menuRestorer() { return null; }

    public static void menuExtension(String section, org.lumen.projector.panel.QuickPanel.Extension ext) {
        org.lumen.projector.panel.Play6Menu.quickExtension(section, ext);
    }

    public static void menuLocaleChanged(Context c) {}

    // ---- the power actions of the shared power menu, sleep timer and Home tile (Play6PowerKey.PowerActions)
    public static final long POWER_FADE_SHORT_MS = org.lumen.projector.power.Play6PowerKey.PowerActions.FADE_SHORT_MS;

    public static boolean powerBusy() { return org.lumen.projector.power.Play6PowerKey.PowerActions.isBusy(); }

    public static void powerSleepNow(Context c, String why) { org.lumen.projector.power.Play6PowerKey.PowerActions.sleepNow(c, why); }

    public static void powerSleepWithFade(Context c, String why) { org.lumen.projector.power.Play6PowerKey.PowerActions.sleepWithFade(c, why); }

    public static void powerOff(Context c) { org.lumen.projector.power.Play6PowerKey.PowerActions.powerOff(c); }

    public static void powerRestart(Context c) { org.lumen.projector.power.Play6PowerKey.PowerActions.restart(c); }

    // ---- panel.RotatePanel (and the other adjustment cards): the family's HAL steps and how keys become steps
    /** Keys are queued and sent as few steps as possible: IGmpf2 138 is slow on the Play 6. */
    public static final boolean ADJUST_COALESCE = true;
    /** An adjustment card whose HAL open did not run within this time fails. */
    public static final long ADJUST_WATCHDOG_MS = 6_000;

    public static boolean adjustHalReady() { return Hal.isReady() && Hal.hasGmpf2(); }

    public static boolean adjustOpen() { return org.lumen.projector.panel.Play6AdjustSession.anyOpen(); }

    public static org.lumen.projector.panel.RotateIo rotateIo(Context app, org.lumen.projector.hal.Play6Gmpf g,
            org.lumen.projector.hal.Play6Gmpf2 g2) {
        return org.lumen.projector.panel.Play6PanelIo.rotate(app, g, g2);
    }

    /** The Play 6 cards recover through Play6AdjustSession.install. */
    public static void rotateInstall(Context app) { }
}
