// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;

import org.lumen.parental.PinCode;
import org.lumen.projector.ui.Theme;

/**
 * The child lock password as a row of a menu page (PinPage): four slots, each pressed arrow key drawn as its arrow
 * (Lucide arrow-up, ISC, turned per key). It takes every arrow key while it has the focus (QpdPanel lets it), OK is
 * the forgotten password question.
 */
final class PinRow extends View {
    interface Keys {
        void key(char k);

        void ok();
    }

    private static final float SLOT = 96, GAP = 20, HEIGHT = 132;
    private final Keys keys;
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path up = new Path();
    private final RectF r = new RectF();
    private String entered = "";

    PinRow(Context c, Keys keys) {
        super(c);
        this.keys = keys;
        setFocusable(true);
        card.setColor(0x1FFFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Theme.pxf(c, 3));
        ring.setColor(0xFFFFFFFF);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeCap(Paint.Cap.ROUND);
        arrow.setStrokeJoin(Paint.Join.ROUND);
        arrow.setColor(0xFFFFFFFF);
        up.moveTo(12, 19);                 // lucide arrow-up: M12 19V5 M5 12l7-7 7 7
        up.lineTo(12, 5);
        up.moveTo(5, 12);
        up.lineTo(12, 5);
        up.lineTo(19, 12);
    }

    void setEntered(String k) {
        entered = k == null ? "" : k;
        invalidate();
    }

    String entered() { return entered; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Theme.px(getContext(), HEIGHT));
    }

    @Override protected void onDraw(Canvas c) {
        float slot = Theme.pxf(getContext(), SLOT), gap = Theme.pxf(getContext(), GAP);
        float x = Theme.pxf(getContext(), 8), y = (getHeight() - slot) / 2f;
        for (int i = 0; i < PinCode.LENGTH; i++) {
            r.set(x, y, x + slot, y + slot);
            c.drawRoundRect(r, slot / 5f, slot / 5f, card);
            if (isFocused() && i == entered.length()) c.drawRoundRect(r, slot / 5f, slot / 5f, ring);
            if (i < entered.length()) drawArrow(c, entered.charAt(i), r);
            x += slot + gap;
        }
    }

    private void drawArrow(Canvas c, char k, RectF box) {
        float s = box.width() * 0.6f / 24f;
        arrow.setStrokeWidth(2f);
        c.save();
        c.translate(box.centerX(), box.centerY());
        c.rotate(k == PinCode.RIGHT ? 90 : k == PinCode.DOWN ? 180 : k == PinCode.LEFT ? 270 : 0);
        c.scale(s, s);
        c.translate(-12, -12);
        c.drawPath(up, arrow);
        c.restore();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        char k = PinCode.letter(code);
        if (k != 0) {
            if (e.getRepeatCount() == 0) keys.key(k);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (e.getRepeatCount() == 0) keys.ok();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        return PinCode.letter(code) != 0 || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || super.onKeyUp(code, e);
    }
}
