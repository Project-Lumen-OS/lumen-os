// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

public final class SystemProperties {
    public static String get(String k, String d) { String v = Wire.PROPS.get(k); return v == null ? d : v; }
    public static String get(String k) { return get(k, ""); }
}
