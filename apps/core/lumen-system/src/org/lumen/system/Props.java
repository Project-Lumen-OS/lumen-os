// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

/** android.os.SystemProperties for the platform-signed apps (a hidden API of the framework header jar the apps build with,
 * called directly: no reflection, which the vendor HAL guard tools/devices/check_aidl_hal.py rule 7 refuses). */
final class Props {
    private Props() { }

    static String get(String key) {
        try {
            return android.os.SystemProperties.get(key, "");
        } catch (RuntimeException e) {
            return "";
        }
    }

    static boolean set(String key, String value) {
        try {
            android.os.SystemProperties.set(key, value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
