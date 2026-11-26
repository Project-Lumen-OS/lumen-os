// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
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
        return read(open(url), url, maxBytes);
    }

    /**
     * A channel manifest or its signature, never from a cache: the request says no-cache, asks nothing conditional
     * (no If-None-Match, no If-Modified-Since, so a 304 can never stand for the last copy) and carries a query no cache
     * has seen (GitHub ignores it and redirects to the current asset). The redirect is resolved anew every time.
     */
    public static byte[] fetchFresh(String url, int maxBytes) throws IOException {
        HttpURLConnection c = open(fresh(url));
        c.setIfModifiedSince(0);
        c.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
        c.setRequestProperty("Pragma", "no-cache");
        return read(c, url, maxBytes);
    }

    private static final java.security.SecureRandom NONCE = new java.security.SecureRandom();

    /** {@code url} with a query parameter of its own: lumen_nc=&lt;random&gt;. */
    static String fresh(String url) {
        int f = url.indexOf('#');
        String u = f >= 0 ? url.substring(0, f) : url;
        return u + (u.indexOf('?') >= 0 ? '&' : '?') + "lumen_nc=" + Long.toHexString(NONCE.nextLong() & Long.MAX_VALUE);
    }

    private static byte[] read(HttpURLConnection c, String url, int maxBytes) throws IOException {
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
