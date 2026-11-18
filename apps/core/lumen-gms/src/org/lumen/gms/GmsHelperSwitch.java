// SPDX-License-Identifier: Apache-2.0
package org.lumen.gms;

import android.os.SystemProperties;

/**
 * The Google sign-in helper switch on the unit: the persist properties of {@link GmsHelperPolicy}. Needs the system
 * uid (persist.sys.* is system_prop): the Lumen settings host app is platform-signed. A change takes effect at once:
 * init's helper (lumen_gmsprops restart) sets or clears the wrap properties and stops the affected Google processes.
 */
public final class GmsHelperSwitch {
    private GmsHelperSwitch() { }

    public static GmsHelperRow row() {
        return GmsHelperPolicy.row(get(GmsHelperPolicy.ADDON), get(GmsHelperPolicy.ADDON_PLAY6),
                get(GmsHelperPolicy.PROP));
    }

    /** The user's choice: remembered as a choice first, so a later data default never overrides it. */
    public static void setByUser(boolean on) {
        SystemProperties.set(GmsHelperPolicy.PROP_USER, "1");
        SystemProperties.set(GmsHelperPolicy.PROP, GmsHelperPolicy.userValue(on));
    }

    /** A signed data update's default (Lumen updater, after its signature check); ignored once the user chose. */
    public static void applyDataDefault(boolean defaultOn) {
        String v = GmsHelperPolicy.dataDefault(get(GmsHelperPolicy.PROP_USER), get(GmsHelperPolicy.PROP), defaultOn);
        if (v != null) SystemProperties.set(GmsHelperPolicy.PROP, v);
    }

    private static String get(String key) {
        return SystemProperties.get(key, "");
    }
}
