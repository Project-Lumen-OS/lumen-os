// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.lumen.projector.R;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.TextRow;

/**
 * Miracast diagnostics (dev.mirror_diag, For developers): the screen mirroring receiver's state and its stage report,
 * read from the sink's control provider (org.lumen.miracast StateProvider, permission CONTROL that every projector app
 * holds), one readable row per block, so the D-pad walks and scrolls them and BACK returns. The report is the sink's
 * own English text, as in its dumpsys.
 */
final class MirrorDiagPage {
    static final String AUTHORITY = "org.lumen.miracast";
    private static final Uri URI = Uri.parse("content://" + AUTHORITY);

    private MirrorDiagPage() {}

    /** The image has the sink as a system app. */
    static boolean available(Context c) {
        ProviderInfo pi = c.getPackageManager().resolveContentProvider(AUTHORITY, 0);
        return pi != null && AUTHORITY.equals(pi.packageName) && pi.applicationInfo != null
                && (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    static Page build(Context app) {
        Page page = new Page(app.getString(R.string.lumen_m_mirror_diag));
        Bundle st = call(app, "state"), d = call(app, "diag");
        if (st == null || d == null) {
            page.add(new TextRow(app, "receiver: no answer from " + AUTHORITY).readable());
            return page;
        }
        page.add(new TextRow(app, "receiver: " + st.getString("code") + ", enabled=" + st.getBoolean("enabled")
                + ", ask=" + st.getBoolean("ask") + ", name=" + st.getString("name")
                + (empty(st.getString("detail")) ? "" : "\n" + st.getString("detail"))).readable());
        for (String key : new String[] {"verdict", "report", "last"}) {
            String text = d.getString(key);
            if (empty(text)) continue;
            for (String part : text.trim().split("\n\n")) page.add(new TextRow(app, part.trim()).readable());
        }
        return page;
    }

    private static Bundle call(Context c, String method) {
        try {
            return c.getContentResolver().call(URI, method, null, null);
        } catch (RuntimeException e) {
            Log.w("LumenMenu", "screen mirroring " + method + ": " + e);
            return null;
        }
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
