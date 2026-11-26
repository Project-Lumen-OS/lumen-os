// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

import org.lumen.projector.R;
import org.lumen.projector.ui.Theme;

/**
 * Parts of the panel right of the card (QpdLayout.SIDE_*: every popup and the pencil editor, 2026-11-18): the
 * header (the item's icon in a circle, the title, its value) and the key hints at the bottom (OK Выбрать, the back key
 * with what it does). Drawn in the theme's colours.
 */
final class QpdSide {
    private QpdSide() {}

    private static float px(Context c, float d) { return Theme.pxf(c, d); }

    /** Title block: icon circle (when the item has an icon), title, value line; a small back line above (crumb). */
    static final class Header extends View {
        private final TextPaint title, value, crumb;
        private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Drawable icon;
        private CharSequence t = "", v, back;

        Header(Context c) {
            super(c);
            title = QpdStyle.text(c, 32, 650, QpdStyle.INK);
            title.setLetterSpacing(-0.4f / 32f);
            value = QpdStyle.text(c, 17, 450, QpdStyle.MUTE);
            crumb = QpdStyle.text(c, 17, 500, QpdStyle.MUTE);
            setFocusable(false);
        }

        void set(int iconRes, CharSequence titleText, CharSequence valueText, CharSequence crumbText) {
            icon = iconRes == 0 ? null : getContext().getDrawable(iconRes);
            if (icon != null) icon = icon.mutate();
            t = titleText == null ? "" : titleText;
            v = valueText == null || valueText.length() == 0 ? null : valueText;
            back = crumbText;
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            Context c = getContext();
            float top = back == null ? 0 : px(c, 32);
            float body = icon != null ? px(c, 64) : px(c, 40) + (v == null ? 0 : px(c, 26));
            setMeasuredDimension(MeasureSpec.getSize(w), Math.round(top + body + px(c, 22)));
        }

        @Override
        protected void onDraw(Canvas c) {
            Context ctx = getContext();
            float y0 = 0;
            if (back != null) {
                crumb.setColor(QpdStyle.MUTE);
                Drawable a = ctx.getDrawable(R.drawable.lumen_ic_arrow_back);
                float s = px(ctx, 20);
                if (a != null) {
                    a = a.mutate();
                    a.setTint(QpdStyle.MUTE);
                    a.setBounds(0, Math.round(px(ctx, 2)), Math.round(s), Math.round(px(ctx, 2) + s));
                    a.draw(c);
                }
                c.drawText(back, 0, back.length(), s + px(ctx, 8), px(ctx, 17), crumb);
                y0 = px(ctx, 32);
            }
            float x = 0;
            if (icon != null) {
                float d = px(ctx, 64);
                circle.setColor(QpdStyle.TILE);
                c.drawCircle(d / 2f, y0 + d / 2f, d / 2f, circle);
                int s = Math.round(px(ctx, 30));
                icon.setTint(QpdStyle.ICON);
                icon.setBounds(0, 0, s, s);
                int save = c.save();
                c.translate((d - s) / 2f, y0 + (d - s) / 2f);
                icon.draw(c);
                c.restoreToCount(save);
                x = d + px(ctx, 18);
            }
            title.setColor(QpdStyle.INK);
            value.setColor(QpdStyle.MUTE);
            float boxW = Math.max(1f, getWidth() - x);
            float block = icon != null ? px(ctx, 64) : px(ctx, 40) + (v == null ? 0 : px(ctx, 26));
            float th = -title.ascent() + title.descent(), vh = v == null ? 0 : px(ctx, 6) - value.ascent() + value.descent();
            float tb = y0 + (block - th - vh) / 2f - title.ascent();
            QpdStyle.drawFaded(c, t, title, x, boxW, y0, y0 + block, tb, 0f, px(ctx, QpdLayout.FADE));
            if (v != null) {
                float vb = tb + title.descent() + px(ctx, 6) - value.ascent();
                QpdStyle.drawFaded(c, v, value, x, boxW, y0, y0 + block + px(ctx, 8), vb, 0f, px(ctx, QpdLayout.FADE));
            }
        }
    }

    /** The key hints at the bottom right: "OK" chip + what OK does, the back key chip + what BACK does; a line above. */
    static final class Keys extends View {
        private final TextPaint label, chipText;
        private final Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint();
        private final RectF r = new RectF();
        private CharSequence ok, back;

        Keys(Context c) {
            super(c);
            label = QpdStyle.text(c, 17, 500, QpdStyle.MUTE);
            chipText = QpdStyle.text(c, 15, 700, QpdStyle.INK);
            setFocusable(false);
        }

        void set(CharSequence okText, CharSequence backText) {
            ok = okText;
            back = backText;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Math.round(px(getContext(), 66)));
        }

        @Override
        protected void onDraw(Canvas c) {
            Context ctx = getContext();
            line.setColor(QpdStyle.DIVIDER);
            c.drawRect(0, 0, getWidth(), Math.max(1f, px(ctx, 1)), line);
            label.setColor(QpdStyle.MUTE);
            chipText.setColor(QpdStyle.INK);
            chip.setColor(QpdStyle.DIVIDER | 0x0A000000);
            float h = px(ctx, 34), cy = px(ctx, 16) + h / 2f + px(ctx, 6), x = getWidth();
            float gap = px(ctx, 8), between = px(ctx, 16);
            if (back != null) {
                float lw = label.measureText(back, 0, back.length());
                x -= lw;
                c.drawText(back, 0, back.length(), x, cy - (label.descent() + label.ascent()) / 2f, label);
                x -= gap + h;
                r.set(x, cy - h / 2f, x + h, cy + h / 2f);
                c.drawRoundRect(r, px(ctx, 9), px(ctx, 9), chip);
                Drawable a = ctx.getDrawable(R.drawable.lumen_ic_arrow_back);
                if (a != null) {
                    a = a.mutate();
                    a.setTint(QpdStyle.INK);
                    int s = Math.round(px(ctx, 20));
                    a.setBounds(Math.round(x + (h - s) / 2f), Math.round(cy - s / 2f), Math.round(x + (h + s) / 2f), Math.round(cy + s / 2f));
                    a.draw(c);
                }
                x -= between;
            }
            if (ok != null) {
                float lw = label.measureText(ok, 0, ok.length());
                x -= lw;
                c.drawText(ok, 0, ok.length(), x, cy - (label.descent() + label.ascent()) / 2f, label);
                float cw = chipText.measureText("OK") + px(ctx, 16);
                x -= gap + cw;
                r.set(x, cy - h / 2f, x + cw, cy + h / 2f);
                c.drawRoundRect(r, px(ctx, 9), px(ctx, 9), chip);
                c.drawText("OK", x + px(ctx, 8), cy - (chipText.descent() + chipText.ascent()) / 2f, chipText);
            }
        }
    }
}
