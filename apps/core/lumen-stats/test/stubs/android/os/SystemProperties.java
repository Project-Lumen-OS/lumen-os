// SPDX-License-Identifier: Apache-2.0
package android.os;

import java.util.HashMap;
import java.util.Map;

/** Host test stand-in for the hidden android.os.SystemProperties: values from a map (test/run.sh). */
public final class SystemProperties {
    private SystemProperties() {}

    public static final Map<String, String> VALUES = new HashMap<>();

    public static String get(String key, String def) {
        String v = VALUES.get(key);
        return v == null ? def : v;
    }
}
