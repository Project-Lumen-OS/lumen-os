// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.download;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Host check of the parallel download (Segments, ConnectionScaler, Rate, ParallelDownload) against a local server that behaves like GitHub releases: a stable URL that redirects to a signed CDN link, links that
 * expire (403), Range with ETag and If-Range, a throttle per connection, cut connections, a server ignoring Range.
 */
public final class DownloadTest {
    private static int fails, runs;
    private static File dir;

    private static void ok(String what, boolean cond) {
        runs++;
        if (!cond) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    /** GitHub releases in small: /stable/f redirects to /cdn/GEN/f, an older GEN answers 403. */
    public static final class Server {
        final HttpServer http;
        public volatile byte[] data;
        volatile String etag = "\"v1\"";
        public volatile boolean ignoreRange, gone;
        volatile long perConnBps;
        /** The slowRange-th range request is sent at slowBps, as one crawling connection of a projector. */
        volatile int slowRange;
        volatile long slowBps;
        final AtomicInteger ranges = new AtomicInteger();
        volatile int cutAfter;
        volatile long expireAt = -1, swapAt = -1;
        volatile byte[] swapData;
        final AtomicInteger cuts = new AtomicInteger(), gen = new AtomicInteger(), wholeFirst = new AtomicInteger();
        final AtomicLong served = new AtomicLong();
        final AtomicInteger active = new AtomicInteger(), maxActive = new AtomicInteger();
        public final AtomicInteger stableHits = new AtomicInteger(), refused = new AtomicInteger(), fullBodies = new AtomicInteger();
        final List<Long> rangeStarts = Collections.synchronizedList(new ArrayList<>());

        public Server(byte[] data) throws IOException {
            this.data = data;
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 64);
            http.setExecutor(Executors.newCachedThreadPool());
            http.createContext("/stable/", this::stable);
            http.createContext("/cdn/", this::cdn);
            http.start();
        }

        public String url() { return "http://127.0.0.1:" + http.getAddress().getPort() + "/stable/f.zip"; }

        public void stop() { http.stop(0); }

        private void stable(HttpExchange x) throws IOException {
            stableHits.incrementAndGet();
            if (gone) {
                x.sendResponseHeaders(404, -1);
            } else {
                x.getResponseHeaders().add("Location", "/cdn/" + gen.get() + "/f.zip");
                x.sendResponseHeaders(302, -1);
            }
            x.close();
        }

        private void cdn(HttpExchange x) throws IOException {
            int g = Integer.parseInt(x.getRequestURI().getPath().split("/")[2]);
            if (g != gen.get()) {
                refused.incrementAndGet();
                x.sendResponseHeaders(403, -1);
                x.close();
                return;
            }
            byte[] d = data;
            String range = x.getRequestHeaders().getFirst("Range");
            String ifRange = x.getRequestHeaders().getFirst("If-Range");
            long a = 0, b = d.length - 1, bps = perConnBps;
            // RFC 9110: a weak validator in If-Range never matches; wholeFirst = a client that lost Range on the redirect
            boolean partial = range != null && !ignoreRange && wholeFirst.getAndDecrement() <= 0
                    && (ifRange == null || ifRange.equals(etag) && !ifRange.startsWith("W/"));
            if (partial) {
                String[] r = range.substring("bytes=".length()).split("-", -1);
                a = Long.parseLong(r[0]);
                if (!r[1].isEmpty()) b = Math.min(b, Long.parseLong(r[1]));
                if (!"bytes=0-0".equals(range)) {
                    rangeStarts.add(a);
                    if (ranges.incrementAndGet() == slowRange) bps = slowBps;
                }
                x.getResponseHeaders().add("Content-Range", "bytes " + a + "-" + b + "/" + d.length);
            } else {
                fullBodies.incrementAndGet();
            }
            x.getResponseHeaders().add("ETag", etag);
            x.getResponseHeaders().add("Accept-Ranges", "bytes");
            long len = b - a + 1;
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            boolean cut = cuts.getAndDecrement() > 0;
            try (OutputStream o = x.getResponseBody()) {
                // a cut answer is chunked: closing it early ends the body at once, as a dropped connection would
                x.sendResponseHeaders(partial ? 206 : 200, cut ? 0 : len);
                long t0 = System.nanoTime(), sent = 0;
                while (sent < len) {
                    int n = (int) Math.min(16384, len - sent);
                    if (cut && sent + n > cutAfter) break;
                    o.write(d, (int) (a + sent), n);
                    sent += n;
                    long total = served.addAndGet(n);
                    if (expireAt >= 0 && total >= expireAt) {
                        expireAt = -1;
                        gen.incrementAndGet();
                    }
                    if (swapAt >= 0 && total >= swapAt) {
                        swapAt = -1;
                        data = swapData;
                        etag = "\"v2\"";
                    }
                    if (bps > 0) {
                        long due = t0 + sent * 1_000_000_000L / bps;
                        long wait = due - System.nanoTime();
                        if (wait > 0) Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                    }
                }
            } catch (IOException | InterruptedException e) {
                // the client hung up (a cut range, a probe, a stop)
            } finally {
                active.decrementAndGet();
                x.close();
            }
        }
    }

    public static ParallelDownload.Opener opener() {
        return new ParallelDownload.Opener() {
            @Override
            public HttpURLConnection open(String url) throws IOException {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(2000);
                c.setReadTimeout(2000);           // below the 5 s stall of the test tuning, as 30 s is below 60 s
                c.setUseCaches(false);
                return c;
            }

            @Override
            public boolean allowed(URL u) { return "127.0.0.1".equals(u.getHost()); }
        };
    }

    public static final ParallelDownload.Tuning FAST = new ParallelDownload.Tuning(4, 8, 64 << 10, 300, 200, 5000);
    static final ParallelDownload.Tuning ONE = new ParallelDownload.Tuning(1, 1, 64 << 10, 300, 200, 5000);

    public static ParallelDownload dl(ParallelDownload.Tuning t) {
        return new ParallelDownload(opener(), r -> new Thread(r, "dl-test"), t, m -> { });
    }

    public static byte[] random(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    public static String sha(byte[] b) throws Exception {
        StringBuilder h = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) h.append(String.format("%02x", x & 0xff));
        return h.toString();
    }

    public static File fresh(File dir, String name) {
        File f = new File(dir, name);
        ParallelDownload.discard(f);
        return f;
    }

    static File fresh(String name) { return fresh(dir, name); }

    public static boolean same(File f, byte[] want) throws IOException {
        return f.isFile() && Arrays.equals(Files.readAllBytes(f.toPath()), want);
    }

    // ------------------------------------------------------------------ planner, scaler, rate
    static void planner() {
        Segments s = Segments.plan(10L << 20, 4, 2L << 20);
        List<long[]> m = s.missing();
        ok("plan: 4 ranges", m.size() == 4);
        long at = 0;
        for (long[] r : m) {
            ok("plan: no gap at " + at, r[0] == at);
            at = r[1];
        }
        ok("plan: covers the file", at == 10L << 20);
        ok("plan: small file one range", Segments.plan(3L << 20, 4, 2L << 20).missing().size() == 1);
        ok("plan: tiny file one range", Segments.plan(10, 4, 2L << 20).missing().size() == 1);

        Segments t = Segments.plan(100, 2, 10);
        Segments.Seg a = t.claim(), b = t.claim();
        ok("claim: front half", a.pos == 0 && a.end == 50);
        ok("claim: back half", b.pos == 50 && b.end == 100);
        t.commit(a, t.reserve(a, 10));
        Segments.Seg c = t.claim();
        ok("claim: splits the largest range in use", c != null && c.pos == 75 && c.end == 100 && b.end == 75);
        ok("reserve: stops at the cut", t.reserve(b, 30) == 25);
        t.commit(b, 25);
        ok("reserve: range done after the cut", t.reserve(b, 5) == 0);
        t.release(b);
        ok("done counts written bytes", t.done() == 35);
        List<long[]> left = t.missing();
        ok("missing after split", left.size() == 2 && left.get(0)[0] == 10 && left.get(0)[1] == 50
                && left.get(1)[0] == 75 && left.get(1)[1] == 100);
        Segments u = Segments.plan(15, 1, 10);
        u.claim();
        ok("claim: no split below twice the minimum", u.claim() == null && !u.hasWork());
        ok("of: rejects overlapping ranges", Segments.of(100, 1, Arrays.asList(new long[] {0, 50}, new long[] {40, 60})) == null);
        ok("of: rejects ranges past the end", Segments.of(100, 1, Collections.singletonList(new long[] {90, 101})) == null);
    }

    /** The last ranges, too small to halve: a connection at least twice as fast takes over a slow one's rest. */
    static void tailPlanner() {
        Segments s = Segments.plan(300, 2, 100);
        Segments.Seg a = s.claim(0, 0), b = s.claim(0, 0);
        s.commit(a, s.reserve(a, 150));
        s.release(a);
        s.commit(b, s.reserve(b, 10));
        ok("tail: no takeover while the slow range is new", s.claim(1000, 150) == null);
        Segments.Seg c = s.claim(3000, 150);
        ok("tail: a faster connection takes the whole rest of a slow one", c != null && c.pos == 160 && c.end == 300
                && b.end == 160);
        ok("tail: the slow one ends at its last written byte", s.reserve(b, 5) == 0);
        s.release(b);
        ok("tail: the taken rest is not handed back at once", s.claim(3500, 1 << 20) == null && s.done() == 160);

        Segments e = Segments.plan(300, 2, 100);
        Segments.Seg x = e.claim(0, 0), y = e.claim(0, 0);
        e.commit(x, e.reserve(x, 150));
        e.release(x);
        e.commit(y, e.reserve(y, 60));          // 20 bytes/s, 4.5 s left
        ok("tail: no takeover by a connection less than twice as fast", e.claim(3000, 40) == null);
        ok("tail: no takeover by a connection of unknown speed", e.claim(3000, 0) == null);
        Segments.Seg z = e.claim(3000, 41);
        ok("tail: takeover by one twice as fast", z != null && z.pos == 210 && y.end == 210);

        Segments g = Segments.plan(300, 2, 100);
        Segments.Seg u = g.claim(0, 0), v = g.claim(0, 0);
        g.commit(u, g.reserve(u, 150));
        g.release(u);
        g.commit(v, g.reserve(v, 140));         // 10 bytes left, read in 0.2 s
        ok("tail: no takeover of a rest that ends soon", g.claim(3000, 1 << 20) == null);

        Segments h = Segments.plan(300, 2, 100);
        Segments.Seg m = h.claim(0, 0), n = h.claim(0, 0);
        h.commit(m, h.reserve(m, 150));
        h.release(m);
        h.reserve(n, 30);                       // a connection with nothing written, 30 bytes in flight
        Segments.Seg w = h.claim(5000, 1);
        ok("tail: a stalled connection keeps only the bytes in flight", w != null && w.pos == 180 && n.end == 180);
    }

    static void scaler() {
        ConnectionScaler s = new ConnectionScaler(4, 8, 1.10);
        ok("scaler starts at 4", s.target() == 4);
        ok("scaler: the first window is a warm-up", s.onWindow(1.0, true) == 4);
        ok("scaler: a window without data decides nothing", s.onWindow(0, true) == 4);
        ok("scaler adds after the first measure", s.onWindow(4.0, true) == 5);
        ok("scaler: the window after an addition is a warm-up", s.onWindow(9.0, true) == 5);
        ok("scaler adds while the total grows", s.onWindow(4.8, true) == 6);
        s.onWindow(0, true);
        ok("scaler: a window with a failed connection decides nothing", s.onWindow(5.0, false) == 6);
        ok("scaler grows on after a failed window", s.onWindow(6.0, true) == 7);
        s.onWindow(0, true);
        ok("scaler stops when it no longer grows", s.onWindow(6.2, true) == 7);
        ok("scaler stays settled", s.onWindow(50.0, true) == 7);
        ConnectionScaler g = new ConnectionScaler(4, 8, 1.10);
        double v = 1;
        for (int i = 0; i < 20; i++) g.onWindow(v *= 2, true);
        ok("scaler caps at 8", g.target() == 8);
        ok("scaler start above max", new ConnectionScaler(9, 8, 1.1).target() == 8);
    }

    static void rate() {
        Rate r = new Rate(10000);
        ok("rate: nothing at first", r.add(0, 0) == 0);
        ok("rate: nothing under a second", r.add(500, 500) == 0);
        ok("rate: bytes per second", r.add(1000, 1000) == 1000);
        r.add(20000, 1000);
        ok("rate: old samples drop out", r.add(21000, 3000) == 2000);
    }

    // ------------------------------------------------------------------ against the server
    static void parallel() throws Exception {
        byte[] d = random(8 << 20, 1);
        Server s = new Server(d);
        s.perConnBps = 2L << 20;
        File f = fresh("par.zip");
        long t0 = System.nanoTime();
        String tag = dl(FAST).run(s.url(), f, d.length, sha(d), (b, t, sp) -> true);
        double par = (System.nanoTime() - t0) / 1e9;
        ok("parallel: content", same(f, d));
        ok("parallel: etag", "\"v1\"".equals(tag));
        ok("parallel: no state left", !ParallelDownload.part(f).exists() && !ParallelDownload.state(f).exists());
        ok("parallel: at least 4 connections at once (" + s.maxActive.get() + ")", s.maxActive.get() >= 4);
        ok("parallel: adds connections while the total grows (" + s.maxActive.get() + ")", s.maxActive.get() >= 5);
        ok("parallel: served once (" + s.served.get() + ")", s.served.get() <= d.length + (1 << 20));
        Server one = new Server(d);
        one.perConnBps = 2L << 20;
        File g = fresh("one.zip");
        t0 = System.nanoTime();
        dl(ONE).run(one.url(), g, d.length, sha(d), (b, t, sp) -> true);
        double single = (System.nanoTime() - t0) / 1e9;
        ok("one connection: content", same(g, d));
        System.out.printf("speed-up through a 2 MB/s per connection throttle: one %.1f s, parallel %.1f s, x%.1f%n",
                single, par, single / par);
        ok("parallel at least 2.5x faster than one connection", single / par >= 2.5);
        s.stop();
        one.stop();
    }

    static void resume() throws Exception {
        byte[] d = random(6 << 20, 2);
        Server s = new Server(d);
        s.perConnBps = 512 << 10;
        File f = fresh("res.zip");
        boolean stopped = false;
        try {
            dl(FAST).run(s.url(), f, d.length, sha(d), (b, t, sp) -> b < d.length * 4L / 10);
        } catch (ParallelDownload.Stopped e) {
            stopped = true;
        }
        long have = ParallelDownload.have(f, d.length);
        ok("resume: stopped", stopped);
        ok("resume: no final file yet", !f.exists() && ParallelDownload.part(f).isFile());
        ok("resume: progress saved (" + have + ")", have >= d.length * 3L / 10 && have < d.length);
        long before = s.served.get();
        dl(FAST).run(s.url(), f, d.length, sha(d), (b, t, sp) -> true);   // a new instance: as after a restart
        ok("resume: content", same(f, d));
        ok("resume: only the rest fetched (" + (s.served.get() - before) + ")",
                s.served.get() - before <= d.length - have + (512 << 10));
        File g = fresh("res2.zip");
        try {
            dl(FAST).run(s.url(), g, d.length, "another package", (b, t, sp) -> b < d.length / 2);
        } catch (ParallelDownload.Stopped e) {
            // expected
        }
        long served = s.served.get();
        dl(FAST).run(s.url(), g, d.length, sha(d), (b, t, sp) -> true);
        ok("resume: the state of another package is not used", same(g, d) && s.served.get() - served >= d.length);
        s.stop();
    }

    static void expiredLink() throws Exception {
        byte[] d = random(4 << 20, 3);
        Server s = new Server(d);
        s.perConnBps = 1L << 20;
        s.expireAt = 512 << 10;         // the links expire while the first ranges run, then every range is cut
        s.cutAfter = 300 << 10;
        s.cuts.set(4);
        File f = fresh("exp.zip");
        dl(FAST).run(s.url(), f, d.length, sha(d), (b, t, sp) -> true);
        ok("403: content", same(f, d));
        ok("403: refused at least once (" + s.refused.get() + ")", s.refused.get() >= 1);
        ok("403: link resolved again (" + s.stableHits.get() + ")", s.stableHits.get() >= 2);
        s.stop();
    }

    static void refusedAtStart() throws Exception {
        byte[] d = random(2 << 20, 13);
        Server s = new Server(d);
        s.expireAt = 1;                 // the link expires right after the probe: every first range request gets 403
        File f = fresh("refused.zip");
        ParallelDownload.Tuning tight = new ParallelDownload.Tuning(4, 8, 64 << 10, 300, 200, 800);
        dl(tight).run(s.url(), f, d.length, sha(d), (b, t, sp) -> true);
        ok("403 at the start: renewed and on at once, no stall", same(f, d) && s.refused.get() >= 1);
        s.stop();
    }

    static void noRange() throws Exception {
        byte[] d = random(3 << 20, 4);
        Server s = new Server(d);
        s.ignoreRange = true;
        File f = fresh("norange.zip");
        dl(FAST).run(s.url(), f, d.length, sha(d), (b, t, sp) -> true);
        ok("no Range: content", same(f, d));
        ok("no Range: no range request after the probe", s.rangeStarts.isEmpty());
        ok("no Range: probe, probe of the CDN link, one download (" + s.fullBodies.get() + ")", s.fullBodies.get() == 3);
        s.stop();
    }

    static void rangeLostOnRedirect() throws Exception {
        byte[] d = random(2 << 20, 11);
        Server s = new Server(d);
        s.wholeFirst.set(1);
        File f = fresh("redirect.zip");
        dl(FAST).run(s.url(), f, d.length, sha(d), (b, t, sp) -> true);
        ok("Range lost on the redirect: content", same(f, d));
        ok("Range lost on the redirect: the CDN link asked directly, ranges used", !s.rangeStarts.isEmpty());
        s.stop();
    }

    static void weakEtag() throws Exception {
        byte[] d = random(2 << 20, 12);
        Server s = new Server(d);
        s.etag = "W/\"v1\"";
        File f = fresh("weak.zip");
        dl(FAST).run(s.url(), f, d.length, sha(d), (b, t, sp) -> true);
        ok("weak ETag: content", same(f, d));
        ok("weak ETag: no If-Range, ranges used (" + s.fullBodies.get() + " whole answers)",
                !s.rangeStarts.isEmpty() && s.fullBodies.get() == 0);
        s.stop();
    }

    static void cutConnections() throws Exception {
        byte[] d = random(4 << 20, 5);
        Server s = new Server(d);
        s.cutAfter = 100 << 10;
        s.cuts.set(6);
        File f = fresh("cut.zip");
        dl(FAST).run(s.url(), f, d.length, sha(d), (b, t, sp) -> true);
        ok("cut connections: content", same(f, d));
        s.stop();
    }

    static void changedOnServer() throws Exception {
        byte[] d = random(4 << 20, 6), d2 = random(4 << 20, 7);
        Server s = new Server(d);
        s.perConnBps = 1L << 20;
        s.swapData = d2;
        s.swapAt = d.length / 3;
        File f = fresh("swap.zip");
        dl(FAST).run(s.url(), f, d.length, sha(d2), (b, t, sp) -> true);
        ok("changed during the download: the new file, whole", same(f, d2));
        Server t = new Server(d);
        File g = fresh("swap2.zip");
        try {
            dl(FAST).run(t.url(), g, d.length, "k", (b, x, sp) -> b < d.length / 3);
        } catch (ParallelDownload.Stopped e) {
            // expected
        }
        t.data = d2;
        t.etag = "\"v2\"";
        dl(FAST).run(t.url(), g, d.length, "k", (b, x, sp) -> true);
        ok("changed between runs: the new file, whole", same(g, d2));
        s.stop();
        t.stop();
    }

    static void legacyPrefix() throws Exception {
        byte[] d = random(4 << 20, 8);
        Server s = new Server(d);
        File f = fresh("legacy.zip");
        Files.write(f.toPath(), Arrays.copyOf(d, 1 << 20));
        dl(FAST).run(s.url(), f, d.length, sha(d), (b, t, sp) -> true);
        ok("one-connection partial: content", same(f, d));
        long first = Long.MAX_VALUE;
        synchronized (s.rangeStarts) {
            for (long a : s.rangeStarts) first = Math.min(first, a);
        }
        ok("one-connection partial: continues after its bytes (" + first + ")", first >= 1 << 20);
        s.stop();
    }

    /** One connection crawls (8 KB/s) while the others get 2 MB/s: the end of the file must not wait for it. */
    static void slowTail() throws Exception {
        byte[] d = random(4 << 20, 10);
        Server s = new Server(d);
        s.perConnBps = 2L << 20;
        s.slowRange = 2;
        s.slowBps = 8 << 10;
        File f = fresh("tail.zip");
        long t0 = System.nanoTime();
        dl(FAST).run(s.url(), f, d.length, sha(d), (b, t, sp) -> true);
        double secs = (System.nanoTime() - t0) / 1e9;
        ok("slow connection: content", same(f, d));
        ok(String.format("slow connection: its rest moves to a fast one (%.1f s)", secs), secs < 6);
        s.stop();
    }

    static void gone() throws Exception {
        Server s = new Server(random(1 << 20, 9));
        s.gone = true;
        int code = 0;
        try {
            dl(FAST).run(s.url(), fresh("gone.zip"), 1 << 20, "k", (b, t, sp) -> true);
        } catch (HttpError e) {
            code = e.code;
        }
        ok("404: reported to the caller", code == 404);
        s.stop();
    }

    public static void main(String[] a) throws Exception {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(240_000);
            } catch (InterruptedException e) {
                return;
            }
            System.out.println("FAIL download tests hang (240 s)");
            System.exit(2);
        });
        watchdog.setDaemon(true);
        watchdog.start();
        dir = Files.createTempDirectory("lumen_dl").toFile();
        try {
            planner();
            tailPlanner();
            scaler();
            rate();
            parallel();
            resume();
            expiredLink();
            refusedAtStart();
            noRange();
            rangeLostOnRedirect();
            weakEtag();
            cutConnections();
            changedOnServer();
            legacyPrefix();
            slowTail();
            gone();
        } catch (Throwable e) {     // the servers' threads would keep a failed run alive
            e.printStackTrace(System.out);
            System.out.println("FAIL " + e);
            System.exit(1);
        }
        System.out.println("download passed=" + (runs - fails) + " failed=" + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
