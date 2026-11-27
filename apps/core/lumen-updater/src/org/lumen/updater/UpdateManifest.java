// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The signed update manifest (schema 1: tools/ota/make_manifest.py, tools/devices/ota/make_manifest.py,
 * tools/play6/ota/make_manifest_play6.py). Parsed only after its signature was verified. What a package must carry
 * follows this model's InstallKind (payloadFacts). Deltas: "delta" entries of "packages" (the 1.0.9 form, read by every
 * updater) and the optional "incremental" list (read only by updaters that fall back to the full package when a delta
 * fails: DeltaRule); a malformed incremental entry is skipped, it never costs the full package.
 */
public final class UpdateManifest {
    public final String json;
    public final int schema;
    public final String device, channel, version, buildId;
    public final int versionCode, minVersionCode;
    public final long buildUtc;
    public final List<String> vendorIncremental = new ArrayList<>();
    public final List<String> blobsSets = new ArrayList<>();
    public final JSONObject changelog;
    public final List<Pkg> packages = new ArrayList<>();
    public final List<Pkg> incremental = new ArrayList<>();

    public static final class Pkg {
        public final String json;
        public final String type, fromBuildId, fromVersion, url, sha256, metadataUrl, metadataSha256;
        public final int fromVersionCode;
        public final long size, payloadOffset, payloadSize, metadataSize;
        public final String[] properties;

        Pkg(JSONObject o) throws JSONException {
            json = o.toString();
            type = o.getString("type");
            fromBuildId = o.optString("from_build_id", "");
            fromVersion = o.optString("from_version", "");
            fromVersionCode = o.optInt("from_version_code", 0);
            url = o.getString("url");
            size = o.getLong("size");
            sha256 = o.getString("sha256").toLowerCase(Locale.ROOT);
            // update_engine needs the payload range, its metadata and properties; a whole package for the recovery
            // (Play 6, tools/play6/ota/make_manifest_play6.py) has none of them and is only ever full
            boolean payload = Ota.T.install.payloadFacts;
            payloadOffset = payload ? o.getLong("payload_offset") : o.optLong("payload_offset", 0);
            payloadSize = payload ? o.getLong("payload_size") : o.optLong("payload_size", 0);
            metadataUrl = payload ? o.getString("metadata_url") : o.optString("metadata_url", "");
            metadataSize = o.optLong("metadata_size", 0);
            metadataSha256 = o.optString("metadata_sha256", "").toLowerCase(Locale.ROOT);
            JSONArray pr = payload ? o.getJSONArray("payload_properties") : o.optJSONArray("payload_properties");
            properties = new String[pr == null ? 0 : pr.length()];
            for (int i = 0; i < properties.length; i++) properties[i] = pr.getString(i);
            if (!sha256.matches("[0-9a-f]{64}")) throw new JSONException("bad sha256");
            if (payload) {
                if (!url.startsWith("https://") || !metadataUrl.startsWith("https://")) throw new JSONException("not https");
                if (payloadOffset <= 0 || payloadSize <= 0 || payloadOffset + payloadSize > size)
                    throw new JSONException("bad payload range");
                if (!"full".equals(type) && !"delta".equals(type)) throw new JSONException("bad type " + type);
            } else {
                if (!url.startsWith("https://")) throw new JSONException("not https");
                if (size <= 0) throw new JSONException("bad size");
                // full packages only for now (incrementals: design notes phase 4)
                if (!"full".equals(type)) throw new JSONException("bad type " + type);
            }
        }

        public static Pkg parse(String s) throws JSONException { return new Pkg(new JSONObject(s)); }

        public boolean isDelta() { return "delta".equals(type); }

        public String fileName() {
            int i = url.lastIndexOf('/');
            String n = url.substring(i + 1).replaceAll("[^A-Za-z0-9._-]", "_");
            return n.isEmpty() ? "update.zip" : n;
        }
    }

    public UpdateManifest(String json) throws JSONException {
        this.json = json;
        JSONObject o = new JSONObject(json);
        schema = o.getInt("schema");
        device = o.getString("device");
        channel = o.optString("channel", "stable");
        version = o.getString("version");
        versionCode = o.getInt("version_code");
        buildId = o.getString("build_id");
        buildUtc = o.optLong("build_utc", 0);
        JSONObject req = o.optJSONObject("requires");
        int minVc = 0;
        if (req != null) {
            JSONArray a = req.optJSONArray("vendor_incremental");
            for (int i = 0; a != null && i < a.length(); i++) vendorIncremental.add(a.getString(i));
            JSONArray b = req.optJSONArray("blobs_set");
            for (int i = 0; b != null && i < b.length(); i++) blobsSets.add(b.getString(i));
            minVc = req.optInt("min_version_code", 0);
        }
        minVersionCode = minVc;
        changelog = o.optJSONObject("changelog");
        JSONArray pk = o.getJSONArray("packages");
        for (int i = 0; i < pk.length(); i++) packages.add(new Pkg(pk.getJSONObject(i)));
        JSONArray inc = o.optJSONArray("incremental");
        for (int i = 0; inc != null && i < inc.length(); i++) {
            try {
                Pkg d = new Pkg(inc.getJSONObject(i));
                if (d.isDelta() && !d.fromBuildId.isEmpty()) incremental.add(d);
            } catch (JSONException skipped) {
                // an entry this updater cannot use: the full package stays the offer
            }
        }
        if (schema != 1) throw new JSONException("unsupported schema " + schema);
    }

    /** The full package (the first one; null if none). */
    public Pkg full() {
        for (Pkg p : packages) if (!p.isDelta()) return p;
        return null;
    }

    /** Every delta of this manifest: the incremental list first, then the deltas of "packages". */
    public List<Pkg> deltas() {
        List<Pkg> d = new ArrayList<>(incremental);
        for (Pkg p : packages) if (p.isDelta()) d.add(p);
        return d;
    }

    /** Changelog in the UI language, else English, else the first one. */
    public String changelogFor(Locale l) {
        if (changelog == null) return "";
        String tag = l.toLanguageTag();
        String[] keys = {tag, l.getLanguage() + "-" + l.getCountry(), l.getLanguage(), "en"};
        for (String k : keys) {
            if (changelog.has(k)) return changelog.optString(k, "");
        }
        java.util.Iterator<String> it = changelog.keys();
        return it.hasNext() ? changelog.optString(it.next(), "") : "";
    }
}
