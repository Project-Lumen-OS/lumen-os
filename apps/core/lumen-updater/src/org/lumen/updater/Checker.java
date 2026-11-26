// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import org.json.JSONException;
import org.lumen.download.HttpError;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Fetches the update manifest + .sig (with beta updates on: the stable and the beta one, Channels), verifies the
 * signature with the system OTA certificates, checks applicability and records the offer of the newest version
 * (Newest: the rule every path follows). Runs on a worker thread.
 */
public final class Checker {
    private Checker() {}

    /**
     * Online check: every channel manifest read fresh (Http.fetchFresh), the newest verified build is the offer
     * (Newest.check). Also while a download runs: a newer build replaces it.
     */
    public static synchronized CheckResult check(Context c) {
        Stats.maybeSend(c);   // own thread, at most once a day, when the user has not turned it off
        Store st = Store.get(c);
        // beta on: the stable and the beta manifest, the higher version is offered; a beta manifest that is
        // missing, unreachable or does not verify is skipped (Channels)
        String[] urls = Channels.read();
        CheckResult r = Newest.check(st, urls, Http::fetchFresh, Trust::verifyWithSystemCerts, Ota.currentVersionCode(),
                System.currentTimeMillis());
        if (urls.length == 1 && r.outcome != CheckOutcome.BUSY) probeBeta(st);
        return r;
    }

    /**
     * With beta updates off on an edition that has a beta channel: is a beta published? Only then the updater shows
     * the "Beta updates" switch (a switch with nothing behind it is not shown). A network error keeps the last answer.
     */
    private static void probeBeta(Store st) {
        String[] c = Ota.channels(Ota.imageManifestUrl());
        if (c == null) return;
        Newest.Read r = Newest.read(Http::fetchFresh, Trust::verifyWithSystemCerts, c[1]);
        if (r.error == null) st.setBetaPublished(r.manifest != null);
        else if (r.error instanceof HttpError && ((HttpError) r.error).code == 404) st.setBetaPublished(false);
    }

    /** Offline: an update copied to the root (or lumen-os/) of a USB stick. */
    public static synchronized CheckResult checkUsb(Context c) {
        Store st = Store.get(c);
        UpdateState before = st.state();
        if (before == UpdateState.DOWNLOADING || before == UpdateState.INSTALLING || before == UpdateState.READY) {
            return new CheckResult(CheckOutcome.BUSY, null, "", "");
        }
        for (File dir : usbDirs(c)) {
            // only this image's own manifest files (an update of another edition is never offered); with beta on
            // the stable and the beta file, the higher version first, then the other one
            String[] files = Channels.files();
            byte[][] json = new byte[files.length][], sig = new byte[files.length][];
            int[] codes = new int[files.length];
            int unverified = -1;
            for (int i = 0; i < files.length; i++) {
                codes[i] = Channels.MISSING;
                File j = new File(dir, files[i]), sg = new File(dir, files[i] + ".sig");
                if (!j.isFile() || !sg.isFile() || j.length() > Newest.MAX_MANIFEST) continue;
                try {
                    json[i] = Files.readAllBytes(j.toPath());
                    sig[i] = Files.readAllBytes(sg.toPath());
                } catch (IOException e) {
                    Log.w(Ota.TAG, "USB " + j + ": " + e);
                    continue;
                }
                UpdateManifest m = Newest.verified(json[i], sig[i], Trust::verifyWithSystemCerts);
                if (m != null) codes[i] = m.versionCode;
                else if (unverified < 0) unverified = i;
            }
            if (Channels.pick(codes) < 0 && unverified >= 0) {
                // only an unverified manifest here: recorded as before (err_signature), the next folder is tried
                Log.w(Ota.TAG, "USB manifest signature invalid: " + files[unverified]);
                st.setError("err_signature", "");
                continue;
            }
            for (int k; (k = Channels.pick(codes)) >= 0; codes[k] = Channels.MISSING) {
                try {
                    UpdateManifest m = new UpdateManifest(new String(json[k], StandardCharsets.UTF_8));
                    CheckResult r = Newest.accept(st, m, true, dir.getAbsolutePath(), before, Ota.currentVersionCode(),
                            System.currentTimeMillis());
                    if (r.kept) return r;   // a newer offer than this stick's build is saved already: it stays
                    if (r.outcome == CheckOutcome.OFFER) {
                        UpdateManifest.Pkg p = UpdateManifest.Pkg.parse(st.packageJson());
                        if (new File(dir, p.fileName()).isFile()) return r;
                        Log.w(Ota.TAG, "USB: " + p.fileName() + " not next to " + files[k]);
                        st.clearOffer();
                        st.setState(before == UpdateState.CHECKING ? UpdateState.IDLE : before);
                    } else if (r.outcome != CheckOutcome.ERROR) {
                        return r;
                    }
                } catch (JSONException e) {
                    Log.w(Ota.TAG, "USB " + files[k] + ": " + e);
                }
            }
        }
        return new CheckResult(CheckOutcome.ERROR, null, "usb_none", "");
    }

    static File[] usbDirs(Context c) {
        java.util.List<File> out = new java.util.ArrayList<>();
        StorageManager sm = c.getSystemService(StorageManager.class);
        if (sm == null) return new File[0];
        for (StorageVolume v : sm.getStorageVolumes()) {
            if (!v.isRemovable() || !Environment.MEDIA_MOUNTED.equals(v.getState())) continue;
            File root = v.getDirectory();
            if (root == null) continue;
            out.add(root);
            out.add(new File(root, "lumen-os"));
        }
        return out.toArray(new File[0]);
    }

    /**
     * Drops a saved offer that is not newer than the running build (Newest.dropStale): at boot, before a download
     * and before an install. @return true = it was stale and is gone
     */
    static boolean dropStaleOffer(Context c, String why) {
        return Newest.dropStale(Store.get(c), Ota.currentVersionCode(), why);
    }
}
