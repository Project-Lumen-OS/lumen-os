// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The sink's RTSP side against the message sequences real senders use, fed as raw wire text through
 * the parser (as on the socket), with every sink reply serialized and parsed back.
 *
 * Sources of the transcripts (no packet capture of our own exists yet):
 *  - Android: AOSP frameworks/av media/libstagefright/wifi-display/source/WifiDisplaySource.cpp
 *    (LineageOS 21 = Android 14; the Miracast source in AOSP based phones such as Xiaomi HyperOS
 *    "Cast"): sendM1/M3/M4/M16, AppendCommonResponse (Date, Server, CSeq, Session;timeout=30),
 *    onSetupRequest's Transport reply. The sink reply is checked with the same parsing rules as
 *    WifiDisplaySource::onReceiveM3Response and VideoFormats::parseFormatSpec (fixed 58 + 2 char
 *    codec entries, client RTP port1 must be 0).
 *  - Windows 10 / 11: [MS-WFDPE] v20260810 examples (M2 with Server: MSMiracastSource, M3 requests)
 *    plus every extension parameter MS-WFDPE defines, each with its ABNF checked on our reply.
 * Addresses are RFC 5737 documentation addresses.
 */
public final class SenderTest {
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

    public static void main(String[] a) throws Exception {
        aosp();
        windows();
        windowsTeardownRequest();
        grammarEveryQuality();
        System.out.println("miracast senders: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ harness

    /** Records what the sink sends (re-parsed from its wire bytes) and what it tells its listener. */
    static final class Sink implements WfdSession.Out, WfdSession.Listener {
        final List<RtspMessage> sent = new ArrayList<>();
        final List<String> steps = new ArrayList<>();
        final RtspParser back = new RtspParser();
        WfdFormats.Video video;
        WfdFormats.Audio audio;
        boolean playing;
        String closed;

        @Override
        public void send(RtspMessage m) {
            byte[] w = m.serialize();
            try {
                sent.addAll(back.feed(w, 0, w.length));
            } catch (RtspParser.ProtocolException e) {
                failed++;
                System.out.println("FAIL sink sent unparsable bytes: " + e.getMessage());
            }
        }

        RtspMessage last() {
            return sent.get(sent.size() - 1);
        }

        @Override
        public void onFormats(WfdFormats.Video v, WfdFormats.Audio au) {
            video = v;
            audio = au;
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

        @Override
        public void onStep(String step) {
            steps.add(step);
        }
    }

    /** Feeds raw source text (one or more messages) to the session through a fresh parser chunk. */
    static void feed(RtspParser p, WfdSession s, String wire, long now) throws Exception {
        byte[] b = wire.getBytes(StandardCharsets.UTF_8);
        for (RtspMessage m : p.feed(b, 0, b.length)) s.onMessage(m, now);
    }

    static String body(String start, String headers, String body) {
        return start + "\r\n" + headers + "Content-Type: text/parameters\r\nContent-Length: "
                + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body;
    }

    static WfdSession.Config cfg(WfdQuality q) {
        WfdSession.Config c = new WfdSession.Config();
        c.friendlyName = "Lumen Z9X";
        c.model = "Z9X";
        c.version = "1.0.11";
        c.rtpPort = 19000;
        c.quality = q;
        return c;
    }

    // ------------------------------------------------------------------ AOSP WifiDisplaySource

    static final String AOSP_COMMON = "Date: Thu, 12 Nov 2026 11:20:00 +0000\r\nServer: stagefright/1.2 (Linux;Android 14)\r\n";

    static void aosp() throws Exception {
        Sink k = new Sink();
        WfdSession s = new WfdSession(cfg(WfdQuality.P1080_60), "198.51.100.20", k, k);
        RtspParser p = new RtspParser();
        s.start(0);
        // M1 (sendM1)
        feed(p, s, "OPTIONS * RTSP/1.0\r\n" + AOSP_COMMON + "CSeq: 1\r\nRequire: org.wfa.wfd1.0\r\n\r\n", 10);
        eq(2, k.sent.size(), "aosp: M1 reply + M2");
        eq(200, k.sent.get(0).status, "aosp: M1 200");
        eq(1, k.sent.get(0).cseq(), "aosp: M1 CSeq echoed");
        RtspMessage m2 = k.sent.get(1);
        eq("OPTIONS", m2.method, "aosp: M2");
        eq("*", m2.uri, "aosp: M2 uri");
        check(k.steps.get(0).contains("stagefright"), "aosp: M1 step names the sender stack " + k.steps);
        // M2 reply (onOptionsRequest is the other way; the source answers our M2 like this)
        feed(p, s, "RTSP/1.0 200 OK\r\n" + AOSP_COMMON + "CSeq: " + m2.cseq()
                + "\r\nPublic: org.wfa.wfd1.0, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER\r\n\r\n", 20);
        // M3 (sendM3: exactly these four)
        feed(p, s, body("GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", AOSP_COMMON + "CSeq: 2\r\n",
                "wfd_content_protection\r\nwfd_video_formats\r\nwfd_audio_codecs\r\nwfd_client_rtp_ports\r\n"), 30);
        RtspMessage m3 = k.last();
        eq(200, m3.status, "aosp: M3 200");
        eq(2, m3.cseq(), "aosp: M3 CSeq");
        Map<String, String> v = WfdParams.parse(m3.bodyText());
        eq(4, v.size(), "aosp: M3 answers all four");
        check(aospRtpPortsOk(v.get("wfd_client_rtp_ports")), "aosp: onReceiveM3Response accepts rtp ports " + v.get("wfd_client_rtp_ports"));
        check(aospParseFormatSpec(v.get("wfd_video_formats")), "aosp: VideoFormats::parseFormatSpec accepts " + v.get("wfd_video_formats"));
        eq("none", v.get("wfd_content_protection"), "aosp: no HDCP (source then streams unprotected)");
        check(aospAudioModes(v.get("wfd_audio_codecs"), "AAC") % 2 == 1, "aosp: AAC 2ch 48 kHz offered");
        check((aospAudioModes(v.get("wfd_audio_codecs"), "LPCM") & 2) != 0, "aosp: LPCM 2ch 48 kHz offered");
        long cea = Long.parseLong(v.get("wfd_video_formats").split(" ")[4], 16);
        check((cea & (1L << 5)) != 0, "aosp: 1280x720p30 (the AOSP source default) offered");
        // M4 (sendM4: chosen format with native 00, AAC, presentation URL, the rtp ports echoed)
        feed(p, s, body("SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", AOSP_COMMON + "CSeq: 3\r\n",
                "wfd_video_formats: 00 00 02 02 00000020 00000000 00000000 00 0000 0000 00 none none\r\n"
                        + "wfd_audio_codecs: AAC 00000001 00\r\n"
                        + "wfd_presentation_URL: rtsp://198.51.100.20/wfd1.0/streamid=0 none\r\n"
                        + "wfd_client_rtp_ports: RTP/AVP/UDP;unicast 19000 0 mode=play\r\n"), 40);
        eq(200, k.last().status, "aosp: M4 200");
        check(k.video != null && k.video.width == 1280 && k.video.height == 720 && k.video.fps == 30, "aosp: M4 720p30 " + k.video);
        check(k.audio != null && k.audio.codec == WfdFormats.Audio.AAC, "aosp: M4 AAC");
        // M5 trigger SETUP (sendTrigger)
        int n = k.sent.size();
        feed(p, s, body("SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", AOSP_COMMON + "CSeq: 4\r\n",
                "wfd_trigger_method: SETUP\r\n"), 50);
        eq(n + 2, k.sent.size(), "aosp: M5 reply + M6");
        RtspMessage m6 = k.last();
        eq("SETUP", m6.method, "aosp: M6 SETUP");
        eq("rtsp://198.51.100.20/wfd1.0/streamid=0", m6.uri, "aosp: M6 to the presentation URL");
        String tr = m6.header("Transport");
        check(tr != null && tr.startsWith("RTP/AVP/UDP;unicast;") && tr.contains("client_port=19000-19001"),
                "aosp: onSetupRequest accepts the Transport " + tr);
        // SETUP reply (onSetupRequest + AppendCommonResponse with the playback session)
        feed(p, s, "RTSP/1.0 200 OK\r\n" + AOSP_COMMON + "CSeq: " + m6.cseq() + "\r\nSession: 1804289383;timeout=30\r\n"
                + "Transport: RTP/AVP/UDP;unicast;client_port=19000-19001;server_port=15550-15551\r\n\r\n", 60);
        RtspMessage m7 = k.last();
        eq("PLAY", m7.method, "aosp: M7 PLAY");
        eq("1804289383", m7.header("Session"), "aosp: M7 Session (findPlaybackSession reads the number)");
        eq("1804289383", s.sessionId(), "aosp: session id without timeout");
        feed(p, s, "RTSP/1.0 200 OK\r\n" + AOSP_COMMON + "CSeq: " + m7.cseq() + "\r\nSession: 1804289383;timeout=30\r\n\r\n", 70);
        check(k.playing, "aosp: playing after PLAY 200");
        eq(WfdState.PLAYING, s.state(), "aosp: state PLAYING");
        // M16 keep-alive (sendM16)
        feed(p, s, "GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\n" + AOSP_COMMON + "CSeq: 5\r\nSession: 1804289383\r\n\r\n", 25_000);
        eq(200, k.last().status, "aosp: M16 200");
        eq(5, k.last().cseq(), "aosp: M16 CSeq");
        check(!s.timedOut(25_000 + 40_000), "aosp: keep-alive resets the timeout");
        check(s.timedOut(25_000 + 46_000), "aosp: silent past timeout 30 s + margin");
        // M13 IDR (onSetParameterRequest needs the Session header and wfd_idr_request\r\n)
        check(s.requestIdr(26_000), "aosp: IDR request sent");
        RtspMessage idr = k.last();
        eq("SET_PARAMETER", idr.method, "aosp: IDR is SET_PARAMETER");
        eq("1804289383", idr.header("Session"), "aosp: IDR carries Session");
        check(idr.bodyText().contains("wfd_idr_request\r\n"), "aosp: IDR body");
        // M5 trigger TEARDOWN -> M8
        feed(p, s, body("SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", AOSP_COMMON + "CSeq: 6\r\n",
                "wfd_trigger_method: TEARDOWN\r\n"), 27_000);
        RtspMessage m8 = k.last();
        eq("TEARDOWN", m8.method, "aosp: M8 TEARDOWN");
        eq("1804289383", m8.header("Session"), "aosp: M8 Session");
        feed(p, s, "RTSP/1.0 200 OK\r\n" + AOSP_COMMON + "CSeq: " + m8.cseq() + "\r\n\r\n", 27_100);
        eq(WfdState.CLOSED, s.state(), "aosp: closed after M8 200");
        eq("teardown done", k.closed, "aosp: close reason");
        eq(0, p.pending(), "aosp: nothing left in the parser");
        check(k.steps.contains("PLAY ok") && k.steps.contains("SETUP ok, M7 PLAY sent"), "aosp: steps " + k.steps);
    }

    /** WifiDisplaySource::onReceiveM3Response: sscanf "RTP/AVP/UDP;unicast %u %u mode=play", port0 1..65535, port1 0. */
    static boolean aospRtpPortsOk(String v) {
        if (v == null) return false;
        java.util.regex.Matcher m = Pattern.compile("RTP/AVP/UDP;unicast (\\d+) (\\d+) mode=play").matcher(v);
        if (!m.matches()) return false;
        long p0 = Long.parseLong(m.group(1)), p1 = Long.parseLong(m.group(2));
        return p0 > 0 && p0 <= 65535 && p1 == 0;
    }

    /** VideoFormats::parseFormatSpec + parseH264Codec: "%02x %02x " then 58-char codec entries 60 apart. */
    static boolean aospParseFormatSpec(String spec) {
        if (spec == null || !Pattern.compile("[0-9a-fA-F]{2} [0-9a-fA-F]{2} .*").matcher(spec).matches()) return false;
        int size = spec.length(), offset = 6;
        if (offset + 58 > size) return false;   // CHECK_LE(offset + 58, size): the source aborts
        Pattern codec = Pattern.compile("[0-9a-fA-F]{2} [0-9a-fA-F]{2} [0-9a-fA-F]{8} [0-9a-fA-F]{8} [0-9a-fA-F]{8} .*");
        int entries = 0;
        while (offset < size) {
            if (!codec.matcher(spec.substring(offset)).matches()) return false;   // the entry would be skipped
            entries++;
            offset += 60;
        }
        return entries > 0 && spec.length() == 6 + entries * 58 + (entries - 1) * 2;
    }

    /** WifiDisplaySource::GetAudioModes: the hex modes after "<codec> ", 0 when absent. */
    static long aospAudioModes(String v, String codec) {
        for (String e : v.split(",")) {
            String[] t = e.trim().split(" ");
            if (t.length >= 2 && t[0].equals(codec)) return Long.parseLong(t[1], 16);
        }
        return 0;
    }

    // ------------------------------------------------------------------ Windows (MS-WFDPE)

    static final String WIN_SERVER = "Server: MSMiracastSource/10.00.10011.0000 guid/be113d06-9e40-43e4-98e6-540a325e9ced\r\n";

    /** Every parameter MS-WFDPE defines for the M3 request, plus the WFD R1/R2 names Windows asks. */
    static final String WIN_M3 = "wfd_video_formats\r\nwfd_audio_codecs\r\nwfd_3d_video_formats\r\nwfd_content_protection\r\n"
            + "wfd_display_edid\r\nwfd_coupled_sink\r\nwfd_client_rtp_ports\r\nwfd_uibc_capability\r\n"
            + "wfd_standby_resume_capability\r\nwfd_connector_type\r\nwfd_idr_request_capability\r\n"
            + "intel_friendly_name\r\nintel_sink_manufacturer_name\r\nintel_sink_model_name\r\nintel_sink_device_URL\r\n"
            + "intel_sink_version\r\nmicrosoft_cursor\r\nmicrosoft_rtcp_capability\r\nmicrosoft_latency_management_capability\r\n"
            + "microsoft_format_change_capability\r\nmicrosoft_diagnostics_capability\r\nmicrosoft_video_formats\r\n"
            + "microsoft_custom_video_formats\r\nmicrosoft_max_bitrate\r\nmicrosoft_color_space_conversion\r\n"
            + "microsoft_multiscreen_projection\r\nmicrosoft_audio_mute\r\nwfdx_video_formats\r\n";

    static void windows() throws Exception {
        Sink k = new Sink();
        WfdSession s = new WfdSession(cfg(WfdQuality.P1080_60), "198.51.100.7", k, k);
        RtspParser p = new RtspParser();
        s.start(0);
        feed(p, s, "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nRequire: org.wfa.wfd1.0\r\n\r\n", 10);
        RtspMessage m2 = k.last();
        eq("OPTIONS", m2.method, "windows: M2 after M1");
        eq("org.wfa.wfd1.0", m2.header("Require"), "windows: M2 Require");
        // MS-WFDPE 3.x example: M2 response with Date, Public, Server
        feed(p, s, "RTSP/1.0 200 OK\r\nCSeq: " + m2.cseq() + "\r\nDate: Sun, Aug 21 2011 04:20:53 GMT\r\n"
                + "Public: org.wfa.wfd1.0, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER\r\n" + WIN_SERVER + "\r\n", 20);
        feed(p, s, body("GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", "CSeq: 2\r\n", WIN_M3), 30);
        RtspMessage m3 = k.last();
        eq(200, m3.status, "windows: M3 200");
        eq(2, m3.cseq(), "windows: M3 CSeq");
        Map<String, String> v = WfdParams.parse(m3.bodyText());
        for (Map.Entry<String, String> e : v.entrySet()) {
            check(validValue(e.getKey(), e.getValue()), "windows: M3 value fits its grammar: " + e.getKey() + ": " + e.getValue());
        }
        for (String n : new String[] {"microsoft_video_formats", "microsoft_max_bitrate", "wfdx_video_formats", "microsoft_custom_video_formats"}) {
            check(!v.containsKey(n), "windows: " + n + " left out (a \"none\" there is malformed or hides wfd_video_formats)");
        }
        for (String n : new String[] {"wfd_video_formats", "wfd_audio_codecs", "wfd_client_rtp_ports", "wfd_content_protection",
                "wfd_display_edid", "wfd_coupled_sink", "wfd_uibc_capability", "wfd_standby_resume_capability",
                "wfd_connector_type", "wfd_3d_video_formats", "wfd_idr_request_capability", "intel_friendly_name"}) {
            check(v.containsKey(n), "windows: " + n + " answered");
        }
        eq("1", v.get("wfd_idr_request_capability"), "windows: IDR requests supported");
        eq("Lumen Z9X", v.get("intel_friendly_name"), "windows: friendly name");
        check(k.steps.stream().anyMatch(x -> x.startsWith("M3 answered") && x.contains("microsoft_max_bitrate")),
                "windows: the step lists what was left out " + k.steps);
        // M4: 1080p30 CHP, LPCM 48 kHz (what Windows picks from our list on a 1080p desktop)
        feed(p, s, body("SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", "CSeq: 3\r\n",
                "wfd_video_formats: 00 00 02 10 00000080 00000000 00000000 00 0000 0000 00 none none\r\n"
                        + "wfd_audio_codecs: LPCM 00000002 00\r\n"
                        + "wfd_presentation_URL: rtsp://198.51.100.7/wfd1.0/streamid=0 none\r\n"
                        + "wfd_client_rtp_ports: RTP/AVP/UDP;unicast 19000 0 mode=play\r\n"), 40);
        eq(200, k.last().status, "windows: M4 200");
        check(k.video != null && k.video.width == 1920 && k.video.height == 1080 && k.video.fps == 30, "windows: M4 1080p30 " + k.video);
        check(k.audio != null && k.audio.codec == WfdFormats.Audio.LPCM && k.audio.sampleRate == 48000, "windows: M4 LPCM 48k " + k.audio);
        feed(p, s, body("SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", "CSeq: 4\r\n", "wfd_trigger_method: SETUP\r\n"), 50);
        RtspMessage m6 = k.last();
        eq("SETUP", m6.method, "windows: M6 SETUP");
        eq("rtsp://198.51.100.7/wfd1.0/streamid=0", m6.uri, "windows: M6 uri");
        // microsoft_rtcp_capability left out = none: exactly one server port (MS-WFDPE 2.8.1.1)
        feed(p, s, "RTSP/1.0 200 OK\r\nCSeq: " + m6.cseq() + "\r\nSession: 6B8B4567;timeout=30\r\n"
                + "Transport: RTP/AVP/UDP;unicast;client_port=19000;server_port=50000\r\n" + WIN_SERVER + "\r\n", 60);
        RtspMessage m7 = k.last();
        eq("PLAY", m7.method, "windows: M7 PLAY");
        eq("6B8B4567", m7.header("Session"), "windows: M7 Session");
        feed(p, s, "RTSP/1.0 200 OK\r\nCSeq: " + m7.cseq() + "\r\nSession: 6B8B4567;timeout=30\r\nRange: npt=now-\r\n\r\n", 70);
        check(k.playing, "windows: playing");
        // keep-alive, then a format change M4 while playing (a new desktop resolution)
        feed(p, s, "GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\nCSeq: 5\r\nSession: 6B8B4567\r\n\r\n", 20_000);
        eq(200, k.last().status, "windows: keep-alive 200");
        check(k.steps.contains("M16 keep-alive answered"), "windows: keep-alive step");
        feed(p, s, body("SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", "CSeq: 6\r\nSession: 6B8B4567\r\n",
                "wfd_video_formats: 00 00 02 10 00000040 00000000 00000000 00 0000 0000 00 none none\r\n"), 21_000);
        eq(200, k.last().status, "windows: format change 200");
        check(k.video != null && k.video.height == 720 && k.video.fps == 60, "windows: format change to 720p60 " + k.video);
        eq(WfdState.PLAYING, s.state(), "windows: still playing after the format change");
        // M5 trigger TEARDOWN
        feed(p, s, body("SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", "CSeq: 7\r\nSession: 6B8B4567\r\n",
                "wfd_trigger_method: TEARDOWN\r\n"), 22_000);
        RtspMessage m8 = k.last();
        eq("TEARDOWN", m8.method, "windows: M8");
        feed(p, s, "RTSP/1.0 200 OK\r\nCSeq: " + m8.cseq() + "\r\nSession: 6B8B4567\r\n\r\n", 22_100);
        eq(WfdState.CLOSED, s.state(), "windows: closed");
    }

    /** A source that sends TEARDOWN itself (not a trigger) ends the session at once. */
    static void windowsTeardownRequest() throws Exception {
        Sink k = new Sink();
        WfdSession s = new WfdSession(cfg(WfdQuality.P1080_30), "198.51.100.7", k, k);
        RtspParser p = new RtspParser();
        s.start(0);
        feed(p, s, "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nRequire: org.wfa.wfd1.0\r\n\r\n", 10);
        feed(p, s, "TEARDOWN rtsp://198.51.100.7/wfd1.0/streamid=0 RTSP/1.0\r\nCSeq: 2\r\n\r\n", 20);
        eq(200, k.last().status, "teardown request: 200");
        eq(WfdState.CLOSED, s.state(), "teardown request: closed");
        eq("source teardown", k.closed, "teardown request: reason");
    }

    /** Our M3 reply for every quality setting passes the AOSP parser and the WFD / MS-WFDPE grammar. */
    static void grammarEveryQuality() throws Exception {
        for (WfdQuality q : WfdQuality.values()) {
            Sink k = new Sink();
            WfdSession s = new WfdSession(cfg(q), "198.51.100.7", k, k);
            RtspParser p = new RtspParser();
            feed(p, s, "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nRequire: org.wfa.wfd1.0\r\n\r\n", 10);
            feed(p, s, body("GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", "CSeq: 2\r\n", WIN_M3), 20);
            Map<String, String> v = WfdParams.parse(k.last().bodyText());
            check(aospParseFormatSpec(v.get("wfd_video_formats")), "grammar " + q + ": AOSP video parse");
            for (Map.Entry<String, String> e : v.entrySet()) {
                check(validValue(e.getKey(), e.getValue()), "grammar " + q + ": " + e.getKey() + ": " + e.getValue());
            }
        }
    }

    static final String HEX = "[0-9a-fA-F]";

    /** WFD 1.0 6.1 and MS-WFDPE 2.x value grammars for what a sink may send in an M3 reply. */
    static boolean validValue(String name, String value) {
        String codec = HEX + "{2} " + HEX + "{2} " + HEX + "{8} " + HEX + "{8} " + HEX + "{8} " + HEX + "{2} " + HEX + "{4} "
                + HEX + "{4} " + HEX + "{2} (none|" + HEX + "{4}) (none|" + HEX + "{4})";
        String audio = "(LPCM|AAC|AC3) " + HEX + "{8} " + HEX + "{2}";
        Map<String, String> g = Map.ofEntries(
                Map.entry("wfd_video_formats", "none|" + HEX + "{2} " + HEX + "{2} " + codec + "(, " + codec + ")*"),
                Map.entry("wfd_audio_codecs", "none|" + audio + "(, " + audio + ")*"),
                Map.entry("wfd_client_rtp_ports", "RTP/AVP/(UDP|TCP);unicast \\d{1,5} \\d{1,5} mode=play"),
                Map.entry("wfd_connector_type", "none|" + HEX + "{2}"),
                Map.entry("wfd_idr_request_capability", "0|1"),
                Map.entry("wfd_content_protection", "none|HDCP2\\.[0-9] port=\\d+"),
                Map.entry("wfd_display_edid", "none|" + HEX + "{4} " + HEX + "+"),
                Map.entry("wfd_coupled_sink", "none|" + HEX + "{2} (none|" + HEX + "{12})"),
                Map.entry("wfd_uibc_capability", "none|input_category_list=.*"),
                Map.entry("wfd_standby_resume_capability", "none|supported"),
                Map.entry("wfd_3d_video_formats", "none|.+"),
                Map.entry("wfd_I2C", "none|\\d+"),
                Map.entry("microsoft_video_formats", HEX + "{12}"),
                Map.entry("microsoft_max_bitrate", "\\d{1,10}"),
                Map.entry("microsoft_custom_video_formats", "none|.+"),
                Map.entry("microsoft_rtcp_capability", "supported|none"),
                Map.entry("microsoft_latency_management_capability", "supported|none|low|normal|high"),
                Map.entry("microsoft_format_change_capability", "supported|none"),
                Map.entry("microsoft_diagnostics_capability", "supported|none"),
                Map.entry("microsoft_color_space_conversion", "supported|none"),
                Map.entry("microsoft_multiscreen_projection", "supported|none"),
                Map.entry("microsoft_audio_mute", "supported|none"));
        if (name.startsWith("intel_")) return !value.isEmpty() && !value.equals("none");
        String re = g.get(name);
        if (re == null) {
            System.out.println("  (no grammar for " + name.toLowerCase(Locale.ROOT) + ")");
            return false;
        }
        return Pattern.compile(re).matcher(value).matches();
    }
}
