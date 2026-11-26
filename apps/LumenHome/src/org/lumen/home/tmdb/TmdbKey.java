// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.tmdb;

import android.content.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The build's TMDB key: the asset "tmdb_key" (build_apk.sh TMDB_KEY_FILE), not in the sources. Null when absent or
 * malformed, then TMDB features are off. Never logged.
 */
public final class TmdbKey {
    private TmdbKey() {}

    public static String read(Context c) {
        String key = null;
        try (InputStream in = c.getAssets().open("tmdb_key")) {
            byte[] b = new byte[128];
            int n = in.read(b);
            if (n > 0) key = new String(b, 0, n, StandardCharsets.US_ASCII).trim();
        } catch (Throwable ignored) {
            // no key in this build
        }
        return TmdbArt.validKey(key) ? key : null;
    }
}
