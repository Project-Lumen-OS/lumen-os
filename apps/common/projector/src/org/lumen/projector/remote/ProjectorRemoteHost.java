package org.lumen.projector.remote;

import android.content.Context;

import org.lumen.projector.ak.AkOverlay;
import org.lumen.projector.cec.CecPolicy;
import org.lumen.projector.eye.EyeGuard;
import org.lumen.projector.power.StandbyController;
import org.lumen.projector.power.WakeCurtain;
import org.lumen.projector.setup.SetupEvents;
import org.lumen.projector.sys.BootReason;
import org.lumen.remote.RemoteHost;

/**
 * The remote module's host in the Z9X, Z9X HB and Play 6 projector apps (apps/core/lumen-remote README): the Setup
 * event of this app family, its boot reason rule and its own blockers of the remote-lost prompt (standby, an HDMI-CEC
 * wake, the auto keystone overlay, the wake curtain, eye protection). The pairing itself is the shared RemotePairing.
 */
public final class ProjectorRemoteHost implements RemoteHost {
    @Override public void remoteChanged(Context app) {
        SetupEvents.remote(app);
    }

    @Override public String unattendedBootReason() {
        return BootReason.unattended(
                android.os.SystemProperties.get("sys.boot.reason", ""),
                android.os.SystemProperties.get("sys.boot.reason.last", ""),
                android.os.SystemProperties.get("persist.sys.boot.reason", ""),
                android.os.SystemProperties.get("ro.boot.bootreason", ""));
    }

    @Override public int promptGate(Context app, boolean bondedCase) {
        if (inStandby()) return SKIP;
        if (bondedCase && CecPolicy.cecWakeActive()) return SKIP;    // woken by an HDMI device: the user may only watch
        if (AkOverlay.isActive() || WakeCurtain.isShowing() || EyeGuard.isActive()) return LATER;
        return FREE;
    }

    @Override public boolean inStandby() {
        return StandbyController.isActive() || StandbyController.shutdownInFlight();
    }
}
