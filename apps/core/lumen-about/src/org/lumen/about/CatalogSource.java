// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.about;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;

/**
 * Lumen Installer's signed catalog for the way back: the last verified copy kept in the app's files, a fresh one
 * read from GitHub when the screen opens. Only a copy that verifies with the list certificate (the one Lumen
 * Installer carries, assets/lumen_about/index.x509.pem) is used or kept, and never one older than the kept copy.
 */
public final class CatalogSource {
    static final String TAG = "LumenAbout";
    static final String CERT_ASSET = "lumen_about/index.x509.pem";
    private static final String DIR = "lumen_about", FILE = "catalog-v2.json", SIG = FILE + ".sig";
    private static final int CONNECT_MS = 10_000, READ_MS = 15_000;

    private CatalogSource() {}

    /** the kept copy, null when there is none that verifies */
    public static RestoreCatalog kept(Context c) {
        File d = new File(c.getFilesDir(), DIR);
        try {
            return RestoreCatalog.read(Files.readAllBytes(new File(d, FILE).toPath()), Files.readAllBytes(new File(d, SIG).toPath()),
                    key(c));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** a fresh copy from GitHub (kept when it is not older than the kept one), null when it cannot be read or verified */
    public static RestoreCatalog fresh(Context c, RestoreCatalog kept) {
        try {
            byte[] json = get(RestoreCatalog.ADDRESS, RestoreCatalog.MAX_BYTES);
            byte[] sig = get(RestoreCatalog.ADDRESS + ".sig", RestoreCatalog.MAX_SIG);
            RestoreCatalog got = RestoreCatalog.read(json, sig, key(c));
            if (!RestoreCatalog.replaces(got, kept)) return null;
            keep(c, json, sig);
            return got;
        } catch (IOException | RuntimeException e) {
            Log.i(TAG, "catalog: " + e);
            return null;
        }
    }

    static PublicKey key(Context c) {
        try (InputStream in = c.getAssets().open(CERT_ASSET)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(in).getPublicKey();
        } catch (IOException | GeneralSecurityException e) {
            Log.w(TAG, "list certificate: " + e);
            return null;
        }
    }

    private static void keep(Context c, byte[] json, byte[] sig) throws IOException {
        File d = new File(c.getFilesDir(), DIR);
        if (!d.isDirectory() && !d.mkdirs()) throw new IOException("no " + d);
        write(new File(d, SIG), sig);
        write(new File(d, FILE), json);
    }

    private static void write(File f, byte[] b) throws IOException {
        File t = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(t)) {
            o.write(b);
            o.getFD().sync();
        }
        if (!t.renameTo(f)) throw new IOException("rename " + t);
    }

    private static byte[] get(String address, int max) throws IOException {
        HttpURLConnection h = (HttpURLConnection) URI.create(address).toURL().openConnection();
        h.setConnectTimeout(CONNECT_MS);
        h.setReadTimeout(READ_MS);
        h.setInstanceFollowRedirects(true);
        h.setRequestProperty("User-Agent", "lumen-about");
        try {
            if (h.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException(address + ": HTTP " + h.getResponseCode());
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) {
                    if (out.size() + n > max) throw new IOException(address + ": more than " + max + " bytes");
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            h.disconnect();
        }
    }
}
