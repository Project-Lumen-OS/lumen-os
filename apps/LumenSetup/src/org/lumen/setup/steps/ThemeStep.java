package org.lumen.setup.steps;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.setup.L;
import org.lumen.setup.R;
import org.lumen.setup.SetupActivity;
import org.lumen.setup.ThemeChoice;
import org.lumen.setup.ui.Icon;
import org.lumen.setup.ui.Row;
import org.lumen.setup.ui.ThemePreview;
import org.lumen.setup.ui.Ui;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.ThemeMode;

/**
 * Theme (right after the language, 2026-11-19): Светлая (the default, focused first), Тёмная, Авто. The preview
 * on the right follows the focused row at once; OK writes the shared lumen_ui_theme through lumen-ui (LumenTheme), so
 * Home and the quick menu switch with it, and the wizard itself takes the new colours before the next step.
 * Layout like a SPLIT step drawn by the step itself (WIDE): title, explanation and the rows in the left column (x 96,
 * title from y 300), the preview at the right column (x 880, 944 x 531).
 */
public class ThemeStep extends Step {
    public static final String ID = "theme";
    private static final int PREVIEW_W = 944, PREVIEW_H = 531, LEFT_W = 660, TOP = 300;
    private ThemePreview mPreview;
    private View mFocus;

    public ThemeStep(SetupActivity h) {
        super(h);
    }

    @Override
    public String id() { return ID; }

    @Override
    public int layout() { return WIDE; }

    @Override
    public int rightTop() { return 0; }

    @Override
    public CharSequence title() { return s(R.string.theme_title); }

    @Override
    public View createContent() {
        ThemeMode stored = LumenTheme.mode(host);
        LinearLayout v = Ui.hbox(ctx());
        v.setGravity(Gravity.TOP | Gravity.START);
        v.setPadding(0, Ui.px(TOP), 0, 0);

        LinearLayout left = Ui.vbox(ctx());
        TextView t = Ui.title(ctx());
        t.setText(s(R.string.theme_title));
        left.addView(t, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sub = Ui.subtitle(ctx());
        sub.setText(s(R.string.theme_subtitle));
        left.addView(sub, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 22));
        LinearLayout list = Ui.vbox(ctx());
        int first = ThemeChoice.initialRow(stored);
        mPreview = new ThemePreview(ctx());
        mFocus = null;
        for (int i = 0; i < ThemeChoice.ORDER.length; i++) {
            final ThemeMode m = ThemeChoice.ORDER[i];
            Row r = new Row(ctx(), s(label(m)));
            if (m == ThemeMode.AUTO) r.subtitle(s(R.string.theme_auto_sub));
            r.end(new Icon(ctx(), i == first ? Icon.RADIO_ON : Icon.RADIO_OFF), Ui.px(32), Ui.px(32));
            r.setOnFocusChangeListener((x, f) -> {
                if (f) mPreview.show(ThemeChoice.lightShare(m), true);
            });
            r.setOnClickListener(x -> pick(m));
            if (i == first) mFocus = r;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.px(6);
            list.addView(r, lp);
        }
        // the rows' text lines up with the title: the row's own 28 px padding hangs out to the left
        LinearLayout.LayoutParams ll = Ui.lpTop(Ui.px(LEFT_W + 28), ViewGroup.LayoutParams.WRAP_CONTENT, 44);
        ll.setMarginStart(-Ui.px(28));
        left.addView(list, ll);
        v.addView(left, new LinearLayout.LayoutParams(Ui.px(LEFT_W), ViewGroup.LayoutParams.WRAP_CONTENT));

        mPreview.show(ThemeChoice.lightShare(ThemeChoice.ORDER[first]), false);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.px(PREVIEW_W), Ui.px(PREVIEW_H));
        pl.setMarginStart(Ui.px(org.lumen.ui.Tokens.SETUP_SPLIT_X - org.lumen.ui.Tokens.MARGIN - LEFT_W));
        v.addView(mPreview, pl);
        return v;
    }

    private static int label(ThemeMode m) {
        switch (m) {
            case DARK: return R.string.theme_dark;
            case AUTO: return R.string.theme_auto;
            default: return R.string.theme_light;
        }
    }

    @Override
    public View initialFocus() { return mFocus; }

    /** OK on a row: the shared choice is written, the wizard takes the new colours, then the next step. */
    private void pick(ThemeMode m) {
        boolean wrote = LumenTheme.setMode(host, m);
        L.i("theme choice=" + m.id + (wrote ? "" : " (not written)"));
        if (ThemeChoice.needsRetheme(Ui.P.dark, LumenTheme.dark(host))) {
            host.retheme(() -> host.next("next"));
        } else {
            host.next("next");
        }
    }
}
