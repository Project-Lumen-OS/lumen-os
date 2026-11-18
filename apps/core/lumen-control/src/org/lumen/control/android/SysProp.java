// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.android;

import android.os.SystemProperties;

/**
 * A system property, trimmed; "" when unset or unreadable. The same reader as org.lumen.stats.Device.prop, kept here
 * so lumen-stats (built into every updater, a pinned Z9X APK) stays unchanged until the modules share one helper.
 */
final class SysProp {
    private SysProp() {}

    static String get(String key) {
        try {
            String v = SystemProperties.get(key, "");
            return v == null ? "" : v.trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
