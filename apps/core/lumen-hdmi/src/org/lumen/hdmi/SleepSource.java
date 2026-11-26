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
 * (SleepCec), then PowerManager.goToSleep at once. The framework's own standby frames (ARC termination,
 * {@code <Standby>}) follow when the display is off, so the bus order is the stock one: {@code <Active Source>
 * 0.0.0.0}, ARC termination, {@code <Standby>}. Paths that press the system power key (the X50 family) call
 * {@link #tvTakesSource} before the key. Any thread; never throws.
 */
public final class SleepSource {
    private static final String TAG = "LumenSleepCec";
    /** An HDMI port was on screen when the projector's standby began (its black screen covers it before the sleep). */
    private static volatile boolean sShownAtStandby;

    private SleepSource() {}

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

    /** The Android sleep: {@link #tvTakesSource}, then goToSleep(now, reason, flags). Rethrows the sleep's failure. */
    public static void goToSleep(Context ctx, int reason, int flags) {
        tvTakesSource(ctx);
        ctx.getSystemService(PowerManager.class).goToSleep(SystemClock.uptimeMillis(), reason, flags);
    }
}
