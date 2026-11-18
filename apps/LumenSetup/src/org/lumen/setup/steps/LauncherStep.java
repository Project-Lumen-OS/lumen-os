package org.lumen.setup.steps;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.setup.L;
import org.lumen.setup.R;
import org.lumen.setup.SetupActivity;
import org.lumen.setup.SetupState;
import org.lumen.setup.Sys;
import org.lumen.setup.ui.Icon;
import org.lumen.setup.ui.LauncherPreview;
import org.lumen.setup.ui.Ring;
import org.lumen.setup.ui.Ui;

/**
 * Home screen choice: two big cards, Lumen Home (recommended, focused by
 * default) and the classic Android TV launcher. Only stored here; Finish applies it through
 * Z9xProjector's LauncherSwitcher (bridge set_launcher). Hidden when only one launcher is installed.
 * Also the only step of the one-time "choose your home screen" after an upgrade over old data.
 */
public class LauncherStep extends Step {
    public static final String ID = "launcher";
    /** Two cards + 80 px gap fit the 1600 px content width; total height fits the 800 px column. */
    private static final int CARD_W = 820, PREVIEW_H = 462;
    private View mLumenCard;
    private View mClassicCard;

    public LauncherStep(SetupActivity h) {
        super(h);
    }

    @Override
    public String id() { return ID; }

    @Override
    public boolean available() { return Sys.hasLumenHome(host) && Sys.hasClassic(host); }

    @Override
    public int layout() { return WIDE; }

    @Override
    public CharSequence title() { return s(R.string.launcher_title); }

    /** the design notes WIDE: title on top, two cards of 820 with a 820 x 462 preview and a caption, 88 apart. */
    @Override
    public View createContent() {
        LinearLayout v = Ui.vbox(ctx());
        TextView t = Ui.title(ctx());
        t.setText(s(R.string.launcher_title));
        v.addView(t);
        TextView sub = Ui.subtitle(ctx());
        sub.setText(s(R.string.launcher_subtitle));
        v.addView(sub, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 22));
        LinearLayout cards = Ui.hbox(ctx());
        cards.setGravity(Gravity.TOP | Gravity.START);
        boolean classic = SetupState.LAUNCHER_CLASSIC.equals(host.state.launcher());
        mLumenCard = card(homePreview(), s(R.string.launcher_lumen), s(R.string.launcher_lumen_desc), true,
                !classic, SetupState.LAUNCHER_LUMEN);
        mClassicCard = card(new LauncherPreview(ctx()), s(R.string.launcher_classic), s(R.string.launcher_classic_desc),
                false, classic, SetupState.LAUNCHER_CLASSIC);
        int in = Ui.px(Ring.INSET);
        cards.addView(mLumenCard, new LinearLayout.LayoutParams(Ui.px(CARD_W) + 2 * in, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.px(CARD_W) + 2 * in, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.setMarginStart(Ui.px(88) - 2 * in);
        cards.addView(mClassicCard, cl);
        LinearLayout.LayoutParams sl = Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 64);
        sl.setMarginStart(-in);
        v.addView(cards, sl);
        v.setPadding(0, Ui.px(18), 0, 0);
        return v;
    }

    /**
     * the design notes: a real picture of Lumen Home 2.0 in the theme on screen, rendered from Home itself with the demo
     * data of the render harness (stills of Blender open movies, CC BY: credited in the Licences screen), English and
     * Russian (res/drawable-nodpi, res/drawable-ru-nodpi).
     */
    private View homePreview() {
        android.widget.ImageView iv = new android.widget.ImageView(ctx());
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        iv.setImageResource(Ui.P.dark ? R.drawable.setup_home_preview_dark : R.drawable.setup_home_preview_light);
        return iv;
    }

    private View card(View p, String title, String desc, boolean recommended, boolean chosen, String value) {
        Card c = new Card(ctx());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setFocusable(true);
        c.setClickable(true);
        int in = Ui.px(Ring.INSET);
        FrameLayout pv = new FrameLayout(ctx());
        pv.setDuplicateParentStateEnabled(true);
        pv.setBackground(new Ring(0, 0, 24));
        pv.setPadding(in, in, in, in);
        p.setClipToOutline(true);
        p.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.pxf(24));
            }
        });
        pv.addView(p, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        c.addView(pv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(PREVIEW_H) + 2 * in));
        LinearLayout head = Ui.hbox(ctx());
        head.setPaddingRelative(in + Ui.px(6), 0, in + Ui.px(6), 0);
        TextView tt = Ui.text(ctx(), 30, Ui.TEXT, 650);
        tt.setLetterSpacing(-0.3f / 30f);
        tt.setText(title);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ttl.weight = 0f;
        head.addView(tt, ttl);
        if (recommended) {
            TextView badge = Ui.text(ctx(), 18, Ui.BG, 650);
            badge.setText(s(R.string.launcher_recommended));
            badge.setBackground(Ui.roundRect(Ui.TEXT, 10));
            badge.setPadding(Ui.px(12), Ui.px(6), Ui.px(12), Ui.px(6));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.setMarginStart(Ui.px(14));
            head.addView(badge, bl);
        }
        // the choice mark sits at the end of the caption: on the picture it covered the header of the home screen
        head.addView(new View(ctx()), new LinearLayout.LayoutParams(0, 1, 1f));
        Icon radio = new Icon(ctx(), chosen ? Icon.RADIO_ON : Icon.RADIO_OFF).color(Ui.TEXT);
        head.addView(radio, new LinearLayout.LayoutParams(Ui.px(36), Ui.px(36)));
        c.addView(head, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 26));
        TextView dd = Ui.text(ctx(), 24, Ui.TEXT_DIM, Ui.W_REGULAR);
        dd.setText(desc);
        dd.setMaxLines(2);
        dd.setEllipsize(TextUtils.TruncateAt.END);
        dd.setPaddingRelative(in + Ui.px(6), 0, in + Ui.px(6), 0);
        c.addView(dd, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8));
        c.setOnClickListener(x -> {
            host.state.setLauncher(value);
            L.i("launcher choice=" + value);
            host.next("next");
        });
        return c;
    }

    @Override
    public View initialFocus() {
        return SetupState.LAUNCHER_CLASSIC.equals(host.state.launcher()) ? mClassicCard : mLumenCard;
    }

    /** A launcher card: scale 1.03 on focus, its preview frame draws the ring (duplicateParentState). */
    private static final class Card extends LinearLayout {
        Card(Context c) {
            super(c);
            setClipChildren(false);
        }

        @Override
        protected void onFocusChanged(boolean f, int dir, android.graphics.Rect prev) {
            super.onFocusChanged(f, dir, prev);
            Ui.focusScale(this, f, 1.03f);
        }
    }
}
