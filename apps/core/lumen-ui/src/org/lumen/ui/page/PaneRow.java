// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui.page;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.ui.Motion;
import org.lumen.ui.Palette;
import org.lumen.ui.Tokens;

/**
 * One action of a {@link TwoPane}: icon, title, optional second line and at the end a switch or a chevron (the stock
 * Android TV row: a switch for an on/off setting, a chevron for a row that opens a page). Focused it is the filled
 * pill of the palette ({@link Palette#focus} under {@link Palette#onFocus}), a little larger and lifted. OK clicks it.
 */
public final class PaneRow extends LinearLayout {
    private final Palette pal;
    private final ImageView icon;
    private final TextView title;
    private TextView sub;
    private ImageView chevron;
    private Toggle toggle;
    private final GradientDrawable fill = new GradientDrawable();

    PaneRow(Context c, Palette p, Drawable ic, CharSequence label, CharSequence second) {
        super(c);
        pal = p;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(PaneStyle.px(c, PaneStyle.ROW_H));
        int pad = PaneStyle.px(c, PaneStyle.ROW_PAD);
        setPaddingRelative(pad, 0, pad, 0);
        setFocusable(true);
        setClickable(true);
        setDefaultFocusHighlightEnabled(false);
        final int radius = PaneStyle.px(c, PaneStyle.ROW_RADIUS);
        fill.setCornerRadius(radius);
        fill.setColor(0);
        setBackground(fill);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setClipToOutline(true);
        setOutlineAmbientShadowColor(p.shadow);
        setOutlineSpotShadowColor(p.shadow);

        icon = new ImageView(c);
        icon.setImageDrawable(ic == null ? null : ic.mutate());
        int is = PaneStyle.px(c, PaneStyle.ROW_ICON);
        LayoutParams ilp = new LayoutParams(is, is);
        ilp.setMarginEnd(PaneStyle.px(c, PaneStyle.ROW_ICON_GAP));
        addView(icon, ilp);

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(VERTICAL);
        title = PaneStyle.text(c, PaneStyle.ROW_TITLE, PaneStyle.W_ROW_TITLE, p.text1, 0);
        texts.addView(title);
        title.setText(label);
        if (second != null) {
            sub = PaneStyle.text(c, PaneStyle.ROW_SUB, 400, p.text3, 0);
            sub.setText(second);
            LayoutParams slp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = PaneStyle.px(c, PaneStyle.ROW_SUB_GAP);
            texts.addView(sub, slp);
        }
        LayoutParams tlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.topMargin = tlp.bottomMargin = PaneStyle.px(c, 16);
        addView(texts, tlp);
        paint(false);
    }

    /** A switch at the end (an on/off setting); {@link #setOn} moves it. */
    public PaneRow withSwitch(boolean on) {
        Context c = getContext();
        toggle = new Toggle(c);
        toggle.on = on;
        LayoutParams lp = new LayoutParams(PaneStyle.px(c, PaneStyle.SWITCH_W), PaneStyle.px(c, PaneStyle.SWITCH_H));
        lp.setMarginStart(PaneStyle.px(c, PaneStyle.ROW_ICON_GAP));
        addView(toggle, lp);
        paint(isFocused());
        return this;
    }

    /** A chevron at the end (the row opens a page); an auto-mirrored drawable points along the reading direction. */
    public PaneRow withChevron(Drawable d) {
        Context c = getContext();
        chevron = new ImageView(c);
        chevron.setImageDrawable(d.mutate());
        int s = PaneStyle.px(c, PaneStyle.CHEVRON);
        LayoutParams lp = new LayoutParams(s, s);
        lp.setMarginStart(PaneStyle.px(c, PaneStyle.ROW_ICON_GAP));
        addView(chevron, lp);
        paint(isFocused());
        return this;
    }

    public boolean isOn() {
        return toggle != null && toggle.on;
    }

    public void setOn(boolean on) {
        if (toggle == null || toggle.on == on) return;
        toggle.on = on;
        toggle.invalidate();
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    public CharSequence title() {
        return title.getText();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        paint(focused);
        float s = focused ? PaneStyle.ROW_FOCUS_SCALE : 1f;
        boolean still = Motion.reduced(getContext());
        animate().scaleX(s).scaleY(s).translationZ(0)
                .setDuration(still ? 0 : focused ? Tokens.MS_FOCUS_IN : Tokens.MS_FOCUS_OUT)
                .setInterpolator(Motion.STANDARD).start();
        setElevation(focused ? PaneStyle.px(getContext(), PaneStyle.ROW_ELEVATION) : 0);
    }

    private void paint(boolean focused) {
        int ink = focused ? pal.onFocus : pal.text1;
        fill.setColor(focused ? pal.focus : 0);
        title.setTextColor(ink);
        if (sub != null) sub.setTextColor(focused ? Palette.alpha(pal.onFocus, PaneStyle.SUB_ON_FOCUS) : pal.text3);
        icon.setColorFilter(ink);
        if (chevron != null) chevron.setColorFilter(ink);
        if (toggle != null) toggle.invalidate();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (toggle != null) {
            info.setClassName("android.widget.Switch");
            info.setCheckable(true);
            info.setChecked(toggle.on);
        }
    }

    /** The switch: track and knob in the row's colours, on = the knob at the end. */
    private final class Toggle extends View {
        boolean on;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Toggle(Context c) {
            super(c);
            setFocusable(false);
            setClickable(false);
        }

        @Override
        protected void onDraw(Canvas cv) {
            boolean f = PaneRow.this.isFocused();
            int track, knob;
            if (on) {
                track = f ? pal.onFocus : pal.text1;
                knob = f ? pal.focus : pal.onFocus;
            } else {
                track = f ? Palette.alpha(pal.onFocus, 0.25f) : pal.panelLine;
                knob = f ? Palette.alpha(pal.onFocus, 0.6f) : pal.text3;
            }
            float w = getWidth(), h = getHeight(), r = h / 2f;
            paint.setColor(track);
            cv.drawRoundRect(0, 0, w, h, r, r, paint);
            float k = PaneStyle.px(getContext(), PaneStyle.KNOB) / 2f, in = PaneStyle.px(getContext(), PaneStyle.KNOB_INSET);
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            boolean end = on != rtl;
            float cx = end ? w - in - k : in + k;
            paint.setColor(knob);
            cv.drawCircle(cx, h / 2f, k, paint);
        }
    }
}
