// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui.page;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.ui.Palette;

/**
 * The two-pane settings page of Lumen (the Android TV settings shape, owner pick 2026-11-26 for the problem report):
 * the guide on the left says where the user is and what the page does (path, title, explanation, one fact such as the
 * last report, a privacy line at the bottom), the pane on the right holds the actions as {@link PaneRow}s or a page's
 * content (sections, list lines, a progress, a code). One look for every such page, colours from the palette given;
 * a theme change builds the page again in the new palette.
 */
public final class TwoPane extends FrameLayout {
    private final Palette pal;
    private final TextView crumb, title, body, metaLabel, metaValue, footText;
    private final ImageView footIcon;
    private final LinearLayout guide, pane, foot;
    private boolean dense;

    public TwoPane(Context c, Palette p) {
        super(c);
        pal = p;
        GradientDrawable field = new GradientDrawable();
        field.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        field.setGradientCenter(PaneStyle.GLOW_X, PaneStyle.GLOW_Y);
        field.setGradientRadius(PaneStyle.px(c, PaneStyle.GLOW_R));
        field.setColors(new int[] {p.bgGlow, p.bg});
        setBackground(field);

        LinearLayout split = new LinearLayout(c);
        split.setOrientation(LinearLayout.HORIZONTAL);
        addView(split, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        guide = new LinearLayout(c);
        guide.setOrientation(LinearLayout.VERTICAL);
        guide.setGravity(Gravity.CENTER_VERTICAL);
        guide.setPaddingRelative(px(PaneStyle.GUIDE_PAD_START), 0, px(PaneStyle.GUIDE_PAD_END), px(PaneStyle.PAD_BOTTOM));
        split.addView(guide, new LinearLayout.LayoutParams(px(PaneStyle.GUIDE_W), ViewGroup.LayoutParams.MATCH_PARENT));
        crumb = PaneStyle.text(c, PaneStyle.CRUMB, PaneStyle.W_CRUMB, p.text3, 0);
        guide.addView(crumb);
        title = PaneStyle.text(c, PaneStyle.TITLE, PaneStyle.W_TITLE, p.text1, PaneStyle.TITLE_LH);
        title.setLetterSpacing(PaneStyle.TITLE_TRACK);
        guide.addView(title, top(PaneStyle.CRUMB_GAP));
        body = PaneStyle.text(c, PaneStyle.BODY, 400, p.text2, PaneStyle.BODY_LH);
        guide.addView(body, top(PaneStyle.BODY_GAP));
        metaLabel = PaneStyle.text(c, PaneStyle.META_LABEL, 400, p.text3, 0);
        guide.addView(metaLabel, top(PaneStyle.META_GAP));
        metaValue = PaneStyle.text(c, PaneStyle.META_VALUE, PaneStyle.W_META, p.text2, 0);
        guide.addView(metaValue, top(PaneStyle.META_VALUE_GAP));
        meta(null, null);

        pane = new LinearLayout(c);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setGravity(Gravity.CENTER_VERTICAL);
        pane.setBackgroundColor(p.bgLow);
        pane.setClipToPadding(false);   // a focused row grows past the padding (PaneStyle.ROW_FOCUS_SCALE)
        split.addView(pane, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        setDense(false);

        foot = new LinearLayout(c);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        footIcon = new ImageView(c);
        footIcon.setColorFilter(p.text3);
        int fi = px(PaneStyle.FOOT_ICON);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(fi, fi);
        ilp.setMarginEnd(px(PaneStyle.FOOT_GAP));
        foot.addView(footIcon, ilp);
        footText = PaneStyle.text(c, PaneStyle.FOOT, 400, p.text3, PaneStyle.FOOT_LH);
        foot.addView(footText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LayoutParams flp = new LayoutParams(px(PaneStyle.FOOT_W), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        flp.setMarginStart(px(PaneStyle.GUIDE_PAD_START));
        flp.bottomMargin = px(PaneStyle.FOOT_BOTTOM);
        addView(foot, flp);
        foot.setVisibility(GONE);
    }

    private int px(float d) {
        return PaneStyle.px(getContext(), d);
    }

    private LinearLayout.LayoutParams top(float d) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(d);
        return lp;
    }

    public Palette palette() {
        return pal;
    }

    // ------------------------------------------------------------------ the guide
    /** Where the page is (e.g. All settings and General), its title and what it does. */
    public void guide(CharSequence path, CharSequence heading, CharSequence text) {
        crumb.setText(path);
        crumb.setVisibility(path == null ? GONE : VISIBLE);
        title.setText(heading);
        body.setText(text);
        body.setVisibility(text == null ? GONE : VISIBLE);
    }

    /** One fact under the explanation (label above, value below); null hides it. */
    public void meta(CharSequence label, CharSequence value) {
        int v = label == null || value == null ? GONE : VISIBLE;
        metaLabel.setText(label);
        metaValue.setText(value);
        metaLabel.setVisibility(v);
        metaValue.setVisibility(v);
    }

    /** The quiet line at the bottom of the guide (privacy, retention). */
    public void footer(Drawable icon, CharSequence text) {
        footIcon.setImageDrawable(icon == null ? null : icon.mutate());
        footText.setText(text);
        foot.setVisibility(text == null ? GONE : VISIBLE);
    }

    // ------------------------------------------------------------------ the pane
    /** Empties the pane; {@code list} = a page of sections and lines (tighter, down to the bottom edge). */
    public void clear(boolean list) {
        pane.removeAllViews();
        setDense(list);
    }

    private void setDense(boolean d) {
        dense = d;
        pane.setPaddingRelative(px(PaneStyle.PANE_PAD_START), 0, px(PaneStyle.PANE_PAD_END), d ? 0 : px(PaneStyle.PAD_BOTTOM));
    }

    private LinearLayout.LayoutParams next(float extra) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pane.getChildCount() == 0 ? 0 : px((dense ? PaneStyle.GAP_DENSE : PaneStyle.GAP) + extra);
        return lp;
    }

    /** An action row (title, optional second line). */
    public PaneRow row(Drawable icon, CharSequence label, CharSequence second) {
        PaneRow r = new PaneRow(getContext(), pal, icon, label, second);
        pane.addView(r, next(0));
        return r;
    }

    /** A section heading of a list page. */
    public void section(CharSequence text) {
        TextView t = PaneStyle.text(getContext(), PaneStyle.SECTION, PaneStyle.W_SECTION, pal.text3, 0);
        t.setLetterSpacing(PaneStyle.SECTION_TRACK);
        t.setText(text);
        t.setPaddingRelative(px(PaneStyle.ROW_PAD), 0, 0, 0);
        LinearLayout.LayoutParams lp = next(pane.getChildCount() == 0 ? 0 : PaneStyle.SECTION_BREAK);
        lp.topMargin += px(PaneStyle.SECTION_TOP);
        lp.bottomMargin = px(PaneStyle.SECTION_BOTTOM);
        pane.addView(t, lp);
    }

    /** A line of a list page; {@code quiet} draws its icon dimmer (a thing that is left out). */
    public void item(Drawable icon, CharSequence text, boolean quiet) {
        Context c = getContext();
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        int pad = px(PaneStyle.ROW_PAD), v = px(PaneStyle.ITEM_PAD_V);
        line.setPaddingRelative(pad, v, pad, v);
        ImageView i = new ImageView(c);
        i.setImageDrawable(icon == null ? null : icon.mutate());
        i.setColorFilter(quiet ? pal.text3 : pal.text2);
        int s = px(PaneStyle.ITEM_ICON);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(s, s);
        ilp.setMarginEnd(px(PaneStyle.ITEM_GAP));
        line.addView(i, ilp);
        TextView t = PaneStyle.text(c, PaneStyle.ITEM, 400, pal.text2, 0);
        t.setText(text);
        line.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pane.addView(line, next(0));
    }

    /** Empty room of {@code design} px before the next view. */
    public void space(float design) {
        pane.addView(new View(getContext()), new LinearLayout.LayoutParams(1, px(design)));
    }

    /** A running job: title, bar, step line. */
    public PaneProgress progress() {
        PaneProgress p = new PaneProgress(getContext(), pal);
        pane.addView(p, next(0));
        return p;
    }

    /** A code the user reads out (a report code): label and the code in large, even digits. Returns the code view. */
    public TextView code(CharSequence label, CharSequence code) {
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.surface);
        bg.setCornerRadius(px(PaneStyle.CODE_RADIUS));
        box.setBackground(bg);
        int ph = px(PaneStyle.CODE_PAD_H), pv = px(PaneStyle.CODE_PAD_V);
        box.setPaddingRelative(ph, pv, ph, pv);
        TextView k = PaneStyle.text(c, PaneStyle.CODE_LABEL, 400, pal.text3, 0);
        k.setText(label);
        box.addView(k);
        TextView v = PaneStyle.text(c, PaneStyle.CODE, PaneStyle.W_CODE, pal.text1, 0);
        v.setFontFeatureSettings(PaneStyle.FEATURES + ", 'tnum'");
        v.setLetterSpacing(PaneStyle.CODE_TRACK);
        v.setSingleLine(true);
        v.setTextDirection(TEXT_DIRECTION_LTR);
        v.setText(code);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = px(PaneStyle.CODE_GAP);
        box.addView(v, vlp);
        LinearLayout.LayoutParams lp = next(0);
        int m = px(PaneStyle.ROW_PAD);
        lp.setMarginStart(m);
        lp.setMarginEnd(m);
        lp.bottomMargin = px(PaneStyle.CODE_BOTTOM);
        pane.addView(box, lp);
        return v;
    }
}
