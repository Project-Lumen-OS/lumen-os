// SPDX-License-Identifier: Apache-2.0
package android.os;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Host test stand-in for the hidden android.os.SystemProperties: a map, and the order of writes (test/run.sh). */
public final class SystemProperties {
    private SystemProperties() {}

    public static final Map<String, String> VALUES = new HashMap<>();
    public static final List<String> WRITES = new ArrayList<>();

    public static String get(String key, String def) {
        String v = VALUES.get(key);
        return v == null ? def : v;
    }

    public static void set(String key, String value) {
        VALUES.put(key, value);
        WRITES.add(key + "=" + value);
    }
}
