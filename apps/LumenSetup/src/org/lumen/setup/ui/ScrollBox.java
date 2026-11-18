package org.lumen.setup.ui;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * A scrolling list of rows that keeps the focus look whole: the box reaches {@link #ROOM} design px
 * past the column on both sides ({@link #TOP} above, {@link #BOTTOM} below) and clips there, so a focused row's scale
 * and shadow draw fully while rows scrolled out stay hidden behind the faded edges. {@link #lp} gives it negative
 * margins of the same size, so the rows sit exactly where a plain list would. The room above and below lives inside
 * the list (not as scroll padding), so the fades sit at the box's real edges. The scroll view itself never takes
 * focus: with no rows yet, focus goes to the next real row, never to an invisible container.
 */
public final class ScrollBox extends FrameLayout {
    /** Design px of room on each side: the 1.02 row scale (about 10) plus the focus shadow (blur 23). */
    public static final int ROOM = 36, TOP = 8, BOTTOM = 28;

    public final ScrollView scroll;
    public final LinearLayout list;

    public ScrollBox(Context c, float fadeDesign) {
        super(c);
        setClipChildren(true);
        setClipToPadding(true);
        scroll = new ScrollView(c);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(fadeDesign > 0);
        scroll.setFadingEdgeLength(Ui.px(fadeDesign));
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setPadding(Ui.px(ROOM), 0, Ui.px(ROOM), 0);
        list = Ui.vbox(c);
        list.setPadding(0, Ui.px(TOP), 0, Ui.px(BOTTOM));
        scroll.addView(list);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /**
     * Layout params in a vertical column: the room reaches past the column on every side; {@code weight} 1 lets it
     * shrink. {@code height} counts the rows only (the room above and below is added).
     */
    public static LinearLayout.LayoutParams lp(int height, float weight) {
        int h = height > 0 ? height + Ui.px(TOP) + Ui.px(BOTTOM) : height;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h, weight);
        p.setMarginStart(-Ui.px(ROOM));
        p.setMarginEnd(-Ui.px(ROOM));
        p.topMargin = -Ui.px(TOP);
        p.bottomMargin = -Ui.px(BOTTOM);
        return p;
    }
}
