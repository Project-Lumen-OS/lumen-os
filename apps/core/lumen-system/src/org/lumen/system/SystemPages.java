// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Opens an Android settings screen: the first way of {@link SystemIntent#chain} that some app handles. */
public final class SystemPages {
    private static final String TAG = "LumenSystem";

    private SystemPages() { }

    /** Starts {@code page} in a new task; false when no way starts (the caller tells the user). */
    public static boolean open(Context ctx, SettingsPage page) {
        for (SystemIntent w : SystemIntent.chain(page)) {
            try {
                Intent i = new Intent(w.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (w.pkg != null) i.setPackage(w.pkg);
                if (w.extra != null) i.putExtra(w.extra, w.value);
                if (i.resolveActivity(ctx.getPackageManager()) == null) continue;
                ctx.startActivity(i);
                return true;
            } catch (Throwable t) {
                Log.w(TAG, page + " via " + w + ": " + t);
            }
        }
        return false;
    }
}
