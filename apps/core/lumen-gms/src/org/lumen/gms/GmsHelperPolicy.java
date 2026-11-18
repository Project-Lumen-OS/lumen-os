// SPDX-License-Identifier: Apache-2.0
package org.lumen.gms;

/**
 * Rules of the Google sign-in helper switch, without Android (host tests: test/run.sh). The same rules as
 * gp_helper_on in lineage/gmsprops/src/gmsprops_conf.c, which init's helper and the library run.
 * The switch is one persist property, {@link #PROP}: "0" off, anything else on, unset = on (the default for
 * every unit with Google services). Google services are there when {@link #ADDON} is "ok" (the user's add-on) or
 * "skip" (the Google edition), or {@link #ADDON_PLAY6} is "mounted:N". {@link #PROP_USER} is "1" once the user
 * chose: from then on a data default never changes the switch.
 */
public final class GmsHelperPolicy {
    public static final String PROP = "persist.sys.lumen.gms_compat";
    public static final String PROP_USER = "persist.sys.lumen.gms_compat.user";
    /** the add-on mount result: Z9X, HB, X50 family, RS30, Titan */
    public static final String ADDON = "sys.z9x.gapps";
    /** the add-on mount result: Play 6 */
    public static final String ADDON_PLAY6 = "sys.lumen.gapps";
    static final String ON = "1";
    static final String OFF = "0";

    private GmsHelperPolicy() { }

    /** 1 when Google services are on this unit (the add-on is mounted or the image is the Google edition). */
    public static boolean google(String addon, String addonPlay6) {
        return "ok".equals(addon) || "skip".equals(addon)
                || (addonPlay6 != null && addonPlay6.startsWith("mounted:") && addonPlay6.length() > 8);
    }

    /** Whether a switch value means on: everything but "0" (unset included). */
    public static boolean on(String value) {
        return !OFF.equals(value);
    }

    /** The row: hidden without Google services, else the switch. */
    public static GmsHelperRow row(String addon, String addonPlay6, String value) {
        if (!google(addon, addonPlay6)) return GmsHelperRow.HIDDEN;
        return on(value) ? GmsHelperRow.ON : GmsHelperRow.OFF;
    }

    /** Value of {@link #PROP} for the user's choice. */
    public static String userValue(boolean on) {
        return on ? ON : OFF;
    }

    /**
     * Value a signed data update's default may write to {@link #PROP}, or null to leave it: never once the user
     * chose ({@code userChose} "1"), never when the switch already means that.
     */
    public static String dataDefault(String userChose, String current, boolean defaultOn) {
        if (ON.equals(userChose)) return null;
        return on(current) == defaultOn ? null : userValue(defaultOn);
    }
}
