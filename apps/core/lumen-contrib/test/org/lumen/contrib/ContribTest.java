// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.contrib;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Host tests of the contribution client: the signed body byte for byte, the signature, every answer the base can
 *  give and every failure turning into "no card". With -Dlumen.contrib.vector=FILE it also writes one signed body
 *  (body, key, signature) for a check by the server side. */
public final class ContribTest {
    static int run, failed;

    static void check(boolean ok, String what) {
        run++;
        if (!ok) {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    static void throwsIae(Runnable r, String what) {
        try {
            r.run();
            check(false, what + " (no exception)");
        } catch (IllegalArgumentException e) {
            check(true, what);
        }
    }

    static final class JdkSigner implements Signer {
        final KeyPair kp;

        JdkSigner() throws GeneralSecurityException {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            kp = g.generateKeyPair();
        }

        public byte[] publicKey() {
            return kp.getPublic().getEncoded();
        }

        public byte[] sign(byte[] body) throws GeneralSecurityException {
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(kp.getPrivate());
            s.update(body);
            return s.sign();
        }
    }

    static final class FakeNet implements Transport {
        final List<String> urls = new ArrayList<>();
        Map<String, String> headers;
        byte[] body;
        Reply reply = new Reply(200, "{}");
        IOException fail;

        public Reply get(String url) throws IOException {
            urls.add(url);
            if (fail != null) throw fail;
            return reply;
        }

        public Reply post(String url, byte[] b, Map<String, String> h) throws IOException {
            urls.add(url);
            body = b;
            headers = h;
            if (fail != null) throw fail;
            return reply;
        }
    }

    public static void main(String[] a) throws Exception {
        body();
        json();
        pairAndCards();
        send();
        mine();
        shown();
        oneCode();
        transport();
        System.out.println("ContribTest: " + (run - failed) + " of " + run + " passed");
        if (failed > 0) System.exit(1);
    }

    static void body() {
        String sh = ContribBody.serialHash(" SN1234567890X ");
        check(sh.equals("320a843368f0d04e4589ee9157e6661b0508381b7558e6d7a9dc2414fe2504ca"), "serial hash vector");
        throwsIae(() -> ContribBody.serialHash(" "), "empty serial");
        throwsIae(() -> ContribBody.serialHash(null), "null serial");
        byte[] b = ContribBody.build(Kind.REPORT, "G0082", "V6.15.30", sh, "LR-AB12CD", 1800000000L, "0123456789abcdef");
        String want = "LUMEN-CONTRIB-V1\nkind=report\nmodel=G0082\nfw=V6.15.30\nsh=" + sh
                + "\nref=LR-AB12CD\nt=1800000000\nn=0123456789abcdef\n";
        check(new String(b, StandardCharsets.US_ASCII).equals(want), "body bytes");
        check(new String(ContribBody.build(Kind.INSTALL, "H0063", "1", sh, "", 0, "a".repeat(32)), StandardCharsets.US_ASCII)
                .contains("kind=install\nmodel=H0063\nfw=1\n"), "install body, empty ref, 32 hex nonce, time 0");
        check(Kind.TRIAL.wire.equals("trial"), "trial wire name");
        String[][] bad = {{"g0082", "V1", "", "0123456789abcdef"}, {"G00821", "V1", "", "0123456789abcdef"},
                {"G0082", "", "", "0123456789abcdef"}, {"G0082", "x".repeat(33), "", "0123456789abcdef"},
                {"G0082", "V 1", "", "0123456789abcdef"}, {"G0082", "V1", "r".repeat(41), "0123456789abcdef"},
                {"G0082", "V1", "a b", "0123456789abcdef"}, {"G0082", "V1", "", "0123456789abcde"},
                {"G0082", "V1", "", "a".repeat(33)}, {"G0082", "V1", "", "0123456789ABCDEF"},
                {null, "V1", "", "0123456789abcdef"}, {"G0082", null, "", "0123456789abcdef"}, {"G0082", "V1", null, "0123456789abcdef"}};
        for (String[] x : bad) throwsIae(() -> ContribBody.build(Kind.REPORT, x[0], x[1], sh, x[2], 1, x[3]), "refused " + Arrays.toString(x));
        throwsIae(() -> ContribBody.build(Kind.REPORT, "G0082", "V1", sh.toUpperCase(), "", 1, "0123456789abcdef"), "upper hash");
        throwsIae(() -> ContribBody.build(Kind.REPORT, "G0082", "V1", sh.substring(1), "", 1, "0123456789abcdef"), "short hash");
        throwsIae(() -> ContribBody.build(Kind.REPORT, "G0082", "V1", null, "", 1, "0123456789abcdef"), "null hash");
        throwsIae(() -> ContribBody.build(Kind.REPORT, "G0082", "V1", sh, "", -1, "0123456789abcdef"), "negative time");
        check(Contrib.nonce().matches("[0-9a-f]{16}") && !Contrib.nonce().equals(Contrib.nonce()), "nonce");
        check(Hex.of(new byte[] {0, 15, 16, (byte) 255}).equals("000f10ff"), "hex");
    }

    static void json() {
        Map<String, Object> m = FlatJson.parse(" {\"a\" : \"x\\\"y\\\\z\\/\\n\\t\\u0416\", \"n\": -12, \"l\": [\"p\", \"q\"], \"e\": []} ");
        check(m.get("a").equals("x\"y\\z/\n\tЖ") && m.get("n").equals(-12L) && m.get("l").equals(Arrays.asList("p", "q"))
                && m.get("e").equals(new ArrayList<>()), "flat json values");
        check(FlatJson.parse("{}").isEmpty(), "empty object");
        for (String bad : new String[] {"", "[]", "{\"a\":1} x", "{\"a\":{}}", "{\"a\":true}", "{\"a\":-}", "{\"a\":1;}",
                "{\"a\":[1]}", "{\"a\":[\"x\";]}", "{\"a\":\"\\q\"}", "{\"a\":\"open", "{a:1}", "{\"a\" 1}", "{\"a\":1"}) {
            throwsIae(() -> FlatJson.parse(bad), "json refused: " + bad);
        }
        throwsIae(() -> FlatJson.parse(null), "json null");
    }

    static void pairAndCards() throws Exception {
        FakeNet net = new FakeNet();
        Contrib c = new Contrib("https://base.test", net, new JdkSigner());
        net.reply = new Reply(200, "{\"k\":0,\"o\":[\"report\",\"trial\",\"install\"]}");
        Offers o = c.pair("G0082", "V6.15.30");
        check(net.urls.get(0).equals("https://base.test/v1/pair?m=G0082&fw=V6.15.30"), "pair url");
        check(!o.known && o.has(Kind.REPORT) && o.has(Kind.TRIAL) && o.has(Kind.INSTALL), "pair offers");
        check(Contrib.card(o, false) == Card.SEND_INFO && Contrib.card(o, true) == Card.NONE, "send info card");
        net.reply = new Reply(200, "{\"k\":1,\"o\":[\"install\"]}");
        Offers k = c.pair("G0082", "V6.15.30");
        check(k.known && !k.has(Kind.REPORT) && Contrib.card(k, false) == Card.NONE, "known firmware: no card");
        int calls = net.urls.size();
        check(c.pair("g0082", "V1") == Offers.NONE && c.pair("G0082", "V 1") == Offers.NONE && c.pair(null, "V1") == Offers.NONE
                && c.pair("G0082", null) == Offers.NONE && net.urls.size() == calls, "bad model or version: no call");
        net.reply = new Reply(503, "{\"e\":\"busy\"}");
        check(c.pair("G0082", "V1") == Offers.NONE, "503: none");
        for (String bad : new String[] {"nonsense", "{\"k\":\"0\",\"o\":[]}", "{\"k\":0}", "{\"o\":[]}"}) {
            net.reply = new Reply(200, bad);
            check(c.pair("G0082", "V1") == Offers.NONE, "bad pair reply: none " + bad);
        }
        net.fail = new IOException("offline");
        check(c.pair("G0082", "V1") == Offers.NONE, "offline: none");
        check(!Offers.NONE.known && Offers.NONE.open.isEmpty(), "NONE offers nothing");
    }

    static void send() throws Exception {
        FakeNet net = new FakeNet();
        JdkSigner s = new JdkSigner();
        Contrib c = new Contrib("https://base.test", net, s);
        net.reply = new Reply(200, "{\"st\":\"claim\",\"code\":\"K7M2QX9P\",\"exp\":1802592000,\"w\":1,\"l\":\"https://t.me/Lumen_OS_Bot?start=f_K7M2QX9P\"}");
        Claim cl = c.send(Kind.REPORT, "G0082", "V6.15.30", "SN1234567890X", "LR-AB12CD", 1800000000L);
        check(net.urls.get(0).equals("https://base.test/v1/earn"), "earn url");
        String text = new String(net.body, StandardCharsets.US_ASCII);
        check(text.startsWith("LUMEN-CONTRIB-V1\nkind=report\nmodel=G0082\nfw=V6.15.30\nsh=320a8433") && text.contains("\nt=1800000000\n")
                && !text.contains("SN1234567890X"), "sent body, no serial");
        check(net.headers.get("Content-Type").equals("text/plain; charset=us-ascii"), "content type");
        byte[] key = Base64.getUrlDecoder().decode(net.headers.get("X-Lumen-Key"));
        byte[] sig = Base64.getUrlDecoder().decode(net.headers.get("X-Lumen-Sig"));
        check(!net.headers.get("X-Lumen-Key").contains("=") && Arrays.equals(key, s.publicKey()), "key header");
        Signature v = Signature.getInstance("SHA256withECDSA");
        v.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(key)));
        v.update(net.body);
        check(v.verify(sig), "signature verifies over the body");
        check("K7M2QX9P".equals(cl.code) && cl.expires == 1802592000L && cl.waiting && cl.link.endsWith("f_K7M2QX9P")
                && cl.why.isEmpty() && cl.shown().equals("K7M2 QX9P"), "claim parsed");
        check(Contrib.card(cl) == Card.CODE, "code card");
        net.reply = new Reply(200, "{\"st\":\"claim\",\"code\":\"B4TR9WJN\",\"exp\":5,\"w\":0,\"l\":\"https://t.me/x\"}");
        Claim now = c.send(Kind.TRIAL, "H0063", "1.0.10", "SN9", "", 1);
        check(!now.waiting && now.code.equals("B4TR9WJN"), "claim without a check");
        net.reply = new Reply(200, "{\"st\":\"none\",\"why\":\"installed_before\"}");
        Claim before = c.send(Kind.INSTALL, "G0082", "1.0.10", "SN1", "", 1);
        check(before.code == null && before.why.equals("installed_before") && Contrib.card(before) == Card.INSTALLED_BEFORE
                && before.shown().isEmpty(), "installed before");
        Claim kept = Claim.kept("K7M2QX9P", 99L, true, "https://t.me/Lumen_OS_Bot?start=f_K7M2QX9P", "", 0);
        check(kept.code.equals("K7M2QX9P") && kept.expires == 99L && kept.waiting && kept.link.endsWith("f_K7M2QX9P")
                && kept.why.isEmpty() && Contrib.card(kept) == Card.CODE, "kept answer shows the code again");
        Claim keptNone = Claim.kept(null, 0L, false, "", "installed_before", 0);
        check(keptNone.code == null && !keptNone.waiting && Contrib.card(keptNone) == Card.INSTALLED_BEFORE, "kept refusal");
        net.reply = new Reply(200, "{\"st\":\"none\",\"why\":\"pair_known\"}");
        check(Contrib.card(c.send(Kind.REPORT, "G0082", "V1", "SN1", "", 1)) == Card.NONE, "pair known: no card");
        net.reply = new Reply(429, "{\"e\":\"rate_limited\"}");
        Claim lim = c.send(Kind.REPORT, "G0082", "V1", "SN1", "", 1);
        check(lim.code == null && lim.why.equals("rate_limited") && Contrib.card(lim) == Card.NONE, "refusal code kept");
        net.reply = new Reply(502, "{}");
        check(c.send(Kind.REPORT, "G0082", "V1", "SN1", "", 1).why.equals("http_502"), "status without a code");
        String[] bad = {"<html>", "{\"st\":\"claim\",\"code\":\"k7m2qx9p\",\"exp\":1,\"w\":0,\"l\":\"https://t.me/x\"}",
                "{\"st\":\"claim\",\"code\":\"K7M2QX9\",\"exp\":1,\"w\":0,\"l\":\"https://t.me/x\"}",
                "{\"st\":\"claim\",\"code\":\"K7M2QX9P\",\"exp\":\"1\",\"w\":0,\"l\":\"https://t.me/x\"}",
                "{\"st\":\"claim\",\"code\":\"K7M2QX9P\",\"exp\":1,\"w\":\"0\",\"l\":\"https://t.me/x\"}",
                "{\"st\":\"claim\",\"code\":\"K7M2QX9P\",\"exp\":1,\"w\":0,\"l\":\"http://evil.test\"}",
                "{\"st\":\"claim\",\"code\":\"K7M2QX9P\",\"exp\":1,\"w\":0}", "{\"st\":\"maybe\",\"why\":\"x\"}", "{\"st\":\"none\"}"};
        for (String b : bad) {
            net.reply = new Reply(200, b);
            Claim x = c.send(Kind.REPORT, "G0082", "V1", "SN1", "", 1);
            check(x == Claim.OFFLINE && Contrib.card(x) == Card.NONE, "strange answer: no card " + b);
        }
        net.fail = new IOException("offline");
        check(c.send(Kind.REPORT, "G0082", "V1", "SN1", "", 1) == Claim.OFFLINE, "offline");
        net.fail = null;
        check(c.send(Kind.REPORT, "bad", "V1", "SN1", "", 1) == Claim.OFFLINE && c.send(Kind.REPORT, "G0082", "V1", "", "", 1) == Claim.OFFLINE,
                "bad input: nothing sent");
        Contrib broken = new Contrib("https://base.test", net, new Signer() {
            public byte[] publicKey() throws GeneralSecurityException { throw new GeneralSecurityException("no keystore"); }
            public byte[] sign(byte[] b) { return new byte[0]; }
        });
        check(broken.send(Kind.REPORT, "G0082", "V1", "SN1", "", 1) == Claim.OFFLINE, "keystore failure: nothing");
        String out = System.getProperty("lumen.contrib.vector");
        if (out != null) {
            net.reply = new Reply(200, "{\"st\":\"none\",\"why\":\"pair_known\"}");
            long t = System.currentTimeMillis() / 1000;
            c.send(Kind.REPORT, "G0082", "V6.15.30", "SN1234567890X", "LR-AB12CD", t);
            Files.write(Paths.get(out), (new String(net.body, StandardCharsets.US_ASCII).replace("\n", "\\n") + "\n"
                    + net.headers.get("X-Lumen-Key") + "\n" + net.headers.get("X-Lumen-Sig") + "\n").getBytes(StandardCharsets.US_ASCII));
        }
    }

    static final String HAVE = "{\"st\":\"have\",\"k\":\"report\",\"code\":\"K7M2QX9P\",\"d\":1800000000,\"t\":0,\"w\":1,"
            + "\"exp\":1802592000,\"l\":\"https://t.me/Lumen_OS_Bot?start=f_K7M2QX9P\",\"n\":2,\"rs\":1}";

    static void mine() throws Exception {
        FakeNet net = new FakeNet();
        JdkSigner s = new JdkSigner();
        Contrib c = new Contrib("https://base.test", net, s);
        net.reply = new Reply(200, HAVE);
        Mine m = c.mine("G0130", "V6.17.12", "SN1234567890X", 1800000000L);
        check(net.urls.get(0).equals("https://base.test/v1/mine"), "mine url");
        String text = new String(net.body, StandardCharsets.US_ASCII);
        check(text.startsWith("LUMEN-CONTRIB-V1\nkind=mine\nmodel=G0130\nfw=V6.17.12\nsh=320a8433") && text.contains("\nref=\nt=1800000000\nn="),
                "mine body: " + text);
        Signature v = Signature.getInstance("SHA256withECDSA");
        v.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getUrlDecoder().decode(net.headers.get("X-Lumen-Key")))));
        v.update(net.body);
        check(v.verify(Base64.getUrlDecoder().decode(net.headers.get("X-Lumen-Sig"))), "mine signature");
        check(m.asked && m.has && m.kind.equals("report") && m.code.equals("K7M2QX9P") && m.earned == 1800000000L && !m.taken
                && m.waiting && m.expires == 1802592000L && m.link.endsWith("f_K7M2QX9P") && m.reportSent, "mine answer");
        net.reply = new Reply(200, "{\"st\":\"have\",\"k\":\"install\",\"code\":\"\",\"d\":5,\"t\":1,\"w\":0,\"exp\":9,\"l\":\"\",\"n\":1,\"rs\":0}");
        Mine t = c.mine("G0130", "1.0.10", "SN1", 1);
        check(t.has && t.taken && t.code.isEmpty() && !t.waiting && t.link.isEmpty() && !t.reportSent && t.earned == 5, "taken, code not kept");
        net.reply = new Reply(200, "{\"st\":\"none\",\"rs\":0}");
        check(c.mine("G0130", "V1", "SN1", 1) == Mine.NONE && Mine.NONE.asked && !Mine.NONE.has, "nothing earned");
        net.reply = new Reply(200, "{\"st\":\"none\",\"rs\":1}");
        Mine sent = c.mine("G0130", "V1", "SN1", 1);
        check(sent.asked && !sent.has && sent.reportSent, "nothing earned, a report sent");
        net.reply = new Reply(503, HAVE);
        check(c.mine("G0130", "V1", "SN1", 1) == Mine.OFFLINE && !Mine.OFFLINE.asked, "503: offline");
        for (String bad : new String[] { "{\"st\":\"have\"}", "{\"st\":\"none\"}", "{\"st\":\"x\",\"rs\":0}", "[]",
                HAVE.replace("\"k\":\"report\"", "\"k\":\"mine\""), HAVE.replace("K7M2QX9P\",\"d\"", "K7M2QX9\",\"d\""),
                HAVE.replace("\"d\":1800000000", "\"d\":\"1\""), HAVE.replace("\"t\":0", "\"t\":\"0\""),
                HAVE.replace("\"w\":1", "\"w\":\"1\""), HAVE.replace("\"exp\":1802592000", "\"exp\":\"1\""),
                HAVE.replace("https://t.me/", "https://x.example/"), HAVE.replace("\"rs\":1", "\"rs\":\"1\""),
                HAVE.replace("\"l\":\"https", "\"l\":1,\"x\":\"https"), HAVE.replace("\"code\":\"K7M2QX9P\"", "\"code\":7") }) {
            net.reply = new Reply(200, bad);
            check(c.mine("G0130", "V1", "SN1", 1) == Mine.OFFLINE, "strange mine answer: offline " + bad);
        }
        net.reply = new Reply(200, HAVE);
        check(c.mine("bad", "V1", "SN1", 1) == Mine.OFFLINE && c.mine("G0130", "V1", " ", 1) == Mine.OFFLINE, "bad input: offline");
        net.fail = new IOException("down");
        check(c.mine("G0130", "V1", "SN1", 1) == Mine.OFFLINE, "no network: offline");
        net.fail = null;
        int calls = net.urls.size();
        check(c.send(Kind.MINE, "G0130", "V1", "SN1", "", 1) == Claim.OFFLINE && net.urls.size() == calls, "mine is no earn event");
        check(Kind.MINE.wire.equals("mine"), "mine wire name");
        Claim at = Claim.kept("K7M2QX9P", 9L, false, "l", "", 0).at(77L);
        check(at.earned == 77L && at.code.equals("K7M2QX9P") && at.expires == 9L, "the time of an answer");
    }

    /** Slava 2026-11-23: a second report from the same projector must not show another code. The base answers a repeat
     *  (another report id, a retry, a reinstalled app with a new key and nothing kept) with the same code. */
    static void oneCode() throws Exception {
        long now = 1800000100L;
        Offers newFw = Offers.from(FlatJson.parse("{\"k\":0,\"o\":[\"report\",\"install\"]}"));
        String earn = "{\"st\":\"claim\",\"code\":\"K7M2QX9P\",\"exp\":1802592000,\"w\":1,\"l\":\"https://t.me/Lumen_OS_Bot?start=f_K7M2QX9P\"}";
        String seen = null;
        for (int i = 0; i < 6; i++) {
            FakeNet net = new FakeNet();
            Contrib c = new Contrib("https://base.test", net, new JdkSigner());   // every other round: a reinstall, new key
            net.reply = new Reply(200, earn);
            Claim cl = c.send(Kind.REPORT, "G0111", "v1.3.38", "SN1234567890X", "LR-B0000" + (i % 3), now + i).at(now + i);
            String sh = new String(net.body, StandardCharsets.US_ASCII).split("\nsh=")[1].substring(0, 64);
            check(sh.equals(ContribBody.serialHash(" SN1234567890X ")), "one projector, one serial hash " + i);
            net.reply = new Reply(200, HAVE);
            List<Claim> kept = i % 2 == 0 ? new ArrayList<>() : new ArrayList<>(Arrays.asList(cl));
            Shown s = Shown.decide(newFw, c.mine("G0111", "v1.3.38", "SN1234567890X", now + i), kept, true, "", now + i);
            check(s.card == Card.CODE && (seen == null || s.code.equals(seen)), "report " + i + ": the same code " + s.code);
            seen = s.code;
            check(Shown.decide(newFw, Mine.OFFLINE, kept, true, "", now + i).card == (kept.isEmpty() ? Card.NONE : Card.CODE),
                    "offline round " + i);
        }
        Mine taken = mineOf(HAVE.replace("\"t\":0", "\"t\":1"));
        Shown after = Shown.decide(newFw, taken, new ArrayList<>(), true, "sent_before", now);
        check(after.card == Card.TAKEN && after.code.equals(seen) && !after.reportFirst, "taken: already got a Spark");
        check(!ContribBody.serialHash("SN1234567890X").equals(ContribBody.serialHash("SN1234567890Y")), "another projector, another hash");
    }

    static Mine mineOf(String json) {
        return Mine.from(200, FlatJson.parse(json));
    }

    static void shown() {
        long now = 1800000100L;
        Offers newFw = Offers.from(FlatJson.parse("{\"k\":0,\"o\":[\"report\",\"install\"]}"));
        Offers known = Offers.from(FlatJson.parse("{\"k\":1,\"o\":[\"install\"]}"));
        List<Claim> none = new ArrayList<>();
        // the base answered: a code still to take
        Shown s = Shown.decide(known, mineOf(HAVE), none, false, "", now);
        check(s.card == Card.CODE && s.code.equals("K7M2QX9P") && s.shown().equals("K7M2 QX9P") && s.earned == 1800000000L
                && s.expires == 1802592000L && s.waiting && s.link.endsWith("f_K7M2QX9P") && !s.reportFirst, "code from the base");
        check(Shown.decide(known, mineOf(HAVE), none, false, "", 1802592000L).card == Card.CODE, "the last second of the code");
        // its code ran out untaken: what else applies (the offer of a new firmware, else nothing)
        check(Shown.decide(known, mineOf(HAVE), none, false, "", 1802592001L) == Shown.NONE, "ran out: nothing");
        Mine taken = mineOf(HAVE.replace("\"t\":0", "\"t\":1").replace("\"rs\":1", "\"rs\":0"));
        Shown t = Shown.decide(newFw, taken, none, false, "", now);
        check(t.card == Card.TAKEN && t.code.equals("K7M2QX9P") && t.earned == 1800000000L && !t.reportFirst, "already got a Spark");
        Mine lost = mineOf(HAVE.replace("\"t\":0", "\"t\":1").replace("\"code\":\"K7M2QX9P\"", "\"code\":\"\""));
        check(Shown.decide(known, lost, none, false, "", now).card == Card.TAKEN && Shown.decide(known, lost, none, false, "", now).shown().isEmpty(),
                "already got a Spark, code not kept");
        Mine noCode = mineOf(HAVE.replace("\"code\":\"K7M2QX9P\"", "\"code\":\"\""));
        check(Shown.decide(known, noCode, none, false, "", now) == Shown.NONE, "to take but no code: nothing to show");
        // the base wins over the app's own answers, and its "nothing" hides them too
        List<Claim> kept = new ArrayList<>(Arrays.asList(Claim.kept("AAAA2222", now + 10, false, "https://t.me/x?start=f_AAAA2222", "", 50)));
        check(Shown.decide(known, mineOf(HAVE), kept, false, "", now).code.equals("K7M2QX9P"), "the base wins");
        check(Shown.decide(known, Mine.NONE, kept, false, "", now) == Shown.NONE, "the base knows nothing: the kept code goes");
        // offline: the oldest kept code still to take (the base shows that one too)
        kept.add(0, Claim.kept("BBBB3333", now + 10, true, "https://t.me/x?start=f_BBBB3333", "", 40));
        kept.add(Claim.kept("CCCC4444", now - 1, false, "https://t.me/x?start=f_CCCC4444", "", 30));
        kept.add(Claim.kept(null, 0, false, "", "pair_known", 20));
        Shown off = Shown.decide(known, Mine.OFFLINE, kept, false, "", now);
        check(off.card == Card.CODE && off.code.equals("BBBB3333") && off.waiting && off.earned == 40 && off.expires == now + 10,
                "offline: the oldest kept code that still works");
        check(Shown.decide(known, Mine.OFFLINE, Arrays.asList(kept.get(1), kept.get(0)), false, "", now).code.equals("BBBB3333"),
                "offline: the oldest whatever the order");
        kept = new ArrayList<>(kept.subList(1, 4));
        check(Shown.decide(known, Mine.OFFLINE, kept.subList(0, 1), false, "", now + 10).code.equals("AAAA2222"), "kept, last second");
        check(Shown.decide(known, Mine.OFFLINE, kept.subList(1, 3), false, "", now) == Shown.NONE, "kept, ran out or none");
        // the offer: a new firmware and no report from this projector
        Shown offer = Shown.decide(newFw, Mine.NONE, none, false, "", now);
        check(offer.card == Card.SEND_INFO && offer.reportFirst, "a new firmware: the report leads");
        check(Shown.decide(newFw, Mine.OFFLINE, none, false, "", now).reportFirst, "offline base, the pair said new: the report leads");
        check(Shown.decide(newFw, Mine.NONE, none, true, "", now) == Shown.NONE, "a report went from this app: no offer");
        check(Shown.decide(newFw, mineOf("{\"st\":\"none\",\"rs\":1}"), none, false, "", now) == Shown.NONE, "the base saw a report of this projector");
        check(Shown.decide(known, Mine.NONE, none, false, "", now) == Shown.NONE && Shown.decide(Offers.NONE, Mine.NONE, none, false, "", now) == Shown.NONE,
                "a known firmware: no offer");
        check(Shown.decide(newFw, mineOf(HAVE), none, false, "", now).card == Card.CODE, "a code to take comes before the offer");
        check(Shown.decide(known, Mine.NONE, none, false, "installed_before", now).card == Card.INSTALLED_BEFORE
                && Shown.decide(known, Mine.NONE, none, false, "pair_known", now) == Shown.NONE, "installed before notice");
        check(Shown.NONE.card == Card.NONE && !Shown.NONE.reportFirst && Shown.NONE.shown().isEmpty(), "NONE shows nothing");
    }

    static void transport() {
        try {
            check(HttpTransport.read(new ByteArrayInputStream(new byte[HttpTransport.ANSWER_MAX])).length() == HttpTransport.ANSWER_MAX,
                    "answer at the cap");
        } catch (IOException e) {
            check(false, "answer at the cap");
        }
        try {
            HttpTransport.read(new ByteArrayInputStream(new byte[HttpTransport.ANSWER_MAX + 1]));
            check(false, "answer over the cap");
        } catch (IOException e) {
            check(true, "answer over the cap");
        }
        try {
            new HttpTransport().get("http://base.test/v1/pair");
            check(false, "plain http refused");
        } catch (IOException e) {
            check(e.getMessage().equals("https only"), "plain http refused");
        }
        check(HttpTransport.TIMEOUT_MS == 10_000 && HttpTransport.ANSWER_MAX == 4096 && Contrib.BASE.equals("https://spark.lumen-os.net"),
                "timeouts, cap, address");
    }
}
