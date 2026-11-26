// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

/**
 * The Inter face of the Lumen look at a weight and optical size: the variable font {@link #ASSET} (SIL OFL) every
 * projector app ships with lumen-menu, else the system sans (an app without the asset still draws, only plainer).
 */
public final class Inter {
    public static final String ASSET = "fonts/InterVariable.ttf";
    private static final SparseArray<Typeface> FACES = new SparseArray<>();

    private Inter() {}

    /** {@code weight} 100..900, {@code designSize} the text size in design px (the optical size, 14..32). */
    public static synchronized Typeface of(Context c, int weight, float designSize) {
        int opsz = Math.max(14, Math.min(32, Math.round(designSize)));
        int key = weight * 100 + opsz;
        Typeface t = FACES.get(key);
        if (t != null) return t;
        try {
            t = new Typeface.Builder(c.getAssets(), ASSET)
                    .setFontVariationSettings("'wght' " + weight + ", 'opsz' " + opsz).build();
        } catch (RuntimeException e) {
            t = null;
        }
        if (t == null) t = Typeface.create(Typeface.DEFAULT, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        FACES.put(key, t);
        return t;
    }
}
