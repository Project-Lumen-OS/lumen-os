// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.Theme;

/**
 * The shared pieces of the geometry cards (zoom and shift, rotation): the full-screen window and the card. The picture
 * edge behind a card is the stock frame, org.lumen.projector.panel.EdgeFrameView (lumen-menu).
 */
final class EdgeGrid {
    private EdgeGrid() {}

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
