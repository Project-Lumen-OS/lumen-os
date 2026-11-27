// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.util.Log;

import org.json.JSONException;
import org.lumen.download.HttpError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * The one rule that keeps the updater on the newest build: every check reads every channel manifest fresh (never from
 * a cache) and the highest verified version_code is the offer. A saved offer (found, downloading, paused, downloaded) is
 * replaced by any newer build and its files go. It is dropped once the running build is not older (the unit was
 * updated another way). It stays when a check sees an older manifest (a stale copy of a cache or a mirror) or none at
 * all (offline, a 404 while a release is uploaded), so a stale answer never takes the newest offer away.
 * The same rule runs at boot, on every check (the 6 hour job, the update screen at every opening, the job that resumes
 * a download), during a download (as it starts, every {@link Recheck#EVERY_MS} and after a network gap) and right before
 * an install ({@link #beforeInstall}). Plain Java: host test NewestTest against a fake manifest server.
 */
final class Newest {
    private Newest() {}

    static final int MAX_MANIFEST = 256 * 1024, MAX_SIG = 4096;
    /** A manifest whose signature does not match is read again: a release upload replaces the json and the .sig one by one. */
    static final int PAIR_TRIES = 3;
    static long pairPauseMs = 4000;
    /** Newer builds that may replace the offer during one download or install run (a guard, never reached in practice). */
    static final int MAX_SWITCHES = 8;
    /** Held while the offer changes and while a download commits its result. */
    static final Object LOCK = new Object();

    /** Where the manifests come from: Http.fetchFresh on a projector, a fake server in the host test. */
    interface Source {
        byte[] fetch(String url, int maxBytes) throws IOException;
    }

    /** The signature check (Trust.verifyWithSystemCerts on a projector). */
    interface Verifier {
        boolean verify(byte[] json, byte[] sig);
    }

    /** The saved offer and its state (Store) with the downloaded files. */
    interface Offers {
        UpdateState state();
        void setState(UpdateState s);
        String manifestJson();
        String packageJson();
        boolean fromUsb();
        void setOffer(String manifestJson, String packageJson, boolean usb, String usbPath);
        void clearOffer();
        void setError(String res, String arg);
        void setLastCheck(long t);
        void setBetaPublished(boolean on);
        void deleteFiles();
        /** sha256 of the deltas that failed on the running build (DeltaRule: never chosen again). */
        Set<String> refusedDeltas();
        void refuseDelta(String sha256);
    }

    /** One channel file as read: the verified manifest, or the bytes that did not verify, or the error. */
    static final class Read {
        final byte[] json;
        final UpdateManifest manifest;
        final IOException error;

        Read(byte[] json, UpdateManifest m, IOException e) { this.json = json; manifest = m; error = e; }
    }

    /** The manifest and its .sig; a pair that does not verify is read again after a pause (an upload in progress). */
    static Read read(Source src, Verifier v, String url) {
        byte[] json = null;
        IOException err = null;
        for (int i = 0; i < PAIR_TRIES; i++) {
            if (i > 0) pause(pairPauseMs);
            byte[] sig;
            try {
                json = src.fetch(url, MAX_MANIFEST);
            } catch (IOException e) {
                return new Read(null, null, e);
            }
            try {
                sig = src.fetch(url + ".sig", MAX_SIG);
            } catch (IOException e) {
                err = e;
                if (!(e instanceof HttpError)) return new Read(json, null, e);
                continue;   // the .sig is being replaced right now
            }
            err = null;
            UpdateManifest m = verified(json, sig, v);
            if (m != null) return new Read(json, m, null);
        }
        return new Read(json, null, err);
    }

    static UpdateManifest verified(byte[] json, byte[] sig, Verifier v) {
        if (json == null || sig == null || !v.verify(json, sig)) return null;
        try {
            return new UpdateManifest(new String(json, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * An online check of the manifests {@code urls} (Channels.read: the stable one first). Never while an install runs
     * or waits for the restart. A running download goes on while the check runs; a newer build found meanwhile
     * becomes the offer and the download worker switches to it ({@link #superseded}).
     */
    static CheckResult check(Offers o, String[] urls, Source src, Verifier v, int running, long now) {
        UpdateState before = o.state();
        if (before == UpdateState.INSTALLING || before == UpdateState.READY) return result(CheckOutcome.BUSY, null, "");
        dropStale(o, running, "check");
        before = o.state();
        if (before != UpdateState.DOWNLOADING) o.setState(UpdateState.CHECKING);
        Read[] r = new Read[urls.length];
        int[] codes = new int[urls.length];
        for (int i = 0; i < urls.length; i++) {
            r[i] = read(src, v, urls[i]);
            codes[i] = r[i].manifest != null ? r[i].manifest.versionCode : Channels.MISSING;
            if (r[i].manifest == null) log("manifest " + i + " not used: " + (r[i].error != null ? r[i].error : "not verified"));
        }
        if (urls.length > 1) o.setBetaPublished(codes[1] != Channels.MISSING);
        int k = Channels.pick(codes);
        if (k >= 0) return accept(o, r[k].manifest, false, "", before, running, now);
        if (r[0].json != null && r[0].error == null) return failed(o, before, "err_signature");
        if (r[0].error instanceof HttpError && ((HttpError) r[0].error).code == 404) {
            // no release published (or the channel file is being replaced): nothing new, a saved offer stays
            synchronized (LOCK) {
                o.setLastCheck(now);
                o.setState(keeps(o, before) ? before : UpdateState.IDLE);
            }
            return result(CheckOutcome.UP_TO_DATE, null, "");
        }
        return failed(o, before, "err_network");
    }

    /** A check that found nothing usable never hides a saved offer: its state comes back, else the error shows. */
    private static CheckResult failed(Offers o, UpdateState before, String res) {
        synchronized (LOCK) {
            if (keeps(o, before)) o.setState(before);
            else o.setError(res, "");
        }
        return result(CheckOutcome.ERROR, null, res);
    }

    /** A saved offer is there and its state is one an empty or failed check keeps (Outcome.keepOnCheckError). */
    static boolean keeps(Offers o, UpdateState before) {
        return !o.packageJson().isEmpty() && Outcome.keepOnCheckError(before.name());
    }

    /**
     * Records the verified manifest {@code m} against the saved offer: a newer build replaces the offer (its files go),
     * the same package keeps its state, an older one leaves the newer offer as it is.
     * @param before the state before the check (DOWNLOADING: a worker owns the files and switches itself)
     */
    static CheckResult accept(Offers o, UpdateManifest m, boolean usb, String usbDir, UpdateState before, int running,
            long now) {
        synchronized (LOCK) {
            o.setLastCheck(now);
            UpdateManifest saved = saved(o);
            if (saved != null && m.versionCode < saved.versionCode) {
                // an older manifest than the offer this unit already has (a cache or a mirror answered with an old
                // copy, a USB stick holds an older build): the newer offer stays
                log("manifest " + m.version + " (" + m.versionCode + ") is older than the offer " + saved.version
                        + " (" + saved.versionCode + "): the offer stays");
                o.setState(keeps(o, before) ? before : UpdateState.AVAILABLE);
                return new CheckResult(CheckOutcome.OFFER, saved, "", "", true);
            }
            Preflight.Block b = Preflight.applicable(m);
            if (b != null && "uptodate".equals(b.res)) {
                // nothing newer than the running build (a saved offer is newer than it, so none is left here)
                if (!o.packageJson().isEmpty()) {
                    o.deleteFiles();
                    o.clearOffer();
                }
                o.setState(UpdateState.IDLE);
                return result(CheckOutcome.UP_TO_DATE, m, "");
            }
            if (b != null) {
                o.setError(b.res, b.arg);
                return new CheckResult(CheckOutcome.BLOCKED, m, b.res, b.arg);
            }
            UpdateManifest.Pkg p = DeltaRule.choose(m, Ota.buildId(), running, usb, o.refusedDeltas());
            if (p == null) {
                o.setError("err_signature", "");
                return result(CheckOutcome.ERROR, m, "err_signature");
            }
            if (saved != null && p.sha256.equals(sha(o.packageJson())) && o.fromUsb() == usb) {
                o.setState(keeps(o, before) ? before : UpdateState.AVAILABLE);
            } else if (before == UpdateState.DOWNLOADING) {
                // the running download is of an older build: the worker sees the new package, stops, deletes its
                // files and fetches this one (downloadNewest)
                o.setOffer(m.json, p.json, usb, usbDir);
                o.setState(UpdateState.DOWNLOADING);
            } else {
                o.deleteFiles();
                o.setOffer(m.json, p.json, usb, usbDir);
                o.setState(UpdateState.AVAILABLE);
            }
            log("offer " + m.version + " (" + m.versionCode + ") " + p.type + " " + p.size + " B" + (usb ? " (USB)" : ""));
            return result(CheckOutcome.OFFER, m, "");
        }
    }

    /**
     * The offer is a delta and it failed ({@code why}: its sha256, its metadata, its file gone, update_engine's code): it
     * is refused for the running build, its files go and the full package of the same manifest becomes the offer, in
     * state {@code next} (DOWNLOADING: the download worker switches to it, Newest.downloadNewest; AVAILABLE after an
     * install). @return false when the offer is no delta or the manifest has no full package (the error stays)
     */
    static boolean deltaFailed(Offers o, String why, UpdateState next) {
        synchronized (LOCK) {
            UpdateManifest m = saved(o);
            UpdateManifest.Pkg d;
            try {
                d = o.packageJson().isEmpty() ? null : UpdateManifest.Pkg.parse(o.packageJson());
            } catch (JSONException e) {
                d = null;
            }
            UpdateManifest.Pkg full = m == null ? null : m.full();
            if (d == null || !d.isDelta() || full == null) return false;
            log("delta from " + d.fromBuildId + " failed (" + why + "): the full package " + full.size + " B instead");
            o.refuseDelta(d.sha256);
            o.deleteFiles();
            o.setOffer(m.json, full.json, false, "");
            o.setState(next);
            return true;
        }
    }

    /**
     * Drops a saved offer that is not newer than the running build (Outcome.offerStale) or of a channel this check no
     * longer reads: its files, the offer, state IDLE. Never while an install runs or waits for the restart.
     * @return true = it was dropped
     */
    static boolean dropStale(Offers o, int running, String why) {
        synchronized (LOCK) {
            UpdateState s = o.state();
            if (s == UpdateState.INSTALLING || s == UpdateState.READY) return false;
            UpdateManifest saved = saved(o);
            int code = saved != null ? saved.versionCode : -1;
            boolean channel = saved != null && !Channels.accepts(saved.channel);
            if (!Outcome.offerStale(code, running) && !channel) return false;
            log(why + ": offer " + saved.version + " (" + code + ", " + s + ") dropped: "
                    + (channel ? "channel " + saved.channel + " is not read any more" : "not newer than the running " + running));
            o.deleteFiles();
            o.clearOffer();
            o.setState(UpdateState.IDLE);
            return true;
        }
    }

    /** The package a download works on is no longer the offer: a newer build replaced it, or the offer is gone. */
    static boolean superseded(String working, Offers o) {
        return !working.equals(o.packageJson());
    }

    /**
     * Downloads the offer; when a newer build replaces it meanwhile (a check during the download), the old files go
     * and the newest one is downloaded. DOWNLOADED is set here, under the same lock a check changes the offer with, so a
     * finished download of a replaced build is never marked ready.
     * @param once downloads exactly the package given (its json) and verifies it; it stops early once that package is
     *             superseded. true = complete and verified
     */
    static boolean downloadNewest(Offers o, Predicate<String> once) {
        for (int i = 0; i < MAX_SWITCHES; i++) {
            String pkg = o.packageJson();
            if (pkg.isEmpty()) return false;
            boolean ok = once.test(pkg);
            synchronized (LOCK) {
                if (!superseded(pkg, o)) {
                    if (ok) o.setState(UpdateState.DOWNLOADED);
                    return ok;
                }
                log("download replaced by a newer build: the old files go");
                o.deleteFiles();
                if (o.packageJson().isEmpty()) return false;
            }
        }
        return false;
    }

    /**
     * Right before an install: one more fresh check. A newer build found meanwhile replaces the downloaded one and is
     * downloaded first, so Install never installs anything but the newest verified build. When the check cannot reach
     * the server, the saved offer (verified, newer than the running build) is installed.
     * @param check    Checker.check
     * @param download UpdateService's download (downloadNewest)
     * @return true = the offer is the newest known build and is downloaded
     */
    static boolean beforeInstall(Offers o, Runnable check, BooleanSupplier download) {
        for (int i = 0; i < MAX_SWITCHES; i++) {
            check.run();
            UpdateState s = o.state();
            if (s == UpdateState.DOWNLOADED) return true;
            if (s != UpdateState.AVAILABLE || !download.getAsBoolean()) return false;
        }
        return false;
    }

    /**
     * When a running download checks the manifests again: as it starts (a resumed or long waiting download may be of
     * an old build), every EVERY_MS and once the network is back after a gap.
     */
    static final class Recheck {
        static final long EVERY_MS = 20 * 60 * 1000L;
        private long last;
        private boolean start = true, gap;

        Recheck(long nowMs) { last = nowMs; }

        synchronized void offline() { gap = true; }

        /** @param nowMs a monotonic clock (SystemClock.elapsedRealtime): a wall clock set by the network jumps */
        synchronized boolean due(long nowMs, boolean online) {
            if (!online) return false;
            if (!start && !gap && nowMs - last < EVERY_MS && nowMs >= last) return false;
            start = false;
            gap = false;
            last = nowMs;
            return true;
        }
    }

    static UpdateManifest saved(Offers o) {
        String j = o.manifestJson();
        if (j.isEmpty()) return null;
        try {
            return new UpdateManifest(j);
        } catch (JSONException e) {
            return null;
        }
    }

    private static String sha(String pkgJson) {
        try {
            return pkgJson.isEmpty() ? "" : UpdateManifest.Pkg.parse(pkgJson).sha256;
        } catch (JSONException e) {
            return "";
        }
    }

    private static CheckResult result(CheckOutcome c, UpdateManifest m, String res) {
        return new CheckResult(c, m, res, "");
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String s) {
        try {
            Log.i(Ota.TAG, s);
        } catch (Throwable ignored) {   // the host test has no android.util.Log
        }
    }
}
