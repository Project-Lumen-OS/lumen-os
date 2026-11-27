// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

import android.content.Context;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.hardware.hdmi.HdmiTvClient;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/**
 * The one Android sleep of every Lumen path (standby, power key, sleep timer, idle, the watched device turning off):
 * first the TV takes the active source back when an HDMI picture is (or was, when the standby began) on screen
 * (SleepCec), then the family's vendor sleep step ({@link VendorPrepare}: the stock xgimiPrepareSleep, which stock runs
 * inside PowerManager.goToSleep before the screen goes off), then PowerManager.goToSleep. The framework's own standby
 * frames (ARC termination, {@code <Standby>}) follow when the display is off, so the order is the stock one:
 * {@code <Active Source> 0.0.0.0}, the vendor's sleep, ARC termination, {@code <Standby>}. Paths that press the system
 * power key (the X50 family) call {@link #beforeSleep} before the key. A sleep Lumen did not start (an incoming CEC
 * {@code <Standby>}, adb) reaches the app only as SCREEN_OFF; the families send the vendor step there as before.
 * Any thread but the family's HAL thread; never throws.
 */
public final class SleepSource {
    private static final String TAG = "LumenSleepCec";
    /** An HDMI port was on screen when the projector's standby began (its black screen covers it before the sleep). */
    private static volatile boolean sShownAtStandby;

    /** The family's vendor sleep step, sent and answered (or given up after a short bound) before it returns. */
    public interface VendorPrepare {
        void beforeSleep(Context ctx);
    }

    private static volatile VendorPrepare sPrepare;

    private SleepSource() {}

    /** App start: the family's vendor sleep step (null = none). */
    public static void setVendorPrepare(VendorPrepare p) {
        sPrepare = p;
    }

    /** Before any Lumen sleep, as stock: {@link #tvTakesSource}, then the family's vendor sleep step. */
    public static void beforeSleep(Context ctx) {
        tvTakesSource(ctx);
        VendorPrepare p = sPrepare;
        if (p == null) return;
        try {
            p.beforeSleep(ctx);
        } catch (Throwable t) {
            Log.w(TAG, "sleep: vendor step: " + t);
        }
    }

    /** The standby begins (before its black screen): remember whether an HDMI picture was up. */
    public static void standbyBegins() {
        sShownAtStandby = SourceFollow.shownPort() > 0;
    }

    /** The standby ended awake. */
    public static void standbyEnds() {
        sShownAtStandby = false;
    }

    /** Before any Lumen sleep: {@code <Active Source>} 0.0.0.0 when an HDMI picture is up, as stock going Home. */
    public static void tvTakesSource(Context ctx) {
        try {
            boolean shown = SourceFollow.shownPort() > 0 || sShownAtStandby;
            HdmiControlManager m = ctx == null ? null : ctx.getSystemService(HdmiControlManager.class);
            boolean cecOn = m != null && m.getHdmiCecEnabled() == HdmiControlManager.HDMI_CEC_CONTROL_ENABLED;
            if (!SleepCec.tvTakesSource(cecOn, shown)) return;
            HdmiTvClient tv = m.getTvClient();
            if (tv == null) return;
            // HdmiCecLocalDeviceTv.deviceSelect(ADDR_INTERNAL) -> handleSelectInternalSource: <Active Source> from
            // the TV, only when a device is the active source; on the service thread before the screen-off standby
            tv.selectDevice(HdmiDeviceInfo.ADDR_INTERNAL, Runnable::run,
                    (result, la) -> Log.i(TAG, "sleep: the TV takes the active source back -> " + result));
        } catch (Throwable t) {
            Log.w(TAG, "sleep: internal source: " + t);
        } finally {
            sShownAtStandby = false;
        }
    }

    /** The Android sleep: {@link #beforeSleep}, then goToSleep(now, reason, flags). Rethrows the sleep's failure. */
    public static void goToSleep(Context ctx, int reason, int flags) {
        beforeSleep(ctx);
        ctx.getSystemService(PowerManager.class).goToSleep(SystemClock.uptimeMillis(), reason, flags);
    }
}
