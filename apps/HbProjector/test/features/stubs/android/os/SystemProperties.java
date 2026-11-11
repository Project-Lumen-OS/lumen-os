package android.os;

/** Host stub: HbFeatures.init() reads the model code; the test calls init(model, xml) directly. */
public final class SystemProperties {
    private SystemProperties() {}

    public static String get(String key, String def) {
        return def;
    }
}
