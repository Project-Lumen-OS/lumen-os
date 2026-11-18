// SPDX-License-Identifier: Apache-2.0
package org.lumen.about;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.regex.Pattern;

/**
 * The way back of each model as Lumen Installer's signed catalog says it (catalog-v2.json on the release 'index' of
 * Project-Lumen-OS/lumen-installer, made by tools/devices/installer_catalog.py from devices/&lt;codename&gt;/catalog.conf):
 * per model code the stick file name and, per stock version, the official XGIMI file to put back.
 * <pre>
 *   ENTRY ... "restore": {"file": "XGIMI_X.bin", "files": [{"stock": "v6.15.58", "version": "v6.15.62",
 *                                                           "size": N, "url": "https://product-library.../XGIMI_X.bin"}]}
 * </pre>
 * Only a catalog signed with the list key is read. An official file must sit on XGIMI's own firmware server under the
 * stick name and must not be older than the stock version (the stick may refuse an older one); anything else in a row
 * is left out (the screen then shows the guide instead).
 */
public final class RestoreCatalog {
    public static final String ADDRESS =
            "https://github.com/Project-Lumen-OS/lumen-installer/releases/download/index/catalog-v2.json";
    public static final int MAX_BYTES = 1 << 20, MAX_SIG = 4096;
    static final String KIND = "lumen-installer-catalog";
    static final String CDN = "https://product-library.static.xgimi.com/firmware/";
    private static final Pattern SAFE_URL = Pattern.compile("[A-Za-z0-9._~/:-]{1,300}");

    public final long serial;
    private final JSONArray models;

    private RestoreCatalog(long serial, JSONArray models) {
        this.serial = serial;
        this.models = models;
    }

    /** The catalog when the signature verifies with the list key and the frame is right, else null. */
    public static RestoreCatalog read(byte[] json, byte[] sig, PublicKey key) {
        if (json == null || sig == null || key == null || json.length > MAX_BYTES || sig.length > MAX_SIG) return null;
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(key);
            s.update(json);
            if (!s.verify(sig)) return null;
            JSONObject o = new JSONObject(new String(json, StandardCharsets.UTF_8));
            long serial = o.getLong("serial");
            if (o.getInt("schema") != 1 || !KIND.equals(o.getString("kind")) || serial <= 0) return null;
            return new RestoreCatalog(serial, o.getJSONArray("models"));
        } catch (GeneralSecurityException | JSONException | RuntimeException e) {
            return null;
        }
    }

    /** true when a freshly read catalog may replace the kept one: it verified and is not older */
    public static boolean replaces(RestoreCatalog fresh, RestoreCatalog kept) {
        return fresh != null && (kept == null || fresh.serial >= kept.serial);
    }

    /** the stick file name the catalog gives for the model, "" when it gives none */
    public String usbFile(String model) {
        JSONObject r = restore(model);
        String f = r == null ? "" : r.optString("file", "");
        return XgimiFacts.BIN.matcher(f).matches() ? f : "";
    }

    /** the official file for this model on this stock version, null when the catalog knows none */
    public OfficialFile file(String model, String stock) {
        JSONObject r = restore(model);
        String name = usbFile(model);
        JSONArray rows = r == null ? null : r.optJSONArray("files");
        String own = XgimiFacts.firmware(stock);
        if (name.isEmpty() || rows == null || own.isEmpty()) return null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject f = rows.optJSONObject(i);
            if (f == null || !own.equalsIgnoreCase(f.optString("stock"))) continue;
            String version = XgimiFacts.firmware(f.optString("version"));
            String url = f.optString("url");
            long size = f.optLong("size", 0);
            if (version.isEmpty() || size <= 0 || !official(url, name) || older(version, own)) return null;
            return new OfficialFile(name, version, size, url);
        }
        return null;
    }

    /** true when version a (v6.15.19) is lower than b (v6.15.58), part by part as numbers */
    static boolean older(String a, String b) {
        String[] x = a.substring(1).split("\\."), y = b.substring(1).split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p < q;
        }
        return false;
    }

    /** an address on XGIMI's firmware server whose last part is the stick file name */
    static boolean official(String url, String name) {
        return url.startsWith(CDN) && url.endsWith("/" + name) && SAFE_URL.matcher(url).matches() && !url.contains("..");
    }

    private JSONObject restore(String model) {
        for (int i = 0; i < models.length(); i++) {
            JSONObject e = models.optJSONObject(i);
            if (e != null && model.equals(e.optString("model"))) return e.optJSONObject("restore");
        }
        return null;
    }
}
