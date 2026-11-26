// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.content.Context;
import android.text.format.Formatter;
import android.util.Log;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * update_engine writes the other slot (the Z9X, the HB, the X50 family): the download is the OTA zip plus its
 * payload metadata file, the install is applyPayload with a slot switch on the next restart.
 */
final class ViaUpdateEngine implements InstallWay {
    @Override
    public void listen(Engine.Listener l, boolean on) {
        if (on) Engine.get().addListener(l);
        else Engine.get().removeListener(l);
    }

    @Override
    public int status() { return Engine.get().status(); }

    @Override
    public void cancel() { Engine.get().cancel(); }

    @Override
    public void revertReady() { Engine.get().revertReady(); }

    /** The metadata file must be the payload's prefix (update_engine checks its signature at install). */
    @Override
    public boolean verifyDownload(Store st, File src, UpdateManifest.Pkg p, File meta) throws IOException {
        if (!metadataMatches(src, p, meta)) {
            if (!st.fromUsb()) src.delete();
            meta.delete();
            st.setError("err_hash", "");
            return false;
        }
        meta.setReadable(true, false);   // update_engine reads it by path
        return true;
    }

    private static boolean metadataMatches(File zip, UpdateManifest.Pkg p, File meta) throws IOException {
        byte[] m = java.nio.file.Files.readAllBytes(meta.toPath());
        if (m.length == 0 || m.length > p.payloadSize) return false;
        byte[] z = new byte[m.length];
        try (RandomAccessFile r = new RandomAccessFile(zip, "r")) {
            r.seek(p.payloadOffset);
            r.readFully(z);
        }
        return Arrays.equals(m, z);
    }

    @Override
    public boolean dropsInstall(ServiceHost h) { return h.repeat(ServiceJob.INSTALL); }

    @Override
    public void install(ServiceHost h, Store st, UpdateManifest m, UpdateManifest.Pkg p) {
        Context c = h.context();
        // update_engine may already be applying it: a second install request (a second start of this service a
        // few seconds after the first, 2026-10-28) got "Already processing an update" back, showed code -1
        // and stopped following an install that then finished fine. Follow the running one instead.
        int ue = Engine.get().status();
        if (applying(ue) || ue == Ota.UE_NEED_REBOOT) {
            attach(h, st, m, ue);
            return;
        }
        File src = UpdateService.payloadSource(c, st, p), meta = UpdateService.metaFile(c, p);
        if (!src.isFile() || !meta.isFile()) {
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
        if (!Engine.get().verifyMetadata(meta)) {
            st.setError("err_signature", "");
            return;
        }
        long need = Engine.get().allocateSpace(meta, p);
        if (need != 0) {
            if (need > 0) st.setError("blk_space", Formatter.formatShortFileSize(c, need));
            else st.setError("blk_engine", "");
            return;
        }
        // 2026-11-04: under the Checker lock, so a background check cannot write CHECKING (and then the old state)
        // between these lines and end the wait loop below; a stop asked meanwhile (Cancel) is honoured here
        synchronized (Checker.class) {
            if (h.stopRequested() || st.state() != UpdateState.DOWNLOADED) {
                Log.i(Ota.TAG, "install not started: stop " + h.stopRequested() + ", state " + st.state());
                return;
            }
            st.setPending(m.buildId, m.version, Ota.buildId(), Ota.currentVersion());
            st.setProgress(0f);
            st.setState(UpdateState.INSTALLING);
        }
        Ota.setBusy(c, true, "installing");
        h.acquireWake();
        try {
            Engine.get().apply(src, p);
            Log.i(Ota.TAG, "applyPayload started: " + p.fileName() + " -> slot " + Ota.otherSlot());
            // wait here (worker thread) until update_engine reports completion
            while (st.state() == UpdateState.INSTALLING && !h.stopRequested()) h.sleep(1000);
        } catch (IOException e) {
            Log.w(Ota.TAG, "apply failed", e);
            int now = Engine.get().status();
            if (applying(now) || now == Ota.UE_NEED_REBOOT) {   // it is running (or done) after all
                attach(h, st, m, now);
                return;
            }
            st.clearPending();
            st.setError("err_install_unknown", "");
            Ota.setBusy(c, false, "error");
            h.releaseWake();
        }
    }

    /** update_engine is writing, checking or finishing a payload (this app is its only client). */
    private static boolean applying(int ue) {
        return ue == Ota.UE_DOWNLOADING || ue == Ota.UE_VERIFYING || ue == Ota.UE_FINALIZING;
    }

    /**
     * Follow an install update_engine already runs, as if this request had started it: INSTALLING with the
     * progress from onStatus and the end from onComplete. A finished one (UE_NEED_REBOOT) goes straight to READY.
     */
    private static void attach(ServiceHost h, Store st, UpdateManifest m, int ue) {
        Log.i(Ota.TAG, "install: update_engine already has it (status " + ue + "), following it");
        st.setPending(m.buildId, m.version, Ota.buildId(), Ota.currentVersion());
        st.setState(UpdateState.INSTALLING);
        if (ue == Ota.UE_NEED_REBOOT) {
            h.complete(Ota.ERR_SUCCESS);
            return;
        }
        Ota.setBusy(h.context(), true, "installing");
        h.acquireWake();
        while (st.state() == UpdateState.INSTALLING && !h.stopRequested()) h.sleep(1000);
    }

    /**
     * Follow what update_engine does with an INSTALLING state (finding 5): applying or finished = attach (the end
     * then goes through onComplete: READY, busy off, the result, the wakelock, the foreground service); idle = the
     * install is gone; no answer = asked again 5 s later (3 times) before it counts as failed.
     */
    @Override
    public void watch(ServiceHost h, Store st) {
        int s = Engine.get().status();
        for (int i = 0; s < 0 && i < 3 && !h.stopRequested() && st.state() == UpdateState.INSTALLING; i++) {
            Log.i(Ota.TAG, "watch: update_engine status unknown, asking again in 5 s");
            h.sleep(5000);
            s = Engine.get().status();
        }
        if (st.state() == UpdateState.INSTALLING) {
            if (applying(s) || s == Ota.UE_NEED_REBOOT) {
                UpdateManifest m = null;
                try { m = new UpdateManifest(st.manifestJson()); } catch (JSONException ignored) { }
                if (m != null) attach(h, st, m, s);
                else if (s == Ota.UE_NEED_REBOOT) h.complete(Ota.ERR_SUCCESS);
            } else {
                Log.w(Ota.TAG, "watch: INSTALLING but update_engine reports " + s);
                st.clearPending();
                st.setError("err_install_unknown", "");
                Ota.setBusy(h.context(), false, "error");
                h.releaseWake();
            }
        } else if (s == Ota.UE_NEED_REBOOT && st.state() != UpdateState.READY && st.state() != UpdateState.AVAILABLE) {
            st.setState(UpdateState.READY);
            Ota.setBusy(h.context(), false, "ready");
        }
    }

    @Override
    public String dryRun(ServiceHost h, Store st, UpdateManifest.Pkg p) {
        Context c = h.context();
        File meta = UpdateService.metaFile(c, p);
        Preflight.Block b = Preflight.install(c);
        boolean md = Engine.get().verifyMetadata(meta);
        long need = md ? Engine.get().allocateSpace(meta, p) : -2;
        return "verify-only: preflight=" + (b == null ? "ok" : b.res + ":" + b.arg)
                + " metadata_signature=" + (md ? "ok" : "FAILED")
                + " space=" + (need == 0 ? "ok" : need > 0 ? "needs " + need : "error " + need)
                + " consent=" + st.firmwareConsent() + " (no slot change)";
    }
}
