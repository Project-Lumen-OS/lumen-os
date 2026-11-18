package org.lumen.setup.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.setup.Progress;
import org.lumen.setup.R;
import org.lumen.ui.Palette;

/**
 * "Шаг 3 из 8" and one segment per step, top right at the height of the logo: segments 36 x 6 with
 * 8 between, the current one 64 wide in text1, the passed ones text1 at 75 %, the coming ones at 22 %.
 */
public class Dots extends LinearLayout {
    private final TextView mLabel;
    private final Segs mSegs;

    public Dots(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        mLabel = Ui.text(c, 22, Ui.TEXT_DIM, 560);
        mLabel.setSingleLine(true);
        addView(mLabel);
        mSegs = new Segs(c);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(Ui.px(18));
        addView(mSegs, lp);
    }

    /** count = the steps shown on this unit, current = the 0-based place of the step on screen. */
    public void set(int count, int current) {
        Progress p = new Progress(count, current);
        mLabel.setText(getContext().getString(R.string.setup_step_of, p.number(), p.count()));
        mSegs.set(p);
    }

    private static final class Segs extends View {
        private final Paint mP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mR = new RectF();
        private Progress mProg = new Progress(1, 0);

        Segs(Context c) {
            super(c);
        }

        void set(Progress p) {
            mProg = p;
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension((int) Math.ceil(Ui.pxf(mProg.width(36, 64, 8))), Ui.px(6));
        }

        @Override
        protected void onDraw(Canvas c) {
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float x = 0, hgt = Ui.pxf(6);
            for (int i = 0; i < mProg.count(); i++) {
                float w = Ui.pxf(i == mProg.current() ? 64 : 36);
                float left = rtl ? getWidth() - x - w : x;
                mR.set(left, 0, left + w, hgt);
                mP.setColor(Palette.alpha(Ui.TEXT, mProg.opacity(i)));
                c.drawRoundRect(mR, hgt / 2, hgt / 2, mP);
                x += w + Ui.pxf(8);
            }
        }
    }
}
