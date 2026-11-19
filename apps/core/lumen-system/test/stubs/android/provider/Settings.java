package android.provider;

import android.content.ContentResolver;
import java.util.HashMap;
import java.util.Map;

/** Host stub: Settings.Secure as a map; FAIL makes every call throw (a provider that refuses). */
public final class Settings {
    private Settings() { }

    public static final class Secure {
        public static final Map<String, Integer> VALUES = new HashMap<>();
        public static boolean FAIL;

        private Secure() { }

        public static int getInt(ContentResolver cr, String key, int def) {
            if (FAIL) throw new SecurityException("refused");
            Integer v = VALUES.get(key);
            return v == null ? def : v;
        }

        public static boolean putInt(ContentResolver cr, String key, int value) {
            if (FAIL) throw new SecurityException("refused");
            VALUES.put(key, value);
            return true;
        }
    }

    public static final class System {
        public static final String SCREEN_OFF_TIMEOUT = "screen_off_timeout";
        public static final Map<String, Long> VALUES = new HashMap<>();
        public static boolean FAIL;

        private System() { }

        public static long getLong(ContentResolver cr, String key, long def) {
            if (FAIL) throw new SecurityException("refused");
            Long v = VALUES.get(key);
            return v == null ? def : v;
        }

        public static boolean putLong(ContentResolver cr, String key, long value) {
            if (FAIL) throw new SecurityException("refused");
            VALUES.put(key, value);
            return true;
        }
    }
}
