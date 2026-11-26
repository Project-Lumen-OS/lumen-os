// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

import parity.World;

public final class SystemProperties {
    private SystemProperties() { }
    public static String get(String k, String d) { String v = World.props.get(k); return v == null ? d : v; }
    public static String get(String k) { return get(k, ""); }
    public static void set(String k, String v) { World.event("prop " + k + "=" + v); World.props.put(k, v); }
}
