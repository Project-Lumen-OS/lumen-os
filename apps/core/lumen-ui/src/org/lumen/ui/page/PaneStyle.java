// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui.page;

import android.content.Context;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.TextView;

import org.lumen.ui.Inter;

/**
 * The measures of the two-pane page ({@link TwoPane}): design px of the 1920x1080 interface, taken from the approved
 * mock (report screen option A, owner pick 2026-11-26). Colours come from the shared
 * {@link org.lumen.ui.Palette}, never from here.
 */
public final class PaneStyle {
    private PaneStyle() {}

    public static final float DESIGN_W = 1920f;

    // ------------------------------------------------------------------ the guide (left) and the pane (right)
    static final int GUIDE_W = 800, GUIDE_PAD_START = 120, GUIDE_PAD_END = 72, PAD_BOTTOM = 120;
    static final int PANE_PAD_START = 64, PANE_PAD_END = 120, GAP = 12, GAP_DENSE = 4;
    static final int FOOT_W = 620, FOOT_BOTTOM = 72, FOOT_ICON = 26, FOOT_GAP = 14;
    /** The field's glow: centre at 30 % / 10 % of the screen, radius in design px. */
    static final float GLOW_X = 0.3f, GLOW_Y = 0.1f;
    static final int GLOW_R = 1000;

    // ------------------------------------------------------------------ type: size, weight, line height (x size)
    static final int CRUMB = 26, W_CRUMB = 500, CRUMB_GAP = 22;
    static final int TITLE = 64, W_TITLE = 600;
    static final float TITLE_LH = 1.08f, TITLE_TRACK = -0.015f;
    static final int BODY = 30, BODY_GAP = 28;
    static final float BODY_LH = 1.42f;
    static final int META_LABEL = 24, META_VALUE = 30, W_META = 500, META_GAP = 56, META_VALUE_GAP = 8;
    static final int FOOT = 22;
    static final float FOOT_LH = 1.35f;

    // ------------------------------------------------------------------ rows
    static final int ROW_H = 108, ROW_PAD = 32, ROW_RADIUS = 20, ROW_ICON = 36, ROW_ICON_GAP = 28;
    static final int ROW_TITLE = 32, W_ROW_TITLE = 500, ROW_SUB = 24, ROW_SUB_GAP = 6;
    static final float ROW_FOCUS_SCALE = 1.03f, SUB_ON_FOCUS = 0.72f;
    static final int ROW_ELEVATION = 18;
    static final int SWITCH_W = 76, SWITCH_H = 42, KNOB = 32, KNOB_INSET = 5, CHEVRON = 34;

    // ------------------------------------------------------------------ lists, progress, code
    static final int SECTION = 24, W_SECTION = 600, SECTION_TOP = 6, SECTION_BOTTOM = 4, SECTION_BREAK = 26;
    static final float SECTION_TRACK = 0.02f;
    static final int ITEM = 27, ITEM_ICON = 32, ITEM_GAP = 22, ITEM_PAD_V = 7;
    static final int PROG_TITLE = 34, W_PROG = 500, PROG_PAD_TOP = 8, PROG_PAD_BOTTOM = 28, BAR_H = 10, BAR_GAP = 28,
            PROG_SUB = 24, PROG_SUB_GAP = 18;
    static final int CODE_LABEL = 24, CODE = 92, W_CODE = 600, CODE_GAP = 10, CODE_PAD_V = 40, CODE_PAD_H = 44,
            CODE_RADIUS = 24, CODE_BOTTOM = 18;
    static final float CODE_TRACK = 0.01f;
    /** Inter's open digits and single-storey a, as the mock draws every text. */
    static final String FEATURES = "'cv11', 'ss01'";

    /** Real px per design px: the screen's long side / 1920 (1 on the 1080p UI, 2 at the 4K default). */
    public static float scale(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        int w = Math.max(m.widthPixels, m.heightPixels);
        return w <= 0 ? 1f : w / DESIGN_W;
    }

    public static int px(Context c, float design) {
        return Math.round(design * scale(c));
    }

    /** A text in Inter at a design size and weight, its line height {@code lh} x size (0: the font's own). */
    public static TextView text(Context c, float size, int weight, int color, float lh) {
        TextView t = new TextView(c);
        t.setIncludeFontPadding(false);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * scale(c));
        t.setTypeface(Inter.of(c, weight, size));
        t.setFontFeatureSettings(FEATURES);
        t.setTextColor(color);
        t.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY);
        if (lh > 0) t.setLineHeight(Math.round(size * lh * scale(c)));
        return t;
    }
}
