// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.ui;

import android.content.Context;
import android.content.SharedPreferences;

import org.lumen.menu.LampOff;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/**
 * A {@link LampOff.Marker} in the app's device-protected preferences, keyed by the kernel boot id: it outlives a
 * restart of the app process and tells this boot from an earlier one.
 */
public final class BootMark implements LampOff.Marker {
    private static final String BOOT_ID = "/proc/sys/kernel/random/boot_id";
    private static final String KEY = "boot";

    private final SharedPreferences prefs;
    private final String boot;

    public BootMark(Context c, String prefsName) {
        prefs = c.createDeviceProtectedStorageContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        boot = bootId();
    }

    @Override public boolean thisBoot() { return !boot.isEmpty() && boot.equals(saved()); }

    @Override public boolean earlierBoot() { return !saved().isEmpty() && !boot.equals(saved()); }

    @Override public void mark(boolean off) {
        if (off && !boot.isEmpty()) prefs.edit().putString(KEY, boot).apply();
        else prefs.edit().remove(KEY).apply();
    }

    private String saved() { return prefs.getString(KEY, ""); }

    private static String bootId() {
        try (FileInputStream in = new FileInputStream(BOOT_ID)) {
            byte[] b = new byte[64];
            int n = in.read(b);
            return n > 0 ? new String(b, 0, n, StandardCharsets.US_ASCII).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
