// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.ui.Tokens;

/**
 * Tile of the picture step: a 52 px icon at the top, the name
 * (24 / 620) and an optional line (20 / 520, text3) at the bottom, surface fill; focused it scales 1.06 and gets the
 * card ring ({@link Ring}, drawn in its own margin so nothing clips it). A busy tile keeps its focus, ignores clicks
 * and looks dimmed, as a busy Row.
 */
public class Tile extends LinearLayout {
    public static final int W = 300, H = 208;
    public final TextView title;
    public final TextView sub;
    private boolean mBusy;

    public Tile(Context c, int icon, CharSequence name) {
        super(c);
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        setFocusableInTouchMode(false);
        int in = Ui.px(Ring.INSET);
        setPadding(in + Ui.px(26), in + Ui.px(26), in + Ui.px(26), in + Ui.px(22));
        setBackground(new Ring(Ui.SURFACE, Ui.P.dark ? 0 : Ui.PANEL_LINE, Tokens.R_TILE_SETUP));
        Icon i = new Icon(c, icon).color(Ui.TEXT);
        addView(i, new LayoutParams(Ui.px(52), Ui.px(52)));
        addView(new android.view.View(c), new LayoutParams(1, 0, 1f));
        title = Ui.text(c, 24, Ui.TEXT, Ui.W_BUTTON);
        title.setText(name);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        addView(title);
        sub = Ui.text(c, 20, Ui.TEXT3, Ui.W_CAPTION);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setVisibility(GONE);
        addView(sub, Ui.lpTop(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 4));
        setGravity(Gravity.START);
    }

    /** Layout params of a tile: its visible size plus the ring margin on every side. */
    public static LayoutParams lp() {
        int in = Ui.px(Ring.INSET);
        return new LayoutParams(Ui.px(W) + 2 * in, Ui.px(H) + 2 * in);
    }

    public Tile subtitle(CharSequence s) {
        sub.setText(s);
        sub.setVisibility(s == null || s.length() == 0 ? GONE : VISIBLE);
        return this;
    }

    public void setBusy(boolean busy) {
        mBusy = busy;
        setAlpha(busy ? 0.45f : 1f);
    }

    @Override
    public boolean performClick() {
        if (mBusy) return true;
        return super.performClick();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        Ui.focusScale(this, gainFocus, Tokens.SCALE_TILE);
    }
}
