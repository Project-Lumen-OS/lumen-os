// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.os.SystemProperties;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntConsumer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * The updater always offers and installs the newest build (Newest), against a fake GitHub release server over HTTPS:
 * a channel file answers 302 to an asset (a new asset id per upload, as GitHub does), the asset has an ETag and a
 * max-age. The device side is the real code: Http.fetchFresh, Trust.verify, Channels.pick, Preflight, Newest.check,
 * downloadNewest, beforeInstall, dropStale, Recheck; only the store (Offers), the package download and the install are
 * in memory. UpdateService and CheckJob call exactly these (test/run.sh checks the wiring).
 * Scenario: publish A, the unit finds A; B comes while A downloads; C after the newest is downloaded; offline then
 * online (also inside a download); a cache answers an old copy, then the fresh one; the json and its .sig change one
 * after the other; a 404 while a file is replaced; a build comes right when Install is pressed; after the install the
 * same build is never offered again; a lower manifest is never a downgrade; beta off; a download left over after the
 * unit was updated another way.
 * After every step the offer is checked against the newest published build.
 * Usage: java -Djavax.net.ssl.trustStore=KS ... org.lumen.updater.NewestTest KS PASSWORD
 */
public final class NewestTest {
    private static int passed, failed;

    private static void eq(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    // ------------------------------------------------------------------ the fake release server
    static final class Server {
        final HttpsServer s;
        final Map<String, byte[]> live = Collections.synchronizedMap(new HashMap<>());
        final Map<String, byte[]> staleBytes = Collections.synchronizedMap(new HashMap<>());
        final Map<String, Integer> staleLeft = Collections.synchronizedMap(new HashMap<>());
        final Map<Integer, byte[]> assets = Collections.synchronizedMap(new HashMap<>());
        final List<String> nonces = Collections.synchronizedList(new ArrayList<>());
        final List<String> bad = Collections.synchronizedList(new ArrayList<>());
        volatile boolean offline;
        volatile int requests, assetHits;
        /** While set: the unit's state at every channel read (a check must not stop a running download). */
        volatile java.util.function.Supplier<Object> probe;
        final List<Object> probed = Collections.synchronizedList(new ArrayList<>());
        final PrivateKey key;

        Server(SSLContext ctx, PrivateKey key) throws IOException {
            this.key = key;
            s = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.setHttpsConfigurator(new HttpsConfigurator(ctx));
            s.createContext("/", this::handle);
            s.setExecutor(r -> {
                Thread t = new Thread(r, "fake-github");
                t.setDaemon(true);
                t.start();
            });
            s.start();
        }

        String base() { return "https://127.0.0.1:" + s.getAddress().getPort() + "/download/"; }

        void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath(), q = ex.getRequestURI().getRawQuery();
            if (offline) {   // no route: the connection ends without an answer
                ex.close();
                return;
            }
            if (path.startsWith("/download/")) {
                requests++;
                if (probe != null) probed.add(probe.get());
                String file = path.substring(10);
                Headers h = ex.getRequestHeaders();
                if (q == null || !q.matches("lumen_nc=[0-9a-f]+")) bad.add(file + ": no cache-busting query (" + q + ")");
                else nonces.add(q);
                String cc = h.getFirst("Cache-Control");
                if (cc == null || !cc.contains("no-cache")) bad.add(file + ": Cache-Control " + cc);
                if (!"no-cache".equals(h.getFirst("Pragma"))) bad.add(file + ": Pragma " + h.getFirst("Pragma"));
                if (h.containsKey("If-None-Match") || h.containsKey("If-Modified-Since")) bad.add(file + ": conditional request");
                byte[] b;
                synchronized (this) {
                    Integer left = staleLeft.get(file);
                    if (left != null && left > 0) {
                        staleLeft.put(file, left - 1);
                        b = staleBytes.get(file);
                    } else {
                        b = live.get(file);
                    }
                }
                if (b == null) {
                    send(ex, 404, new byte[0], null);
                    return;
                }
                int id = Collections.unmodifiableMap(assets).size() + 1;
                assets.put(id, b);
                ex.getResponseHeaders().add("Location", "https://127.0.0.1:" + s.getAddress().getPort() + "/asset/" + id);
                ex.getResponseHeaders().add("Cache-Control", "no-cache");
                ex.sendResponseHeaders(302, -1);
                ex.close();
                return;
            }
            if (path.startsWith("/asset/")) {
                assetHits++;
                byte[] b = assets.get(Integer.parseInt(path.substring(7)));
                send(ex, b == null ? 404 : 200, b == null ? new byte[0] : b, "\"" + path.substring(7) + "\"");
                return;
            }
            send(ex, 404, new byte[0], null);
        }

        private static void send(HttpExchange ex, int code, byte[] b, String etag) throws IOException {
            if (etag != null) {
                ex.getResponseHeaders().add("ETag", etag);
                ex.getResponseHeaders().add("Last-Modified", "Mon, 23 Nov 2026 00:26:27 GMT");
                ex.getResponseHeaders().add("Cache-Control", "max-age=300");
            }
            ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
            if (b.length > 0) try (OutputStream o = ex.getResponseBody()) {
                o.write(b);
            }
            ex.close();
        }

        byte[] sign(byte[] json) {
            try {
                Signature g = Signature.getInstance("SHA256withRSA");
                g.initSign(key);
                g.update(json);
                return g.sign();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        synchronized void publish(String file, byte[] json) {
            live.put(file, json);
            live.put(file + ".sig", sign(json));
        }

        /** The json is new at once, its .sig answers the old one for the next n reads (an upload in progress). */
        synchronized void publishSigLater(String file, byte[] json, int n) {
            byte[] old = live.get(file + ".sig");
            publish(file, json);
            stale(file + ".sig", old, n);
        }

        /** A cache answers an older copy (json and its .sig) for the next n reads. */
        synchronized void staleCopy(String file, byte[] json, int n) {
            stale(file, json, n);
            stale(file + ".sig", sign(json), n);
        }

        synchronized void stale(String file, byte[] b, int n) {
            staleBytes.put(file, b);
            staleLeft.put(file, n);
        }

        /** version_code of a live channel file, -1 none. */
        int code(String file) {
            byte[] b = live.get(file);
            if (b == null) return -1;
            try {
                return new UpdateManifest(new String(b, StandardCharsets.UTF_8)).versionCode;
            } catch (Exception e) {
                return -1;
            }
        }
    }

    // ------------------------------------------------------------------ the unit: store, download, install in memory
    static final class Mem implements Newest.Offers {
        UpdateState state = UpdateState.IDLE;
        String manifest = "", pkg = "", usbPath = "", err = "";
        boolean usb, betaPublished;
        long lastCheck;
        final Set<String> files = new TreeSet<>();

        @Override public UpdateState state() { return state; }
        @Override public void setState(UpdateState s) { state = s; }
        @Override public String manifestJson() { return manifest; }
        @Override public String packageJson() { return pkg; }
        @Override public boolean fromUsb() { return usb; }
        @Override public void setOffer(String m, String p, boolean u, String dir) { manifest = m; pkg = p; usb = u; usbPath = dir; }
        @Override public void clearOffer() { manifest = ""; pkg = ""; usb = false; usbPath = ""; }
        @Override public void setError(String res, String arg) { state = UpdateState.ERROR; err = res; }
        @Override public void setLastCheck(long t) { lastCheck = t; }
        @Override public void setBetaPublished(boolean on) { betaPublished = on; }
        @Override public void deleteFiles() { files.clear(); }

        int offered() {
            UpdateManifest m = Newest.saved(this);
            return m == null ? -1 : m.versionCode;
        }
    }

    static final String STABLE = "update-public-nogms.json", BETA = "update-public-beta.json";
    static Server srv;
    static Mem unit;
    static int running, installed = -1;
    static String[] urls;
    static final Map<Integer, byte[]> BUILDS = new HashMap<>();

    static void run(int vc, String version) {
        running = vc;
        SystemProperties.VALUES.put("ro.z9x.version_code", String.valueOf(vc));
        SystemProperties.VALUES.put("ro.z9x.version", version);
        SystemProperties.VALUES.put("ro.z9x.build_id", "lumen-" + version);
    }

    static byte[] build(String channel, String version, int vc) throws Exception {
        String sha = hex(MessageDigest.getInstance("SHA-256").digest(version.getBytes(StandardCharsets.UTF_8)));
        String z = "https://example.invalid/z9x-" + version + "/lumen-os-" + version + "-full-ota.zip";
        byte[] b = ("{\"schema\":1,\"device\":\"z9x\",\"channel\":\"" + channel + "\",\"version\":\"" + version
                + "\",\"version_code\":" + vc + ",\"build_id\":\"lumen-" + version + "\",\"packages\":[{\"type\":\"full\","
                + "\"url\":\"" + z + "\",\"size\":1000,\"sha256\":\"" + sha + "\",\"payload_offset\":100,"
                + "\"payload_size\":800,\"metadata_url\":\"" + z + ".meta\",\"payload_properties\":[\"FILE_HASH=x\"]}]}")
                .getBytes(StandardCharsets.UTF_8);
        BUILDS.put(vc, b);
        return b;
    }

    static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static Newest.Verifier verifier;

    /** Checker.check: the same call with this unit's store. */
    static CheckResult check() {
        return Newest.check(unit, urls, Http::fetchFresh, verifier, running, System.currentTimeMillis());
    }

    /** UpdateService.download: one package at a time in four parts; the hook runs before each part. */
    static boolean download(IntConsumer hook) {
        return Newest.downloadNewest(unit, pkg -> {
            unit.state = UpdateState.DOWNLOADING;
            for (int part = 0; part < 4; part++) {
                hook.accept(part);
                if (Newest.superseded(pkg, unit)) return false;   // PackageDownload.Host.stopRequested
            }
            try {
                unit.files.add(UpdateManifest.Pkg.parse(pkg).fileName());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return true;
        });
    }

    /** "Install now": UpdateService ACTION_INSTALL (dropStale, beforeInstall), then the restart into the build. */
    static void install() {
        if (Newest.dropStale(unit, running, "install")) return;
        if (!Newest.beforeInstall(unit, NewestTest::check, () -> download(p -> { }))) return;
        UpdateManifest m = Newest.saved(unit);
        installed = m.versionCode;
        run(m.versionCode, m.version);
        unit.deleteFiles();   // BootReceiver.finish after the update's boot
        unit.clearOffer();
        unit.state = UpdateState.IDLE;
        Newest.dropStale(unit, running, "boot");
    }

    static int newestPublished() {
        int n = -1;
        for (String u : urls) n = Math.max(n, srv.code(u.substring(u.lastIndexOf('/') + 1)));
        return n;
    }

    /** The invariant after an online check: the offer is the newest published build when that one is newer. */
    static void newest(String step) {
        int n = newestPublished();
        eq(step + ": offer is the newest published", n > running ? n : -1, unit.offered());
    }

    static String file(int vc) {
        try {
            return UpdateManifest.Pkg.parse(new UpdateManifest(new String(BUILDS.get(vc), StandardCharsets.UTF_8))
                    .packages.get(0).json).fileName();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] a) {
        try {
            scenario(a[0], a[1].toCharArray());
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            System.exit(1);   // the server's threads must not keep a failed run alive
        }
        System.out.println("newest passed=" + passed + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    static void scenario(String ks, char[] pw) throws Exception {
        KeyStore k = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(ks)) {
            k.load(in, pw);
        }
        String alias = k.aliases().nextElement();
        X509Certificate cert = (X509Certificate) k.getCertificate(alias);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(k, pw);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        srv = new Server(ctx, (PrivateKey) k.getKey(alias, pw));
        verifier = (j, s) -> Trust.verify(j, s, List.of(cert));
        Newest.pairPauseMs = 20;
        urls = new String[] {srv.base() + STABLE, srv.base() + BETA};
        unit = new Mem();
        run(1010002, "1.1.0-beta2");

        // the cache-busting address
        eq("fresh query", true, Http.fresh("https://h/x.json").matches("https://h/x\\.json\\?lumen_nc=[0-9a-f]+"));
        eq("fresh query after one", true, Http.fresh("https://h/x.json?a=1#f").matches("https://h/x\\.json\\?a=1&lumen_nc=[0-9a-f]+"));
        eq("fresh query differs", false, Http.fresh("https://h/x").equals(Http.fresh("https://h/x")));

        // 0. the unit runs 1.1.0-beta2, the beta channel names it, the stable one 1.0.10: up to date
        srv.publish(STABLE, build("public-nogms", "1.0.10", 1001099));
        srv.publish(BETA, build("public-beta", "1.1.0-beta2", 1010002));
        eq("0 running build: up to date", CheckOutcome.UP_TO_DATE, check().outcome);
        eq("0 state", UpdateState.IDLE, unit.state);
        newest("0");
        eq("0 beta published", true, unit.betaPublished);

        // 1. publish A: found
        srv.publish(BETA, build("public-beta", "1.1.0-beta3", 1010003));
        eq("1 A found", CheckOutcome.OFFER, check().outcome);
        eq("1 state", UpdateState.AVAILABLE, unit.state);
        newest("1");

        // 2. B comes while A downloads: the download's recheck switches to B, A's part goes, B is downloaded
        boolean[] seen = new boolean[1];
        boolean ok = download(part -> {
            if (part != 1 || seen[0]) return;
            seen[0] = true;
            try {
                srv.publish(BETA, build("public-beta", "1.1.0-beta4", 1010004));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            srv.probe = () -> unit.state;
            CheckResult r = check();
            srv.probe = null;
            eq("2 the download stays DOWNLOADING while the check reads", "[DOWNLOADING, DOWNLOADING, DOWNLOADING, DOWNLOADING]",
                    srv.probed);
            eq("2 check during the download", CheckOutcome.OFFER, r.outcome);
            eq("2 the download goes on (no CHECKING)", UpdateState.DOWNLOADING, unit.state);
            newest("2 during the download");
        });
        eq("2 download of the newest", true, ok);
        eq("2 state", UpdateState.DOWNLOADED, unit.state);
        eq("2 only B's files", "[" + file(1010004) + "]", unit.files);
        newest("2");

        // 3. C after the newest is downloaded: the screen opens and checks, C replaces the downloaded B
        srv.publish(BETA, build("public-beta", "1.1.0-beta5", 1010005));
        eq("3 C found", CheckOutcome.OFFER, check().outcome);
        eq("3 B's download is gone", "[]", unit.files);
        eq("3 state", UpdateState.AVAILABLE, unit.state);
        newest("3");
        eq("3 C downloaded", true, download(p -> { }));
        eq("3 C's files", "[" + file(1010005) + "]", unit.files);

        // 4. offline: D is published, the check cannot see it and keeps C as it is; online again: D
        srv.offline = true;
        srv.publish(BETA, build("public-beta", "1.1.0-beta6", 1010006));
        eq("4 offline check", CheckOutcome.ERROR, check().outcome);
        eq("4 offline keeps the downloaded C", UpdateState.DOWNLOADED, unit.state);
        eq("4 offline keeps C's files", "[" + file(1010005) + "]", unit.files);
        eq("4 offline offer", 1010005, unit.offered());
        srv.offline = false;
        eq("4 online: D found", CheckOutcome.OFFER, check().outcome);
        eq("4 C's files gone", "[]", unit.files);
        newest("4");
        // a network gap inside the download: the first answer after it rechecks and E replaces D
        Newest.Recheck rc = new Newest.Recheck(0);
        eq("4 the download's start check", true, rc.due(0, true));
        boolean[] gap = new boolean[2];
        ok = download(part -> {
            if (part == 1 && !gap[0]) {
                gap[0] = true;
                srv.offline = true;
                rc.offline();
                eq("4 no recheck while offline", false, rc.due(1000, false));
            } else if (part == 2 && gap[0] && !gap[1]) {
                gap[1] = true;
                srv.offline = false;
                try {
                    srv.publish(BETA, build("public-beta", "1.1.0-beta7", 1010007));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                eq("4 recheck once the network is back", true, rc.due(2000, true));
                check();
                eq("4 no second recheck at once", false, rc.due(2001, true));
            }
        });
        eq("4 E downloaded after the gap", true, ok);
        eq("4 only E's files", "[" + file(1010007) + "]", unit.files);
        newest("4 after the gap");

        // 5. a cache answers the old C (a valid signed copy), then the fresh E: E and its files stay
        srv.staleCopy(BETA, BUILDS.get(1010005), 1);
        CheckResult r = check();
        eq("5 stale copy: offer kept", true, r.kept);
        eq("5 stale copy: kept offer is E", 1010007, r.manifest.versionCode);
        eq("5 stale copy: still downloaded", UpdateState.DOWNLOADED, unit.state);
        eq("5 stale copy: E's files stay", "[" + file(1010007) + "]", unit.files);
        r = check();
        eq("5 fresh answer: E", 1010007, r.manifest.versionCode);
        eq("5 fresh answer: state", UpdateState.DOWNLOADED, unit.state);
        newest("5");

        // 6. F: its json is up before its .sig (an upload in progress): read again, F verified and offered
        srv.publishSigLater(BETA, build("public-beta", "1.1.0-beta8", 1010008), 1);
        eq("6 F after the pair settled", CheckOutcome.OFFER, check().outcome);
        newest("6");
        eq("6 E's files gone", "[]", unit.files);
        // a 404 while the beta file is replaced: the lower stable one is read, the found F stays found
        byte[] f = srv.live.remove(BETA), st = srv.live.remove(STABLE);
        srv.live.put(STABLE, st);
        r = check();
        eq("6 beta 404: offer kept", true, r.kept);
        eq("6 beta 404 keeps the state", UpdateState.AVAILABLE, unit.state);
        // both files gone for a moment: nothing published, the offer and its state stay
        srv.live.remove(STABLE);
        eq("6 404: nothing new", CheckOutcome.UP_TO_DATE, check().outcome);
        eq("6 404 keeps the offer", UpdateState.AVAILABLE, unit.state);
        eq("6 404 offer", 1010008, unit.offered());
        srv.live.put(BETA, f);
        srv.live.put(STABLE, st);
        eq("6 F downloaded", true, download(p -> { }));

        // 7. G is published right when Install is pressed on the downloaded F: G is downloaded and installed
        srv.publish(BETA, build("public-beta", "1.1.0-beta9", 1010009));
        install();
        eq("7 installed the newest", 1010009, installed);
        eq("7 installed = newest published", newestPublished(), installed);
        eq("7 running", 1010009, running);

        // 8. after the install: the same build is never offered again, an old copy neither
        eq("8 same build: up to date", CheckOutcome.UP_TO_DATE, check().outcome);
        eq("8 no offer", -1, unit.offered());
        srv.staleCopy(BETA, BUILDS.get(1010008), 1);
        eq("8 older copy: up to date", CheckOutcome.UP_TO_DATE, check().outcome);
        eq("8 older copy: no offer", -1, unit.offered());
        eq("8 state", UpdateState.IDLE, unit.state);

        // 9. the channel pair: a stable build above the beta one is offered; a lower manifest never is
        srv.publish(STABLE, build("public-nogms", "1.1.0", 1010099));
        eq("9 stable above beta", CheckOutcome.OFFER, check().outcome);
        newest("9");
        eq("9 offer", 1010099, unit.offered());
        srv.publish(STABLE, BUILDS.get(1001099));   // the stable channel goes back (stale mirror): the offer stays
        r = check();
        eq("9 lower stable: kept", true, r.kept);
        eq("9 lower stable: offer", 1010099, unit.offered());
        // beta off (the user's choice): the beta channel's offer goes, the lower stable one is no downgrade
        srv.publish(BETA, build("public-beta", "1.1.0-beta10", 1010010));
        check();
        eq("9 beta 10 offered", 1010099, unit.offered());   // 1.1.0 final is above beta10
        unit.setOffer(new String(BUILDS.get(1010010), StandardCharsets.UTF_8),
                new UpdateManifest(new String(BUILDS.get(1010010), StandardCharsets.UTF_8)).packages.get(0).json, false, "");
        unit.state = UpdateState.AVAILABLE;
        Ota.setBeta(false);
        String[] both = urls;
        urls = new String[] {both[0]};
        eq("9 beta off: up to date", CheckOutcome.UP_TO_DATE, check().outcome);
        eq("9 beta off: beta offer dropped", -1, unit.offered());
        Ota.setBeta(null);
        urls = both;

        // 10. beta15 downloaded on beta14, then 1.1.0-beta1 put on another way (USB, computer, adb): dropped at boot
        run(1010001, "1.1.0-beta1");
        byte[] b15 = build("public-beta", "1.0.11-beta15", 1001115);
        String p15 = new UpdateManifest(new String(b15, StandardCharsets.UTF_8)).packages.get(0).json;
        unit.setOffer(new String(b15, StandardCharsets.UTF_8), p15, false, "");
        unit.files.add(file(1001115));
        unit.state = UpdateState.DOWNLOADED;
        eq("10 boot drops the older download", true, Newest.dropStale(unit, running, "boot"));
        eq("10 its files", "[]", unit.files);
        eq("10 state", UpdateState.IDLE, unit.state);
        unit.setOffer(new String(b15, StandardCharsets.UTF_8), p15, false, "");
        unit.state = UpdateState.DOWNLOADED;
        install();
        eq("10 Install never installs it", 1010009, installed);
        eq("10 Install dropped it", -1, unit.offered());
        unit.setOffer(new String(b15, StandardCharsets.UTF_8), p15, false, "");
        unit.state = UpdateState.DOWNLOADED;
        eq("10 a check moves to the newest", CheckOutcome.OFFER, check().outcome);
        newest("10");
        // never while an install runs or waits for the restart
        unit.state = UpdateState.READY;
        eq("10 busy while ready", CheckOutcome.BUSY, check().outcome);

        // 11. the rechecks of a download (monotonic clock): at its start, after a network gap, every 20 minutes
        Newest.Recheck t = new Newest.Recheck(0);
        eq("11 not while offline", false, t.due(0, false));
        eq("11 at the start", true, t.due(1, true));
        eq("11 not right after", false, t.due(2, true));
        t.offline();
        eq("11 not during the gap", false, t.due(3, false));
        eq("11 after the gap", true, t.due(4, true));
        eq("11 not again", false, t.due(5, true));
        eq("11 not before 20 minutes", false, t.due(4 + Newest.Recheck.EVERY_MS - 1, true));
        eq("11 after 20 minutes", true, t.due(4 + Newest.Recheck.EVERY_MS, true));
        eq("11 not twice", false, t.due(5 + Newest.Recheck.EVERY_MS, true));

        // 12. every manifest read went past every cache
        eq("12 no cache was asked", "[]", srv.bad);
        eq("12 every query new", srv.nonces.size(), new HashSet<>(srv.nonces).size());
        eq("12 reads seen", true, srv.requests > 40);
        eq("12 redirects followed", true, srv.assetHits > 40);
        srv.s.stop(0);
    }
}
