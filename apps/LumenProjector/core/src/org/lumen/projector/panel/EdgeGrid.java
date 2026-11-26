// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.Theme;

/** Grey grid with a white line on the outermost pixels (the picture edge), and the shared card pieces. */
final class EdgeGrid extends View {
    private static final int BG = 0xFF8A8A8A, LINE = 0xFFE6E6E6;
    private final Paint line = new Paint(), edge = new Paint();

    EdgeGrid(Context c) {
        super(c);
        line.setColor(LINE);
        edge.setColor(0xFFFFFFFF);
    }

    private float px(float design) { return Math.max(1f, Math.round(Theme.pxf(getContext(), design))); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cell = px(120), t = px(2), e = px(6);
        c.drawColor(BG);
        for (float x = cell; x < w - 1; x += cell) c.drawRect(x - t / 2, 0, x + t / 2, h, line);
        for (float y = cell; y < h - 1; y += cell) c.drawRect(0, y - t / 2, w, y + t / 2, line);
        c.drawRect(0, 0, w, e, edge);
        c.drawRect(0, h - e, w, h, edge);
        c.drawRect(0, 0, e, h, edge);
        c.drawRect(w - e, 0, w, h, edge);
    }

    static WindowManager.LayoutParams fullscreen(Context c, String tag) {
        WindowManager.LayoutParams lp = OverlayHost.fullscreenParams(true);
        try {
            android.graphics.Point real = new android.graphics.Point();
            c.getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(real);
            if (real.x > 0 && real.y > 0) { lp.width = real.x; lp.height = real.y; }
        } catch (Throwable t) {
            Log.w(tag, "real display size: " + t);
        }
        lp.x = 0;
        lp.y = 0;
        lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        return lp;
    }

    static LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Theme.px(c, 36);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.PANEL_BG);
        bg.setCornerRadius(Theme.px(c, 24));
        card.setBackground(bg);
        return card;
    }

    static TextView text(Context c, CharSequence s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Theme.pxf(c, size));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Theme.px(c, 6), 0, Theme.px(c, 6));
        return t;
    }
}
