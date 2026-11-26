// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.lumen.icons.Icons;
import org.lumen.ui.Palette;

import java.util.List;

/** The actions of a row (long OK or MENU): a panel on the right over a dim field, filled focus rows, no ring. */
public final class Sheet extends FrameLayout {
    /** The view that had the focus when the panel came up. */
    private View mBack;
    /** Where the focus goes when that view is gone (the screen keeps it for the list). */
    Runnable lost;
    /** One row of the panel. */
    public static final class Action {
        final int icon;
        final CharSequence label;
        final Runnable run;

        public Action(int icon, CharSequence label, Runnable run) {
            this.icon = icon;
            this.label = label;
            this.run = run;
        }
    }

    static final int W = 600;
    private final LinearLayout mPanel, mRows;
    private final TextView mTitle, mSub;

    public Sheet(Context c) {
        super(c);
        setVisibility(GONE);
        setClickable(true);
        mPanel = new LinearLayout(c);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        mPanel.setPadding(Ui.px(40), Ui.px(Ui.SAFE), Ui.px(40), Ui.px(Ui.SAFE));
        mTitle = Ui.line(c, "", 30, Ui.SEMIBOLD, Ui.P.ptext1);
        mSub = Ui.line(c, "", 22, Ui.REGULAR, Ui.P.ptext2);
        mPanel.addView(mTitle);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = Ui.px(8);
        sl.bottomMargin = Ui.px(28);
        mPanel.addView(mSub, sl);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setDefaultFocusHighlightEnabled(false);
        mRows = new LinearLayout(c);
        mRows.setOrientation(LinearLayout.VERTICAL);
        sv.addView(mRows);
        mPanel.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(mPanel, new FrameLayout.LayoutParams(Ui.px(W), -1, Gravity.END));
        restyle();
    }

    public void restyle() {
        Palette p = Ui.P;
        setBackgroundColor(Palette.alpha(p.scrim, 0.45f));
        mPanel.setBackground(Ui.round(p.panel, 0));
        mTitle.setTextColor(p.ptext1);
        mSub.setTextColor(p.ptext2);
    }

    public boolean shown() {
        return getVisibility() == VISIBLE;
    }

    public void show(CharSequence title, CharSequence sub, List<Action> actions) {
        restyle();
        mTitle.setText(title);
        mSub.setText(sub);
        mSub.setVisibility(sub == null || sub.length() == 0 ? GONE : VISIBLE);
        if (!shown()) mBack = getRootView().findFocus();
        mRows.removeAllViews();
        Context c = getContext();
        Box first = null;
        for (Action a : actions) {
            Box b = new Box(c);
            b.ring = false;
            b.radius = 14;
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setPadding(Ui.px(20), 0, Ui.px(20), 0);
            ImageView i = Icons.view(c, a.icon, 24);
            b.addView(b.inked(i), new LinearLayout.LayoutParams(Ui.px(32), Ui.px(32)));
            TextView t = Ui.line(c, a.label, 24, Ui.MEDIUM, Ui.P.ptext1);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.setMarginStart(Ui.px(20));
            b.addView(b.inked(t), tl);
            b.look(0, Ui.P.pfocus, Ui.P.ptext1, Ui.P.onPfocus);
            b.setOnClickListener(v -> {
                hide();
                a.run.run();
            });
            mRows.addView(b, new LinearLayout.LayoutParams(-1, Ui.px(68)));
            if (first == null) first = b;
        }
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(160).start();
        if (first != null) first.requestFocus();
    }

    public void hide() {
        give(mBack, lost);
        mBack = null;
        setVisibility(GONE);
        mRows.removeAllViews();
    }

    /**
     * The focus goes back to the row the panel was opened on before the panel's own rows go, else Android would hand it
     * to the first view of the screen (the Search pill). The action then refocuses whatever it changed.
     */
    static void give(View back, Runnable lost) {
        if (back != null && back.isAttachedToWindow() && back.isShown()) back.requestFocus();
        else if (lost != null) lost.run();
    }

    /** Keeps the focus inside the panel while it is up. */
    public boolean owns(View focused) {
        for (View v = focused; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) if (v == this) return true;
        return false;
    }
}
