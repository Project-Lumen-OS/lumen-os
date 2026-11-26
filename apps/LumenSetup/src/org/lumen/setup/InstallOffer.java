// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup;

import android.content.Context;
import android.content.Intent;

/** Install offer on the language step: none in this build. */
public final class InstallOffer {
    private InstallOffer() {}

    public static boolean gate(String a, String b) {
        return false;
    }

    public static boolean available(Context c) {
        return false;
    }

    public static Intent intent() {
        return new Intent();
    }

    public static String text(Context c, String name, String fallback) {
        return fallback;
    }
}
