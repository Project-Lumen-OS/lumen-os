// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One RTSP/1.0 message (request or response) as used by Wi-Fi Display (WFD 1.0 section 6).
 * Plain Java, no android.* imports: the RTSP/WFD core is tested on the host (test/run_core.sh).
 */
public final class RtspMessage {
    public static final String VERSION = "RTSP/1.0";

    /** Request: method + uri. Response: status + reason (method/uri null). */
    public final String method;
    public final String uri;
    public final int status;
    public final String reason;
    private final List<String[]> headers = new ArrayList<>();
    private byte[] body = new byte[0];

    private RtspMessage(String method, String uri, int status, String reason) {
        this.method = method;
        this.uri = uri;
        this.status = status;
        this.reason = reason;
    }

    public static RtspMessage request(String method, String uri) {
        return new RtspMessage(method, uri, 0, null);
    }

    public static RtspMessage response(int status, String reason) {
        return new RtspMessage(null, null, status, reason);
    }

    public boolean isRequest() {
        return method != null;
    }

    /** Case-insensitive lookup; first match; null when absent. Value trimmed. */
    public String header(String name) {
        for (String[] h : headers) {
            if (h[0].equalsIgnoreCase(name)) return h[1];
        }
        return null;
    }

    public RtspMessage setHeader(String name, String value) {
        for (String[] h : headers) {
            if (h[0].equalsIgnoreCase(name)) {
                h[1] = value;
                return this;
            }
        }
        headers.add(new String[] {name, value});
        return this;
    }

    public List<String[]> headers() {
        return headers;
    }

    /** CSeq header as int, -1 when absent or not a number. */
    public int cseq() {
        String v = header("CSeq");
        if (v == null) return -1;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public byte[] body() {
        return body;
    }

    public String bodyText() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public RtspMessage setBody(byte[] b) {
        body = b == null ? new byte[0] : b;
        return this;
    }

    /** Text body (UTF-8) with Content-Type; Content-Length is written by {@link #serialize()}. */
    public RtspMessage setTextBody(String contentType, String text) {
        setBody(text.getBytes(StandardCharsets.UTF_8));
        if (contentType != null) setHeader("Content-Type", contentType);
        return this;
    }

    /** Wire bytes: start line, headers (Content-Length forced to the body size), CRLF, body. */
    public byte[] serialize() {
        StringBuilder sb = new StringBuilder(256);
        if (isRequest()) {
            sb.append(method).append(' ').append(uri).append(' ').append(VERSION).append("\r\n");
        } else {
            sb.append(VERSION).append(' ').append(status).append(' ').append(reason).append("\r\n");
        }
        for (String[] h : headers) {
            if (h[0].equalsIgnoreCase("Content-Length")) continue;
            sb.append(h[0]).append(": ").append(h[1]).append("\r\n");
        }
        if (body.length > 0) sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("\r\n");
        byte[] head = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[head.length + body.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(body, 0, out, head.length, body.length);
        return out;
    }

    /** Short one-line form for logs (no body). */
    @Override
    public String toString() {
        String start = isRequest() ? method + " " + uri : status + " " + reason;
        return String.format(Locale.ROOT, "%s cseq=%d len=%d", start, cseq(), body.length);
    }
}
