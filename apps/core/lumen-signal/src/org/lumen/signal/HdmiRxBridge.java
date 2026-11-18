// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

import android.os.Handler;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The app side of the image's HDMI receiver bridge (image/lumen_hdmirx.rc and .sh, root, oneshot): the app cannot open
 * the receiver's root-only node, so it sets a request token, init starts the bridge, the bridge writes the filtered
 * info lines to {@link #FILE} and answers "&lt;token&gt; ok" in {@link #PROP_DONE}. This class never touches
 * /proc/utopia_mdb itself. One request at a time on its own thread (never the HAL thread); FormatWatch decides when.
 */
final class HdmiRxBridge {
    static final String PROP_REQ = "sys.lumen.hdmirx.req", PROP_DONE = "sys.lumen.hdmirx.done";
    static final String FILE = "/dev/lumen_hdmirx/info.txt", OK = "ok";
    /** the bridge needs about 0.3 s on the Z9X, 1.7 s on the HB, up to 6 s when its first way of asking gives nothing */
    static final long TIMEOUT_MS = 7_000L, POLL_MS = 40L;
    static final int MAX_BYTES = 32 * 1024;

    private static final ExecutorService ONE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lumen-hdmirx");
        t.setDaemon(true);
        return t;
    });
    private static int sSeq;

    private HdmiRxBridge() {}

    /** Asks the bridge once; {@code cb} gets the info (null = no answer) on {@code main}. */
    static void fetch(Handler main, Consumer<HdmiRxInfo> cb) {
        ONE.execute(() -> {
            HdmiRxInfo info = null;
            try {
                info = HdmiRxInfo.parse(ask());
            } catch (Throwable t) {
                Log.w(SignalPanel.TAG, "hdmirx: " + t);
            }
            final HdmiRxInfo out = info;
            main.post(() -> cb.accept(out));
        });
    }

    /** The bridge's lines for a fresh token, null on refusal, timeout or an old file. */
    private static String ask() throws Exception {
        String token = Long.toString(SystemClock.uptimeMillis(), 36) + "-" + (++sSeq);
        SystemProperties.set(PROP_REQ, token);
        long end = SystemClock.uptimeMillis() + TIMEOUT_MS;
        String done;
        while (!(done = SystemProperties.get(PROP_DONE, "")).startsWith(token + " ")) {
            if (SystemClock.uptimeMillis() >= end) {
                Log.i(SignalPanel.TAG, "hdmirx: no answer for " + token);
                return null;
            }
            SystemClock.sleep(POLL_MS);
        }
        String status = done.substring(token.length() + 1).trim();
        if (!OK.equals(status)) {
            Log.i(SignalPanel.TAG, "hdmirx: " + status);
            return null;
        }
        String text = read();
        return text != null && text.startsWith("token " + token + "\n") ? text : null;
    }

    private static String read() throws Exception {
        try (InputStream in = new FileInputStream(FILE)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0 && out.size() < MAX_BYTES; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
