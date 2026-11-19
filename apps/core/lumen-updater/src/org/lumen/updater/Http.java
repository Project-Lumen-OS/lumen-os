// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import org.lumen.download.HttpError;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTPS only. Every request starts at the stable GitHub URL, which redirects to a short-lived CDN URL
 * (ParallelDownload resolves it again when the CDN refuses it). Only a User-Agent is sent: no device id.
 * The one exception is {@link #post}, used only by Stats (the anonymous version count the user can
 * turn off).
 */
public final class Http {
    private Http() {}

    static HttpURLConnection open(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("not https: " + url);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", Ota.USER_AGENT);
        c.setRequestProperty("Accept-Encoding", "identity");
        return c;
    }

    public static byte[] fetch(String url, int maxBytes) throws IOException {
        HttpURLConnection c = open(url);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new HttpError(code, "HTTP " + code + " for " + url);
            if (!c.getURL().getProtocol().equals("https")) throw new IOException("redirected off https");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) {
                    bo.write(b, 0, n);
                    if (bo.size() > maxBytes) throw new IOException("response too large: " + url);
                }
                return bo.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    /** POST a small JSON body, no redirects followed, short timeouts; any answer but 2xx is an error. */
    public static void post(String url, byte[] json, int timeoutMs) throws IOException {
        HttpURLConnection c = open(url);
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(json.length);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(json);
            }
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new HttpError(code, "HTTP " + code + " for " + url);
        } finally {
            c.disconnect();
        }
    }

}
