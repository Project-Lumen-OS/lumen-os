// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * What the remote app keys (slots 1..5, stock VIDEO0..VIDEO4) open while the user gave them no app ("As before" of
 * "Remote buttons", apps/core/lumen-remote keys: a key with an app never reaches this class). Z9X remotes differ: on
 * one remote (2026-11-05, getevent) the "China TV" / iQIYI key is scancode 230 (VIDEO1, slot 2) and the bilibili key
 * is 134 (VIDEO4, slot 5), on others 235 (slot 3) and 144 (slot 4). So slot 2 opens what slot 3 opens and slot 5 what
 * slot 4 opens (keySlot); slot 1 stays its own.
 */
final class AppSlots {
    static final String TAG = "Z9xSlots";
    static final String PLAY_STORE = "com.android.vending";
    /** Default without Play (Lumen OS without Google): the key opens Lumen Home's Apps page (ACTION_ALL_APPS). */
    static final String HOME_APPS = "org.lumen.home";
    /** Slot 3 default: first installed of these, else the Play Store, else Lumen Home's Apps page. */
    private static final String[] SLOT3_DEFAULTS = {
            "com.google.android.youtube.tv", "com.teamsmart.videomanager.tv"};

    private AppSlots() {}

    static String defaultPackage(Context c, int slot) {
        if (slot == 3) {
            for (String p : SLOT3_DEFAULTS) if (launchIntent(c, p) != null) return p;
        }
        return launchIntent(c, PLAY_STORE) != null ? PLAY_STORE : HOME_APPS;
    }

    /** Leanback launch intent first, then the normal one (HOME_APPS: Lumen Home's AllAppsAlias). */
    static Intent launchIntent(Context c, String pkg) {
        if (pkg == null) return null;
        if (HOME_APPS.equals(pkg)) {
            Intent a = new Intent(Intent.ACTION_ALL_APPS).setPackage(HOME_APPS);
            try {
                return c.getPackageManager().resolveActivity(a, 0) != null ? a : null;
            } catch (Throwable t) {
                return null;
            }
        }
        try {
            PackageManager pm = c.getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            return i;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The settings slot a key slot follows: 2 -> 3 (iQIYI row), 5 -> 4 (bilibili row), else itself. */
    static int keySlot(int slot) {
        return slot == 2 ? 3 : slot == 5 ? 4 : slot;
    }

    /** Launch the default app of key slot 1..5 (called on the main thread from KeyReceiver). */
    static void launch(Context c, int keySlot) {
        int slot = keySlot(keySlot);
        if (slot != keySlot) Log.i(TAG, "key slot " + keySlot + " follows slot " + slot);
        String pkg = defaultPackage(c, slot);
        Intent i = launchIntent(c, pkg);
        if (i == null) {
            Log.w(TAG, "slot " + slot + ": nothing to launch (" + pkg + ")");
            Ui.toast(c, R.string.app_not_found);
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            Ui.wakeFromDream(c);
            c.startActivity(i);
            Log.i(TAG, "slot " + slot + " -> " + pkg);
        } catch (Throwable t) {
            Log.w(TAG, "slot " + slot + " launch " + pkg + ": " + t);
            Ui.toast(c, R.string.app_not_found);
        }
    }
}
