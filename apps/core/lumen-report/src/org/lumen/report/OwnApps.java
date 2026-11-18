// SPDX-License-Identifier: Apache-2.0
package org.lumen.report;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The apps of the image itself (Lumen, AOSP and LineageOS packages). Only their log lines and crash records may go
 * into a report automatically; apps the user installed never do. Plain Java.
 */
final class OwnApps {
    private static final String[] PREFIXES = {"org.lumen.", "com.android.", "android.", "org.lineageos.", "lineageos.",
            "com.lineageos."};
    /** Google apps that happen to use the com.android prefix: they are not part of the image. */
    private static final Set<String> NOT_OWN = new HashSet<>(Arrays.asList("com.android.vending", "com.android.chrome"));

    private OwnApps() {}

    /** True for a package or process name of the image ("org.lumen.home", "com.android.systemui:screenshot"). */
    static boolean own(String name) {
        if (name == null) return false;
        int colon = name.indexOf(':');
        String pkg = colon >= 0 ? name.substring(0, colon) : name;
        if (pkg.equals("android") || pkg.equals("system") || pkg.equals("system_server")) return true;
        if (NOT_OWN.contains(pkg)) return false;
        for (String p : PREFIXES) if (pkg.startsWith(p)) return true;
        return false;
    }
}
