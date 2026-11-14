package android.os;

public final class SystemProperties {
    public static String get(String k, String d) { String v = Wire.PROPS.get(k); return v == null ? d : v; }
    public static String get(String k) { return get(k, ""); }
}
