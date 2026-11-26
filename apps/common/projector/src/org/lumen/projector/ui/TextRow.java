// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.view.View;

/** Multi-line, non-focusable text (dialog message, hints). */
public class TextRow extends View {
    private final TextPaint paint;
    private final int baseColor;
    private final float padH, padV;
    private CharSequence text;
    private Paint focusPaint;
    private float radius;
    private StaticLayout layout;
    private int layoutWidth = -1;

    public TextRow(Context c, CharSequence text) {
        this(c, text, Theme.ROW_VALUE_SIZE, Theme.TEXT_DIM);
    }

    public TextRow(Context c, CharSequence text, float designSize, int color) {
        super(c);
        this.text = text == null ? "" : text;
        paint = Theme.text(c, designSize, color, false);
        baseColor = color;
        padH = Theme.pxf(c, Theme.ROW_PAD_H);
        padV = Theme.pxf(c, 12);
        setFocusable(false);
        setWillNotDraw(false);
    }

    /** A long text the reader walks with the D-pad (Licences): it takes the focus and shows it as a row does. */
    public TextRow readable() {
        focusPaint = Theme.fill(Theme.ROW_BG_FOCUSED);
        radius = Theme.pxf(getContext(), Theme.ROW_RADIUS);
        setFocusable(true);
        return this;
    }

    public void setText(CharSequence t) {
        text = t == null ? "" : t;
        layoutWidth = -1;
        requestLayout();
        invalidate();
    }

    private void build(int w) {
        int inner = Math.max(1, (int) (w - 2 * padH));
        if (inner == layoutWidth && layout != null) return;
        // Paragraph direction follows the row (RTL locale: ALIGN_NORMAL = right-aligned, also for a
        // line that starts with Latin text such as "HDMI 1 · HDR10").
        layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, inner)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setTextDirection(getLayoutDirection() == LAYOUT_DIRECTION_RTL
                        ? TextDirectionHeuristics.FIRSTSTRONG_RTL : TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .build();
        layoutWidth = inner;
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        layoutWidth = -1;                                 // paragraph direction changed: rebuild
        requestLayout();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        build(w);
        setMeasuredDimension(w, Math.round(layout.getHeight() + 2 * padV));
    }

    @Override
    protected void onDraw(Canvas c) {
        build(getWidth());
        boolean focused = focusPaint != null && isFocused();
        if (focused) c.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, focusPaint);
        paint.setColor(focused ? Theme.TEXT_FOCUSED : baseColor);
        c.save();
        c.translate(padH, padV);
        layout.draw(c);
        c.restore();
    }
}
