// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * The classic Android TV home screen as a schematic (the design notes launcher: blocks only, no Google artwork) on its
 * own dark grey: two header pills on the left, a row of six app tiles, two rows of four wide cards. Drawn at the
 * spec's 820 x 462 and scaled to the view. The Lumen Home card shows a real picture of Home 2.0 instead
 * (res/drawable*-nodpi/setup_home_preview_*.jpg).
 */
public class LauncherPreview extends View {
    private static final float W = 820f, GAP = 14f;
    private static final int BG = 0xFF202124;
    private static final int[] TILES = {0xFF3C4043, 0xFF5F6368, 0xFF3C4043, 0xFF4A4E52, 0xFF3C4043, 0xFF5F6368};
    private static final int[] WIDE = {0xFF2D3134, 0xFF3C4043, 0xFF2D3134, 0xFF3C4043};
    private final Paint mP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mR = new RectF();

    public LauncherPreview(Context c) {
        super(c);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
    }

    private void rr(Canvas c, float k, float l, float t, float w, float h, float rad, int color) {
        mP.setColor(color);
        mR.set(l * k, t * k, (l + w) * k, (t + h) * k);
        c.drawRoundRect(mR, rad * k, rad * k, mP);
    }

    @Override
    protected void onDraw(Canvas c) {
        float k = getWidth() / W;
        c.drawColor(BG);
        float x = 44, y = 40;
        rr(c, k, x, y, 120, 20, 10, 0xFF5F6368);
        rr(c, k, x + 132, y, 60, 20, 10, 0xFF3C4043);
        y += 20 + 40;
        // the rows fill the width between the 44 margins (the spec's flex rows shrink 118 / 180 wide blocks to fit)
        float inner = W - 2 * x, tw = (inner - (TILES.length - 1) * GAP) / TILES.length, ww = (inner - (WIDE.length - 1) * GAP) / WIDE.length;
        for (int i = 0; i < TILES.length; i++) rr(c, k, x + i * (tw + GAP), y, tw, 66, 8, TILES[i]);
        y += 66 + 30;
        for (int r = 0; r < 2; r++) {
            for (int i = 0; i < WIDE.length; i++) rr(c, k, x + i * (ww + GAP), y, ww, 102, 8, WIDE[i]);
            y += 102 + 22;
        }
    }
}
