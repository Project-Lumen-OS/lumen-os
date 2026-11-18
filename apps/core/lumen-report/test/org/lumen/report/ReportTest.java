// SPDX-License-Identifier: Apache-2.0
package org.lumen.report;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Host checks of the plain-Java part of problem reports (no JUnit, no Android): sh apps/core/lumen-report/test/run.sh
 * Scrubber (personal data removal), LogcatFilter (which log lines may go in, manual and automatic), PropFilter (the
 * getprop subset), OwnApps, IncidentScan (which crashes are the image's), AutoPolicy (consent and limits of automatic
 * reports), ReportQueue (caps, retry waits, expiry), ReportUrl, ReportZip (size limit, newest lines kept), the
 * answer parsing of ReportUploader, and the unexpected stop (Alive, AliveState, AliveFile: every normal way out of a
 * start against a freeze and a pulled plug).
 */
public final class ReportTest {
    private static int failed, passed;
    /** Built at run time: the publish check takes any literal address for personal data. */
    private static final String AT = "@";

    public static void main(String[] a) throws Exception {
        scrubber();
        literals();
        speed();
        logcatFilter();
        props();
        ownApps();
        incidents();
        policy();
        alive();
        aliveStarts();
        queue();
        url();
        failover();
        zip();
        uploader();
        parts();
        System.out.println("report host tests passed=" + passed + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ PartUpload
    /** A local part server as tools/stats/server/lumen_reports.py answers: parts in any order (the byte ranges it has),
     *  202 with the complete start, 201 with the code at the end and again for a later part, the state query (404 for
     *  an upload it does not have). choke: a part over 20000 bytes gets no answer, as a Russian network does with a
     *  Cloudflare address; fail: the calls answered 503 once or dropped without an answer. */
    private static final class PartServer {
        final java.util.Map<String, byte[]> data = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.Map<String, java.util.TreeMap<Long, Long>> ranges = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.List<String> seen = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final java.util.Set<Integer> fail503 = new java.util.HashSet<>(), drop = new java.util.HashSet<>();
        final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        volatile boolean choke;
        final com.sun.net.httpserver.HttpServer srv;

        PartServer() throws IOException {
            srv = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            srv.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
            srv.createContext("/lumen/v1/report/part", ex -> {
                int n = calls.incrementAndGet();
                com.sun.net.httpserver.Headers h = ex.getRequestHeaders();
                String up = h.getFirst("X-Lumen-Upload");
                long off = Long.parseLong(h.getFirst("X-Lumen-Offset")), total = Long.parseLong(h.getFirst("X-Lumen-Total"));
                byte[] body = ex.getRequestBody().readAllBytes();
                boolean query = "1".equals(h.getFirst("X-Lumen-Query"));
                seen.add((query ? "q" : off + ":" + body.length) + ":" + h.getFirst("X-Lumen-Kind") + ":" + h.getFirst("X-Lumen-Serial")
                        + ":" + h.getFirst("Connection") + ":" + h.getFirst("X-Lumen-Model"));
                int code;
                String ans;
                synchronized (this) {
                    if ((choke && body.length > 20000) || drop.remove(n)) { ex.close(); return; }
                    java.util.TreeMap<Long, Long> r = ranges.get(up);
                    if (fail503.remove(n)) { code = 503; ans = "{}"; }
                    else if (r != null && r.isEmpty()) { code = 200; ans = "{\"code\":\"LR-PART01\"}"; }
                    else if (query && r == null) { code = 404; ans = "{\"error\":\"no upload\"}"; }
                    else {
                        if (r == null) { r = new java.util.TreeMap<>(); ranges.put(up, r); data.put(up, new byte[(int) total]); }
                        if (!query) {
                            System.arraycopy(body, 0, data.get(up), (int) off, body.length);
                            long a = off, b = off + body.length;
                            java.util.Map.Entry<Long, Long> lo = r.floorEntry(a);
                            if (lo != null && lo.getValue() >= a) { a = lo.getKey(); b = Math.max(b, lo.getValue()); r.remove(lo.getKey()); }
                            for (java.util.Map.Entry<Long, Long> e; (e = r.ceilingEntry(a)) != null && e.getKey() <= b; ) { b = Math.max(b, e.getValue()); r.remove(e.getKey()); }
                            r.put(a, b);
                        }
                        long have = r.containsKey(0L) ? r.get(0L) : 0;
                        if (have == total) { r.clear(); code = 201; ans = "{\"code\":\"LR-PART01\"}"; }
                        else { code = 202; ans = "{\"have\":" + have + "}"; }
                    }
                }
                byte[] b = ans.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ex.sendResponseHeaders(code, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            srv.start();
        }

        String url() { return "http://127.0.0.1:" + srv.getAddress().getPort() + "/lumen/v1/report"; }
    }

    private static void parts() throws Exception {
        java.io.File zip = java.io.File.createTempFile("lumen_parts", ".zip");
        byte[] data = new byte[PartUpload.PART_BIG * 2 + PartUpload.PART * 5 + 777];
        new java.util.Random(5).nextBytes(data);
        java.nio.file.Files.write(zip.toPath(), data);
        long oldDeadline = PartUpload.partDeadlineMs;
        PartUpload.partDeadlineMs = 2000;
        PartServer ps = new PartServer();
        try {
            // a choked network: the first large part gets no answer, the rest goes in small parts, several at once
            ps.choke = true;
            ps.fail503.add(3);
            ps.drop.add(6);
            PartUpload u = new PartUpload("0123456789abcdef01234567", "6.17.12 (beta)", "g0111", "dump", "CZW1234567MK", true);
            u.retryDelayMs = 1;
            long[] last = {0};
            String up = PartUpload.newUploadId();
            String code = u.send(java.util.Arrays.asList(ps.url()), zip, up, false, (s, t) -> { synchronized (last) { last[0] = Math.max(last[0], s); } return true; });
            check("LR-PART01".equals(code), "parts: the code of the last answer: " + code);
            check(java.util.Arrays.equals(ps.data.get(up), data), "parts: the server got the whole zip");
            check(u.choked(), "parts: the choke was found and remembered");
            boolean small = true, heads = true;
            int big = 0;
            for (String s : new java.util.ArrayList<>(ps.seen)) {
                String[] f = s.split(":");
                if (!f[0].equals("q")) {
                    int n = Integer.parseInt(f[1]);
                    if (n > PartUpload.PART) big++;
                    heads &= f[2].equals("dump") && f[3].equals("CZW1234567MK") && f[4].equals("close") && f[5].equals("g0111");
                }
            }
            check(big >= 1 && big <= PartUpload.LANES && heads, "parts: at most one large try per lane, then small parts; kind, serial, model and Connection: close on every part: " + big);
            check(last[0] >= data.length - PartUpload.PART * (PartUpload.LANES + 1), "parts: progress near the end: " + last[0]);
            // the next run of a choked upload starts small, and nothing large is tried again
            ps.seen.clear();
            String up2 = PartUpload.newUploadId();
            PartUpload u2 = new PartUpload("0123456789abcdef01234567", "1", "g0111", "dump", "", true);
            check("LR-PART01".equals(u2.send(java.util.Arrays.asList(ps.url()), zip, up2, true, (s, t) -> true)), "parts: started small");
            for (String s : new java.util.ArrayList<>(ps.seen)) small &= s.startsWith("q") || Integer.parseInt(s.split(":")[1]) <= PartUpload.PART;
            check(small, "parts: a choked upload never tries a large part again");
            // no choke: large parts, few calls
            ps.choke = false;
            ps.seen.clear();
            String up3 = PartUpload.newUploadId();
            PartUpload u3 = new PartUpload("0123456789abcdef01234567", "1", "g0111", "dump", "", true);
            check("LR-PART01".equals(u3.send(java.util.Arrays.asList(ps.url()), zip, up3, (s, t) -> true)) && !u3.choked()
                    && ps.seen.size() <= 1 + 3 + PartUpload.LANES, "parts: large parts where nothing is choked: " + ps.seen.size());
            // resume: the server already has the first part of an upload, the next run sends only the rest
            String up4 = PartUpload.newUploadId();
            PartUpload first = new PartUpload("0123456789abcdef01234567", "1", "g0111", "dump", "", true);
            check(first.post(ps.url() + "/part", java.util.Arrays.copyOf(data, PartUpload.PART_BIG), 0, data.length,
                    PartUpload.sha256(zip), up4).have == PartUpload.PART_BIG, "parts: one part stored");
            ps.seen.clear();
            check("LR-PART01".equals(new PartUpload("0123456789abcdef01234567", "1", "g0111", "dump", "", true)
                    .send(java.util.Arrays.asList(ps.url()), zip, up4, (s, t) -> true)), "parts: resumed");
            boolean rest = ps.seen.get(0).startsWith("q");
            for (String s : ps.seen.subList(1, ps.seen.size())) rest &= Long.parseLong(s.split(":")[0]) >= PartUpload.PART_BIG;
            check(rest && java.util.Arrays.equals(ps.data.get(up4), data), "parts: the query first, then only what the server lacks: " + ps.seen);
            // a finished upload: the query answers with its code, nothing is sent again
            ps.seen.clear();
            check("LR-PART01".equals(new PartUpload("0123456789abcdef01234567", "1", "g0111", "dump", "", true)
                    .send(java.util.Arrays.asList(ps.url()), zip, up4, (s, t) -> true)) && ps.seen.size() == 1, "parts: finished already: " + ps.seen);
            // canceled from the progress: stops, an IOException
            try {
                new PartUpload("0123456789abcdef01234567", "1", "g0111", "dump", "", true)
                        .send(java.util.Arrays.asList(ps.url()), zip, PartUpload.newUploadId(), (s, t) -> false);
                check(false, "parts: cancel stops");
            } catch (CanceledException e) {
                check(true, "parts: cancel stops");
            }
            PartUpload bad = new PartUpload("0123456789abcdef01234567", "1", "G0111!", "dump", "bad serial!", false);
            try {
                bad.send(java.util.Arrays.asList(ps.url()), zip, PartUpload.newUploadId(), (s, t) -> true);
                check(false, "parts: plain http refused without localHttp");
            } catch (PartUpload.Failed e) {
                check(!e.retry, "parts: plain http refused without localHttp, not retried");
            }
            check(PartUpload.newUploadId().matches("[0-9a-f]{32}"), "parts: upload id format");
            check(PropFilter.denied("ro.serialno") && PropFilter.denied("ro.boot.wifimac") && PropFilter.denied("android_id")
                    && !PropFilter.denied("ro.build.fingerprint") && !PropFilter.denied("ro.vendor.xgimi.product.name"),
                    "PropFilter.denied: serial, address, ids out, build keys in");
            check(!ReportKind.DUMP.automatic() && ReportKind.of("dump") == ReportKind.DUMP, "kind dump: not automatic");
            check(ReportUrl.choose("", "https://direct.example:8446/lumen/v1/report https://prompt.az/lumen/v1/report")
                    .equals(java.util.Arrays.asList("https://direct.example:8446/lumen/v1/report", "https://prompt.az/lumen/v1/report")),
                    "addresses: a direct one first, Cloudflare after it");
        } finally {
            PartUpload.partDeadlineMs = oldDeadline;
            ps.srv.stop(0);
            zip.delete();
        }
    }

    // ------------------------------------------------------------------ Scrubber
    private static void scrubber() {
        Scrubber s = new Scrubber();
        // e-mail and HIDL names
        same(s, "I AccountManager: added foo.bar" + AT + "example.com to sync", "I AccountManager: added <email> to sync");
        same(s, "mail user" + AT + "example.org.", "mail <email>.");
        same(s, "dlopen android.hardware.audio@7.1-impl.so ok", null);
        same(s, "FileNotFoundException: com.android.adservices@350090000.decompressed.apex-16--1134902950", null);
        same(s, "getService vendor.xgimi.hardware.gmpf@1.0::IGmpf/default", null);
        same(s, "BinderProxy@4f3a2c1 died", null);
        // MAC
        same(s, "wlan0: CTRL-EVENT-CONNECTED - Connection to aa:bb:cc:dd:ee:0f completed",
                "wlan0: CTRL-EVENT-CONNECTED - Connection to <mac> completed");
        same(s, "bt addr XX:XX:XX:XX:12:34 bonded", "bt addr <mac> bonded");
        same(s, "eth AA-BB-CC-DD-EE-FF up", "eth <mac> up");
        same(s, "10-08 21:17:28.123  1000:  1234  1250 I ActivityManager: Start proc 4321", null);
        // IPv4
        same(s, "dhcp ip=198.51.100.23/24 gw 198.51.100.1", "dhcp ip=<ip>/24 gw <ip>");
        same(s, "listen 127.0.0.1:8090 and 0.0.0.0 mask 255.255.255.0", null);
        same(s, "UA Mozilla/5.0 Chrome/120.0.6099.230 Safari", null);
        same(s, "version 1.2.3.4.5", null);
        same(s, "connected to 203.0.113.5.", "connected to <ip>.");
        same(s, "url http://192.0.2.10:8090/stream", "url http://<ip>:8090/stream");
        // IPv6
        same(s, "addr fe80::1c2b:3cff:fe4d:5e6f%wlan0 scope link", "addr <ip6> scope link");
        same(s, "dns 2001:db8:85a3:0:0:8a2e:370:7334 ok", "dns <ip6> ok");
        same(s, "bind ::1 and :: fine", null);
        same(s, "android::base::Foo at 12:00:00", null);
        // Wi-Fi names
        same(s, "WifiInfo SSID: \"Alex Home 5G\", BSSID: <mac>", "WifiInfo SSID: \"<ssid>\", BSSID: <mac>");
        same(s, "select network ssid=MyNet id=3", "select network ssid=<ssid> id=3");
        same(s, "wlan0: Trying to associate with SSID 'Home Net'", "wlan0: Trying to associate with SSID \"<ssid>\"");
        same(s, "configKey=\"Cafe Free\"WPA_PSK", "configKey=\"<ssid>\"WPA_PSK");
        same(s, "SSID: <unknown ssid>", null);
        same(s, "mWifiSsid=\"Dacha\" rssi=-60", "mWifiSsid=\"<ssid>\" rssi=-60");
        // wpa_supplicant's id_str carries the config key URL-encoded (seen on a Z9X: Cyrillic SSID as %D0..)
        same(s, "CTRL-EVENT-CONNECTED - Connection to <mac> completed [id=0 id_str=%7B%22configKey%22%3A%22%5C%22"
                + "%D0%94%D0%BE%D0%BC_5G%5C%22WPA_PSK%22%2C%22creatorUid%22%3A%221000%22%7D]",
                "CTRL-EVENT-CONNECTED - Connection to <mac> completed [id=0 id_str=<redacted>]");
        // Android 14 WifiInfo network key, lowercase security type right after the quoted SSID
        same(s, "Requesting package name: <none>\"Dacha 5G\"wpa2-pskMLO Information: ",
                "Requesting package name: <none>\"<ssid>\"wpa2-pskMLO Information: ");
        same(s, "key \"Cafe\"wpa3-sae and \"Open Cafe\"open", "key \"<ssid>\"wpa3-sae and \"<ssid>\"open");
        same(s, "Log.d(\"tag\"); \"value\"opened", null);
        // secrets and URLs
        same(s, "wpa psk=hunter2 key_mgmt=WPA-PSK", "wpa psk=<redacted> key_mgmt=WPA-PSK");
        same(s, "mPassword: \"abc def\" next", "mPassword: <redacted> next");
        same(s, "Authorization: Bearer abc.def.ghi", "Authorization: <redacted>");
        same(s, "GET https://user:pw" + AT + "host.example/x?token=abc123&x=1",
                "GET https://<redacted>" + AT + "host.example/x?token=<redacted>&x=1");
        // accounts and users
        same(s, "Account {name=alex, type=com.google}", "Account {name=<account>, type=com.google}");
        same(s, "UserInfo{0:Alex:c13} started", "UserInfo{0:<user>:c13} started");
        same(s, "login userName=alex ok", "login userName=<account> ok");
        // phone numbers
        same(s, "call +7 (912) 345-67-89 ok", "call <phone> ok");
        same(s, "Displayed org.foo/.Main: +1s234ms", null);
        same(s, "phone_number=89123456789 x", "phone_number=<phone> x");
        same(s, "uri tel:+79123456789", "uri tel:<phone>");
        // serials and device ids, also the Google services ids
        same(s, "[ro.serialno]: [ABC123XYZ]", "[ro.serialno]: [<serial>]");
        same(s, "cmdline androidboot.serialno=XYZ987 console=ttyS0", "cmdline androidboot.serialno=<serial> console=ttyS0");
        same(s, "usb 1-1: SerialNumber: 0123ABCD", "usb 1-1: SerialNumber: <serial>");
        same(s, "[ro.boot.sn]: [Q1234]", "[ro.boot.sn]: [<serial>]");
        same(s, "InputReader: deviceId=5 name=remote", null);
        same(s, "ssaid android_id=1234abcd5678ef90", "ssaid android_id=<id>");
        same(s, "Checkin: gsf_id=3a7b1c9d2e4f5a6b saved", "Checkin: gsf_id=<id> saved");
        same(s, "GmsCore gsfId: 4123456789012345678 ok", "GmsCore gsfId: <id> ok");
        same(s, "auth gaia_id=118234567890123456789 done", "auth gaia_id=<id> done");
        same(s, "androidboot.wifimac=001122AABBCC rest", "androidboot.wifimac=<mac> rest");
        // mDNS
        same(s, "resolved Alexs-iPhone.local. at <ip>", "resolved <host>.local at <ip>");
        check(s.replacedCount() > 30, "replacement counter counts");
    }

    private static void literals() {
        Scrubber s = new Scrubber()
                .addLiteral("\"Alex Home 5G\"", "wifi")
                .addLiteral("Alex Home", "wifi")
                .addLiteral("Alex’s AirPods", "bt-device")
                .addLiteral("ab", "wifi")                 // too short: ignored
                .addLiteral("SN0123456789", "serial");
        same(s, "network Alex Home 5G lost, Alex Home found", "network <wifi-1> lost, <wifi-2> found");
        same(s, "A2DP connected Alex’s AirPods", "A2DP connected <bt-device-1>");
        same(s, "tab ab cd", null);
        same(s, "sn SN0123456789 seen", "sn <serial> seen");
        check(s.scrubText("a\nAlex Home 5G\nb").equals("a\n<wifi-1>\nb"), "scrubText keeps lines");
        // URL-encoded forms of a known name get the same token
        Scrubber u = new Scrubber().addLiteral("\u0414\u043e\u043c 5G", "wifi").addLiteral("Alex's Phone", "bt-device");
        same(u, "ssid %D0%94%D0%BE%D0%BC+5G and %D0%94%D0%BE%D0%BC%205G", "ssid <wifi-1> and <wifi-1>");
        same(u, "dev Alex%27s+Phone / Alex's Phone", "dev <bt-device-1> / <bt-device-1>");
    }

    /** Pathological lines must not make a regex backtrack for seconds on the projector's slow CPU. */
    private static void speed() {
        StringBuilder dots = new StringBuilder(), unders = new StringBuilder(), colons = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            dots.append("ab.");
            unders.append("ab_");
            colons.append("ab:");
        }
        String[] lines = {
            dots + "x", unders + "sn", colons + "x", "user" + unders, "+1" + "2".repeat(3000),
            "a@" + dots + "x", "ssid" + dots, "serial" + unders + "=x", "gsf_id" + unders,
        };
        Scrubber s = new Scrubber();
        long t0 = System.nanoTime();
        for (int k = 0; k < 20; k++) for (String l : lines) s.scrub(l);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        check(ms < 2000, "pathological lines scrub fast (" + ms + " ms for 180 lines)");
        String line = "10-08 21:17:28.123  1000:  1234  1250 I ActivityManager: Start proc 4321:org.lumen.home/u0a52 for activity";
        t0 = System.nanoTime();
        for (int k = 0; k < 50_000; k++) s.scrub(line);
        ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("  50k typical lines: " + ms + " ms on this host");
        check(ms < 5000, "typical lines scrub fast");
    }

    // ------------------------------------------------------------------ LogcatFilter
    private static void logcatFilter() {
        Set<Integer> allowed = new HashSet<>();
        allowed.add(10052);                                   // e.g. org.lumen.home
        LogcatFilter f = new LogcatFilter(allowed, 10070);    // this app = 10070
        check(f.keep("--------- beginning of main"), "buffer marker kept");
        check(!f.sawOtherProcesses(), "no other process yet");
        check(f.keep("10-08 21:17:28.123  1000:  1234  1250 I ActivityManager: x"), "system uid kept");
        check(f.sawOtherProcesses(), "system uid counts as another process");
        check(f.keep("10-08 21:17:28.123  root:   123   123 I init: x"), "root kept");
        check(f.keep("10-08 21:17:28.123 media:   555   560 W MediaCodec: x"), "short user name kept");
        check(f.keep("10-08 21:17:28.123 10052:  2222  2230 I LumenHome: x"), "allowed image app kept");
        check(!f.keep("10-08 21:17:28.123 10123:  3333  3333 I Lampa: secret title"), "user app dropped");
        check(!f.keep("10-08 21:17:28.123 u0_a7:  2222  2222 D x: y"), "short app name dropped");
        check(f.keep("10-08 21:17:28.123 10070:  4444  4444 I LumenReport: x"), "own app kept");
        check(!f.keep("10-08 21:17:28.123 99012:  5555  5555 I chromium: x"), "isolated process dropped");
        check(!f.keep("10-08 21:17:28.123 1110123:  5555  5555 I x: y"), "second user app dropped");
        check(f.keep("10-08 21:17:28.123 1101000: 6666  6666 I x: y"), "system uid of user 11 kept");
        check(!f.keep("logcat: Unknown -v format"), "unparsable line dropped");
        check(!f.keep("10-08 21:17:28.123  3333  3333 I Lampa: no uid column"), "line without uid column dropped");
        check(!f.keep("10-08 21:17:28.123 12345 12350 I Lampa: no uid column"), "5-digit pid without uid dropped");
        check(f.keep("--------- switch to crash"), "crash marker kept");
        check(f.keep("10-08 21:17:28.123 10123:  3333  3333 E AndroidRuntime: FATAL EXCEPTION: main"),
                "manual: crash buffer line of a user app kept");
        check(f.keep("--------- switch to main"), "main marker kept");
        check(!f.keep("10-08 21:17:28.123 10123:  3333  3333 I Lampa: x"), "user app dropped again after crash");
        check(f.keep("10-08 21:17:28.123  1000:12345 12350 I ActivityManager: 5-digit pid"), "5-digit pid parses");
        check(LogcatFilter.uidOf("u0_a7") == 10007 && LogcatFilter.uidOf("u10_a3") == 1010003
                && LogcatFilter.uidOf("u0_i12") == 99012 && LogcatFilter.uidOf("shell") == 1000
                && LogcatFilter.uidOf("root") == 0 && LogcatFilter.uidOf("1041") == 1041, "uid names");
        LogcatFilter own = new LogcatFilter(Collections.emptySet(), 10070);
        own.keep("10-08 21:17:28.123 10070:  4444  4444 I LumenReport: x");
        check(!own.sawOtherProcesses(), "only own lines = limited log access");
        // automatic reports: the crash buffer is limited to the system and the image's apps too
        LogcatFilter auto = new LogcatFilter(allowed, 10070, false);
        auto.keep("--------- beginning of crash");
        check(!auto.keep("10-08 21:17:28.123 10123:  3333  3333 E AndroidRuntime: FATAL EXCEPTION: main"),
                "automatic: crash of a user app dropped");
        check(auto.keep("10-08 21:17:28.123 10052:  2222  2222 E AndroidRuntime: FATAL EXCEPTION: main"),
                "automatic: crash of an image app kept");
        check(auto.keep("10-08 21:17:28.123  1000:  1234  1250 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS"),
                "automatic: system_server crash kept");
    }

    // ------------------------------------------------------------------ PropFilter, OwnApps
    private static void props() {
        for (String k : new String[] {"ro.build.fingerprint", "ro.build.display.id", "ro.boot.xgimi.modelname",
                "ro.lumen.version", "ro.lumen.version_code", "persist.lumen.lang", "sys.boot.reason",
                "init.svc.gmpf_main", "ro.gsid.image_running", "ro.product.model", "persist.sys.locale"}) {
            check(PropFilter.allowed(k), "prop allowed: " + k);
        }
        for (String k : new String[] {"ro.serialno", "ro.boot.serialno", "ro.boot.wifimac", "ro.boot.btmacaddr",
                "persist.lumen.account_name", "ro.lumen.gsf_id", "ro.boot.xgimi.sn", "net.hostname", "dhcp.wlan0.ipaddress",
                "persist.sys.wifi.ssid", "ro.lumen.owner", "persist.lumen.user_name", "ro.lumen.token", "ro.lumen.dns1",
                "ro.lumen.email", "ro.vendor.xgimi.product.name", "gsm.sim.operator", "ro.boot.xgimi.serial",
                "persist.vendor.display.uuid", "sys.lumen.device_name"}) {
            check(!PropFilter.allowed(k), "prop denied: " + k);
        }
        check(PropFilter.keep("[ro.build.id]: [UQ1A.240205.004]"), "getprop line kept");
        check(!PropFilter.keep("[ro.serialno]: [ABC]"), "getprop serial line dropped");
        check(!PropFilter.keep("ro.build.id=UQ1A"), "odd line dropped");
        check(!PropFilter.allowed(null), "null key");
    }

    private static void ownApps() {
        check(OwnApps.own("org.lumen.home"), "lumen app own");
        check(OwnApps.own("com.android.systemui:screenshot"), "process suffix stripped");
        check(OwnApps.own("android") && OwnApps.own("system_server") && OwnApps.own("android.process.media"), "system");
        check(!OwnApps.own("com.android.vending"), "Play Store is not the image's");
        check(!OwnApps.own("com.android.chrome"), "Chrome is not the image's");
        check(!OwnApps.own("androidx.example.app"), "androidx prefix is not android.");
        check(!OwnApps.own("ru.kinopoisk.tv") && !OwnApps.own("com.lampa"), "user apps");
        check(!OwnApps.own(null), "null");
    }

    // ------------------------------------------------------------------ IncidentScan
    private static void incidents() {
        long now = 10L * 24 * 3600 * 1000, since = now - 3600_000L, t = now - 60_000L;
        Incident c = IncidentScan.of("system_app_crash", t, "Process: org.lumen.home\nFlags: 0x1\n\njava.lang.NPE", since, now);
        check(c != null && c.kind == ReportKind.CRASH && "org.lumen.home".equals(c.process), "image app crash");
        check(c != null && "crash:org.lumen.home".equals(c.key()), "incident key");
        Incident sub = IncidentScan.of("system_app_anr", t, "Process: com.android.systemui:screenshot\nPID: 4", since, now);
        check(sub != null && sub.kind == ReportKind.ANR && "com.android.systemui".equals(sub.process), "image app ANR");
        check(IncidentScan.of("system_app_crash", t, "Process: com.android.vending\n", since, now) == null, "Play Store crash");
        check(IncidentScan.of("system_app_crash", t, "no header", since, now) == null, "crash without process");
        check(IncidentScan.of("data_app_crash", t, "Process: org.lumen.home\n", since, now) == null, "data_app never");
        check(IncidentScan.of("data_app_anr", t, "Process: ru.kinopoisk.tv\n", since, now) == null, "user app ANR never");
        Incident ss = IncidentScan.of("system_server_anr", t, "Subject: Input dispatching timed out", since, now);
        check(ss != null && ss.kind == ReportKind.ANR && "system_server".equals(ss.process), "system_server ANR");
        Incident wd = IncidentScan.of("system_server_watchdog", t, "Subject: Blocked in handler", since, now);
        check(wd != null && wd.kind == ReportKind.BOOT, "watchdog = boot failure");
        Incident rs = IncidentScan.of("SYSTEM_RESTART", t, "", since, now);
        check(rs != null && rs.kind == ReportKind.BOOT && "system_server".equals(rs.process), "framework restart");
        check(IncidentScan.of("SYSTEM_LAST_KMSG", t, "[    0.000] Booting Linux\n[ 3.1] reboot: Restarting", since, now) == null,
                "last kmsg of a normal restart");
        Incident kp = IncidentScan.of("SYSTEM_LAST_KMSG", t, "...\n[ 99.1] Kernel panic - not syncing: Fatal exception\n", since, now);
        check(kp != null && kp.kind == ReportKind.BOOT && "kernel".equals(kp.process), "kernel panic");
        check(IncidentScan.of("SYSTEM_LAST_KMSG", t, "Last boot reason: kernel_panic\n", since, now) != null, "panic boot reason header");
        check(IncidentScan.of("SYSTEM_LAST_KMSG", t, null, since, now) == null, "kmsg without text");
        check(IncidentScan.of("system_server_crash", since, "x", since, now) == null, "not newer than the last scan");
        check(IncidentScan.of("system_server_crash", now - IncidentScan.MAX_AGE_MS - 1, "x", 0, now) == null, "too old");
        check(IncidentScan.of("SYSTEM_BOOT", t, "x", since, now) == null, "a normal boot is no incident");
        check(IncidentScan.of("SYSTEM_TOMBSTONE", t, "Cmdline: com.lampa", since, now) == null, "tombstones are not watched");
        check(IncidentScan.kindOf("system_app_native_crash") == ReportKind.CRASH, "native crash tag");
        check(IncidentScan.tags().length == 9 && Arrays.asList(IncidentScan.tags()).contains("SYSTEM_RESTART"), "tag list");
        check(IncidentScan.bootReason("kernel_panic", t) != null && IncidentScan.bootReason("watchdog", t) != null
                && IncidentScan.bootReason("hw_wdt_reset", t) != null, "bad boot reasons");
        check(IncidentScan.bootReason("reboot", t) == null && IncidentScan.bootReason("cold,powerkey", t) == null
                && IncidentScan.bootReason("", t) == null && IncidentScan.bootReason(null, t) == null, "normal boot reasons");
        check("org.lumen.x".equals(IncidentScan.process("Build: x\nProcess: org.lumen.x:remote\n")), "process line anywhere");
    }

    // ------------------------------------------------------------------ AutoPolicy
    private static void policy() {
        check(AutoPolicy.DEFAULT_ON, "automatic reports are on by default (like the statistics)");
        check(AutoPolicy.allowed(true, true, true), "allowed");
        check(!AutoPolicy.allowed(false, true, true), "image without a server: never");
        check(!AutoPolicy.allowed(true, false, true), "switch off: never");
        check(!AutoPolicy.allowed(true, true, false), "before the first-run setup ended: never");
        long now = 100L * 24 * 3600 * 1000;
        List<Incident> found = new ArrayList<>();
        found.add(new Incident("system_app_crash", now - 5000, ReportKind.CRASH, "org.lumen.home"));
        found.add(new Incident("system_app_crash", now - 1000, ReportKind.CRASH, "org.lumen.home"));
        found.add(new Incident("system_server_anr", now - 3000, ReportKind.ANR, "system_server"));
        List<Incident> p = AutoPolicy.pick(found, "", now);
        check(p.size() == 2 && p.get(0).time == now - 1000 && p.get(1).kind == ReportKind.ANR, "one per key, newest first");
        String log = AutoPolicy.record("", p, now);
        check(AutoPolicy.pick(found, log, now + 60_000).isEmpty(), "the same keys are not reported again the same day");
        check(AutoPolicy.pick(found, log, now + AutoPolicy.DAY_MS).size() == 2, "again after a day");
        List<Incident> other = Collections.singletonList(new Incident("system_app_crash", now, ReportKind.CRASH, "org.lumen.store"));
        check(AutoPolicy.pick(other, log, now + 60_000).size() == 1, "another key the same day");
        StringBuilder full = new StringBuilder();
        for (int i = 0; i < AutoPolicy.MAX_PER_DAY; i++) full.append(now - 1000 * i).append(" crash:k").append(i).append('\n');
        check(AutoPolicy.pick(other, full.toString(), now).isEmpty(), "no more than MAX_PER_DAY a day");
        check(AutoPolicy.MAX_PER_DAY == 4, "MAX_PER_DAY is 4");
        check(AutoPolicy.pick(other, "garbage\n12x key\n\n" + (now - 1) + " \n", now).size() == 1, "damaged log lines skipped");
        String old = (now - AutoPolicy.DAY_MS - 1) + " crash:org.lumen.store\n";
        check(AutoPolicy.pick(other, old, now).size() == 1, "entries older than a day do not count");
        String rec = AutoPolicy.record(old + (now - 10) + " anr:system_server\n", other, now);
        check(!rec.contains(String.valueOf(now - AutoPolicy.DAY_MS - 1)) && rec.contains("anr:system_server")
                && rec.endsWith(now + " crash:org.lumen.store\n"), "record prunes and appends");
        List<Incident> many = new ArrayList<>();
        for (int i = 0; i < 9; i++) many.add(new Incident("system_app_crash", now - i, ReportKind.CRASH, "org.lumen.a" + i));
        check(AutoPolicy.pick(many, "", now).size() == AutoPolicy.MAX_PER_REPORT, "at most MAX_PER_REPORT incidents in one report");
        check(AutoPolicy.pick(found, (now + 999_999) + " crash:org.lumen.home\n", now).size() == 2, "a future log line is ignored");
    }

    // ------------------------------------------------------------------ Alive: the record and the rules
    private static final String HOME = "org.lumen.home/org.lumen.home.HomeActivity";
    private static final TimeZone MSK = TimeZone.getTimeZone("GMT+03:00");

    private static void alive() throws Exception {
        String boot1 = "8c1e4f8a-0001", boot2 = "8c1e4f8a-0002";
        long t0 = 1_763_100_000_000L;
        Alive a = new Alive(Alive.UP, "beat", t0, 3_600_000L, boot1, Alive.HDMI_VIEWER, true);
        Alive b = Alive.parse(a.line());
        check(b != null && Alive.UP.equals(b.state) && "beat".equals(b.why) && b.time == t0 && b.up == 3_600_000L
                && boot1.equals(b.boot) && Alive.HDMI_VIEWER.equals(b.app) && b.hdmi && b.screensaver, "alive record round trip");
        check(a.line().startsWith("v=1 state=up why=beat ") && a.line().contains(" input=hdmi screensaver=1"), "alive line format");
        Alive e = Alive.parse(new Alive(Alive.CLEAN, "start", t0, 5, "", "", false).line() + "\nmore");
        check(e != null && e.boot.isEmpty() && e.app.isEmpty() && !e.hdmi && !e.screensaver, "empty fields survive, first line only");
        for (String bad : new String[] {null, "", "garbage", "v=2 state=up why=x time=1 up=1", "v=1 state=odd why=x time=1 up=1",
                "v=1 state=up time=1 up=1", "v=1 state=up why=x time=z up=1", "v=1 state=up why=x time=1",
                "v=1 state=up why=x time=1 up=-5", "v=1 state=up why= time=1 up=1"}) {
            check(Alive.parse(bad) == null, "damaged record: " + bad);
        }
        // boot reasons (bootstat): orderly = init carried out a request
        check(Alive.orderly("reboot,userrequested", "", "") && Alive.orderly("shutdown,userrequested", "", "")
                && Alive.orderly("reboot,z9x-ota", "", "") && Alive.orderly("reboot,adb", "", "reboot")
                && Alive.orderly("reboot,", "", "") && Alive.orderly("shutdown", "", "") && Alive.orderly("recovery", "", "")
                && Alive.orderly("bootloader", "", "") && Alive.orderly("fastboot", "", ""), "orderly init reasons");
        check(!Alive.orderly("reboot", "reboot", "reboot") && !Alive.orderly("kernel_panic", "", "")
                && !Alive.orderly("watchdog", "", "") && !Alive.orderly("cold,powerkey", "", "") && !Alive.orderly("", "", "")
                && !Alive.orderly(null, null, null) && !Alive.orderly("rebooting", "", ""), "power cut and kernel reasons");
        check(!Alive.orderly("reboot,powerloss", "", "smpl") && !Alive.orderly("reboot,longkey", "", "")
                && !Alive.orderly("reboot,undervoltage", "", "") && !Alive.orderly("shutdown,thermal", "", ""), "hardware sub reasons");
        check(!Alive.orderly("reboot,0xf1", "reboot,0xf1", "0xF1") && Alive.orderly("reboot,0xf1", "", ""),
                "an unknown bootloader reason that bootstat echoed");
        check(Alive.orderly("reboot,adb", "reboot,adb", "reboot,adb"), "a bootloader that passes the command on");
        check(Alive.orderly("", "reboot,userrequested", "") && !Alive.orderly("", "reboot", ""), "sys.boot.reason when .last is empty");
        check(!Alive.orderly("reboot", "reboot,userrequested", ""), "sys.boot.reason.last wins");
        check(Alive.orderly(" Reboot,UserRequested ", "", ""), "reasons are normalized like bootstat");
        // one start or another
        Alive up1 = new Alive(Alive.UP, "beat", t0, 7_200_000L, boot1, HOME, false);
        check(Alive.sameBoot(up1, boot1, 1) && !Alive.sameBoot(up1, boot2, 9_000_000L), "the boot id decides");
        Alive noId = new Alive(Alive.UP, "beat", t0, 100, "", "", false);
        check(Alive.sameBoot(noId, boot2, 200) && !Alive.sameBoot(noId, boot2, 50) && !Alive.sameBoot(up1, "", 60_000L),
                "without a boot id the time since the start decides");
        check(Alive.stopped(up1, boot2, 30_000, "reboot", "reboot", "reboot"), "UP, then a power cut = stop");
        check(!Alive.stopped(up1, boot1, 9_000_000L, "reboot", "", ""), "the same start = no stop");
        check(!Alive.stopped(up1, boot2, 30_000, "reboot,adb", "", ""), "an init reboot = no stop");
        check(!Alive.stopped(new Alive(Alive.CLEAN, "screen_off", t0, 1, boot1, "", false), boot2, 30_000, "reboot", "", ""),
                "CLEAN = no stop");
        check(!Alive.stopped(null, boot2, 30_000, "reboot", "", ""), "no record = no stop");
        check(AliveState.beats(true) && !AliveState.beats(false), "beats only while the screen is on");
        // the incident and its text
        String pending = a.line() + "\nfound=" + (t0 + 600_000) + "\nsys.boot.reason.last=reboot\nsys.boot.reason=reboot"
                + "\nro.boot.bootreason=reboot\npersist.sys.boot.reason=-\n";
        Incident i = Alive.incident(pending, MSK);
        check(i != null && Alive.STOP_TAG.equals(i.tag) && i.kind == ReportKind.BOOT && i.time == t0
                && "boot:unexpected_stop".equals(i.key()), "stop incident: kind boot, time of the last sign of life");
        String txt = i == null ? "" : i.detail;
        check(txt.contains("stopped without shutting down") && txt.contains("Last sign of life: 2025-11-14 06:00:00 UTC (09:00:00 local time, UTC+03:00), 1 h 0 min after that start")
                && txt.contains("Input: HDMI") && txt.contains("App in front: " + Alive.HDMI_VIEWER) && txt.contains("Screensaver: on")
                && txt.contains("Noticed at the next start: 2025-11-14 06:10:00 UTC")
                && txt.contains("sys.boot.reason.last=reboot sys.boot.reason=reboot ro.boot.bootreason=reboot persist.sys.boot.reason=none"),
                "stop text: time, app, input, screensaver, boot reasons\n" + txt);
        check(!txt.contains(";") && !txt.contains("\u2014") && !txt.contains("\u2013"), "stop text: plain, no semicolons or dashes");
        check(Alive.incident(null, MSK) == null && Alive.incident("garbage\nfound=1", MSK) == null, "no or damaged pending stop");
        Incident fromApp = Alive.incident(new Alive(Alive.UP, "beat", t0, 60_000, boot1, HOME, false).line() + "\nfound=x\n", MSK);
        check(fromApp != null && fromApp.detail.contains("Input: an app") && fromApp.detail.contains("Screensaver: off")
                && !fromApp.detail.contains("Noticed at"), "stop text of an app, damaged lines skipped");
        // consent and the clock (AutoReports.scan)
        check(AutoPolicy.withStop(true, i) && !AutoPolicy.withStop(false, i) && !AutoPolicy.withStop(true, null),
                "the stop goes in with consent only (switch off drops it)");
        check(!AutoPolicy.dropBoxScan(true, t0 + 1, t0) && !AutoPolicy.dropBoxScan(true, 0, t0)
                && !AutoPolicy.dropBoxScan(false, t0 - 1, t0) && AutoPolicy.dropBoxScan(true, t0 - 1, t0), "DropBox scan rules");
        long reset = 946_684_800_000L;                       // a clock back at 2000 after a power cut
        List<Incident> p = AutoPolicy.pick(Collections.singletonList(i), "", reset);
        check(!AutoPolicy.dropBoxScan(true, t0, reset) && p.size() == 1 && p.get(0) == i, "a reset clock still reports the stop");
        check(AutoPolicy.pick(Collections.singletonList(i), AutoPolicy.record("", p, t0), t0 + 1000).isEmpty(),
                "one stop report per day");
        // DropBox records after a stop
        check(IncidentScan.autoTags(false).length == 9 && IncidentScan.autoTags(true).length == 10
                && Arrays.asList(IncidentScan.autoTags(true)).contains("SYSTEM_BOOT")
                && !Arrays.asList(IncidentScan.autoTags(false)).contains("SYSTEM_BOOT"), "SYSTEM_BOOT only after a stop");
        check(IncidentScan.bootRecord("SYSTEM_BOOT", t0, t0) && IncidentScan.bootRecord("SYSTEM_LAST_KMSG", t0 + 1, t0)
                && !IncidentScan.bootRecord("SYSTEM_BOOT", t0 - 1, t0) && !IncidentScan.bootRecord("system_app_crash", t0, t0),
                "start records of this start only");
        // the files
        File dir = Files.createTempDirectory("alive").toFile();
        try {
            File f = new File(dir, "x");
            check(AliveFile.read(f) == null && AliveFile.take(f) == null, "missing file");
            check(AliveFile.write(f, "one") && AliveFile.write(f, "two") && "two".equals(AliveFile.read(f))
                    && !new File(dir, "x.tmp").exists(), "write replaces, no temp file left");
            check("two".equals(AliveFile.take(f)) && !f.exists() && AliveFile.take(f) == null, "take reads once");
            Files.write(f.toPath(), new byte[70 * 1024]);
            check(AliveFile.read(f) == null, "an oversized file is not read");
        } finally {
            deleteTree(dir);
        }
    }

    /** One projector over several starts: the files of AliveWatch and the state of its process, as AliveWatch runs them. */
    private static final class Unit {
        final File marker, pending;
        String boot = "", front = HOME;
        long up, clock = 1_763_100_000_000L;
        boolean interactive = true;
        int starts;
        AliveState st;

        Unit(File dir) {
            marker = new File(dir, "report_alive");
            pending = new File(dir, "report_stop");
        }

        /** A new start with these boot reasons (REASON_PROPS order); true when it noticed a stop. */
        boolean start(String... reasons) {
            boot = "boot-" + (++starts);
            up = 25_000;
            clock += 60_000;
            interactive = true;
            return process(reasons);
        }

        /** The app process starts (AliveWatch.check, then START). */
        boolean process(String... reasons) {
            Alive prev = Alive.parse(AliveFile.read(marker));
            boolean stop = Alive.bootCheck(marker, pending, boot, up, clock, reasons);
            st = new AliveState(prev, boot, up);
            ev(AliveState.START);
            return stop;
        }

        void ev(String e) {
            Alive a = st.on(e, interactive, front, boot, clock, up);
            if (a != null) AliveFile.write(marker, a.line());
        }

        void beats(int n) {
            for (int k = 0; k < n; k++) {
                up += AliveState.BEAT_MS;
                clock += AliveState.BEAT_MS;
                ev(AliveState.BEAT);
            }
        }

        Alive mark() {
            return Alive.parse(AliveFile.read(marker));
        }

        Incident take() {
            return Alive.incident(AliveFile.take(pending), MSK);
        }
    }

    private static final String[] CUT = {"reboot", "reboot", "reboot", ""};

    private static void aliveStarts() throws Exception {
        File dir = Files.createTempDirectory("alivestarts").toFile();
        try {
            // the freeze: UP with beats, then nothing, then the plug (X50: bare "reboot" everywhere)
            Unit u = new Unit(new File(dir, "freeze"));
            new File(dir, "freeze").mkdirs();
            check(!u.start(CUT) && !u.pending.exists(), "first start ever: nothing to report");
            check(Alive.UP.equals(u.mark().state) && "start".equals(u.mark().why), "UP from the start");
            u.beats(3);
            long lastBeat = u.mark().time;
            check(Alive.UP.equals(u.mark().state) && "beat".equals(u.mark().why) && u.mark().up == 25_000 + 3 * AliveState.BEAT_MS,
                    "beats refresh the record");
            check(u.start(CUT) && u.pending.exists(), "freeze and power pull: noticed at the next start");
            check(!u.process(CUT), "the app process again in the same start: no second stop");
            Incident i = u.take();
            check(i != null && i.time == lastBeat && i.detail.contains("App in front: " + HOME) && i.detail.contains("Input: an app"),
                    "the stop holds the last beat and the app");
            check(!u.process(CUT) && !u.pending.exists(), "max one per start");
            u.ev(AliveState.SCREEN_OFF);
            check(!u.start(CUT) && !u.pending.exists(), "a start after a normal standby: nothing");
            // the record of the look itself: a process that dies right after the check still counts the start as seen
            Unit k = unit(dir, "look");
            k.start(CUT);
            k.beats(1);
            k.boot = "boot-x";
            k.up = 20_000;
            check(Alive.bootCheck(k.marker, k.pending, k.boot, k.up, k.clock, CUT), "check of a new start");
            check(!Alive.bootCheck(k.marker, k.pending, k.boot, k.up, k.clock, CUT), "a second look in that start (process died before START)");
            Unit same = unit(dir, "same");
            same.start(CUT);
            same.beats(1);
            Alive before = same.mark();
            check(!Alive.bootCheck(same.marker, same.pending, same.boot, same.up, same.clock, CUT) && same.mark() != null
                    && same.mark().line().equals(before.line()), "a look in the same start leaves the record alone");
            // standby by screen off (sleep, STR): CLEAN, a power pull in standby is no stop
            Unit s = unit(dir, "standby");
            s.start(CUT);
            s.beats(1);
            s.interactive = false;
            s.ev(AliveState.SCREEN_OFF);
            check(Alive.CLEAN.equals(s.mark().state) && "screen_off".equals(s.mark().why), "screen off: CLEAN");
            check(!s.start(CUT), "standby, then the plug: no stop");
            // lamp-only standby (Z9X, HB, Play 6: Android stays awake, STANDBY_CHANGED true)
            Unit l = unit(dir, "lamp");
            l.start(CUT);
            l.ev(AliveState.STANDBY_ON);
            Alive lamp = l.mark();
            check(Alive.CLEAN.equals(lamp.state) && "standby".equals(lamp.why), "lamp-only standby: CLEAN");
            l.beats(3);
            check(l.mark().line().equals(lamp.line()), "no writes while in lamp-only standby");
            l.ev(AliveState.SCREEN_ON);
            check(Alive.CLEAN.equals(l.mark().state), "screen on during lamp-only standby stays CLEAN");
            check(!l.process(CUT) && Alive.CLEAN.equals(l.mark().state) && "standby".equals(l.mark().why),
                    "the app process again during lamp-only standby: still CLEAN");
            check(!l.start(CUT), "lamp-only standby, then the plug: no stop");
            l.ev(AliveState.STANDBY_ON);
            l.ev(AliveState.STANDBY_OFF);
            check(Alive.UP.equals(l.mark().state) && "standby_off".equals(l.mark().why), "standby ends: UP");
            l.ev(AliveState.STANDBY_ON);
            l.interactive = false;
            l.ev(AliveState.SCREEN_OFF);
            l.interactive = true;
            l.ev(AliveState.SCREEN_ON);
            check(Alive.UP.equals(l.mark().state), "a real screen off ends a lamp-only standby");
            // boot dark (Z9X and HB after an update restart): the standby screen is in front before this process listens
            Unit d = unit(dir, "dark");
            d.front = Alive.STANDBY_ACTIVITY;
            d.start(CUT);
            check(Alive.CLEAN.equals(d.mark().state) && AliveState.STANDBY_SCREEN.equals(d.mark().why), "standby screen in front: CLEAN");
            d.beats(2);
            check(Alive.CLEAN.equals(d.mark().state), "still CLEAN while it stays");
            check(!d.start(CUT), "standby screen, then the plug: no stop");
            d.front = HOME;
            d.beats(1);
            check(Alive.UP.equals(d.mark().state), "the standby screen went away: the next beat says UP");
            // update reboot (lumen-updater: PowerManager.reboot, ACTION_SHUTDOWN), then Play 6 ends its standby quietly
            Unit o = unit(dir, "ota");
            o.start(CUT);
            o.beats(2);
            o.ev(AliveState.SHUTDOWN);
            o.ev(AliveState.STANDBY_OFF);
            o.ev(AliveState.SCREEN_ON);
            check(Alive.CLEAN.equals(o.mark().state) && "shutdown".equals(o.mark().why), "update reboot: CLEAN, and it stays CLEAN");
            check(!o.start(CUT), "update reboot: no stop even with a bare reboot reason");
            // user restart and menu power off (both ShutdownThread), and their init reasons
            Unit r = unit(dir, "restart");
            r.start(CUT);
            r.ev(AliveState.SHUTDOWN);
            check(!r.start("reboot,userrequested", "reboot,userrequested", "reboot", ""), "user restart: no stop");
            r.ev(AliveState.REBOOT);
            check(Alive.CLEAN.equals(r.mark().state) && !r.start(CUT), "ACTION_REBOOT: CLEAN");
            r.ev(AliveState.SHUTDOWN);
            check(!r.start("shutdown,userrequested", "shutdown,userrequested", "", ""), "menu power off: no stop");
            // an init reboot without ShutdownThread (adb reboot, the boot gates): the record says UP, the reason saves it
            Unit n = unit(dir, "init");
            n.start(CUT);
            n.beats(1);
            check(!n.start("reboot,adb", "reboot,adb", "reboot", "") && !n.pending.exists(), "init reboot: no stop");
            n.beats(1);
            check(!n.start("reboot,z9x-ota-rollback", "", "", ""), "boot gate rollback: no stop");
            n.beats(1);
            check(n.start("kernel_panic", "kernel_panic", "kernel_panic", ""), "kernel panic while UP: stop");
            // screensaver and HDMI
            Unit h = unit(dir, "hdmi");
            h.front = Alive.HDMI_VIEWER;
            h.start(CUT);
            h.ev(AliveState.DREAM_ON);
            check(Alive.UP.equals(h.mark().state) && h.mark().screensaver && h.mark().hdmi, "screensaver: UP, flag on");
            check(h.start(CUT), "freeze in the screensaver: stop");
            Incident hi = h.take();
            check(hi != null && hi.detail.contains("Input: HDMI") && hi.detail.contains("Screensaver: on"), "HDMI and screensaver in the text");
            h.ev(AliveState.DREAM_ON);
            h.ev(AliveState.DREAM_OFF);
            check(!h.mark().screensaver, "screensaver off again");
            // damaged or missing record: nothing
            Unit g = unit(dir, "garbled");
            Files.write(g.marker.toPath(), "v=1 state=up why=beat time=x".getBytes(StandardCharsets.UTF_8));
            check(!g.start(CUT) && !g.pending.exists(), "damaged record: no stop");
            g.marker.delete();
            check(!g.start(CUT) && Alive.UP.equals(g.mark().state), "missing record: no stop, a new one");
            // a clock reset by the power cut: the stop is still noticed, its time is the last beat
            Unit c = unit(dir, "clock");
            c.start(CUT);
            c.beats(1);
            long beat = c.mark().time;
            c.clock = 946_684_800_000L;
            check(c.start(CUT), "a reset clock at the next start: stop");
            Incident ci = c.take();
            check(ci != null && ci.time == beat && ci.time > c.clock, "the stop keeps the time of the last beat");
        } finally {
            deleteTree(dir);
        }
    }

    private static Unit unit(File dir, String name) {
        File d = new File(dir, name);
        d.mkdirs();
        return new Unit(d);
    }

    // ------------------------------------------------------------------ ReportQueue
    private static void queue() throws Exception {
        File dir = Files.createTempDirectory("reportqueue").toFile();
        try {
            ReportQueue q = new ReportQueue(new File(dir, "q"));
            long now = 1_000_000_000L;
            check(q.nextDueIn(now) == -1 && q.due(now).isEmpty(), "empty queue");
            ReportQueue.Entry m = q.add(file(dir, "m.zip", 1000), ReportKind.MANUAL, now);
            check(!new File(dir, "m.zip").exists() && m.zip.isFile(), "the zip moves into the queue");
            check(q.due(now).size() == 1 && q.nextDueIn(now) == 0, "a new report is due at once");
            q.failed(q.due(now).get(0), now);
            check(q.due(now + 59_999).isEmpty() && q.due(now + 60_000).size() == 1, "first retry after 1 minute");
            check(q.nextDueIn(now) == 60_000, "next due in 1 minute");
            check(ReportQueue.waitMs(1) == 60_000 && ReportQueue.waitMs(2) == 300_000 && ReportQueue.waitMs(4) == 3_600_000
                    && ReportQueue.waitMs(7) == 12 * 3_600_000L && ReportQueue.waitMs(30) == 12 * 3_600_000L, "growing waits");
            ReportQueue.Entry e = q.due(now + 60_000).get(0);
            check(e.attempts == 1 && e.kind == ReportKind.MANUAL && e.created == now, "meta kept over a retry");
            // caps: the oldest automatic report goes first, the manual one stays
            for (int i = 1; i <= ReportQueue.MAX_ENTRIES; i++) q.add(file(dir, "a" + i + ".zip", 1000), ReportKind.CRASH, now + i);
            List<ReportQueue.Entry> all = q.entries();
            check(all.size() == ReportQueue.MAX_ENTRIES, "at most MAX_ENTRIES (" + all.size() + ")");
            check(all.get(0).kind == ReportKind.MANUAL && all.get(1).created == now + 2, "oldest automatic dropped, manual kept");
            ReportQueue.Entry big = q.add(file(dir, "big.zip", (int) ReportQueue.MAX_BYTES - 3000), ReportKind.BOOT, now + 100);
            long bytes = 0;
            for (ReportQueue.Entry x : q.entries()) bytes += x.zip.length();
            check(bytes <= ReportQueue.MAX_BYTES && big.zip.isFile(), "byte cap keeps the new report (" + bytes + ")");
            for (ReportQueue.Entry x : q.entries()) q.remove(x.id);
            check(q.size() == 0, "removed");
            // expiry by age and by attempts
            q.add(file(dir, "old.zip", 10), ReportKind.ANR, now);
            check(q.due(now + ReportQueue.MAX_AGE_MS + 1).isEmpty() && q.size() == 0, "older than 7 days dropped");
            ReportQueue.Entry t = q.add(file(dir, "t.zip", 10), ReportKind.ANR, now);
            for (int i = 0; i < ReportQueue.MAX_ATTEMPTS; i++) q.failed(q.entries().get(0), now);
            check(q.due(now + ReportQueue.MAX_AGE_MS).isEmpty() && q.size() == 0, "tried MAX_ATTEMPTS times dropped");
            // a damaged meta file is removed, not fatal
            q.add(file(dir, "d.zip", 10), ReportKind.CRASH, now);
            File meta = new File(new File(dir, "q"), q.entries().get(0).id + ".meta");
            Files.write(meta.toPath(), "broken".getBytes(StandardCharsets.UTF_8));
            check(q.entries().isEmpty() && new File(dir, "q").list().length == 0, "damaged entry removed");
            check(t.id.endsWith("-anr"), "id names the kind");
        } finally {
            deleteTree(dir);
        }
    }

    // ------------------------------------------------------------------ ReportUrl, ReportFailure
    private static void url() {
        check(ReportUrl.CLOUDFLARE.equals("https://prompt.az/lumen/v1/report"), "default address = our server");
        check(ReportUrl.DEFAULTS.equals(Arrays.asList(ReportUrl.CLOUDFLARE)), "no direct address in the code");
        check(ReportUrl.DEFAULTS.equals(ReportUrl.choose("", "")) && ReportUrl.DEFAULTS.equals(ReportUrl.choose(null, null)),
                "no property = our server");
        check(ReportUrl.choose("", "off").isEmpty(), "image switch off");
        check(Arrays.asList("https://x.example/r").equals(ReportUrl.choose("https://x.example/r", "off")),
                "debug override wins");
        check(ReportUrl.choose("", "http://x.example/r").isEmpty(), "plain http = off");
        check(ReportUrl.choose("", "https://").isEmpty(), "empty https = off");
        check(Arrays.asList("https://a.example/b").equals(ReportUrl.choose(" ", " https://a.example/b ")), "trimmed");
        check(Arrays.asList("https://a.example/b", "https://c.example/d")
                .equals(ReportUrl.choose("", "https://a.example/b, https://c.example/d https://a.example/b")),
                "several addresses in order, once each");
        check(ReportUrl.choose("", "https://a.example/b http://c.example/d").isEmpty(), "one plain http address = off");
        check(ReportFailure.NETWORK.retry() && ReportFailure.NO_NETWORK.retry() && ReportFailure.SERVER.retry()
                && ReportFailure.RATE_LIMITED.retry(), "failures that pass are retried");
        check(!ReportFailure.REJECTED.retry() && !ReportFailure.COLLECT.retry(), "refusals are not retried");
        check(ReportKind.of("crash") == ReportKind.CRASH && ReportKind.of("x") == null && !ReportKind.MANUAL.automatic()
                && ReportKind.BOOT.automatic(), "kinds");
    }

    // ------------------------------------------------------------------ ReportUploader: addresses in turn, deadline
    private static ReportUploader.UploadException http(int status) {
        return new ReportUploader.UploadException(ReportUploader.failureFor(status), status, "HTTP " + status);
    }

    private static final ReportUploader.UploadException FROZEN =
            new ReportUploader.UploadException(ReportFailure.NETWORK, "no answer within 30000 ms");

    /** uploadAny over fake addresses a, b, c: {@code answers} = what each one does (null = gives code LR-OK0000). */
    private static String any(List<String> tried, IOException... answers) {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < answers.length; i++) urls.add("https://" + (char) ('a' + i) + ".example/r");
        try {
            return ReportUploader.uploadAny(urls, u -> {
                tried.add(u);
                IOException e = answers[urls.indexOf(u)];
                if (e != null) throw e;
                return "LR-OK0000";
            });
        } catch (ReportUploader.UploadException e) {
            return e.failure + " " + e.status;
        } catch (IOException e) {
            return e.getClass().getSimpleName();
        }
    }

    private static void failover() throws Exception {
        List<String> t = new ArrayList<>();
        check("LR-OK0000".equals(any(t, FROZEN, null)) && t.size() == 2, "frozen address: the next one sends it");
        t.clear();
        check("LR-OK0000".equals(any(t, null, FROZEN)) && t.size() == 1, "first address sends: no second try");
        t.clear();
        check("NETWORK 0".equals(any(t, FROZEN, FROZEN)) && t.size() == 2, "all frozen = network (stays queued)");
        for (int s : new int[] {404, 405, 301, 503}) {
            t.clear();
            check("LR-OK0000".equals(any(t, http(s), null)) && t.size() == 2, s + " on one address: the next one");
        }
        t.clear();
        check("SERVER 200".equals(any(t, new ReportUploader.UploadException(ReportFailure.SERVER, 200, "no code"), FROZEN))
                && t.size() == 2, "200 without a code (not our server): the next one, then the first try-again wins");
        for (int s : new int[] {400, 411, 413, 415}) {
            t.clear();
            check(("REJECTED " + s).equals(any(t, http(s), null)) && t.size() == 1, s + " judges the report: no next");
        }
        t.clear();
        check("RATE_LIMITED 429".equals(any(t, http(429), null)) && t.size() == 1, "429: same limits everywhere");
        t.clear();
        check("NETWORK 0".equals(any(t, http(404), FROZEN)), "404 then frozen: kept for a later try, never dropped");
        t.clear();
        check("NETWORK 0".equals(any(t, FROZEN, http(404))) && t.size() == 2, "frozen then 404: the frozen one wins");
        t.clear();
        check("REJECTED 404".equals(any(t, http(404), http(404))), "404 everywhere: refused");
        t.clear();
        check("CanceledException".equals(any(t, new CanceledException(), null)) && t.size() == 1, "cancel stops at once");
        check("REJECTED 0".equals(any(t)), "no address");
        check(ReportUploader.deadlineMs(0) == 30_000L && ReportUploader.deadlineMs(1_000_000) == 92_500L,
                "deadline: 30 s + 16 KB a second");
        check(ReportUploader.tryNext(FROZEN) && !ReportUploader.tryNext(http(413)), "tryNext");

        // a real connection that never answers (a frozen network): cut at the deadline, not after minutes
        File dir = Files.createTempDirectory("reportup").toFile();
        try (ServerSocket ss = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            List<Socket> held = Collections.synchronizedList(new ArrayList<>());
            Thread acc = new Thread(() -> {
                try {
                    while (true) held.add(ss.accept());          // accepts, never reads or answers
                } catch (IOException ignored) {
                }
            });
            acc.setDaemon(true);
            acc.start();
            File z = file(dir, "r.zip", 200_000);
            long t0 = System.nanoTime();
            String got;
            try {
                got = new ReportUploader().upload("https://127.0.0.1:" + ss.getLocalPort() + "/lumen/v1/report", z,
                        "0123456789abcdef01234567", "1.0", "g0111", ReportKind.CRASH, (s, n) -> true, 1500);
            } catch (ReportUploader.UploadException e) {
                got = e.failure + ": " + e.getMessage();
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            check(got.startsWith("NETWORK: no answer within 1500 ms") && ms >= 1400 && ms < 10_000,
                    "frozen connection cut at the deadline (" + ms + " ms: " + got + ")");
            for (Socket s : new ArrayList<>(held)) s.close();
        } finally {
            deleteTree(dir);
        }
        frozenWrite();
    }

    /**
     * The frozen network as it really happens: TLS is up, then the body stops moving half way (the server never
     * reads). The JDK's SSL close waits for the stuck writer, so only the hard socket cut ends it. Key store: run.sh.
     */
    private static void frozenWrite() throws Exception {
        String ks = System.getProperty("lumen.report.tls");
        check(ks != null && new File(ks).isFile(), "test TLS key store from run.sh");
        if (ks == null) return;
        char[] pw = "lumen-test".toCharArray();
        KeyStore k = KeyStore.getInstance("PKCS12");
        try (InputStream in = new java.io.FileInputStream(ks)) {
            k.load(in, pw);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(k, pw);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(k);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        File dir = Files.createTempDirectory("reportfrozen").toFile();
        SSLSocketFactory old = HttpsURLConnection.getDefaultSSLSocketFactory();
        try (SSLServerSocket ss = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket()) {
            ss.setReceiveBufferSize(4096);
            ss.bind(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
            List<Socket> held = Collections.synchronizedList(new ArrayList<>());
            Thread acc = new Thread(() -> {
                try {
                    SSLSocket s = (SSLSocket) ss.accept();
                    held.add(s);
                    s.startHandshake();                          // TLS up, then the body is never read
                } catch (IOException ignored) {
                }
            });
            acc.setDaemon(true);
            acc.start();
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
            File z = file(dir, "big.zip", 8 << 20);
            long[] sent = {0};
            String[] got = {"still running"};
            Thread up = new Thread(() -> {
                try {
                    got[0] = new ReportUploader().upload("https://localhost:" + ss.getLocalPort() + "/lumen/v1/report",
                            z, "0123456789abcdef01234567", "1.0", "g0111", ReportKind.CRASH, (s, n) -> {
                                sent[0] = s;
                                return true;
                            }, 1500);
                } catch (ReportUploader.UploadException e) {
                    got[0] = e.failure + ": " + e.getMessage();
                } catch (IOException e) {
                    got[0] = e.toString();
                }
            });
            up.setDaemon(true);
            long t0 = System.nanoTime();
            up.start();
            up.join(20_000);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            check(!up.isAlive() && got[0].startsWith("NETWORK: no answer within 1500 ms") && sent[0] > 0
                    && sent[0] < (8 << 20) && ms < 10_000,
                    "body frozen half way: cut at the deadline (" + ms + " ms, " + sent[0] + " bytes: " + got[0] + ")");
            for (Socket s : new ArrayList<>(held)) s.close();
        } finally {
            HttpsURLConnection.setDefaultSSLSocketFactory(old);
            deleteTree(dir);
        }
    }

    // ------------------------------------------------------------------ ReportZip
    private static void zip() throws Exception {
        File dir = Files.createTempDirectory("reportzip").toFile();
        try {
            File log = new File(dir, "log.txt");
            try (FileOutputStream o = new FileOutputStream(log)) {
                for (int i = 0; i < 5000; i++) o.write(("line " + i + " some log text here\n").getBytes(StandardCharsets.UTF_8));
            }
            List<ReportZip.Part> parts = new ArrayList<>();
            parts.add(ReportZip.Part.text("summary.txt", "hello"));
            parts.add(ReportZip.Part.file("logcat.txt", log, 10_000));
            parts.add(ReportZip.Part.file("missing.txt", new File(dir, "nope"), 10_000));
            File out = new File(dir, "r.zip");
            int shrink = ReportZip.writeWithin(out, parts, 1_000_000);
            check(shrink == 1, "fits at shrink 1");
            try (ZipFile z = new ZipFile(out)) {
                check(z.getEntry("missing.txt") == null, "missing file part skipped");
                check(read(z, z.getEntry("summary.txt")).equals("hello"), "text part intact");
                String l = read(z, z.getEntry("logcat.txt"));
                check(l.startsWith("[... ") && l.endsWith("line 4999 some log text here\n"), "log keeps the newest lines");
                check(l.length() <= 10_000 + 100, "log part capped (" + l.length() + ")");
                String second = l.split("\n")[1];
                check(second.startsWith("line ") && second.endsWith("here"), "cut at a line start");
            }
            File rnd = new File(dir, "rnd.txt");
            java.util.Random r = new java.util.Random(1);
            try (FileOutputStream o = new FileOutputStream(rnd)) {
                byte[] b = new byte[200_000];
                r.nextBytes(b);
                o.write(b);
            }
            List<ReportZip.Part> big = new ArrayList<>();
            big.add(ReportZip.Part.file("rnd.txt", rnd, 200_000));
            int sh = ReportZip.writeWithin(out, big, 60_000);
            check(sh >= 4 && out.length() <= 60_000, "shrinks until the zip fits (shrink " + sh + ", " + out.length() + " bytes)");
            check(ReportZip.writeWithin(out, big, 1_000) == -1, "reports -1 when nothing fits");
        } finally {
            deleteTree(dir);
        }
    }

    // ------------------------------------------------------------------ ReportUploader
    private static void uploader() {
        check("LR-7K2Q".equals(ReportUploader.parseCode("{\"code\":\"LR-7K2Q\",\"expires\":\"2026-11-07T00:00:00Z\"}")), "code parsed");
        check("LR-7K2Q9M".equals(ReportUploader.parseCode("{ \"code\" : \"LR-7K2Q9M\" }")), "6-char code parsed");
        check(ReportUploader.parseCode("{\"error\":\"rate_limited\"}") == null, "no code in an error");
        check(ReportUploader.parseCode("{\"code\":\"<script>\"}") == null, "odd code refused");
        check(ReportUploader.parseCode(null) == null, "null body");
        check(ReportUploader.failureFor(429) == ReportFailure.RATE_LIMITED, "429 = rate limited");
        check(ReportUploader.failureFor(503) == ReportFailure.SERVER, "503 = server");
        check(ReportUploader.failureFor(413) == ReportFailure.REJECTED, "413 = rejected");
        check("1.0.1".equals(ReportUploader.headerVersion("1.0.1")), "version kept");
        check("unknown".equals(ReportUploader.headerVersion(" ")), "empty version");
        check("1.0.1_beta_".equals(ReportUploader.headerVersion("1.0.1 beta)")), "version sanitized");
        check(ReportUploader.headerVersion("x".repeat(40)).length() == 32, "version capped at 32");
        check("g0111".equals(ReportUploader.headerModel("g0111")), "model kept");
        check("unknown".equals(ReportUploader.headerModel("G0111")) && "unknown".equals(ReportUploader.headerModel(""))
                && "unknown".equals(ReportUploader.headerModel("a;b")) && "unknown".equals(ReportUploader.headerModel(null)),
                "odd model = unknown");
    }

    // ------------------------------------------------------------------ helpers
    private static File file(File dir, String name, int bytes) throws Exception {
        File f = new File(dir, name);
        Files.write(f.toPath(), new byte[bytes]);
        return f;
    }

    private static void deleteTree(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) deleteTree(x);
        f.delete();
    }

    private static String read(ZipFile z, ZipEntry e) throws Exception {
        try (InputStream in = z.getInputStream(e)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** expected null = the line must come back unchanged. */
    private static void same(Scrubber s, String in, String expected) {
        String got = s.scrub(in);
        String want = expected == null ? in : expected;
        if (!want.equals(got)) {
            failed++;
            System.out.println("FAIL scrub: " + in + "\n     want: " + want + "\n      got: " + got);
        } else {
            passed++;
        }
    }

    private static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }
}
