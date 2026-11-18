// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.lumen.projector.R;
import org.lumen.projector.ui.Theme;
import org.lumen.ui.Palette;
import org.lumen.ui.Tokens;

import java.util.ArrayList;
import java.util.List;

/**
 * The sound output page of every projector, owner variant C (2026-11-18, ~/lumen-build-tmp/sound-menu/png/C_*.png):
 * a sheet in the Lumen 2.0 look (light by default, dark by the one theme switch, colours only from lumen-ui Palette),
 * the crumb back to Sound, the title, the card "Sound is playing on", the rows and, at the bottom, the audio format.
 * The same view shows the format choice (the sheet's second page). It only draws and reports keys: what the rows mean
 * is SoundOutputs (org.lumen.menu.sound), the actions are MenuController's; keys are QpdPanel's (LEFT and BACK go back,
 * UP and DOWN move between the rows).
 */
final class OutputSheet extends FrameLayout {
    /** One drawn row: icon, title, line, end marks. */
    static final class Item {
        final String key;
        final int icon;
        final CharSequence title, line, value;
        final boolean selected, playing, offline, radio, nav;
        final Runnable action;

        Item(String key, int icon, CharSequence title, CharSequence line, CharSequence value, boolean radio, boolean selected,
             boolean playing, boolean offline, boolean nav, Runnable action) {
            this.key = key;
            this.icon = icon;
            this.title = title;
            this.line = line;
            this.value = value;
            this.radio = radio;
            this.selected = selected;
            this.playing = playing;
            this.offline = offline;
            this.nav = nav;
            this.action = action;
        }
    }

    /** The card on top: icon, name, line (null name: no card). */
    static final class Now {
        final int icon;
        final CharSequence name, line;

        Now(int icon, CharSequence name, CharSequence line) {
            this.icon = icon;
            this.name = name;
            this.line = line;
        }
    }

    private static final int W = 680, MARGIN_Y = 40;
    private final Palette pal;
    private final LinearLayout body;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final List<String> keys = new ArrayList<>();
    private CharSequence connectText;

    OutputSheet(Context c, Palette pal, CharSequence crumb, CharSequence title) {
        super(c);
        this.pal = pal;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.panel);
        bg.setCornerRadius(pxf(Tokens.R_PANEL));
        bg.setStroke(Math.max(1, px(1)), pal.panelLine);
        setBackground(bg);
        setElevation(pxf(30));
        setPadding(px(16), px(30), px(16), px(18));
        setClipChildren(false);
        setClipToPadding(false);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        LinearLayout cr = new LinearLayout(c);
        cr.setGravity(Gravity.CENTER_VERTICAL);
        cr.setPadding(px(22), 0, px(22), 0);
        cr.addView(icon(R.drawable.lumen_ic_arrow_back, 26, pal.ptext3), new LinearLayout.LayoutParams(px(26), px(26)));
        TextView ct = text(crumb, 20, 600, pal.ptext3);
        ct.setPadding(px(10), 0, 0, 0);
        cr.addView(ct);
        body.addView(cr);
        TextView tt = text(title, 36, 680, pal.ptext1);
        tt.setLetterSpacing(-0.7f / 36f);
        tt.setPadding(px(22), px(8), px(22), px(20));
        body.addView(tt);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(0, px(4), 0, px(4));
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }

    static int width(Context c) { return Theme.px(c, W); }

    void setConnectText(CharSequence t) { connectText = t; }

    /** Shows {@code items} under {@code now}; keeps the focus on the row with the same key, else on {@code focusKey}. */
    void show(Now now, List<Item> items, int separatorBefore, String focusKey) {
        View f = list.findFocus();
        String had = f == null ? null : (String) f.getTag();
        list.removeAllViews();
        keys.clear();
        if (now != null && now.name != null) list.addView(card(now));
        View target = null;
        for (int i = 0; i < items.size(); i++) {
            if (i == separatorBefore) list.addView(separator());
            Item it = items.get(i);
            View r = row(it);
            list.addView(r);
            keys.add(it.key);
            if (it.key.equals(had) || target == null && had == null && it.key.equals(focusKey)) target = r;
        }
        if (target == null) target = firstRow();
        final View t = target;
        if (t != null && (had != null || !hasFocus())) post(() -> { if (t.isAttachedToWindow()) t.requestFocus(); });
    }

    /** The row with focus now, its key (null: none). */
    String focusedKey() {
        View f = list.findFocus();
        return f == null ? null : (String) f.getTag();
    }

    void focusFirst() {
        View t = firstRow();
        if (t != null) t.requestFocus();
    }

    private View firstRow() {
        for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i).isFocusable()) return list.getChildAt(i);
        return null;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = width(getContext());
        int screen = getResources().getDisplayMetrics().heightPixels - 2 * px(MARGIN_Y);
        int limit = MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED ? screen : Math.min(screen, MeasureSpec.getSize(hSpec));
        LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) scroll.getLayoutParams();
        sp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        if (getMeasuredHeight() > limit) {
            sp.height = Math.max(0, scroll.getMeasuredHeight() - (getMeasuredHeight() - limit));
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(limit, MeasureSpec.EXACTLY));
        }
    }

    // ------------------------------------------------------------------ parts
    private View card(Now now) {
        LinearLayout c = new LinearLayout(getContext());
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(px(24), px(22), px(24), px(22));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Palette.mix(pal.panel, pal.ptext1, pal.dark ? 0.06f : 0.04f));   // a quiet inset of the panel
        bg.setCornerRadius(pxf(22));
        c.setBackground(bg);
        FrameLayout bub = new FrameLayout(getContext());
        GradientDrawable bb = new GradientDrawable();
        bb.setShape(GradientDrawable.OVAL);
        bb.setColor(pal.focus);
        bub.setBackground(bb);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(px(40), px(40), Gravity.CENTER);
        bub.addView(icon(now.icon, 40, pal.onFocus), ip);
        c.addView(bub, new LinearLayout.LayoutParams(px(72), px(72)));
        LinearLayout tx = new LinearLayout(getContext());
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(px(22), 0, 0, 0);
        tx.addView(text(getContext().getText(R.string.lumen_m_out_now), 20, 600, pal.ptext3));
        TextView nm = text(now.name, 30, 660, pal.ptext1);
        nm.setLetterSpacing(-0.4f / 30f);
        nm.setPadding(0, px(2), 0, 0);
        tx.addView(nm);
        if (now.line != null) {
            TextView mt = text(now.line, 20, 500, pal.ptext2);
            mt.setPadding(0, px(4), 0, 0);
            tx.addView(mt);
        }
        c.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(6), 0, px(6), px(16));
        c.setLayoutParams(lp);
        return c;
    }

    private View separator() {
        View v = new View(getContext());
        v.setBackgroundColor(pal.panelLine);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1)));
        lp.setMargins(px(22), px(10), px(22), px(10));
        v.setLayoutParams(lp);
        return v;
    }

    private View row(Item it) {
        final LinearLayout r = new LinearLayout(getContext());
        r.setTag(it.key);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(22), 0, px(22), 0);
        r.setFocusable(true);
        r.setFocusableInTouchMode(false);
        r.setClickable(true);
        r.setDefaultFocusHighlightEnabled(false);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(pxf(Tokens.R_ROW));
        bg.setColor(0);
        r.setBackground(bg);
        final int base = it.offline ? pal.ptext3 : pal.ptext1, soft = it.offline ? pal.ptext3 : pal.ptext2;
        final ImageView ic = icon(it.icon, 32, soft);
        r.addView(ic, new LinearLayout.LayoutParams(px(32), px(32)));
        LinearLayout tx = new LinearLayout(getContext());
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(px(20), 0, px(12), 0);
        final TextView t = text(it.title, 22, 600, base);
        tx.addView(t);
        final TextView s = it.line == null ? null : text(it.line, 20, 500, soft);
        if (s != null) {
            s.setPadding(0, px(4), 0, 0);
            tx.addView(s);
        }
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final ImageView eq = it.playing ? icon(R.drawable.lumen_ic_playing, 28, pal.accent) : null;
        if (eq != null) {
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(px(28), px(28));
            ep.rightMargin = px(18);
            r.addView(eq, ep);
        }
        final TextView val = it.value == null ? null : text(it.value, 22, 600, pal.ptext2);
        if (val != null) r.addView(val);
        final TextView chip = it.offline ? chip() : null;
        if (chip != null) r.addView(chip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(40)));
        final ImageView end = it.radio ? icon(it.selected ? R.drawable.lumen_ic_radio_on : R.drawable.lumen_ic_radio_off, 32,
                it.selected ? pal.accent : pal.ptext3) : it.nav ? icon(R.drawable.lumen_ic_chevron_right, 30, pal.ptext3) : null;
        if (end != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(it.nav ? 30 : 32), px(it.nav ? 30 : 32));
            lp.leftMargin = px(it.nav ? 8 : 0);
            r.addView(end, lp);
        }
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(84)));
        r.setOnClickListener(v -> { if (it.action != null) it.action.run(); });
        r.setOnFocusChangeListener((v, has) -> {
            int fg = pal.onPfocus;
            bg.setColor(has ? pal.pfocus : 0);
            r.setElevation(has ? pxf(6) : 0f);
            t.setTextColor(has ? fg : base);
            if (s != null) s.setTextColor(has ? fg : soft);
            tint(ic, has ? fg : soft);
            if (eq != null) tint(eq, has ? fg : pal.accent);
            if (val != null) val.setTextColor(has ? fg : pal.ptext2);
            if (end != null) tint(end, has ? fg : it.radio && it.selected ? pal.accent : pal.ptext3);
            if (chip != null) styleChip(chip, has);
            float sc = has ? Tokens.SCALE_ROW : 1f;
            r.animate().scaleX(sc).scaleY(sc).setDuration(has ? Tokens.MS_FOCUS_IN : Tokens.MS_FOCUS_OUT)
                    .setInterpolator(org.lumen.ui.Motion.EMPHASIZED).start();
        });
        return r;
    }

    private TextView chip() {
        TextView c = text(connectText, 20, 600, pal.ptext1);
        c.setGravity(Gravity.CENTER);
        c.setPadding(px(18), 0, px(18), 0);
        styleChip(c, false);
        return c;
    }

    private void styleChip(TextView c, boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(pxf(20));
        d.setColor(focused ? pal.onPfocus : 0);
        d.setStroke(px(2), focused ? pal.onPfocus : pal.panelLine);
        c.setBackground(d);
        c.setTextColor(focused ? pal.pfocus : pal.ptext1);
    }

    private ImageView icon(int res, int size, int color) {
        ImageView v = new ImageView(getContext());
        v.setImageResource(res);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tint(v, color);
        return v;
    }

    private static void tint(ImageView v, int color) { v.setImageTintList(ColorStateList.valueOf(color)); }

    private TextView text(CharSequence s, int size, int weight, int color) {
        TextView t = new TextView(getContext());
        t.setText(s);
        Typeface f = QpdStyle.inter(getContext(), weight, size);
        t.setTypeface(f);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxf(size));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private int px(float d) { return Theme.px(getContext(), d); }

    private float pxf(float d) { return Theme.pxf(getContext(), d); }
}
