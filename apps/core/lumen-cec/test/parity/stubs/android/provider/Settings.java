// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.provider;

import android.content.ContentResolver;
import parity.World;

public final class Settings {
    private Settings() { }
    public static final class Global {
        public static final String DEVICE_NAME = "device_name";
        public static String getString(ContentResolver cr, String k) {
            if (World.deviceNameThrows) throw new SecurityException("settings");
            return DEVICE_NAME.equals(k) ? World.deviceName : null;
        }
    }
}
