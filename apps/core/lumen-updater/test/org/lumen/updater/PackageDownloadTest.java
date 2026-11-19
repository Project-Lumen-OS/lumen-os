// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import org.lumen.download.DownloadTest;
import org.lumen.download.DownloadTest.Server;
import org.lumen.download.HttpError;
import org.lumen.download.ParallelDownload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.lumen.download.DownloadTest.FAST;
import static org.lumen.download.DownloadTest.dl;
import static org.lumen.download.DownloadTest.fresh;
import static org.lumen.download.DownloadTest.random;
import static org.lumen.download.DownloadTest.same;
import static org.lumen.download.DownloadTest.sha;

/**
 * Host check of the updater's PackageDownload (network wait, retries, the whole-file sha256 with one more pass, 404
 * ends it) over the shared ParallelDownload, against the GitHub-like server of apps/core/lumen-download's test.
 */
public final class PackageDownloadTest {
    private static int fails, runs;
    private static File dir;

    private static void ok(String what, boolean cond) {
        runs++;
        if (!cond) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static final class Host implements PackageDownload.Host {
        final String want;
        final Server s;
        final byte[] good;
        int offline, verifies, offlineChecks;

        Host(String want, Server s, byte[] good) {
            this.want = want;
            this.s = s;
            this.good = good;
        }

        @Override
        public boolean online() { return offlineChecks-- <= 0; }

        @Override
        public boolean stopRequested() { return false; }

        @Override
        public void sleep(long ms) { }

        @Override
        public void offline(long have, long total) { offline++; }

        @Override
        public boolean progress(long bytes, long total, long speed) { return true; }

        @Override
        public boolean verified(File f) throws IOException {
            verifies++;
            String h;
            try {
                h = sha(Files.readAllBytes(f.toPath()));
            } catch (Exception e) {
                throw new IOException(e);
            }
            if (s != null && good != null) s.data = good;   // the corruption was on the way: the next pass is clean
            return h.equals(want);
        }
    }

    static void hashRetry() throws Exception {
        byte[] good = random(2 << 20, 10), bad = good.clone();
        bad[12345] ^= 1;
        Server s = new Server(bad);
        Host h = new Host(sha(good), s, good);
        h.offlineChecks = 2;
        File f = fresh(dir, "hash.zip");
        String tag = new PackageDownload(dl(FAST), h).fetch(s.url(), f, good.length, sha(good), false);
        ok("sha mismatch: downloaded again and verified", tag != null && same(f, good));
        ok("sha mismatch: checked twice (" + h.verifies + ")", h.verifies == 2);
        ok("offline: waited for the network (" + h.offline + ")", h.offline == 2);

        Server always = new Server(bad);
        Host h2 = new Host(sha(good), always, null);
        File g = fresh(dir, "hash2.zip");
        boolean broken = false;
        try {
            new PackageDownload(dl(FAST), h2).fetch(always.url(), g, good.length, sha(good), false);
        } catch (PackageDownload.HashMismatch e) {
            broken = true;
        }
        ok("sha mismatch twice: broken", broken && h2.verifies == 1 + PackageDownload.HASH_RETRIES);
        ok("sha mismatch twice: nothing left", !g.exists() && !ParallelDownload.part(g).exists()
                && !ParallelDownload.state(g).exists());

        always.gone = true;
        int hits = always.stableHits.get(), code = 0;
        try {
            new PackageDownload(dl(FAST), new Host("x", null, null)).fetch(always.url(), fresh(dir, "g.zip"), good.length, "x", false);
        } catch (HttpError e) {
            code = e.code;
        }
        ok("404: no retries (" + (always.stableHits.get() - hits) + ")", code == 404 && always.stableHits.get() - hits == 1);

        File c = fresh(dir, "complete.zip");
        Files.write(c.toPath(), good);
        Host h3 = new Host(sha(good), null, null);
        h3.offlineChecks = 1000;
        ok("complete file: checked without network",
                new PackageDownload(dl(FAST), h3).fetch(s.url(), c, good.length, sha(good), true) != null && h3.offline == 0);
        s.stop();
        always.stop();
    }

    public static void main(String[] a) throws Exception {
        dir = Files.createTempDirectory("lumen_pkg_dl").toFile();
        try {
            hashRetry();
        } catch (Throwable e) {     // the servers' threads would keep a failed run alive
            e.printStackTrace(System.out);
            System.out.println("FAIL " + e);
            System.exit(1);
        }
        System.out.println("package download passed=" + (runs - fails) + " failed=" + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
