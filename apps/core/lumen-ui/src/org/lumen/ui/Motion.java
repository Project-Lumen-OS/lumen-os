package org.lumen.ui;

import android.content.Context;
import android.provider.Settings;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** The motion of the Lumen 2.0 look: curves, and the system "remove animations" switch. */
public final class Motion {
    /** cubic-bezier(0.2, 0, 0, 1): focus, row and page scroll, panels, setup steps. */
    public static final Interpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** cubic-bezier(0, 0, 0, 1): the hero coming in. */
    public static final Interpolator ENTER = new PathInterpolator(0f, 0f, 0f, 1f);
    /** Home A "Витрина": every focus, lift and colour change, ease-out cubic (the approved mock, the stock EaseCubic). */
    public static final Interpolator SHOWCASE = new PathInterpolator(0f, 0f, 0.58f, 1f);
    /** cubic-bezier(0.4, 0, 0.2, 1), the standard ease in and out: the theme cross fade (ThemeFade). */
    public static final Interpolator STANDARD = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private Motion() {}

    /** True when the user turned animations off (animator scale 0): moves and scales become 120 ms fades. */
    public static boolean reduced(Context c) {
        try {
            return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
