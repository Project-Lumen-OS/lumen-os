// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * TCP framing of stock vcontrol (com.xgimi.tcp.utils.ProtocolFactory): a frame is the int -8118 (FF FF E0 4A), the
 * body length (int, big endian) and the UTF-8 body. The reader, like stock, looks for E0 4A anywhere in the stream,
 * so bytes before a frame are skipped.
 */
public final class Frame {
    private static final int MAGIC = -8118;
    private static final int FIRST = 0xE0;
    private static final int SECOND = 0x4A;
    /** Lumen's bound on one body (stock has none); a longer frame closes the connection. */
    static final int MAX_BODY = 1 << 20;

    private Frame() {}

    public static byte[] encode(String body) {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(8 + b.length);
        writeInt(out, MAGIC);
        writeInt(out, b.length);
        out.write(b, 0, b.length);
        return out.toByteArray();
    }

    /**
     * The next body, null at the end of the stream. A body cut short by the end is "" (stock: an empty message, which
     * no handler answers). A length of 0 or less is not a frame: the search goes on, as in stock.
     */
    public static String read(InputStream in) throws IOException {
        while (true) {
            int c = in.read();
            if (c < 0) return null;
            if (c != FIRST) continue;
            int d = in.read();
            if (d < 0) return "";
            if (d != SECOND) continue;
            byte[] len = new byte[4];
            if (!fill(in, len)) return "";
            int n = ((len[0] & 0xFF) << 24) | ((len[1] & 0xFF) << 16) | ((len[2] & 0xFF) << 8) | (len[3] & 0xFF);
            if (n <= 0) continue;
            if (n > MAX_BODY) throw new IOException("frame of " + n + " bytes");
            byte[] body = new byte[n];
            if (!fill(in, body)) return "";
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static boolean fill(InputStream in, byte[] b) throws IOException {
        int got = 0;
        while (got < b.length) {
            int r = in.read(b, got, b.length - got);
            if (r < 0) return false;
            got += r;
        }
        return true;
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v >>> 24);
        out.write(v >>> 16);
        out.write(v >>> 8);
        out.write(v);
    }
}
