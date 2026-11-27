// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.lumen.menu.EdgeFrame;

/**
 * Draws {@link EdgeFrame} over the whole window: the black page, the edge bands and, for GUIDES, the dashed guides.
 * Whole-pixel rects without anti-alias, scaled from the 1920 x 1080 design to the window. Put it behind a geometry
 * screen's card at full size; the projector scales and warps it with the picture, so it marks the picture's edge.
 */
public final class EdgeFrameView extends View {
    private final String kind;
    private final Paint p = new Paint();

    public EdgeFrameView(Context c, String kind) {
        super(c);
        this.kind = kind;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float sx = w / EdgeFrame.DESIGN_W, sy = h / EdgeFrame.DESIGN_H;
        c.drawColor(EdgeFrame.PAGE);
        float[][] bands = EdgeFrame.bands(kind);
        int[] colours = EdgeFrame.colours(kind);
        for (int i = 0; i < bands.length; i++) {
            p.setColor(colours[i]);
            float fx = px(bands[i][0] * sx), tx = px(bands[i][1] * sx), fy = px(bands[i][0] * sy), ty = px(bands[i][1] * sy);
            c.drawRect(fx, fy, w - fx, ty, p);                   // top
            c.drawRect(fx, h - ty, w - fx, h - fy, p);           // bottom
            c.drawRect(fx, fy, tx, h - fy, p);                   // left
            c.drawRect(w - tx, fy, w - fx, h - fy, p);           // right
        }
        for (float[] g : EdgeFrame.guides(kind)) {
            boolean vertical = g[0] == 1;
            p.setColor(g[6] == 1 ? EdgeFrame.GUIDE_CENTRE : EdgeFrame.GUIDE);
            float along = vertical ? sy : sx, across = vertical ? sx : sy;
            float a0 = px(g[1] * across), a1 = a0 + Math.max(1f, px(g[2] * across));
            for (float[] d : EdgeFrame.dashes(g[3], g[4], g[5])) {
                float s = px(d[0] * along), e = Math.max(s + 1f, px(d[1] * along));
                if (vertical) c.drawRect(a0, s, a1, e, p);
                else c.drawRect(s, a0, e, a1, p);
            }
        }
    }

    private static float px(float v) { return Math.round(v); }
}
