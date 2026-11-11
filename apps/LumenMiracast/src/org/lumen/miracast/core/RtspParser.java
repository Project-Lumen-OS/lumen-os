// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Incremental RTSP reader for the WFD control connection. Bytes arrive in arbitrary chunks from the
 * TCP socket; {@link #feed} returns every message completed so far. Tolerates bare LF line ends,
 * skips RTSP interleaved binary frames ('$' channel len16 data) and refuses oversized headers or
 * bodies (a broken or hostile peer must not make the receiver allocate without bound).
 */
public final class RtspParser {
    public static final int MAX_HEADER = 16 * 1024;
    public static final int MAX_BODY = 256 * 1024;

    /** Thrown for input that is not RTSP; the caller drops the connection. */
    public static final class ProtocolException extends Exception {
        private static final long serialVersionUID = 1L;

        public ProtocolException(String m) {
            super(m);
        }
    }

    private byte[] buf = new byte[4096];
    private int len;

    public List<RtspMessage> feed(byte[] data, int off, int n) throws ProtocolException {
        ensure(len + n);
        System.arraycopy(data, off, buf, len, n);
        len += n;
        List<RtspMessage> out = new ArrayList<>();
        while (true) {
            RtspMessage m = next();
            if (m == null) break;
            out.add(m);
        }
        return out;
    }

    private void ensure(int cap) {
        if (cap <= buf.length) return;
        int nc = buf.length;
        while (nc < cap) nc *= 2;
        byte[] nb = new byte[nc];
        System.arraycopy(buf, 0, nb, 0, len);
        buf = nb;
    }

    private void consume(int n) {
        System.arraycopy(buf, n, buf, 0, len - n);
        len -= n;
    }

    private RtspMessage next() throws ProtocolException {
        // skip blank lines between messages
        int start = 0;
        while (start < len && (buf[start] == '\r' || buf[start] == '\n')) start++;
        if (start > 0) consume(start);
        if (len == 0) return null;
        if (buf[0] == '$') { // interleaved binary data: '$' channel(1) length(2)
            if (len < 4) return null;
            int l = ((buf[2] & 0xff) << 8) | (buf[3] & 0xff);
            if (len < 4 + l) return null;
            consume(4 + l);
            return next();
        }
        int[] end = findHeaderEnd();
        if (end == null) {
            if (len > MAX_HEADER) throw new ProtocolException("header too long");
            return null;
        }
        if (end[0] > MAX_HEADER) throw new ProtocolException("header too long");
        String head = new String(buf, 0, end[0], StandardCharsets.UTF_8);
        int bodyStart = end[1];
        String[] lines = head.split("\r?\n");
        RtspMessage m = startLine(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i];
            int c = l.indexOf(':');
            if (c <= 0) continue;
            m.setHeader(l.substring(0, c).trim(), l.substring(c + 1).trim());
        }
        int bodyLen = 0;
        String cl = m.header("Content-Length");
        if (cl != null) {
            try {
                bodyLen = Integer.parseInt(cl.trim());
            } catch (NumberFormatException e) {
                throw new ProtocolException("bad Content-Length " + cl);
            }
            if (bodyLen < 0 || bodyLen > MAX_BODY) throw new ProtocolException("bad Content-Length " + bodyLen);
        }
        if (len < bodyStart + bodyLen) return null;
        byte[] body = new byte[bodyLen];
        System.arraycopy(buf, bodyStart, body, 0, bodyLen);
        m.setBody(body);
        consume(bodyStart + bodyLen);
        return m;
    }

    /**
     * {headerBytes, bodyStart}: headerBytes = length of the start line + header lines (with their line
     * ends), bodyStart = first byte after the empty line (CRLF or LF). Null while incomplete.
     */
    private int[] findHeaderEnd() {
        int lineStart = 0;
        for (int i = 0; i < len; i++) {
            if (buf[i] != '\n') continue;
            int lineLen = i - lineStart;
            if (lineLen == 0 || (lineLen == 1 && buf[lineStart] == '\r')) return new int[] {lineStart, i + 1};
            lineStart = i + 1;
        }
        return null;
    }

    private static RtspMessage startLine(String l) throws ProtocolException {
        String[] p = l.trim().split(" +", 3);
        if (p.length < 2) throw new ProtocolException("bad start line: " + l);
        if (p[0].startsWith("RTSP/")) {
            int status;
            try {
                status = Integer.parseInt(p[1]);
            } catch (NumberFormatException e) {
                throw new ProtocolException("bad status: " + l);
            }
            return RtspMessage.response(status, p.length > 2 ? p[2] : "");
        }
        if (p.length < 3 || !p[2].startsWith("RTSP/")) throw new ProtocolException("bad request line: " + l);
        for (int i = 0; i < p[0].length(); i++) {
            char ch = p[0].charAt(i);
            if (ch < 'A' || ch > 'Z') {
                if (ch != '_') throw new ProtocolException("bad method: " + l);
            }
        }
        return RtspMessage.request(p[0], p[1]);
    }

    /** Unparsed bytes held (tests). */
    public int pending() {
        return len;
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }
}
