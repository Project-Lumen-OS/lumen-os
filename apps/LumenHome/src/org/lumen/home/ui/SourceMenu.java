// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.home.R;
import org.lumen.ui.Tokens;

import java.util.ArrayList;
import java.util.List;

/**
 * "Источник сигнала" (SourceMenu): a 540 px panel under the header's source button. Rows of 80 px: icon,
 * name, the device or state under it, a check mark at the current source; a line, then "Настройки проектора". Opens
 * in 280 ms (24 px down plus fade). UP and DOWN move, OK picks, LEFT and BACK close.
 */
public class SourceMenu extends FrameLayout {
    public static final class Entry {
        public final int icon;
        public final String title, sub;
        public final boolean current, chevron;
        public final Runnable action;

        public Entry(int icon, String title, String sub, boolean current, boolean chevron, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.sub = sub == null ? "" : sub;
            this.current = current;
            this.chevron = chevron;
            this.action = action;
        }
    }

    private final LinearLayout mPanel;
    private final ArrayList<Entry> mEntries = new ArrayList<>();
    private final ArrayList<View> mRows = new ArrayList<>();
    private int mFocus;
    private boolean mOpen;

    public SourceMenu(Context c) {
        super(c);
        setClipChildren(false);
        mPanel = new LinearLayout(c);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        mPanel.setPadding(Theme.px(Tokens.PANEL_PAD), Theme.px(Tokens.PANEL_PAD), Theme.px(Tokens.PANEL_PAD), Theme.px(Tokens.PANEL_PAD));
        mPanel.setClipChildren(false);
        mPanel.setClipToPadding(false);
        mPanel.setElevation(Theme.pxf(40));
        LayoutParams lp = new LayoutParams(Theme.px(Tokens.PANEL_W), LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        lp.topMargin = Theme.px(124);
        lp.setMarginEnd(Theme.px(Tokens.MARGIN));
        addView(mPanel, lp);
        setVisibility(GONE);
    }

    public boolean isOpen() {
        return mOpen;
    }

    public void open(List<Entry> entries, int focus) {
        mEntries.clear();
        mEntries.addAll(entries);
        build();
        mFocus = Math.max(0, Math.min(focus, mEntries.size() - 1));
        mark();
        mOpen = true;
        setVisibility(VISIBLE);
        mPanel.setAlpha(0f);
        mPanel.setTranslationY(-Theme.pxf(Tokens.PANEL_SLIDE));
        mPanel.animate().alpha(1f).translationY(0).setDuration(Theme.PANEL_MS).setInterpolator(Theme.EMPHASIZED).start();
    }

    public void close() {
        if (!mOpen) return;
        mOpen = false;
        mPanel.animate().alpha(0f).translationY(-Theme.pxf(Tokens.PANEL_SLIDE)).setDuration(Theme.FOCUS_OUT_MS)
                .setInterpolator(Theme.EMPHASIZED).withEndAction(() -> setVisibility(GONE)).start();
    }

    /** true when consumed. */
    public boolean key(int dir) {
        switch (dir) {
            case 0: // up
                if (mFocus > 0) mFocus--;
                mark();
                return true;
            case 1: // down
                if (mFocus < mEntries.size() - 1) mFocus++;
                mark();
                return true;
            default:
                return true;
        }
    }

    public void pick() {
        if (mFocus < 0 || mFocus >= mEntries.size()) return;
        Entry e = mEntries.get(mFocus);
        close();
        if (e.action != null) post(e.action);
    }

    private void build() {
        Context c = getContext();
        mPanel.removeAllViews();
        mRows.clear();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.PANEL);
        bg.setCornerRadius(Theme.pxf(Tokens.R_PANEL));
        bg.setStroke(Math.max(1, Theme.px(1)), Theme.PANEL_LINE);
        mPanel.setBackground(bg);
        TextView h = new TextView(c);
        Theme.style(h, Tokens.TYPE_CAPTION, 600, Theme.PTEXT3, 0);
        h.setText(R.string.source_title);
        h.setPadding(Theme.px(18), Theme.px(14), Theme.px(18), Theme.px(10));
        mPanel.addView(h);
        for (int i = 0; i < mEntries.size(); i++) {
            Entry e = mEntries.get(i);
            if (e.chevron && i > 0) {
                View sep = new View(c);
                sep.setBackgroundColor(Theme.PANEL_LINE);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Theme.px(1)));
                sp.setMargins(Theme.px(18), Theme.px(8), Theme.px(18), Theme.px(8));
                mPanel.addView(sep, sp);
            }
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Theme.px(20), 0, Theme.px(20), 0);
            ImageView ic = new ImageView(c);
            ic.setImageDrawable(c.getDrawable(e.icon).mutate());
            row.addView(ic, new LinearLayout.LayoutParams(Theme.px(32), Theme.px(32)));
            LinearLayout tx = new LinearLayout(c);
            tx.setOrientation(LinearLayout.VERTICAL);
            TextView t = new TextView(c);
            t.setText(e.title);
            t.setSingleLine(true);
            tx.addView(t);
            if (!e.sub.isEmpty()) {
                TextView s = new TextView(c);
                s.setText(e.sub);
                s.setSingleLine(true);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                slp.topMargin = Theme.px(3);
                tx.addView(s, slp);
            }
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            tp.setMarginStart(Theme.px(18));
            row.addView(tx, tp);
            if (e.current || e.chevron) {
                ImageView r = new ImageView(c);
                r.setImageDrawable(c.getDrawable(e.current ? R.drawable.ic_check : R.drawable.ic_chevron_right).mutate());
                row.addView(r, new LinearLayout.LayoutParams(Theme.px(30), Theme.px(30)));
            }
            final int idx = i;
            row.setOnClickListener(v -> {
                mFocus = idx;
                mark();
                pick();
            });
            mPanel.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Theme.px(Tokens.PANEL_ROW_H)));
            mRows.add(row);
        }
    }

    private void mark() {
        for (int i = 0; i < mRows.size(); i++) {
            LinearLayout row = (LinearLayout) mRows.get(i);
            Entry e = mEntries.get(i);
            boolean f = i == mFocus;
            GradientDrawable g = null;
            if (f) {
                g = new GradientDrawable();
                g.setColor(Theme.PFOCUS);
                g.setCornerRadius(Theme.pxf(Tokens.R_ROW));
            }
            row.setBackground(g);
            row.setElevation(f ? Theme.pxf(10) : 0);
            float s = f && Theme.animations() ? Tokens.SCALE_ROW : 1f;
            row.animate().scaleX(s).scaleY(s).setDuration(f ? Theme.FOCUS_IN_MS : Theme.FOCUS_OUT_MS).start();
            ImageView ic = (ImageView) row.getChildAt(0);
            ic.getDrawable().setTint(f ? Theme.ON_PFOCUS : Theme.PTEXT2);
            LinearLayout tx = (LinearLayout) row.getChildAt(1);
            Theme.style((TextView) tx.getChildAt(0), Tokens.TYPE_LABEL, 600, f ? Theme.ON_PFOCUS : Theme.PTEXT1, 0);
            if (tx.getChildCount() > 1) {
                Theme.style((TextView) tx.getChildAt(1), Tokens.TYPE_CAPTION, 500, f ? Theme.ON_PFOCUS : Theme.PTEXT2, 0);
            }
            if (row.getChildCount() > 2) {
                Drawable d = ((ImageView) row.getChildAt(2)).getDrawable();
                d.setTint(f ? Theme.ON_PFOCUS : e.current ? Theme.ACCENT : Theme.PTEXT2);
            }
        }
    }
}
