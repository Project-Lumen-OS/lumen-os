// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

import java.util.List;

/**
 * Host test of the Android settings screens of every Lumen app (SystemIntent), run by test/run.sh. Bluetooth opens
 * "Remotes & Accessories" of LineageOS 21 TvSettings directly: its manifest has no BLUETOOTH_SETTINGS activity (the old
 * intent fell back to the settings root), SliceActivity takes android.settings.SLICE_SETTINGS with a slice_uri extra,
 * and connected_devices_slice_uri, the slice of the main page's "Remotes & Accessories" entry, is the general path of
 * com.android.tv.settings.accessories.sliceprovider (TvSettingsTwoPanel.apk of the Z9X, HB and Play 6 images).
 */
public final class SystemIntentTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    private static boolean is(SystemIntent w, String action, String pkg, String extra, String value) {
        return action.equals(w.action) && eq(pkg, w.pkg) && eq(extra, w.extra) && eq(value, w.value);
    }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    public static void main(String[] a) {
        List<SystemIntent> bt = SystemIntent.chain(SettingsPage.BLUETOOTH);
        check(bt.size() == 3, "bluetooth: three ways, got " + bt);
        if (bt.size() == 3) {
            check(is(bt.get(0), "android.settings.SLICE_SETTINGS", "com.android.tv.settings", "slice_uri",
                    "content://com.android.tv.settings.accessories.sliceprovider/general"),
                    "bluetooth: Remotes & Accessories of TvSettings first, got " + bt.get(0));
            check(is(bt.get(1), "android.settings.BLUETOOTH_SETTINGS", null, null, null),
                    "bluetooth: then the Bluetooth settings of any app, got " + bt.get(1));
            check(is(bt.get(2), "android.settings.SETTINGS", null, null, null),
                    "bluetooth: then the settings root, got " + bt.get(2));
        }
        List<SystemIntent> net = SystemIntent.chain(SettingsPage.NETWORK);
        check(net.size() == 2 && is(net.get(0), "android.settings.WIFI_SETTINGS", null, null, null)
                && is(net.get(1), "android.settings.SETTINGS", null, null, null), "network: Wi-Fi, then the root, got " + net);
        for (SettingsPage f : SettingsPage.values()) {
            List<SystemIntent> c = SystemIntent.chain(f);
            check(!c.isEmpty() && is(c.get(c.size() - 1), "android.settings.SETTINGS", null, null, null), f + ": ends on the root");
            for (SystemIntent w : c) check(w.extra == null || w.pkg != null, f + ": a page by extra names its app: " + w);
        }
        System.out.println("SystemIntentTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
