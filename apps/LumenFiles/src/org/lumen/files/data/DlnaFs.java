// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.data;

import org.lumen.files.model.Entry;
import org.lumen.files.model.Fs;
import org.lumen.files.model.Upnp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A DLNA media server (ContentDirectory Browse over HTTP): folders by object id, items by their http address. */
public final class DlnaFs implements Fs {
    static final int PAGE = 200, MAX_ROWS = 2000, CONNECT_MS = 4000, READ_MS = 15000;
    private final Upnp.Device dev;
    private final Map<String, Entry> seen = new HashMap<>();
    private final Map<String, String> parents = new HashMap<>();

    public DlnaFs(Upnp.Device dev) {
        this.dev = dev;
    }

    public Upnp.Device device() {
        return dev;
    }

    @Override
    public String id() {
        return "dlna:" + dev.udn;
    }

    @Override
    public boolean writable() {
        return false;
    }

    @Override
    public synchronized List<Entry> list(String dir) throws IOException {
        List<Entry> out = new ArrayList<>();
        try {
            for (int start = 0; start < MAX_ROWS; ) {
                Upnp.Page p = Upnp.result(post(dev.control, Upnp.browse(dir, start, PAGE)));
                for (Entry e : p.entries) {
                    seen.put(e.path, e);
                    parents.put(e.path, dir);
                    out.add(e);
                }
                start += Math.max(p.returned, p.entries.size());
                if (p.returned <= 0 || start >= p.total) break;
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("bad answer: " + e, e);
        }
        return out;
    }

    static String post(String url, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            c.setRequestProperty("SOAPACTION", Upnp.BROWSE);
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            try (OutputStream o = c.getOutputStream()) {
                o.write(b);
            }
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return text(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            return text(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    static String text(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = i.read(b)) > 0; ) {
                o.write(b, 0, n);
                if (o.size() > (8 << 20)) throw new IOException("answer too large");
            }
            return o.toString("UTF-8");
        }
    }

    @Override
    public synchronized Entry stat(String path) {
        return seen.get(path);
    }

    @Override
    public InputStream read(String path) throws IOException {
        Entry e = stat(path);
        if (e == null || e.url.isEmpty()) throw new IOException("unknown item " + path);
        HttpURLConnection c = (HttpURLConnection) new URL(e.url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        return c.getInputStream();
    }

    @Override
    public OutputStream create(String path) throws IOException {
        throw new IOException("read only");
    }

    @Override
    public void mkdir(String path) throws IOException {
        throw new IOException("read only");
    }

    @Override
    public void remove(String path) throws IOException {
        throw new IOException("read only");
    }

    @Override
    public boolean rename(String from, String to) {
        return false;
    }

    @Override
    public long free(String dir) {
        return -1;
    }

    @Override
    public String child(String dir, String name) {
        return dir + "/" + name;
    }

    @Override
    public synchronized String parent(String path) {
        return Upnp.ROOT.equals(path) ? null : parents.getOrDefault(path, Upnp.ROOT);
    }

    @Override
    public boolean same(Fs other) {
        return other instanceof DlnaFs && ((DlnaFs) other).dev.udn.equals(dev.udn);
    }
}
