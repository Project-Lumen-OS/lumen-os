// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;

import org.lumen.setup.R;

/**
 * An icon of the Lumen set (apps/core/lumen-icons, Material Symbols Rounded vectors): never a hand-drawn glyph. The
 * colour follows the focus state of the parent row (duplicateParentState: text2 at rest, on-focus when focused) unless
 * {@link #color} fixes it. SPINNER turns while shown; WIFI picks the signal bars by {@link #level}.
 */
public class Icon extends View {
    public static final int CHECK = 1, LOCK = 2, WIFI = 3, ETHERNET = 4, CHEVRON = 5, PLUS = 6,
            MORE = 7, BLUETOOTH = 8, SPINNER = 9, DOT = 10, GLOBE = 11, ALERT = 12, DONE = 13, INFO = 14,
            RADIO_ON = 15, RADIO_OFF = 16, TOGGLE_ON = 17, TOGGLE_OFF = 18, PERSON = 19, REFRESH = 20, FORWARD = 21,
            BACK = 22, WIFI_OFF = 23, VISIBILITY = 24, PLAY = 25, FOCUS = 26, FOCUS_WEAK = 27, FIT = 28,
            TRANSFORM = 29, KEYSTONE = 30, WIFI_LINE = 31, INSTALL = 32;

    private int mType;
    private int mLevel = 4;
    private int mColor;       // 0 = follow focus
    private Drawable mD;
    private int mDRes;
    private ValueAnimator mSpin;
    private float mAngle;

    public Icon(Context c, int type) {
        super(c);
        mType = type;
    }

    public Icon type(int t) { mType = t; updateSpin(); invalidate(); return this; }

    public int type() { return mType; }

    public Icon level(int l) { mLevel = Math.max(0, Math.min(4, l)); invalidate(); return this; }

    /** Secured networks are named in the row's subtitle; the icon shows the signal only. */
    public Icon locked(boolean b) { return this; }

    public Icon color(int c) { mColor = c; invalidate(); return this; }

    /** The lumen-icons drawable of a type (and of the WIFI level). */
    static int res(int type, int level) {
        switch (type) {
            case CHECK: return R.drawable.lumen_ic_check;
            case DONE: return R.drawable.lumen_ic_check_circle;
            case WIFI:
                return level >= 4 ? R.drawable.lumen_ic_wifi_4 : level == 3 ? R.drawable.lumen_ic_wifi_3
                        : level == 2 ? R.drawable.lumen_ic_wifi_2 : R.drawable.lumen_ic_wifi_1;
            case ETHERNET: return R.drawable.lumen_ic_lan;
            case CHEVRON: case MORE: return R.drawable.lumen_ic_chevron_right;
            case PLUS: return R.drawable.lumen_ic_add;
            case BLUETOOTH: return R.drawable.lumen_ic_bluetooth;
            case SPINNER: return R.drawable.lumen_ic_progress;
            case GLOBE: return R.drawable.lumen_ic_language;
            case ALERT: return R.drawable.lumen_ic_alert;
            case WIFI_LINE: return R.drawable.lumen_ic_wifi;
            case INSTALL: return R.drawable.lumen_ic_install;
            case RADIO_ON: return R.drawable.lumen_ic_radio_on;
            case RADIO_OFF: case DOT: case LOCK: return R.drawable.lumen_ic_radio_off;
            case TOGGLE_ON: return R.drawable.lumen_ic_toggle_on;
            case TOGGLE_OFF: return R.drawable.lumen_ic_toggle_off;
            case PERSON: return R.drawable.lumen_ic_person;
            case REFRESH: return R.drawable.lumen_ic_refresh;
            case FORWARD: return R.drawable.lumen_ic_arrow_forward;
            case BACK: return R.drawable.lumen_ic_arrow_back;
            case WIFI_OFF: return R.drawable.lumen_ic_wifi_off;
            case VISIBILITY: return R.drawable.lumen_ic_visibility;
            case PLAY: return R.drawable.lumen_ic_play;
            case FOCUS: return R.drawable.lumen_ic_focus;
            case FOCUS_WEAK: return R.drawable.lumen_ic_focus_weak;
            case FIT: return R.drawable.lumen_ic_fit_screen;
            case TRANSFORM: return R.drawable.lumen_ic_transform;
            case KEYSTONE: return R.drawable.lumen_ic_keystone;
            default: return R.drawable.lumen_ic_info;
        }
    }

    private int currentColor() {
        if (mColor != 0) return mColor;
        return Ui.focusedState(this) ? Ui.FOCUS_TEXT : Ui.TEXT_DIM;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSpin();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mSpin != null) mSpin.cancel();
        mSpin = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        updateSpin();
    }

    private void updateSpin() {
        boolean want = mType == SPINNER && isAttachedToWindow() && isShown();
        if (want && mSpin == null) {
            mSpin = ValueAnimator.ofFloat(0f, 360f);
            mSpin.setDuration(1000);
            mSpin.setRepeatCount(ValueAnimator.INFINITE);
            mSpin.setInterpolator(new LinearInterpolator());
            mSpin.addUpdateListener(a -> {
                mAngle = (float) a.getAnimatedValue();
                invalidate();
            });
            mSpin.start();
        } else if (!want && mSpin != null) {
            mSpin.cancel();
            mSpin = null;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int r = res(mType, mLevel);
        if (mD == null || mDRes != r) {
            mD = getContext().getDrawable(r).mutate();
            mDRes = r;
        }
        mD.setLayoutDirection(getLayoutDirection());
        mD.setTintList(null);
        mD.setTint(currentColor());
        int w = getWidth(), h = getHeight(), s = Math.min(w, h);
        int l = (w - s) / 2, t = (h - s) / 2;
        mD.setBounds(l, t, l + s, t + s);
        if (mType == SPINNER) {
            c.save();
            c.rotate(mAngle, w / 2f, h / 2f);
            mD.draw(c);
            c.restore();
        } else {
            mD.draw(c);
        }
    }
}
