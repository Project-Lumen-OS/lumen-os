// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.wallcolor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * Draws WallColorPattern: the measured picture (full screen, animated from {@link #start}) or, with {@code preview},
 * a still picture of it for the menu card. The vendor's trans is {@link #setLoopAlpha}; the black ground stays.
 */
final class PatternView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path corner = new Path();
    private final boolean preview;
    private final int disc;
    private final String running;
    private long started = -1;
    private float loopAlpha = 1f;

    PatternView(Context c, boolean preview, int type, String running) {
        super(c);
        this.preview = preview;
        this.disc = WallColorPattern.disc(type);
        this.running = running;
        text.setColor(WallColorPattern.TEXT);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    void start() {
        started = SystemClock.uptimeMillis();
        invalidate();
    }

    void setLoopAlpha(float a) {
        loopAlpha = Math.max(0f, Math.min(1f, a));
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float sx = getWidth() / WallColorPattern.W, sy = getHeight() / WallColorPattern.H;
        canvasBlack(c);
        if (preview) {
            loop(c, sx, sy, WallColorPattern.LOOP_FIRST, 1f, false);
            return;
        }
        if (started < 0) return;
        long ms = SystemClock.uptimeMillis() - started;
        if (WallColorPattern.loopShown(ms)) loop(c, sx, sy, WallColorPattern.frame(ms), loopAlpha, true);
        if (WallColorPattern.discShown(ms)) {
            float d = WallColorPattern.discScale(ms) * getWidth();
            paint.setColor(disc);
            c.drawOval(getWidth() / 2f - d / 2f, getHeight() / 2f - d / 2f, getWidth() / 2f + d / 2f,
                    getHeight() / 2f + d / 2f, paint);
        }
        postInvalidateOnAnimation();
    }

    private void canvasBlack(Canvas c) {
        c.drawColor(0xFF000000);
    }

    private void loop(Canvas c, float sx, float sy, float frame, float alpha, boolean pill) {
        if (alpha <= 0f) return;
        int a = Math.round(alpha * 255);
        for (int i = 0; i < WallColorPattern.SWATCH.length; i++) {
            float[] at = WallColorPattern.SWATCH_AT[i];
            r.set((at[0] - WallColorPattern.SWATCH_W / 2f) * sx, (at[1] - WallColorPattern.SWATCH_H / 2f) * sy,
                    (at[0] + WallColorPattern.SWATCH_W / 2f) * sx, (at[1] + WallColorPattern.SWATCH_H / 2f) * sy);
            paint.setColor(WallColorPattern.SWATCH[i]);
            paint.setAlpha(a);
            c.drawRoundRect(r, WallColorPattern.SWATCH_R * sx, WallColorPattern.SWATCH_R * sy, paint);
        }
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(Math.round(a * WallColorPattern.cornerAlpha(frame)));
        for (float[] at : WallColorPattern.CORNER_AT) {
            corner.rewind();
            outline(corner, WallColorPattern.CORNER_OUTER, at, sx, sy);
            outline(corner, WallColorPattern.CORNER_INNER, at, sx, sy);
            c.drawPath(corner, paint);
        }
        if (!pill) return;
        // the pill lies below the measured area (the corner marks end at y 894): it grows with a longer text
        text.setTextSize(WallColorPattern.TEXT_PX * sy);
        text.setAlpha(a);
        float h = WallColorPattern.PILL_H * sy, w = Math.max(WallColorPattern.PILL_W * sx, text.measureText(running) + h);
        r.set(WallColorPattern.PILL_X * sx - w / 2f, WallColorPattern.PILL_Y * sy - h / 2f, WallColorPattern.PILL_X * sx + w / 2f,
                WallColorPattern.PILL_Y * sy + h / 2f);
        paint.setColor(WallColorPattern.PILL);
        paint.setAlpha(a);
        c.drawRoundRect(r, h / 2f, h / 2f, paint);
        c.drawText(running, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2f, text);
    }

    private static void outline(Path p, float[] v, float[] at, float sx, float sy) {
        for (int i = 0; i < v.length; i += 2) {
            float x = (at[0] + v[i] * at[2]) * sx, y = (at[1] + v[i + 1] * at[3]) * sy;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
    }
}
