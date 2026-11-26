// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.lumen.menu.quick.QuickLayoutJson;
import org.lumen.menu.quick.QuickStore;

/**
 * The quick menu in Settings.Secure {@code lumen_quick_menu} of the current Android user (secure settings are per
 * user, so every user of the projector has his own menu). The projector apps are platform signed and may write it;
 * the only class that touches the setting. Nothing is written until the user changes his menu.
 */
final class SecureQuickStore implements QuickStore {
    private static final String TAG = "LumenQuickMenu";
    private final ContentResolver cr;

    SecureQuickStore(Context c) { cr = c.getContentResolver(); }

    /** The user in front of the projector when it is not this process's user (a system uid app), else -1. */
    private static int other() {
        try {
            int cur = ActivityManager.getCurrentUser(), me = android.os.UserHandle.myUserId();
            return cur >= 0 && cur != me ? cur : -1;
        } catch (Throwable t) {
            return -1;                                        // no right to ask: our own user is the one
        }
    }

    @Override public String load() {
        try {
            int u = other();
            return u < 0 ? Settings.Secure.getString(cr, QuickLayoutJson.KEY) : Settings.Secure.getStringForUser(cr, QuickLayoutJson.KEY, u);
        } catch (Throwable t) {
            Log.w(TAG, "read: " + t);
            return null;
        }
    }

    @Override public boolean save(String json) { return put(json); }

    @Override public boolean clear() { return put(null); }

    private boolean put(String v) {
        try {
            int u = other();
            return u < 0 ? Settings.Secure.putString(cr, QuickLayoutJson.KEY, v) : Settings.Secure.putStringForUser(cr, QuickLayoutJson.KEY, v, u);
        } catch (Throwable t) {
            Log.w(TAG, "write: " + t);
            return false;
        }
    }
}
