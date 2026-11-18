package org.lumen.projector.ui;

import android.content.Context;

/** Host stub of the shell's Theme: design px times -Dtip.scale (1 = a 1920 px wide screen). */
public final class Theme {
    public static final float SCALE = Float.parseFloat(System.getProperty("tip.scale", "1"));

    private Theme() {}

    public static float pxf(Context c, float design) { return design * SCALE; }
}
