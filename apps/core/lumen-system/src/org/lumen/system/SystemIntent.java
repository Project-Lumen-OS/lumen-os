// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One way to open an Android settings screen: an intent action, the package that must handle it (null: any) and one
 * string extra (null: none). {@link #chain} lists the ways of a page best first. {@link SystemPages#open} starts the
 * first way some app handles and goes on with the next one when it is missing or refuses, so a model whose settings
 * app lacks a screen never crashes and always ends on the settings root.
 */
public final class SystemIntent {
    /** LineageOS 21 TvSettings (TvSettingsTwoPanel) and the screens of it the pages open. */
    static final String TV_SETTINGS = "com.android.tv.settings";
    /** TvSettings SliceActivity: shows the slice named by the {@code slice_uri} extra as a settings page. */
    static final String SLICE_SETTINGS = "android.settings.SLICE_SETTINGS", SLICE_URI = "slice_uri";
    /** The "Remotes & Accessories" page: the slice of the main page's entry (connected_devices_slice_uri). */
    static final String ACCESSORIES = "content://com.android.tv.settings.accessories.sliceprovider/general";
    /** Settings.ACTION_BLUETOOTH_SETTINGS, ACTION_WIFI_SETTINGS, ACTION_SETTINGS (the root every model has). */
    static final String BLUETOOTH = "android.settings.BLUETOOTH_SETTINGS", WIFI = "android.settings.WIFI_SETTINGS",
            ROOT = "android.settings.SETTINGS";
    /** Settings.ACTION_LOCALE_SETTINGS, DATE_SETTINGS, INTERNAL_STORAGE_SETTINGS, SYSTEM_UPDATE_SETTINGS (Lumen updater),
     *  DEVICE_INFO_SETTINGS, APPLICATION_DEVELOPMENT_SETTINGS. */
    static final String LANGUAGE = "android.settings.LOCALE_SETTINGS", DATE_TIME = "android.settings.DATE_SETTINGS",
            STORAGE = "android.settings.INTERNAL_STORAGE_SETTINGS", UPDATE = "android.settings.SYSTEM_UPDATE_SETTINGS",
            ABOUT = "android.settings.DEVICE_INFO_SETTINGS", DEVELOPER = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS";

    public final String action, pkg, extra, value;

    SystemIntent(String action, String pkg, String extra, String value) {
        this.action = action;
        this.pkg = pkg;
        this.extra = extra;
        this.value = value;
    }

    static SystemIntent of(String action) { return new SystemIntent(action, null, null, null); }

    /** The ways to open {@code page}, best first, the settings root last. */
    public static List<SystemIntent> chain(SettingsPage page) {
        switch (page) {
            case BLUETOOTH:
                return Arrays.asList(new SystemIntent(SLICE_SETTINGS, TV_SETTINGS, SLICE_URI, ACCESSORIES),
                        of(BLUETOOTH), of(ROOT));
            case NETWORK:
                return Arrays.asList(of(WIFI), of(ROOT));
            case LANGUAGE: return Arrays.asList(of(LANGUAGE), of(ROOT));
            case DATE_TIME: return Arrays.asList(of(DATE_TIME), of(ROOT));
            case STORAGE: return Arrays.asList(of(STORAGE), of(ROOT));
            case UPDATE: return Arrays.asList(of(UPDATE), of(ROOT));
            case ABOUT: return Arrays.asList(of(ABOUT), of(ROOT));
            case DEVELOPER: return Arrays.asList(of(DEVELOPER), of(ROOT));
            default:
                return Collections.singletonList(of(ROOT));
        }
    }

    @Override public String toString() {
        return action + (pkg == null ? "" : " " + pkg) + (extra == null ? "" : " " + extra + "=" + value);
    }
}
