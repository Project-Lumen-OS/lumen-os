// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * The recovery installs a whole package (the Play 6): no update_engine and no second slot. The package is a whole
 * recovery OTA zip (tools/play6/ota/make_block_ota.py, signed with the model's OTA key) downloaded to
 * /cache/lumen_ota (OtaDir.CACHE) and installed by XGIMI's recovery, which trusts the Lumen certificate after the
 * one-time step (tools/play6/recovery_trust.py): RecoveryPackage.verify, then RecoveryPackage.install (BCB +
 * restart). READY lasts only the seconds between the BCB and the restart; the result is read after the boot
 * (Outcome.afterBoot). The update_engine view of the shared code answers "idle, nothing pending".
 */
final class ViaRecovery implements InstallWay {
    @Override
    public void listen(Engine.Listener l, boolean on) {}

    /** No update_engine: always idle (the install ends in the recovery, the result is read after the boot). */
    @Override
    public int status() { return Ota.UE_IDLE; }

    @Override
    public void cancel() {}

    @Override
    public void revertReady() {}

    /** The whole-file signature against the system OTA certificates. */
    @Override
    public boolean verifyDownload(Store st, File src, UpdateManifest.Pkg p, File meta) {
        if (!RecoveryPackage.verify(src)) {
            if (!st.fromUsb()) src.delete();
            st.setError("err_signature", "");
            return false;
        }
        return true;
    }

    /** One install at a time: a second press must not race the USB copy or the signature check. */
    @Override
    public boolean dropsInstall(ServiceHost h) { return h.busy(); }

    @Override
    public void install(ServiceHost h, Store st, UpdateManifest m, UpdateManifest.Pkg p) {
        Context c = h.context();
        File src = UpdateService.payloadSource(c, st, p);
        if (!src.isFile()) {
            st.setState(UpdateState.AVAILABLE);
            return;
        }
        if (!st.firmwareConsent()) {          // the activity asks first; never install without it
            st.setState(UpdateState.DOWNLOADED);
            return;
        }
        Preflight.Block b = Preflight.install(c);
        if (b != null) {
            st.setError(b.res, b.arg);
            return;
        }
        // a package on a USB stick is copied to /cache first: the recovery reads only /cache
        if (st.fromUsb()) {
            File dst = UpdateService.zipFile(c, p);
            try {
                h.progress(c.getString(R.string.inst_title, h.version()), -1);
                java.nio.file.Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (!Trust.sha256(dst, -1, h::stopRequested).equals(p.sha256)) throw new IOException("copy sha256");
                src = dst;
            } catch (IOException e) {
                Log.w(Ota.TAG, "USB copy to " + dst + ": " + e);
                dst.delete();
                st.setError("err_hash", "");
                return;
            }
        }
        // the whole-file signature, while the state still says DOWNLOADED (the screen offers nothing to restart)
        h.progress(c.getString(R.string.dl_verifying), -1);
        if (!RecoveryPackage.verify(src)) {
            src.delete();
            st.setError("err_signature", "");
            return;
        }
        if (h.stopRequested()) {
            st.setState(UpdateState.DOWNLOADED);
            return;
        }
        // READY only right before the BCB and the restart: after the boot, Outcome.afterBoot says "updated" when
        // the new build runs and "could not be installed, the previous version runs" when the recovery refused.
        // Under the Checker lock (as on the Z9X since 2026-11-04), so a background check cannot write CHECKING
        // (and then the old state) between these lines; a stop asked meanwhile (Cancel) is honoured here
        synchronized (Checker.class) {
            if (h.stopRequested() || st.state() != UpdateState.DOWNLOADED) {
                Log.i(Ota.TAG, "install not started: stop " + h.stopRequested() + ", state " + st.state());
                return;
            }
            st.setPending(m.buildId, m.version, Ota.buildId(), Ota.currentVersion());
            st.setProgress(1f);
            st.setState(UpdateState.READY);
        }
        Ota.setBusy(c, true, "installing");
        h.acquireWake();
        try {
            RecoveryPackage.install(c, src);
        } catch (IOException e) {
            Log.w(Ota.TAG, "apply failed", e);
            st.clearPending();
            st.setError("err_install_unknown", "");
            Ota.setBusy(c, false, "error");
            h.releaseWake();
        }
    }

    /**
     * There is no update_engine (status idle) and the recovery install goes from DOWNLOADED to READY right before
     * the BCB, so an INSTALLING state left behind counts as failed.
     */
    @Override
    public void watch(ServiceHost h, Store st) {
        int s = status();
        if (st.state() == UpdateState.INSTALLING && (s == Ota.UE_IDLE || s < 0)) {
            st.setError("err_install", "-1");
        } else if (s == Ota.UE_NEED_REBOOT) {
            st.setState(UpdateState.READY);
        }
    }

    @Override
    public String dryRun(ServiceHost h, Store st, UpdateManifest.Pkg p) {
        Context c = h.context();
        Preflight.Block b = Preflight.install(c);
        boolean sig = RecoveryPackage.verify(UpdateService.payloadSource(c, st, p));
        return "verify-only: preflight=" + (b == null ? "ok" : b.res + ":" + b.arg)
                + " package_signature=" + (sig ? "ok" : "FAILED")
                + " consent=" + st.firmwareConsent() + " (nothing written)";
    }
}
