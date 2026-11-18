package org.lumen.setup.steps;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.setup.R;
import org.lumen.setup.SetupActivity;
import org.lumen.setup.ui.Pill;
import org.lumen.setup.ui.Ui;
import org.lumen.qr.QrView;

/**
 * Alice link: a placeholder slot. Hidden in Lumen OS 1.0 (user decision: Alice via Yaha
 * Cloud comes later). Shown only when Z9xProjector's bridge answers feature("alice") with
 * enabled=true; the ALICE track then supplies "url" and "code" in that reply.
 */
public class AliceStep extends Step {
    public static final String ID = "alice";

    public AliceStep(SetupActivity h) {
        super(h);
    }

    @Override
    public String id() { return ID; }

    @Override
    public boolean available() {
        Bundle b = host.aliceInfo;
        return b != null && b.getBoolean("enabled", false) && b.getString("url") != null;
    }

    @Override
    public CharSequence title() { return s(R.string.alice_title); }

    @Override
    public CharSequence subtitle() { return s(R.string.alice_subtitle); }

    /** the design notes: a phone with the Yandex app and the same account as the speaker. */
    @Override
    public View leftExtra() { return Ui.note(ctx(), s(R.string.alice_note)); }

    /** The QR card's top is level with the title (y 300). */
    @Override
    public int rightTop() { return 300; }

    /** Design px of the QR inside the 400 card: whole-pixel modules fill about 87 % of it. */
    private static final int CARD = 400, QR = 352;

    /**
     * the design notes: the QR code on a white card (400, radius 28), then "Scan the code with your phone", "Or enter
     * the code" and the code itself on its own line (44, tabular), Continue (focused) and Skip.
     */
    @Override
    public View createContent() {
        Bundle b = host.aliceInfo;
        LinearLayout v = Ui.hbox(ctx());
        v.setGravity(Gravity.TOP);
        FrameLayout card = new FrameLayout(ctx());
        card.setBackground(Ui.roundRect(0xFFFFFFFF, 28));
        card.setElevation(Ui.pxf(Ui.P.shadowBlur) / 4f);
        String url = b == null ? null : b.getString("url");
        if (url != null) {
            card.addView(new QrView(ctx(), url).quiet(0), new FrameLayout.LayoutParams(Ui.px(QR), Ui.px(QR), Gravity.CENTER));
        }
        v.addView(card, new LinearLayout.LayoutParams(Ui.px(CARD), Ui.px(CARD)));
        LinearLayout col = Ui.vbox(ctx());
        col.setPadding(0, Ui.px(40), 0, 0);
        TextView scan = Ui.text(ctx(), 28, Ui.TEXT, Ui.W_STATUS);
        scan.setLetterSpacing(-0.3f / 28f);
        scan.setText(s(R.string.alice_scan));
        col.addView(scan);
        String code = b == null ? null : b.getString("code");
        if (code != null) {
            TextView label = Ui.body(ctx(), s(R.string.alice_code_label));
            col.addView(label, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 20));
            TextView c = Ui.text(ctx(), 44, Ui.TEXT, 650);
            c.setLetterSpacing(4f / 44f);
            c.setFontFeatureSettings("'tnum'");
            c.setText(code);
            col.addView(c, Ui.lpTop(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 10));
        }
        LinearLayout btns = Ui.hbox(ctx());
        mContinue = new Pill(ctx(), s(R.string.action_continue), false);
        mContinue.setOnClickListener(x -> host.next("next"));
        btns.addView(mContinue);
        Pill skip = new Pill(ctx(), s(R.string.action_skip), false);
        skip.setOnClickListener(x -> host.next("skip"));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.setMarginStart(Ui.px(16));
        btns.addView(skip, sl);
        col.addView(btns, Ui.lpTop(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 48));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.setMarginStart(Ui.px(56));
        v.addView(col, cl);
        return v;
    }

    private Pill mContinue;

    @Override
    public View initialFocus() { return mContinue; }
}
