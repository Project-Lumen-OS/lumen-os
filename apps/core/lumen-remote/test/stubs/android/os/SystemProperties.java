// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;
public final class SystemProperties {
    public static String get(String k, String d) { String v = lumen.fake.World.props.get(k); return v == null ? d : v; }
    public static String get(String k) { return get(k, ""); }
    public static int getInt(String k, int d) { try { return Integer.parseInt(get(k, "")); } catch (Exception e) { return d; } }
    public static boolean getBoolean(String k, boolean d) { String v = get(k, ""); return v.isEmpty() ? d : v.equals("1") || v.equals("true"); }
}
