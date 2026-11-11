// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Host checks of the Miracast sink core. sh test/run_core.sh; exit code 0 = all passed. */
public final class CoreTest {
    private static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    static void eq(Object want, Object got, String what) {
        check(want == null ? got == null : want.equals(got), what + " want=" + want + " got=" + got);
    }

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static void main(String[] a) throws Exception {
        rtspParser();
        params();
        formats();
        session();
        rtp();
        reorder();
        ts();
        audio();
        pipeline();
        locator();
        endToEnd();
        System.out.println("miracast core: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ RTSP framing

    static void rtspParser() throws Exception {
        String m1 = "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nRequire: org.wfa.wfd1.0\r\n\r\n";
        String m3 = "GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\nCSeq: 2\r\nContent-Type: text/parameters\r\n"
                + "Content-Length: 41\r\n\r\nwfd_video_formats\r\nwfd_audio_codecs\r\nxx\r\n";
        // byte by byte
        RtspParser p = new RtspParser();
        List<RtspMessage> got = new ArrayList<>();
        byte[] all = b(m1 + m3);
        for (byte x : all) got.addAll(p.feed(new byte[] {x}, 0, 1));
        eq(2, got.size(), "parser: two messages byte by byte");
        eq("OPTIONS", got.get(0).method, "parser: M1 method");
        eq("*", got.get(0).uri, "parser: M1 uri");
        eq(1, got.get(0).cseq(), "parser: M1 cseq");
        eq("org.wfa.wfd1.0", got.get(0).header("require"), "parser: header case-insensitive");
        eq("GET_PARAMETER", got.get(1).method, "parser: M3 method");
        eq(41, got.get(1).body().length, "parser: M3 body length");
        eq(0, p.pending(), "parser: nothing left");
        // one chunk, response with body, LF-only lines
        p = new RtspParser();
        got = p.feed(all, 0, all.length);
        eq(2, got.size(), "parser: two messages in one chunk");
        String r = "RTSP/1.0 200 OK\nCSeq: 7\nSession: 12345678;timeout=30\n\n";
        got = p.feed(b(r), 0, r.length());
        eq(1, got.size(), "parser: LF-only response");
        eq(200, got.get(0).status, "parser: status");
        check(!got.get(0).isRequest(), "parser: response is not a request");
        eq("12345678;timeout=30", got.get(0).header("Session"), "parser: session header");
        // interleaved binary frame skipped
        byte[] inter = RtspParser.concat(new byte[] {'$', 0, 0, 3, 9, 9, 9}, b(m1));
        got = new RtspParser().feed(inter, 0, inter.length);
        eq(1, got.size(), "parser: interleaved frame skipped");
        // incomplete body waits
        p = new RtspParser();
        String part = "SET_PARAMETER rtsp://x/wfd1.0 RTSP/1.0\r\nCSeq: 3\r\nContent-Length: 10\r\n\r\nwfd_";
        eq(0, p.feed(b(part), 0, part.length()).size(), "parser: waits for the body");
        eq(1, p.feed(b("abcdef"), 0, 6).size(), "parser: completes with the body");
        // oversize header refused
        boolean thrown = false;
        try {
            byte[] big = new byte[RtspParser.MAX_HEADER + 10];
            java.util.Arrays.fill(big, (byte) 'a');
            new RtspParser().feed(big, 0, big.length);
        } catch (RtspParser.ProtocolException e) {
            thrown = true;
        }
        check(thrown, "parser: oversize header refused");
        thrown = false;
        try {
            String bad = "HELLO world\r\n\r\n";
            new RtspParser().feed(b(bad), 0, bad.length());
        } catch (RtspParser.ProtocolException e) {
            thrown = true;
        }
        check(thrown, "parser: garbage refused");
        // serialize round trip
        RtspMessage q = RtspMessage.request("SETUP", "rtsp://1.2.3.4/wfd1.0/streamid=0").setHeader("CSeq", "5")
                .setHeader("Transport", "RTP/AVP/UDP;unicast;client_port=19000-19001");
        byte[] ser = q.serialize();
        RtspMessage back = new RtspParser().feed(ser, 0, ser.length).get(0);
        eq("SETUP", back.method, "serialize: method");
        eq("RTP/AVP/UDP;unicast;client_port=19000-19001", back.header("Transport"), "serialize: header");
        RtspMessage withBody = RtspMessage.response(200, "OK").setHeader("CSeq", "2").setTextBody("text/parameters", "a: b\r\n");
        String wire = new String(withBody.serialize(), StandardCharsets.UTF_8);
        check(wire.contains("Content-Length: 6\r\n"), "serialize: content length");
        check(wire.endsWith("\r\n\r\na: b\r\n"), "serialize: body after blank line");
    }

    // ------------------------------------------------------------------ parameters and formats

    static void params() {
        Map<String, String> p = WfdParams.parse("wfd_video_formats\r\nwfd_audio_codecs\r\n\r\n");
        eq(2, p.size(), "params: GET names");
        eq("", p.get("wfd_video_formats"), "params: bare name");
        p = WfdParams.parse("wfd_presentation_URL: rtsp://198.51.100.1/wfd1.0/streamid=0 none\r\nwfd_trigger_method: SETUP");
        eq("rtsp://198.51.100.1/wfd1.0/streamid=0 none", p.get("wfd_presentation_URL"), "params: value with colon kept");
        eq("SETUP", p.get("wfd_trigger_method"), "params: last line without CRLF");
        eq("a: 1\r\nb: none\r\n", WfdParams.format(new java.util.LinkedHashMap<>(Map.of("a", "1"))) + "b: none\r\n",
                "params: format");
    }

    static void formats() {
        String v = WfdFormats.sinkVideoFormats(WfdFormats.Quality.P1080_60);
        String[] t = v.split(",")[0].trim().split("\\s+");
        eq("40", t[0], "formats: native = CEA 1080p60");
        eq("02", t[2], "formats: CHP first");
        eq("10", t[3], "formats: level 4.2 for 1080p60");
        long cea = Long.parseLong(t[4], 16);
        check((cea & (1 << 8)) != 0, "formats: 1080p60 offered");
        check((cea & (1 << 7)) != 0, "formats: 1080p30 offered");
        check((cea & ((1 << 2) | (1 << 4) | (1 << 9) | (1 << 14))) == 0, "formats: no interlaced modes");
        check((cea & 1) != 0, "formats: 640x480 (mandatory) offered");
        long vesa = Long.parseLong(t[5], 16);
        check((vesa & (1L << 29)) == 0, "formats: 1920x1200p60 above level 4.2 not offered");
        check((vesa & (1L << 28)) != 0, "formats: 1920x1200p30 offered");
        eq(13, t.length, "formats: 13 fields per codec entry");
        check(v.contains(", 01 10 "), "formats: CBP entry follows");
        String v30 = WfdFormats.sinkVideoFormats(WfdFormats.Quality.P1080_30);
        long cea30 = Long.parseLong(v30.split("\\s+")[4], 16);
        check((cea30 & (1 << 8)) == 0 && (cea30 & (1 << 7)) != 0, "formats: 1080p30 cap drops 1080p60");
        check((cea30 & (1 << 6)) != 0, "formats: 1080p30 cap keeps 720p60");
        eq("38", v30.substring(0, 2), "formats: native 1080p30");
        String v720 = WfdFormats.sinkVideoFormats(WfdFormats.Quality.P720_60);
        long cea720 = Long.parseLong(v720.split("\\s+")[4], 16);
        check((cea720 & ((1 << 7) | (1 << 8))) == 0 && (cea720 & (1 << 6)) != 0, "formats: 720p cap");
        eq("LPCM 00000003 00, AAC 00000001 00", WfdFormats.sinkAudioCodecs(true, true), "formats: audio offer");
        eq("none", WfdFormats.sinkAudioCodecs(false, false), "formats: no audio");

        WfdFormats.Video c = WfdFormats.parseVideo("00 00 02 04 00000080 00000000 00000000 00 0000 0000 00 none none");
        check(c != null && c.width == 1920 && c.height == 1080 && c.fps == 30 && c.profile == 2, "formats: M4 AOSP 1080p30 " + c);
        c = WfdFormats.parseVideo("00 00 01 10 00000000 10000000 00000000 00 0000 0000 00 none none");
        check(c != null && c.width == 1920 && c.height == 1200 && c.fps == 30, "formats: M4 VESA 1920x1200p30 " + c);
        c = WfdFormats.parseVideo("08 00 02 10 00000100 00000000 00000000 00 0000 0000 00 NONE NONE");
        check(c != null && c.fps == 60 && c.height == 1080, "formats: M4 1080p60 " + c);
        c = WfdFormats.parseVideo("00 00 01 01 00000000 00000000 00000100 00 0000 0000 00 none none");
        check(c != null && c.width == 960 && c.height == 540, "formats: M4 HH 960x540 " + c);
        eq(null, WfdFormats.parseVideo("none"), "formats: no video");
        eq(null, WfdFormats.parseVideo("zz 00 02"), "formats: garbage video");
        WfdFormats.Audio au = WfdFormats.parseAudio("AAC 00000001 00");
        check(au != null && au.codec == WfdFormats.Audio.AAC && au.channels == 2 && au.sampleRate == 48000, "formats: M4 AAC");
        au = WfdFormats.parseAudio("LPCM 00000002 00");
        check(au != null && au.codec == WfdFormats.Audio.LPCM && au.sampleRate == 48000, "formats: M4 LPCM 48k");
        au = WfdFormats.parseAudio("LPCM 00000001 00");
        check(au != null && au.sampleRate == 44100, "formats: M4 LPCM 44.1k");
        eq(null, WfdFormats.parseAudio("none"), "formats: no audio chosen");
    }

    // ------------------------------------------------------------------ the RTSP session (scripted source)

    static final class Recorder implements WfdSession.Out, WfdSession.Listener {
        final List<RtspMessage> sent = new ArrayList<>();
        WfdFormats.Video video;
        WfdFormats.Audio audio;
        boolean playing;
        String closed;

        @Override
        public void send(RtspMessage m) {
            sent.add(m);
        }

        @Override
        public void onFormats(WfdFormats.Video v, WfdFormats.Audio a) {
            video = v;
            audio = a;
        }

        @Override
        public void onPlaying() {
            playing = true;
        }

        @Override
        public void onClosed(String reason) {
            closed = reason;
        }

        @Override
        public void onLog(String line) {
        }

        RtspMessage last() {
            return sent.get(sent.size() - 1);
        }
    }

    static RtspMessage req(String method, String uri, int cseq, String body) {
        RtspMessage m = RtspMessage.request(method, uri).setHeader("CSeq", String.valueOf(cseq));
        if (body != null) m.setTextBody("text/parameters", body);
        return m;
    }

    static RtspMessage resp(int cseq, int status, String... hdr) {
        RtspMessage m = RtspMessage.response(status, status == 200 ? "OK" : "ERR").setHeader("CSeq", String.valueOf(cseq));
        for (int i = 0; i + 1 < hdr.length; i += 2) m.setHeader(hdr[i], hdr[i + 1]);
        return m;
    }

    static void session() {
        WfdSession.Config cfg = new WfdSession.Config();
        cfg.friendlyName = "Lumen Z9X";
        cfg.rtpPort = 19000;
        Recorder r = new Recorder();
        WfdSession s = new WfdSession(cfg, "198.51.100.1", r, r);
        s.start(0);
        // M1
        s.onMessage(req("OPTIONS", "*", 1, null).setHeader("Require", "org.wfa.wfd1.0"), 10);
        eq(2, r.sent.size(), "session: M1 reply + M2");
        RtspMessage m1r = r.sent.get(0);
        eq(200, m1r.status, "session: M1 200");
        eq(1, m1r.cseq(), "session: M1 CSeq echoed");
        check(m1r.header("Public").contains("GET_PARAMETER") && m1r.header("Public").contains("org.wfa.wfd1.0"), "session: Public");
        RtspMessage m2 = r.sent.get(1);
        eq("OPTIONS", m2.method, "session: M2 OPTIONS");
        eq("org.wfa.wfd1.0", m2.header("Require"), "session: M2 Require");
        s.onMessage(resp(m2.cseq(), 200, "Public", "org.wfa.wfd1.0, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER"), 20);
        eq(WfdSession.State.CAPABILITIES, s.state(), "session: capabilities state");
        // M3 (with a Windows key and an unknown key)
        s.onMessage(req("GET_PARAMETER", "rtsp://localhost/wfd1.0", 2,
                "wfd_video_formats\r\nwfd_audio_codecs\r\nwfd_client_rtp_ports\r\nwfd_content_protection\r\n"
                        + "microsoft_cursor\r\nintel_friendly_name\r\nwfd_uibc_capability\r\n"), 30);
        RtspMessage m3r = r.last();
        eq(200, m3r.status, "session: M3 200");
        eq("text/parameters", m3r.header("Content-Type"), "session: M3 content type");
        Map<String, String> vals = WfdParams.parse(m3r.bodyText());
        eq(7, vals.size(), "session: M3 answers every name");
        eq("RTP/AVP/UDP;unicast 19000 0 mode=play", vals.get("wfd_client_rtp_ports"), "session: rtp ports");
        eq("none", vals.get("wfd_content_protection"), "session: no HDCP");
        eq("none", vals.get("microsoft_cursor"), "session: unknown -> none");
        eq("Lumen Z9X", vals.get("intel_friendly_name"), "session: friendly name");
        check(vals.get("wfd_video_formats").startsWith("40 00 02 10"), "session: video formats offered");
        // M4
        s.onMessage(req("SET_PARAMETER", "rtsp://localhost/wfd1.0", 3,
                "wfd_video_formats: 00 00 02 10 00000100 00000000 00000000 00 0000 0000 00 none none\r\n"
                        + "wfd_audio_codecs: AAC 00000001 00\r\n"
                        + "wfd_presentation_URL: rtsp://198.51.100.1/wfd1.0/streamid=0 none\r\n"
                        + "wfd_client_rtp_ports: RTP/AVP/UDP;unicast 19000 0 mode=play\r\n"), 40);
        eq(200, r.last().status, "session: M4 200");
        check(r.video != null && r.video.fps == 60, "session: M4 video to listener");
        check(r.audio != null && r.audio.codec == WfdFormats.Audio.AAC, "session: M4 audio to listener");
        eq("rtsp://198.51.100.1/wfd1.0/streamid=0", s.presentationUrl(), "session: presentation URL");
        // M5 trigger SETUP -> M6
        int before = r.sent.size();
        s.onMessage(req("SET_PARAMETER", "rtsp://localhost/wfd1.0", 4, "wfd_trigger_method: SETUP\r\n"), 50);
        eq(before + 2, r.sent.size(), "session: M5 reply + M6");
        eq(200, r.sent.get(before).status, "session: M5 200");
        RtspMessage m6 = r.last();
        eq("SETUP", m6.method, "session: M6 SETUP");
        eq("rtsp://198.51.100.1/wfd1.0/streamid=0", m6.uri, "session: M6 uri");
        eq("RTP/AVP/UDP;unicast;client_port=19000-19001", m6.header("Transport"), "session: M6 transport");
        check(m6.cseq() > m2.cseq(), "session: own CSeq counter grows");
        // SETUP reply -> M7 PLAY
        s.onMessage(resp(m6.cseq(), 200, "Session", "6B8B4567;timeout=30",
                "Transport", "RTP/AVP/UDP;unicast;client_port=19000-19001;server_port=5000-5001"), 60);
        eq("6B8B4567", s.sessionId(), "session: session id without timeout");
        RtspMessage m7 = r.last();
        eq("PLAY", m7.method, "session: M7 PLAY");
        eq("6B8B4567", m7.header("Session"), "session: M7 Session");
        check(!r.playing, "session: not playing before PLAY reply");
        s.onMessage(resp(m7.cseq(), 200, "Session", "6B8B4567"), 70);
        check(r.playing, "session: playing");
        eq(WfdSession.State.PLAYING, s.state(), "session: PLAYING");
        // M16 keep-alive
        before = r.sent.size();
        s.onMessage(req("GET_PARAMETER", "rtsp://localhost/wfd1.0", 5, null).setHeader("Session", "6B8B4567"), 20000);
        eq(before + 1, r.sent.size(), "session: keep-alive answered");
        eq(200, r.last().status, "session: keep-alive 200");
        eq(0, r.last().body().length, "session: keep-alive empty body");
        check(!s.timedOut(20000 + 44000), "session: within timeout+margin");
        check(s.timedOut(20000 + 46000), "session: timeout 30 s + 15 s margin");
        // M13 IDR, rate limited
        check(s.requestIdr(30000), "session: IDR sent");
        RtspMessage idr = r.last();
        eq("SET_PARAMETER", idr.method, "session: IDR method");
        eq("wfd_idr_request\r\n", idr.bodyText(), "session: IDR body");
        eq("6B8B4567", idr.header("Session"), "session: IDR Session");
        check(!s.requestIdr(30100), "session: IDR rate limit");
        check(s.requestIdr(30600), "session: IDR after the limit");
        s.onMessage(resp(r.last().cseq(), 200), 30700);
        // unknown method
        s.onMessage(req("REDIRECT", "rtsp://x", 6, null), 30800);
        eq(501, r.last().status, "session: unknown method 501");
        // M5 PAUSE / PLAY triggers (the source pauses and resumes): M9 PAUSE and M7 PLAY carry the Session
        before = r.sent.size();
        s.onMessage(req("SET_PARAMETER", "rtsp://localhost/wfd1.0", 8, "wfd_trigger_method: PAUSE\r\n"), 30850);
        eq(before + 2, r.sent.size(), "session: PAUSE trigger reply + M9");
        RtspMessage m9 = r.last();
        eq("PAUSE", m9.method, "session: M9 PAUSE");
        eq("6B8B4567", m9.header("Session"), "session: M9 Session");
        s.onMessage(resp(m9.cseq(), 200), 30860);
        eq(WfdSession.State.PAUSED, s.state(), "session: PAUSED");
        s.onMessage(req("SET_PARAMETER", "rtsp://localhost/wfd1.0", 9, "wfd_trigger_method: PLAY\r\n"), 30870);
        RtspMessage resume = r.last();
        eq("PLAY", resume.method, "session: trigger PLAY");
        eq("6B8B4567", resume.header("Session"), "session: trigger PLAY Session");
        s.onMessage(resp(resume.cseq(), 200, "Session", "6B8B4567"), 30880);
        eq(WfdSession.State.PLAYING, s.state(), "session: PLAYING after resume");
        // M5 TEARDOWN trigger -> M8
        s.onMessage(req("SET_PARAMETER", "rtsp://localhost/wfd1.0", 7, "wfd_trigger_method: TEARDOWN\r\n"), 31000);
        RtspMessage m8 = r.last();
        eq("TEARDOWN", m8.method, "session: M8 TEARDOWN");
        eq(WfdSession.State.TEARING_DOWN, s.state(), "session: tearing down");
        s.onMessage(resp(m8.cseq(), 200), 31100);
        eq(WfdSession.State.CLOSED, s.state(), "session: closed");
        eq("teardown done", r.closed, "session: closed reason");

        // refused SETUP closes
        Recorder r2 = new Recorder();
        WfdSession s2 = new WfdSession(cfg, "192.0.2.5", r2, r2);
        s2.start(0);
        s2.onMessage(req("OPTIONS", "*", 1, null), 1);
        s2.onMessage(req("SET_PARAMETER", "rtsp://localhost/wfd1.0", 2, "wfd_trigger_method: SETUP\r\n"), 2);
        eq("rtsp://192.0.2.5/wfd1.0/streamid=0", r2.last().uri, "session: default presentation URL");
        s2.onMessage(resp(r2.last().cseq(), 454), 3);
        check(r2.closed != null && r2.closed.contains("454"), "session: refused SETUP closes");
        // our own teardown before SETUP closes at once
        Recorder r3 = new Recorder();
        WfdSession s3 = new WfdSession(cfg, "192.0.2.5", r3, r3);
        s3.teardown();
        eq(WfdSession.State.CLOSED, s3.state(), "session: teardown before setup");
        // source TEARDOWN request
        Recorder r4 = new Recorder();
        WfdSession s4 = new WfdSession(cfg, "192.0.2.5", r4, r4);
        s4.onMessage(req("TEARDOWN", "rtsp://x", 9, null), 1);
        eq(200, r4.last().status, "session: source TEARDOWN answered");
        eq(WfdSession.State.CLOSED, s4.state(), "session: source TEARDOWN closes");
        WfdSession s5 = new WfdSession(cfg, "h", r4, r4);
        s5.parseSession("ABC;timeout=5");
        eq("ABC", s5.sessionId(), "session: id parsed with bad timeout");
    }

    // ------------------------------------------------------------------ RTP

    static void rtp() {
        RtpPacket p = new RtpPacket();
        byte[] simple = {(byte) 0x80, (byte) (0x80 | 33), 0x12, 0x34, 0, 0, 0, 1, 0, 0, 0, 2, 7, 7};
        check(p.parse(simple, simple.length), "rtp: parse");
        eq(33, p.payloadType, "rtp: pt");
        eq(0x1234, p.seq, "rtp: seq");
        check(p.marker, "rtp: marker");
        eq(12, p.payloadOffset, "rtp: payload offset");
        eq(2, p.payloadLength, "rtp: payload length");
        // CSRC 1 + extension (1 word) + padding 2
        byte[] cx = {(byte) 0xB1, 33, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 0, 0, 0, 1, 9, 9, 9, 9, 5, 6, 7, 0, 2};
        check(p.parse(cx, cx.length), "rtp: csrc+ext+padding parse");
        eq(24, p.payloadOffset, "rtp: offset after csrc+ext");
        eq(3, p.payloadLength, "rtp: length minus padding");
        check(!p.parse(new byte[] {0x40, 33, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 12), "rtp: version 1 refused");
        check(!p.parse(new byte[] {(byte) 0x8F, 33, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 12), "rtp: csrc past end refused");
    }

    static final class Collect implements RtpReorderer.Sink {
        final List<Integer> got = new ArrayList<>();
        int lost;

        @Override
        public void onPayload(byte[] d, int off, int len) {
            got.add(((d[off] & 0xff) << 8) | (d[off + 1] & 0xff));
        }

        @Override
        public void onLoss(int n) {
            lost += n;
        }
    }

    static void pushSeq(RtpReorderer r, int seq, long now) {
        r.push(seq, new byte[] {(byte) (seq >> 8), (byte) seq}, 0, 2, now);
    }

    static void reorder() {
        Collect c = new Collect();
        RtpReorderer r = new RtpReorderer(4, 40, c);
        for (int s : new int[] {10, 11, 13, 12, 14}) pushSeq(r, s, 0);
        eq(List.of(10, 11, 12, 13, 14), c.got, "reorder: swap fixed");
        eq(0, c.lost, "reorder: nothing lost");
        pushSeq(r, 12, 0);
        eq(5, c.got.size(), "reorder: late duplicate dropped");
        // loss by count: 15 missing, 16..20 held (5 > maxHold 4)
        for (int s = 16; s <= 20; s++) pushSeq(r, s, 1);
        eq(List.of(10, 11, 12, 13, 14, 16, 17, 18, 19, 20), c.got, "reorder: gap given up by count");
        eq(1, c.lost, "reorder: one lost");
        // loss by time
        pushSeq(r, 22, 100);
        eq(10, c.got.size(), "reorder: held while young");
        r.flushStale(130);
        eq(10, c.got.size(), "reorder: still held at 30 ms");
        r.flushStale(141);
        eq(22, (int) c.got.get(c.got.size() - 1), "reorder: released after 40 ms");
        eq(2, c.lost, "reorder: second loss counted");
        // wrap-around
        Collect w = new Collect();
        RtpReorderer rw = new RtpReorderer(8, 40, w);
        for (int s : new int[] {65534, 65535, 1, 0, 2}) pushSeq(rw, s, 0);
        eq(List.of(65534, 65535, 0, 1, 2), w.got, "reorder: wraps at 65536");
        // a source restart (big jump back)
        pushSeq(rw, 30000, 0);
        pushSeq(rw, 30001, 0);
        check(w.got.get(w.got.size() - 1) == 30001, "reorder: big jump delivers on");
    }

    // ------------------------------------------------------------------ TS

    static final class PesLog implements TsDemuxer.Listener {
        final List<String> pes = new ArrayList<>();
        final List<byte[]> data = new ArrayList<>();
        int streams, disc;

        @Override
        public void onPes(int pid, int type, long ptsUs, byte[] d, int off, int len, boolean damaged) {
            pes.add(Integer.toHexString(pid) + ":" + ptsUs + ":" + len + (damaged ? ":D" : ""));
            data.add(java.util.Arrays.copyOfRange(d, off, off + len));
        }

        @Override
        public void onStreams(List<TsDemuxer.Stream> s) {
            streams = s.size();
        }

        @Override
        public void onDiscontinuity(int pid, int type) {
            disc++;
        }
    }

    static byte[] nal(int type, int size) {
        byte[] b = new byte[size];
        b[2] = 1;
        b[3] = (byte) type;
        for (int i = 4; i < size; i++) b[i] = 0x55;
        return b;
    }

    static void ts() {
        TsWriter w = new TsWriter(TsDemuxer.ST_LPCM);
        PesLog log = new PesLog();
        TsDemuxer d = new TsDemuxer(log);
        ByteArrayOutputStream ts = new ByteArrayOutputStream();
        ts.writeBytes(w.psi());
        byte[] f1 = nal(5, 3000), f2 = nal(1, 500);
        ts.writeBytes(w.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 90000, f1, false)));
        byte[] lpcm = new byte[4 + 1920];
        lpcm[0] = (byte) 0xA0;
        lpcm[1] = 6;
        lpcm[3] = 0x11;
        ts.writeBytes(w.packetize(TsWriter.AUDIO_PID, TsWriter.pes(0xBD, 90000, lpcm, true)));
        ts.writeBytes(w.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 93000, f2, false)));
        byte[] all = ts.toByteArray();
        // feed in odd chunks (packets split across RTP payloads)
        for (int pos = 0; pos < all.length; pos += 1000) d.feed(all, pos, Math.min(1000, all.length - pos));
        eq(2, log.streams, "ts: PMT lists video + audio");
        eq(2, log.pes.size(), "ts: audio at its length, video 1 at the next video PUSI");
        eq("1100:1000000:1924", log.pes.get(0), "ts: LPCM PES out first (complete by length)");
        eq("1011:1000000:3000", log.pes.get(1), "ts: first video PES with PTS 1 s");
        check(java.util.Arrays.equals(f1, log.data.get(1)), "ts: video payload intact");
        d.flush();
        eq("1011:1033333:500", log.pes.get(2), "ts: flush emits the last video PES");
        // continuity gap marks damage
        PesLog log2 = new PesLog();
        TsDemuxer d2 = new TsDemuxer(log2);
        TsWriter w2 = new TsWriter(TsDemuxer.ST_AAC_ADTS);
        byte[] psi = w2.psi();
        d2.feed(psi, 0, psi.length);
        byte[] v = w2.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 0, nal(5, 1000), false));
        // drop the 3rd packet
        byte[] cut = new byte[v.length - 188];
        System.arraycopy(v, 0, cut, 0, 2 * 188);
        System.arraycopy(v, 3 * 188, cut, 2 * 188, v.length - 3 * 188);
        d2.feed(cut, 0, cut.length);
        byte[] v2 = w2.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 3000, nal(1, 300), false));
        d2.feed(v2, 0, v2.length);
        eq(1, log2.disc, "ts: continuity error reported");
        check(log2.pes.get(0).endsWith(":D"), "ts: damaged PES flagged");
        // garbage before sync
        PesLog log3 = new PesLog();
        TsDemuxer d3 = new TsDemuxer(log3);
        byte[] junk = RtspParser.concat(new byte[] {1, 2, 3}, w2.psi());
        d3.feed(junk, 0, junk.length);
        eq(2, log3.streams, "ts: resync after junk");
    }

    // ------------------------------------------------------------------ audio payloads

    static void audio() {
        byte[] h48 = {(byte) 0xA0, 6, 0, 0x11, 0x12, 0x34, 0x56, 0x78};
        AudioPayload.LpcmHeader h = AudioPayload.lpcmHeader(h48, 0, h48.length);
        check(h != null && h.sampleRate == 48000 && h.channels == 2, "audio: LPCM 48 kHz header");
        byte[] h44 = {(byte) 0xA0, 6, 0, 0x09};
        h = AudioPayload.lpcmHeader(h44, 0, 4);
        check(h != null && h.sampleRate == 44100, "audio: LPCM 44.1 kHz header");
        check(AudioPayload.lpcmHeader(new byte[] {(byte) 0xA0, 6, 0, 0x12}, 0, 4) == null, "audio: LPCM mono refused");
        byte[] out = new byte[16];
        eq(4, AudioPayload.lpcmToLittleEndian(h48, 0, h48.length, out), "audio: LPCM bytes");
        check(out[0] == 0x34 && out[1] == 0x12 && out[2] == 0x78 && out[3] == 0x56, "audio: LPCM swapped to LE");
        // ADTS AAC LC 48 kHz stereo, frame 100 bytes, no CRC
        byte[] adts = adtsFrame(100);
        AudioPayload.Adts a = AudioPayload.adts(adts, 0, adts.length);
        check(a != null && a.sampleRate == 48000 && a.channels == 2 && a.frameLen == 100 && a.headerLen == 7, "audio: ADTS header");
        byte[] asc = a.audioSpecificConfig();
        check(asc[0] == 0x11 && asc[1] == (byte) 0x90, "audio: AudioSpecificConfig 0x1190");
        check(AudioPayload.adts(new byte[] {0, 0, 0, 0, 0, 0, 0}, 0, 7) == null, "audio: no sync word");
    }

    static byte[] adtsFrame(int len) {
        byte[] f = new byte[len];
        f[0] = (byte) 0xFF;
        f[1] = (byte) 0xF1;
        f[2] = (byte) ((1 << 6) | (3 << 2) | 0); // LC, 48 kHz, ch high bit 0
        f[3] = (byte) ((2 << 6) | ((len >> 11) & 3));
        f[4] = (byte) ((len >> 3) & 0xff);
        f[5] = (byte) (((len & 7) << 5) | 0x1f);
        f[6] = (byte) 0xFC;
        return f;
    }

    // ------------------------------------------------------------------ pipeline

    static final class Units implements MediaPipeline.Output {
        final List<String> v = new ArrayList<>();
        int pcm, aac, need;
        byte[] firstKey;

        @Override
        public void onVideo(byte[] d, int off, int len, long ptsUs, boolean key) {
            v.add((key ? "K" : "P") + len);
            if (key && firstKey == null) firstKey = java.util.Arrays.copyOfRange(d, off, off + len);
        }

        @Override
        public void onPcm(byte[] p, int len, int rate, int ch, long ptsUs) {
            pcm += len;
        }

        @Override
        public void onAac(byte[] d, int off, int len, AudioPayload.Adts hdr, long ptsUs) {
            aac++;
        }

        @Override
        public void onNeedKeyFrame(String why) {
            need++;
        }

        @Override
        public void onStreams(List<TsDemuxer.Stream> s) {
        }
    }

    static void pipeline() {
        Units u = new Units();
        MediaPipeline mp = new MediaPipeline(u);
        TsWriter w = new TsWriter(TsDemuxer.ST_AAC_ADTS);
        ByteArrayOutputStream ts = new ByteArrayOutputStream();
        ts.writeBytes(w.psi());
        ts.writeBytes(w.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 0, nal(1, 400), false)));     // P before key: dropped
        byte[] sps = RtspParser.concat(nal(7, 20), nal(8, 8));
        ts.writeBytes(w.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 0, sps, false)));             // parameter sets alone
        ts.writeBytes(w.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 3000, nal(5, 2000), false)));  // key without SPS
        ts.writeBytes(w.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 6000, nal(1, 700), false)));
        byte[] aacPes = RtspParser.concat(adtsFrame(100), adtsFrame(120));
        ts.writeBytes(w.packetize(TsWriter.AUDIO_PID, TsWriter.pes(0xC0, 3000, aacPes, true)));
        ts.writeBytes(w.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 9000, nal(1, 10), false)));
        List<byte[]> pk = TsWriter.rtp(ts.toByteArray(), 100);
        // deliver with one swap
        for (int i = 0; i < pk.size(); i++) {
            int j = i == 2 ? 3 : i == 3 ? 2 : i;
            mp.onDatagram(pk.get(j), pk.get(j).length, 0);
        }
        eq(List.of("K2028", "P700"), u.v, "pipeline: P before key dropped, key gets SPS/PPS prepended");
        check(u.need >= 1, "pipeline: key frame asked for");
        eq(2, u.aac, "pipeline: two AAC frames from one PES");
        check(u.firstKey != null && H264.hasParameterSets(H264.nalTypes(u.firstKey, 0, u.firstKey.length))
                && H264.isKeyFrame(H264.nalTypes(u.firstKey, 0, u.firstKey.length)), "pipeline: key frame carries SPS/PPS");
        check(mp.stats().contains("lost=0"), "pipeline: stats " + mp.stats());
        // LPCM path
        Units u2 = new Units();
        MediaPipeline mp2 = new MediaPipeline(u2);
        TsWriter w2 = new TsWriter(TsDemuxer.ST_LPCM);
        ByteArrayOutputStream t2 = new ByteArrayOutputStream();
        t2.writeBytes(w2.psi());
        byte[] lpcm = new byte[4 + 1920];
        lpcm[0] = (byte) 0xA0;
        lpcm[1] = 6;
        lpcm[3] = 0x11;
        t2.writeBytes(w2.packetize(TsWriter.AUDIO_PID, TsWriter.pes(0xBD, 0, lpcm, true)));
        for (byte[] p : TsWriter.rtp(t2.toByteArray(), 65530)) mp2.onDatagram(p, p.length, 0);
        eq(1920, u2.pcm, "pipeline: LPCM bytes out");
        // non-MP2T payload type ignored
        byte[] other = {(byte) 0x80, 96, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        mp2.onDatagram(other, other.length, 0);
        check(mp2.stats().contains("bad=1"), "pipeline: other payload type counted bad");
    }

    // ------------------------------------------------------------------ locator

    static void locator() {
        String arp = "IP address       HW type     Flags       HW address            Mask     Device\n"
                + "203.0.113.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0\n"
                + "198.51.100.188   0x1         0x2         <mac>     *        p2p-p2p0-0\n"
                + "198.51.100.200   0x1         0x0         00:00:00:00:00:00     *        p2p-p2p0-0\n";
        List<SourceLocator.Entry> e = SourceLocator.arpEntries(arp, "p2p");
        eq(1, e.size(), "locator: one complete p2p entry");
        eq("198.51.100.188", e.get(0).ip, "locator: ip");
        eq("<mac>", e.get(0).mac, "locator: mac lower case");
        eq(2, SourceLocator.arpEntries(arp, null).size(), "locator: all complete entries");
        List<String> sw = SourceLocator.sweepOrder("198.51.100.1");
        eq("198.51.100.2", sw.get(0), "locator: sweep starts at .2 when we are .1");
        eq(253, sw.size(), "locator: sweep size");
        check(!sw.contains("198.51.100.1"), "locator: own address skipped");
        eq("<mac>", SourceLocator.macKey("<mac>"), "locator: local bit cleared");
    }

    // ------------------------------------------------------------------ end to end over sockets

    /** A scripted WFD source on localhost: RTSP server, then RTP/TS to the sink's port. */
    static void endToEnd() throws Exception {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        int ctrl = server.getLocalPort();
        AtomicReference<String> srcError = new AtomicReference<>();
        List<String> srcSaw = new java.util.concurrent.CopyOnWriteArrayList<>();
        Units units = new Units();
        MediaPipeline mp = new MediaPipeline(units);
        SinkConnection.Clock clock = System::currentTimeMillis;
        RtpReceiver rx = new RtpReceiver(0, mp, clock);
        int rtpPort = rx.localPort();
        Thread rxThread = new Thread(rx, "rtp");
        rxThread.start();

        Thread source = new Thread(() -> {
            try (Socket s = server.accept()) {
                s.setSoTimeout(5000);
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                RtspParser p = new RtspParser();
                List<RtspMessage> q = new ArrayList<>();
                java.util.function.Function<String, RtspMessage> await = (what) -> {
                    try {
                        byte[] buf = new byte[4096];
                        while (q.isEmpty()) {
                            int n = in.read(buf);
                            if (n < 0) throw new RuntimeException("eof waiting " + what);
                            q.addAll(p.feed(buf, 0, n));
                        }
                        RtspMessage m = q.remove(0);
                        srcSaw.add(m.isRequest() ? m.method : String.valueOf(m.status));
                        return m;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
                out.write(req("OPTIONS", "*", 1, null).setHeader("Require", "org.wfa.wfd1.0").serialize());
                await.apply("M1 reply");
                RtspMessage m2 = await.apply("M2");
                out.write(resp(m2.cseq(), 200, "Public", "org.wfa.wfd1.0, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER").serialize());
                out.write(req("GET_PARAMETER", "rtsp://localhost/wfd1.0", 2, "wfd_video_formats\r\nwfd_audio_codecs\r\nwfd_client_rtp_ports\r\n").serialize());
                RtspMessage m3 = await.apply("M3 reply");
                String ports = WfdParams.parse(m3.bodyText()).get("wfd_client_rtp_ports");
                int sinkPort = Integer.parseInt(ports.split("\\s+")[1]);
                out.write(req("SET_PARAMETER", "rtsp://localhost/wfd1.0", 3,
                        "wfd_video_formats: 00 00 02 10 00000080 00000000 00000000 00 0000 0000 00 none none\r\n"
                                + "wfd_audio_codecs: LPCM 00000002 00\r\n"
                                + "wfd_presentation_URL: rtsp://127.0.0.1/wfd1.0/streamid=0 none\r\n").serialize());
                await.apply("M4 reply");
                out.write(req("SET_PARAMETER", "rtsp://localhost/wfd1.0", 4, "wfd_trigger_method: SETUP\r\n").serialize());
                await.apply("M5 reply");
                RtspMessage setup = await.apply("SETUP");
                out.write(resp(setup.cseq(), 200, "Session", "S1;timeout=30", "Transport", setup.header("Transport") + ";server_port=6000-6001").serialize());
                RtspMessage play = await.apply("PLAY");
                out.write(resp(play.cseq(), 200, "Session", "S1").serialize());
                // media: PSI + key frame + P frame + LPCM, then a key-frame request comes back
                TsWriter w = new TsWriter(TsDemuxer.ST_LPCM);
                ByteArrayOutputStream ts = new ByteArrayOutputStream();
                ts.writeBytes(w.psi());
                byte[] key = RtspParser.concat(nal(7, 20), nal(8, 8), nal(5, 5000));
                ts.writeBytes(w.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 0, key, false)));
                ts.writeBytes(w.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 3000, nal(1, 900), false)));
                byte[] lpcm = new byte[4 + 1920];
                lpcm[0] = (byte) 0xA0;
                lpcm[1] = 6;
                lpcm[3] = 0x11;
                ts.writeBytes(w.packetize(TsWriter.AUDIO_PID, TsWriter.pes(0xBD, 0, lpcm, true)));
                ts.writeBytes(w.packetize(TsWriter.VIDEO_PID, TsWriter.pes(0xE0, 6000, nal(1, 10), false)));
                try (DatagramSocket ds = new DatagramSocket()) {
                    for (byte[] pk : TsWriter.rtp(ts.toByteArray(), 4000)) {
                        ds.send(new DatagramPacket(pk, pk.length, InetAddress.getLoopbackAddress(), sinkPort));
                    }
                }
                RtspMessage idr = await.apply("IDR");
                srcSaw.add("body:" + idr.bodyText().trim());
                out.write(resp(idr.cseq(), 200).serialize());
                // keep-alive, then teardown trigger
                out.write(req("GET_PARAMETER", "rtsp://localhost/wfd1.0", 5, null).setHeader("Session", "S1").serialize());
                await.apply("keep-alive reply");
                out.write(req("SET_PARAMETER", "rtsp://localhost/wfd1.0", 6, "wfd_trigger_method: TEARDOWN\r\n").serialize());
                await.apply("M5 teardown reply");
                RtspMessage td = await.apply("TEARDOWN");
                out.write(resp(td.cseq(), 200).serialize());
                Thread.sleep(200);
            } catch (Throwable t) {
                srcError.set(String.valueOf(t));
            }
        }, "fake-source");
        source.start();

        WfdSession.Config cfg = new WfdSession.Config();
        cfg.rtpPort = rtpPort;
        SinkConnection conn = new SinkConnection("127.0.0.1", ctrl, clock);
        List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        WfdSession session = new WfdSession(cfg, "127.0.0.1", conn, new WfdSession.Listener() {
            @Override
            public void onFormats(WfdFormats.Video v, WfdFormats.Audio a) {
                events.add("formats " + v + " " + a);
            }

            @Override
            public void onPlaying() {
                events.add("playing");
            }

            @Override
            public void onClosed(String reason) {
                events.add("closed " + reason);
            }

            @Override
            public void onLog(String line) {
            }
        });
        conn.attach(session);
        // the sink asks for a key frame once media arrives (the test source waits for it)
        Thread idrAsker = new Thread(() -> {
            long end = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < end) {
                if (units.v.size() >= 2) {
                    conn.requestIdr();
                    return;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        idrAsker.start();
        String why = conn.run(3000, 1000);
        source.join(5000);
        idrAsker.join(1000);
        rx.close();
        rxThread.join(1000);
        server.close();
        eq(null, srcError.get(), "e2e: fake source script ran");
        eq("session closed", why, "e2e: run() ends with the session");
        check(events.contains("playing"), "e2e: reached PLAYING " + events);
        check(events.stream().anyMatch(e -> e.startsWith("formats 1920x1080p30")), "e2e: formats " + events);
        check(events.contains("closed teardown done"), "e2e: teardown done " + events);
        check(srcSaw.contains("SETUP") && srcSaw.contains("PLAY") && srcSaw.contains("TEARDOWN"), "e2e: source saw M6 M7 M8 " + srcSaw);
        check(srcSaw.contains("body:wfd_idr_request"), "e2e: IDR request reached the source " + srcSaw);
        eq(List.of("K5028", "P900"), units.v.subList(0, Math.min(2, units.v.size())), "e2e: video units over UDP");
        eq(1920, units.pcm, "e2e: LPCM over UDP");
    }
}
