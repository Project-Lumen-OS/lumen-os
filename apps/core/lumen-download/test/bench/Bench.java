// SPDX-License-Identifier: Apache-2.0
package org.lumen.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The download against a real release asset (bench/run.sh, through throttle_proxy.py): mode "one" = one connection
 * for a number of seconds, "parallel" = the apps' own tuning for the whole file, then its sha256. A mode ending in
 * "+403" spoils one request to the first CDN link after 5 s, so the CDN refuses it (403) as it would refuse an expired
 * link: the connection must renew the link and go on.
 *
 *   Bench MODE URL SIZE SHA256 OUT [SECONDS]
 */
public final class Bench {
    public static void main(String[] a) throws Exception {
        String mode = a[0], url = a[1], sha = a[3];
        long size = Long.parseLong(a[2]), limit = a.length > 5 ? Long.parseLong(a[5]) * 1000 : Long.MAX_VALUE;
        File out = new File(a[4]);
        boolean spoil = mode.endsWith("+403");
        long t0 = System.currentTimeMillis();
        String[] first = {null};
        boolean[] spoiled = {false};
        ParallelDownload.Opener https = u -> {
            if (u.contains("githubusercontent.com") && u.indexOf('?') > 0) {
                if (first[0] == null) first[0] = u;
                if (spoil && !spoiled[0] && u.equals(first[0]) && System.currentTimeMillis() - t0 > 5000) {
                    // the CDN serves a cached file whatever the signature says: another asset path gets its 403
                    spoiled[0] = true;
                    System.out.println("bench: spoiling the first CDN link once");
                    int q = u.indexOf('?');
                    u = u.substring(0, q - 1) + (u.charAt(q - 1) == '0' ? '1' : '0') + u.substring(q);
                }
            }
            if (!u.startsWith("https://")) throw new IOException("not https: " + u);
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setUseCaches(false);
            c.setRequestProperty("User-Agent", "LumenUpdater-bench");
            c.setRequestProperty("Accept-Encoding", "identity");
            return c;
        };
        ParallelDownload.Tuning t = mode.startsWith("one")
                ? new ParallelDownload.Tuning(1, 1, 2L << 20, 4000, 2000, 60000) : ParallelDownload.Tuning.DEFAULT;
        ParallelDownload d = new ParallelDownload(https, Thread::new, t, m -> System.out.println("log: " + m));
        long[] got = {0};
        long[] lastPrint = {0};
        try {
            d.run(url, out, size, sha, (b, tot, sp) -> {
                long now = System.currentTimeMillis();
                got[0] = b;
                if (now - lastPrint[0] >= 10000) {
                    lastPrint[0] = now;
                    System.out.printf("bench: %s %d s %.1f MB of %.1f MB, %.2f MB/s now%n", mode, (now - t0) / 1000,
                            b / 1e6, tot / 1e6, sp / 1e6);
                }
                return now - t0 < limit;
            });
        } catch (ParallelDownload.Stopped e) {
            // the time limit of mode one
        }
        double s = (System.currentTimeMillis() - t0) / 1000.0;
        long bytes = out.isFile() ? out.length() : got[0];
        System.out.printf("RESULT %s %.1f MB in %.1f s = %.2f MB/s%n", mode, bytes / 1e6, s, bytes / 1e6 / s);
        if (out.isFile()) {
            String h = sha256(out);
            System.out.println("SHA256 " + (h.equals(sha) ? "MATCH " : "MISMATCH ") + h);
        }
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[1 << 20];
            for (int n; (n = in.read(b)) > 0; ) md.update(b, 0, n);
        }
        StringBuilder h = new StringBuilder();
        for (byte x : md.digest()) h.append(String.format("%02x", x & 0xff));
        return h.toString();
    }
}
