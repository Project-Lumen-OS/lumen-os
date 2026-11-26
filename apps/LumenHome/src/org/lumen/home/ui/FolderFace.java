// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import org.lumen.home.data.Card;
import org.lumen.home.img.ImageLoader;

import java.util.List;

/**
 * The face of a folder tile on the dock and the Apps page: the banners of its first four apps, two by two, on a calm
 * field of the theme (the shape of iOS and Google TV folders), drawn into one bitmap the tile shows like any banner.
 * Each banner comes in through the image loader and is painted into its cell when it arrives.
 */
public final class FolderFace {
    private static final int PAD = 14, GAP = 8, RADIUS = 8, CELLS = 4;

    private FolderFace() {}

    /** Starts the face of {@code t} for the folder's {@code apps}; the requests go into {@code reqs} (cancelled on rebind). */
    public static void bind(TileView t, List<Card> apps, ImageLoader images, List<ImageLoader.Request> reqs) {
        int w = Theme.px(t.designW()), h = Theme.px(t.designH());
        if (w <= 0 || h <= 0) return;
        Bitmap face = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(face);
        cv.drawColor(Theme.P.surface);
        int pad = Theme.px(PAD), gap = Theme.px(GAP);
        int cw = (w - 2 * pad - gap) / 2, ch = (h - 2 * pad - gap) / 2;
        Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
        empty.setColor(Theme.P.surface2);
        float r = Theme.pxf(RADIUS);
        for (int i = 0; i < CELLS; i++) {
            RectF cell = cell(i, pad, gap, cw, ch);
            cv.drawRoundRect(cell, r, r, empty);
        }
        t.setBitmap(face);
        for (int i = 0; i < CELLS && i < apps.size(); i++) {
            final RectF cell = cell(i, pad, gap, cw, ch);
            Card k = apps.get(i);
            if (k.image == null || k.image.isEmpty()) continue;
            ImageLoader.Request q = images.load(k.image, cw, ch, ImageLoader.KIND_BANNER, k.pkg, k.title, (b, col) -> {
                if (b == null) return;
                Canvas c = new Canvas(face);
                Path clip = new Path();
                clip.addRoundRect(cell, r, r, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
                c.drawBitmap(soft(b), null, cell, bp);
                c.restore();
                t.setBitmap(face);
            });
            if (q != null) reqs.add(q);
        }
    }

    /** A HARDWARE bitmap cannot be drawn into a software canvas: a software copy. */
    private static Bitmap soft(Bitmap b) {
        return b.getConfig() == Bitmap.Config.HARDWARE ? b.copy(Bitmap.Config.ARGB_8888, false) : b;
    }

    private static RectF cell(int i, int pad, int gap, int cw, int ch) {
        int x = pad + (i % 2) * (cw + gap), y = pad + (i / 2) * (ch + gap);
        return new RectF(new Rect(x, y, x + cw, y + ch));
    }
}
