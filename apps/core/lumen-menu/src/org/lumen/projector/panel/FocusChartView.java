// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/**
 * Draws {@link org.lumen.menu.FocusChart} over the whole window of a manual focus screen (FocusChartPainter).
 * {@link #turn} follows the lens steps as the stock rings do.
 */
public final class FocusChartView extends View {
    private final FocusChartPainter chart;

    /** drawn: FocusChart.FULL or SHORT (FocusChart.drawn of the model's style). */
    public FocusChartView(Context c, String drawn) {
        super(c);
        chart = new FocusChartPainter(drawn);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** One lens step: the outer ring turns with it (dir +1 or -1), the inner one against it. */
    public void turn(int dir) {
        chart.turn(dir);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) { chart.draw(c, getWidth(), getHeight()); }
}
