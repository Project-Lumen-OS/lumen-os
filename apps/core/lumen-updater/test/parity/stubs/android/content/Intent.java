// SPDX-License-Identifier: Apache-2.0
package android.content;

import java.util.HashMap;
import java.util.Map;

/** Parity probe stand-in: extras in a map (the battery broadcast of the Play 6 power check). */
public class Intent {
    public static final String ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED";
    public static final int FLAG_RECEIVER_FOREGROUND = 0x10000000, FLAG_RECEIVER_INCLUDE_BACKGROUND = 0x01000000,
            FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public final Map<String, Object> extras = new HashMap<>();
    public String action, pkg;

    public Intent() {}
    public Intent(String action) { this.action = action; }

    public Intent putExtra(String k, boolean v) { extras.put(k, v); return this; }
    public Intent putExtra(String k, int v) { extras.put(k, v); return this; }
    public Intent putExtra(String k, String v) { extras.put(k, v); return this; }
    public Intent setPackage(String p) { pkg = p; return this; }
    public Intent addFlags(int f) { return this; }

    public boolean getBooleanExtra(String k, boolean def) {
        Object v = extras.get(k);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public int getIntExtra(String k, int def) {
        Object v = extras.get(k);
        return v instanceof Integer ? (Integer) v : def;
    }
}
