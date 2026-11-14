// SPDX-License-Identifier: Apache-2.0
package org.lumen.stats;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * The send of the statistics ping (README "Privacy", apps/core/lumen-stats/README.md): at most once per start, once a
 * day and right after an update, when the switch is on, the first-run setup is finished and the host holds nothing
 * back. One HTTPS POST on its own thread; never blocks, never throws, failures are only logged (tag LumenStats).
 */
public final class StatsClient {
    private StatsClient() {}

    public static final String TAG = "LumenStats";
    private static final int TIMEOUT_MS = 10000;

    /** hold: the host's own rule against a send now (the updater: the one-time notice after an update is due). */
    public static void maybeSend(Context c, StatsState st, Release rel, boolean hold) {
        try {
            if (!Ping.allowed(Ping.configured(), st.on(), Device.setupDone(c), hold)) return;
            long now = System.currentTimeMillis();
            String today = Ping.day(now);
            int vc = rel.versionCode, boot = Device.bootCount(c), lastVc = st.sentVc();
            if (!Ping.due(lastVc != 0 && lastVc != vc, boot != st.sentBoot(), !today.equals(st.sentDay()),
                    Math.abs(now - st.lastTry()))) return;
            st.setTry(now);
            String[] u = Ping.update(lastVc, st.sentVersion(), vc, rel.build, st.methodBuild(), st.method());
            byte[] body = Ping.body(st.id(), Device.serial(), Device.model(), Device.xgimiModel(), rel.version, vc,
                    rel.build, Device.trial(), u[0], Integer.parseInt(u[1]), u[2]).getBytes(StandardCharsets.UTF_8);
            Thread t = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                for (String url : Ping.URLS) {
                    try {
                        post(url, body);
                        st.setSent(today, vc, rel.version, boot);
                        Log.i(TAG, "stats sent");
                        return;
                    } catch (Throwable e) {
                        Log.i(TAG, "stats not sent: " + e);
                    }
                }
            }, "lumen-stats");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {
            Log.i(TAG, "stats: " + t);
        }
    }

    /** POST the JSON body, https only, no redirect followed; a non-2xx answer is an IOException. */
    static void post(String url, byte[] json) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("not https: " + url);
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setUseCaches(false);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(json.length);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(json);
            }
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code + " for " + url);
        } finally {
            c.disconnect();
        }
    }
}
