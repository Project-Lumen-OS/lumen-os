package org.lumen.projector.ui;

import android.content.Context;

/** Host stub of the shell's Theme: the numbers the hosts and Panel read. */
public final class Theme {
    public static final float PANEL_WIDTH_FRACTION = 0.34f, PANEL_SLIDE = 608;
    public static final long PANEL_ENTER_MS = 360, PANEL_FADE_MS = 250, PANEL_EXIT_MS = 260;

    public static Object panelInterpolator() { return null; }

    public static float pxf(Context c, float design) { return design; }
}
