// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The unit's model code (one stock build serves several model codes of a line), for the family adapter (Family.init).
 * The property that holds it is vendor data: model_prop of devices/_vendor/<vendor>/vendor.conf, which a recipe ships
 * as the asset {@link #VENDOR_CONF} when its family needs the model code. "" without that asset, without the
 * property or for a value that is no model code (one letter and four digits).
 */
public final class UnitModel {
    private static final String TAG = "Z9xUnitModel";
    static final String VENDOR_CONF = "lumen_vendor/vendor.conf";

    private UnitModel() {}

    public static String read(Context c) {
        String conf;
        try (InputStream in = c.getAssets().open(VENDOR_CONF)) {
            conf = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
        String prop = prop(conf);
        if (prop.isEmpty()) return "";
        String v;
        try {
            v = SystemProperties.get(prop, "").trim();
        } catch (Throwable t) {
            Log.w(TAG, prop + " not readable: " + t);
            return "";
        }
        return code(v);
    }

    /** The model_prop value of a vendor.conf text, "" when it has none. */
    static String prop(String conf) {
        for (String line : conf.split("\n")) {
            line = line.trim();
            if (line.startsWith("model_prop=")) return line.substring("model_prop=".length()).trim();
        }
        return "";
    }

    /** v when it is a model code, else "". */
    static String code(String v) {
        return v != null && v.matches("[A-Z][0-9]{4}") ? v : "";
    }
}
