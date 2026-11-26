// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Parity probe stand-in for the hidden android.os.SystemProperties: a map, set() refused for keys in DENY. */
public final class SystemProperties {
    private SystemProperties() {}

    public static final Map<String, String> VALUES = new TreeMap<>();
    public static final Set<String> DENY = new HashSet<>();
    /** Called after every set(): the probe's fake boot gate answers here. */
    public static Runnable onSet = () -> { };

    public static String get(String key, String def) {
        String v = VALUES.get(key);
        return v == null ? def : v;
    }

    public static String get(String key) { return get(key, ""); }

    public static void set(String key, String val) {
        if (DENY.contains(key)) throw new RuntimeException("failed to set system property " + key);
        VALUES.put(key, val);
        onSet.run();
    }
}
