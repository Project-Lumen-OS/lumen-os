// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WFD sink side of the RTSP control session (WFD 1.0 6.4, messages M1 to M16).
 *
 * The sink opens the TCP connection to the source's control port; the source then drives:
 * M1 OPTIONS (we answer and send M2 OPTIONS), M3 GET_PARAMETER (capabilities), M4 SET_PARAMETER
 * (chosen formats, presentation URL), M5 SET_PARAMETER wfd_trigger_method SETUP (we send M6 SETUP,
 * then M7 PLAY), M16 GET_PARAMETER keep-alives. TEARDOWN comes as a trigger (we send M8) or from us.
 * The sink asks for a key frame with M13 SET_PARAMETER wfd_idr_request.
 *
 * Transport agnostic and single threaded: the caller feeds parsed messages and the current time,
 * and sends what {@link Out#send} gets. Tested on the host against a scripted source.
 */
public final class WfdSession {
    public interface Out {
        void send(RtspMessage m);
    }

    public interface Listener {
        /** M4: formats chosen by the source (also on a later format change). Either may be null. */
        void onFormats(WfdFormats.Video video, WfdFormats.Audio audio);

        /** M7 answered 200: media flows to the RTP port. */
        void onPlaying();

        /** The session is over (source teardown, our teardown done, or a refused SETUP/PLAY). */
        void onClosed(String reason);

        void onLog(String line);
    }

    public static final class Config {
        public String friendlyName = "Lumen";
        public String manufacturer = "Lumen OS";
        public String model = "Projector";
        public String version = "1.0";
        public int rtpPort = 19000;
        public WfdFormats.Quality quality = WfdFormats.Quality.P1080_60;
        public boolean lpcm = true, aac = true;
    }

    public enum State { WAIT_M1, CAPABILITIES, SETUP_SENT, PLAY_SENT, PLAYING, PAUSED, TEARING_DOWN, CLOSED }

    public static final String WFD_TAG = "org.wfa.wfd1.0";
    public static final int MIN_IDR_INTERVAL_MS = 500;

    private final Config cfg;
    private final Out out;
    private final Listener listener;
    private final String sourceHost;
    private final Map<Integer, String> pending = new HashMap<>();
    private State state = State.WAIT_M1;
    private int nextCseq = 1;
    private String presentationUrl;
    private String sessionId;
    private int sessionTimeoutS = 60;
    private WfdFormats.Video video;
    private WfdFormats.Audio audio;
    private long lastSourceMs;
    private long lastIdrMs = Long.MIN_VALUE / 2;

    public WfdSession(Config cfg, String sourceHost, Out out, Listener listener) {
        this.cfg = cfg;
        this.sourceHost = sourceHost;
        this.out = out;
        this.listener = listener;
    }

    public State state() {
        return state;
    }

    public WfdFormats.Video video() {
        return video;
    }

    public WfdFormats.Audio audio() {
        return audio;
    }

    public String sessionId() {
        return sessionId;
    }

    public String presentationUrl() {
        return presentationUrl != null ? presentationUrl : "rtsp://" + sourceHost + "/wfd1.0/streamid=0";
    }

    /** Call once when the TCP connection is up (starts the keep-alive clock). */
    public void start(long nowMs) {
        lastSourceMs = nowMs;
    }

    /**
     * True when the source has been silent longer than its session timeout plus a margin: it sends a
     * keep-alive (M16) at least every timeout seconds; before SETUP the limit is the default 60 s.
     */
    public boolean timedOut(long nowMs) {
        if (state == State.CLOSED) return false;
        return nowMs - lastSourceMs > (sessionTimeoutS + 15) * 1000L;
    }

    public void onMessage(RtspMessage m, long nowMs) {
        lastSourceMs = nowMs;
        if (state == State.CLOSED) return;
        if (m.isRequest()) onRequest(m);
        else onResponse(m);
    }

    // ------------------------------------------------------------------ requests from the source

    private void onRequest(RtspMessage m) {
        switch (m.method) {
            case "OPTIONS": {   // M1
                send(m, 200, "OK", "Public", WFD_TAG + ", GET_PARAMETER, SET_PARAMETER");
                if (state == State.WAIT_M1) {
                    state = State.CAPABILITIES;
                    sendRequest("OPTIONS", "*", "M2").setHeader("Require", WFD_TAG);
                    flushLast();
                }
                return;
            }
            case "GET_PARAMETER": {   // M3 or M16 keep-alive
                List<String> names = WfdParams.names(m.bodyText());
                if (names.isEmpty()) {
                    send(m, 200, "OK");
                    return;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (String n : names) values.put(n, value(n));
                RtspMessage r = reply(m, 200, "OK");
                r.setTextBody("text/parameters", WfdParams.format(values));
                out.send(r);
                return;
            }
            case "SET_PARAMETER": {
                Map<String, String> p = WfdParams.parse(m.bodyText());
                boolean formats = false;
                if (p.containsKey("wfd_video_formats")) {
                    video = WfdFormats.parseVideo(p.get("wfd_video_formats"));
                    formats = true;
                }
                if (p.containsKey("wfd_audio_codecs")) {
                    audio = WfdFormats.parseAudio(p.get("wfd_audio_codecs"));
                    formats = true;
                }
                String url = p.get("wfd_presentation_URL");
                if (url != null) {
                    String first = url.trim().split("\\s+")[0];
                    if (first.startsWith("rtsp://")) presentationUrl = first;
                }
                send(m, 200, "OK");
                if (formats) {
                    log("M4 video=" + video + " audio=" + audio + " url=" + presentationUrl());
                    listener.onFormats(video, audio);
                }
                String trigger = p.get("wfd_trigger_method");
                if (trigger != null) onTrigger(trigger.trim().toUpperCase(Locale.ROOT));
                return;
            }
            case "TEARDOWN":
                send(m, 200, "OK");
                close("source teardown");
                return;
            case "PLAY":
            case "PAUSE":
                send(m, 200, "OK");
                return;
            default:
                send(m, 501, "Not Implemented");
        }
    }

    private void onTrigger(String t) {
        log("trigger " + t);
        switch (t) {
            case "SETUP":
                if (state == State.CAPABILITIES || state == State.WAIT_M1) {
                    state = State.SETUP_SENT;
                    sendRequest("SETUP", presentationUrl(), "SETUP")
                            .setHeader("Transport", "RTP/AVP/UDP;unicast;client_port=" + cfg.rtpPort + "-" + (cfg.rtpPort + 1));
                    flushLast();
                }
                return;
            case "PLAY":
                if (sessionId != null) {
                    state = State.PLAY_SENT;
                    withSession(sendRequest("PLAY", presentationUrl(), "PLAY"));
                    flushLast();
                }
                return;
            case "PAUSE":
                if (sessionId != null) {
                    withSession(sendRequest("PAUSE", presentationUrl(), "PAUSE"));
                    flushLast();
                }
                return;
            case "TEARDOWN":
                teardown();
                return;
            default:
                log("unknown trigger " + t);
        }
    }

    /** Value for one M3 parameter; "none" for anything we do not support. */
    String value(String name) {
        switch (name) {
            case "wfd_video_formats": return WfdFormats.sinkVideoFormats(cfg.quality);
            case "wfd_audio_codecs": return WfdFormats.sinkAudioCodecs(cfg.lpcm, cfg.aac);
            case "wfd_client_rtp_ports": return "RTP/AVP/UDP;unicast " + cfg.rtpPort + " 0 mode=play";
            case "wfd_connector_type": return "05";
            case "intel_friendly_name": return cfg.friendlyName;
            case "intel_sink_manufacturer_name": return cfg.manufacturer;
            case "intel_sink_model_name": return cfg.model;
            case "intel_sink_version": return cfg.version;
            default: return "none";   // content protection, EDID, UIBC, coupled sink, 3D, standby, vendor keys
        }
    }

    // ------------------------------------------------------------------ responses to our requests

    private void onResponse(RtspMessage m) {
        String what = pending.remove(m.cseq());
        if (what == null) {
            log("response without request: " + m);
            return;
        }
        boolean ok = m.status == 200;
        switch (what) {
            case "M2":
                if (!ok) log("M2 refused: " + m.status);
                return;
            case "SETUP": {
                if (!ok) {
                    close("SETUP refused " + m.status);
                    return;
                }
                parseSession(m.header("Session"));
                if (sessionId == null) {
                    close("SETUP without Session");
                    return;
                }
                log("SETUP ok session=" + sessionId + " timeout=" + sessionTimeoutS + " transport=" + m.header("Transport"));
                state = State.PLAY_SENT;
                withSession(sendRequest("PLAY", presentationUrl(), "PLAY"));
                flushLast();
                return;
            }
            case "PLAY":
                if (!ok) {
                    close("PLAY refused " + m.status);
                    return;
                }
                state = State.PLAYING;
                listener.onPlaying();
                return;
            case "PAUSE":
                if (ok) state = State.PAUSED;
                return;
            case "TEARDOWN":
                close("teardown done");
                return;
            case "IDR":
            default:
                if (!ok) log(what + " refused: " + m.status);
        }
    }

    void parseSession(String h) {
        if (h == null) return;
        String[] parts = h.split(";");
        String id = parts[0].trim();
        sessionId = id.isEmpty() ? null : id;
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.toLowerCase(Locale.ROOT).startsWith("timeout=")) {
                try {
                    int t = Integer.parseInt(p.substring(8).trim());
                    if (t >= 10 && t <= 600) sessionTimeoutS = t;
                } catch (NumberFormatException ignored) {
                    // keep the default
                }
            }
        }
    }

    // ------------------------------------------------------------------ sink initiated

    /** M13: ask the source for a key frame. Rate limited; true when a request went out. */
    public boolean requestIdr(long nowMs) {
        if (state != State.PLAYING || sessionId == null) return false;
        if (nowMs - lastIdrMs < MIN_IDR_INTERVAL_MS) return false;
        lastIdrMs = nowMs;
        RtspMessage r = withSession(sendRequest("SET_PARAMETER", presentationUrl(), "IDR"));
        r.setTextBody("text/parameters", "wfd_idr_request\r\n");
        flushLast();
        return true;
    }

    /** M8: end the session from our side (BACK on the remote, disabled, standby). */
    public void teardown() {
        if (state == State.CLOSED || state == State.TEARING_DOWN) return;
        if (sessionId == null) {
            close("teardown before setup");
            return;
        }
        state = State.TEARING_DOWN;
        withSession(sendRequest("TEARDOWN", presentationUrl(), "TEARDOWN"));
        flushLast();
    }

    // ------------------------------------------------------------------ helpers

    private RtspMessage lastRequest;

    private RtspMessage sendRequest(String method, String uri, String what) {
        int c = nextCseq++;
        RtspMessage r = RtspMessage.request(method, uri).setHeader("CSeq", String.valueOf(c));
        pending.put(c, what);
        lastRequest = r;
        return r;
    }

    /** Requests are built, then headers/bodies added, then sent here (keeps the call sites short). */
    private void flushLast() {
        if (lastRequest != null) {
            out.send(lastRequest);
            lastRequest = null;
        }
    }

    private RtspMessage withSession(RtspMessage r) {
        if (sessionId != null) r.setHeader("Session", sessionId);
        return r;
    }

    private static RtspMessage reply(RtspMessage req, int status, String reason) {
        RtspMessage r = RtspMessage.response(status, reason);
        String c = req.header("CSeq");
        if (c != null) r.setHeader("CSeq", c);
        return r;
    }

    private void send(RtspMessage req, int status, String reason, String... headerPairs) {
        RtspMessage r = reply(req, status, reason);
        for (int i = 0; i + 1 < headerPairs.length; i += 2) r.setHeader(headerPairs[i], headerPairs[i + 1]);
        out.send(r);
    }

    private void close(String reason) {
        if (state == State.CLOSED) return;
        state = State.CLOSED;
        log("closed: " + reason);
        listener.onClosed(reason);
    }

    private void log(String s) {
        listener.onLog(s);
    }
}
