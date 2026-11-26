// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.TextPaint;
import android.view.View;

import org.lumen.projector.R;

/**
 * The four-corner keystone screen of every projector that runs it from a tool screen (X50 family CornerActivity), drawn
 * as the stock newsettings KeyStoneWind four-point page (layout keystone_view_layout, 1920 x 1080 design px): an opaque
 * grey page (color_bg_pure_2 #4A4A4A), the title on top with the circle hint under it, the 1728 x 888 frame with a small
 * ring on each corner (keystone_4_bk), the centre circle (320) with its cross (96), one corner mark per corner
 * (KeyStoneArrowBt: a 40 px dot, the arrows 48 px at 44 px from it) where only the chosen corner is in the accent colour
 * and shows the arrows of the directions the projector still accepts (stock arrow probes), and the keys at the bottom.
 * Our own drawing, no stock asset. The window is composed before the projector warps the picture, so the frame and the
 * marks move with the corners on the wall, as on stock. State only: the caller reads the projector and calls
 * {@link #show}.
 */
public final class CornerView extends View {
    /** Stock color_bg_pure_2. */
    static final int PAGE = 0xFF4A4A4A;
    static final int LINE = 0x99FFFFFF, DOT = 0xCCFFFFFF;
    /** Corner order of the vendor and of every Lumen corner tool: top left, top right, bottom right, bottom left. */
    static final int TL = 0, TR = 1, BR = 2, BL = 3;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final TextPaint title, sub, keys;
    private final String titleText, circleText, moveText, moreText;
    private final String[] names;
    private int corner;
    private boolean up, down, left, right;

    public CornerView(Context c) {
        super(c);
        title = QpdStyle.text(c, 36, 600, QpdStyle.INK);
        sub = QpdStyle.text(c, 24, 400, QpdStyle.MUTE);
        keys = QpdStyle.text(c, 22, 400, QpdStyle.INK);
        titleText = c.getString(R.string.lumen_corner_title);
        circleText = c.getString(R.string.lumen_corner_circle);
        moveText = c.getString(R.string.lumen_corner_keys_move);
        moreText = c.getString(R.string.lumen_corner_keys_more);
        names = new String[]{c.getString(R.string.lumen_corner_tl), c.getString(R.string.lumen_corner_tr),
                c.getString(R.string.lumen_corner_br), c.getString(R.string.lumen_corner_bl)};
        line.setStyle(Paint.Style.STROKE);
    }

    /** The chosen corner (TL..BL) and the directions it can still move; redraws. */
    public void show(int corner, boolean up, boolean down, boolean left, boolean right) {
        this.corner = Math.floorMod(corner, 4);
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
        invalidate();
    }

    /** The chosen corner's name (what the screen reader and the tests read). */
    public String cornerName() { return names[corner]; }

    @Override
    protected void onDraw(Canvas c) {
        float s = getWidth() / 1920f, w = getWidth(), h = getHeight();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(PAGE);
        c.drawRect(0, 0, w, h, fill);
        float l = w / 2 - 864 * s, r = w / 2 + 864 * s, t = h / 2 - 444 * s, b = h / 2 + 444 * s;
        line.setColor(LINE);
        line.setStrokeWidth(2 * s);
        c.drawRect(l, t, r, b, line);
        float[][] at = {{l, t}, {r, t}, {r, b}, {l, b}};
        for (float[] p : at) c.drawCircle(p[0], p[1], 9 * s, line);
        line.setStrokeWidth(4 * s);
        c.drawCircle(w / 2, h / 2, 160 * s, line);
        c.drawLine(w / 2 - 48 * s, h / 2, w / 2 + 48 * s, h / 2, line);
        c.drawLine(w / 2, h / 2 - 48 * s, w / 2, h / 2 + 48 * s, line);
        // stock KeyStoneArrowBt: 150 x 150 at 39 / 31 px from the window edges, the dot in its centre
        float[][] mark = {{114 * s, 106 * s}, {w - 114 * s, 106 * s}, {w - 114 * s, h - 106 * s}, {114 * s, h - 106 * s}};
        for (int i = 0; i < 4; i++) {
            fill.setColor(i == corner ? QpdStyle.ACCENT : DOT);
            c.drawCircle(mark[i][0], mark[i][1], (i == corner ? 20 : 14) * s, fill);
        }
        float x = mark[corner][0], y = mark[corner][1], d = 44 * s, a = 14 * s;
        fill.setColor(QpdStyle.ACCENT);
        if (up) arrow(c, x, y - d, 0, -1, a);
        if (down) arrow(c, x, y + d, 0, 1, a);
        if (left) arrow(c, x - d, y, -1, 0, a);
        if (right) arrow(c, x + d, y, 1, 0, a);
        centred(c, titleText, title, w, 160 * s - title.ascent());
        centred(c, circleText, sub, w, 160 * s - title.ascent() + 8 * s + title.descent() - sub.ascent());
        centred(c, moveText, keys, w, b - 96 * s);
        centred(c, moreText, keys, w, b - 52 * s);
    }

    private void arrow(Canvas c, float x, float y, int dx, int dy, float a) {
        path.reset();
        path.moveTo(x + dx * a, y + dy * a);
        path.lineTo(x - dy * a - dx * a * 0.4f, y + dx * a - dy * a * 0.4f);
        path.lineTo(x + dy * a - dx * a * 0.4f, y - dx * a - dy * a * 0.4f);
        path.close();
        c.drawPath(path, fill);
    }

    private static void centred(Canvas c, String s, TextPaint p, float w, float baseline) {
        c.drawText(s, (w - p.measureText(s)) / 2f, baseline, p);
    }
}
