// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.lumen.ui.Palette;

import java.util.ArrayList;
import java.util.List;

/**
 * The centred card of Files: a question (delete, unpack), typed names and server details, the progress of a copy
 * with its Cancel, and the details of a file. One at a time; BACK closes it unless a job runs (then BACK = Cancel).
 */
public final class Prompt extends FrameLayout {
    /** The view that had the focus when the window came up (it gets it back). */
    private View mBack;
    /** Where the focus goes when that view is gone. */
    Runnable lost;
    /** Called with the typed values; return an error to keep the card open, null to close it. */
    public interface Submit {
        String submit(String[] values);
    }

    /** One typed field. */
    public static final class Field {
        final CharSequence label, value, hint;
        final boolean secret;

        public Field(CharSequence label, CharSequence value, CharSequence hint, boolean secret) {
            this.label = label;
            this.value = value;
            this.hint = hint;
            this.secret = secret;
        }
    }

    static final int W = 820;
    private final LinearLayout mCard, mBody, mButtons;
    private final TextView mTitle, mText, mError;
    private final ProgressBar mBar;
    private final List<EditText> mFields = new ArrayList<>();
    private Runnable mCancel;
    private boolean mBusy;

    public Prompt(Context c) {
        super(c);
        setVisibility(GONE);
        setClickable(true);
        setClipChildren(false);
        mCard = new LinearLayout(c);
        mCard.setOrientation(LinearLayout.VERTICAL);
        mCard.setClipChildren(false);
        mCard.setPadding(Ui.px(48), Ui.px(40), Ui.px(48), Ui.px(40));
        mTitle = Ui.line(c, "", 32, Ui.SEMIBOLD, Ui.P.ptext1);
        mCard.addView(mTitle);
        mText = new TextView(c);
        Ui.text(mText, 24, Ui.REGULAR, Ui.P.ptext2);
        mText.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = Ui.px(16);
        mCard.addView(mText, tl);
        mBody = new LinearLayout(c);
        mBody.setOrientation(LinearLayout.VERTICAL);
        mBody.setClipChildren(false);
        mCard.addView(mBody, new LinearLayout.LayoutParams(-1, -2));
        mBar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        mBar.setMax(100);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.px(8));
        bl.topMargin = Ui.px(24);
        mCard.addView(mBar, bl);
        mError = new TextView(c);
        Ui.text(mError, 22, Ui.MEDIUM, Ui.P.errorPanel);
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, -2);
        el.topMargin = Ui.px(16);
        mCard.addView(mError, el);
        mButtons = new LinearLayout(c);
        mButtons.setClipChildren(false);
        mButtons.setGravity(Gravity.END);
        LinearLayout.LayoutParams btl = new LinearLayout.LayoutParams(-1, -2);
        btl.topMargin = Ui.px(36);
        mCard.addView(mButtons, btl);
        addView(mCard, new FrameLayout.LayoutParams(Ui.px(W), -2, Gravity.CENTER));
    }

    public boolean shown() {
        return getVisibility() == VISIBLE;
    }

    public boolean busy() {
        return mBusy;
    }

    private void open(CharSequence title, CharSequence text) {
        Palette p = Ui.P;
        setBackgroundColor(Palette.alpha(p.scrim, 0.55f));
        mCard.setBackground(Ui.round(p.panel, 28));
        mTitle.setTextColor(p.ptext1);
        mText.setTextColor(p.ptext2);
        mError.setTextColor(p.errorPanel);
        mTitle.setText(title);
        mText.setText(text);
        mText.setVisibility(text == null || text.length() == 0 ? GONE : VISIBLE);
        mBody.removeAllViews();
        mButtons.removeAllViews();
        mFields.clear();
        mBar.setVisibility(GONE);
        mError.setVisibility(GONE);
        mBusy = false;
        mCancel = null;
        if (!shown()) mBack = getRootView().findFocus();
        setVisibility(VISIBLE);
    }

    Box button(CharSequence label, boolean primary, Runnable r) {
        Context c = getContext();
        Box b = new Box(c);
        b.radius = 28;
        b.lift = 1.05f;
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.px(32), 0, Ui.px(32), 0);
        TextView t = Ui.line(c, label, 22, Ui.SEMIBOLD, Ui.P.ptext1);
        b.addView(b.inked(t));
        Palette p = Ui.P;
        b.look(primary ? p.surface2 : p.surface, p.focus, p.ptext1, p.onFocus);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, Ui.px(56));
        l.setMarginStart(Ui.px(16));
        mButtons.addView(b, l);
        return b;
    }

    /** A question with a main button and Cancel. */
    public void confirm(CharSequence title, CharSequence text, CharSequence yes, CharSequence no, Runnable ok) {
        ask(title, text, yes, no, ok, false);
    }

    /** A confirm that cannot be undone (delete): the focus starts on Cancel, so a double OK never deletes. */
    public void confirmLoss(CharSequence title, CharSequence text, CharSequence yes, CharSequence no, Runnable ok) {
        ask(title, text, yes, no, ok, true);
    }

    private void ask(CharSequence title, CharSequence text, CharSequence yes, CharSequence no, Runnable ok, boolean safe) {
        open(title, text);
        Box n = button(no, false, this::hide);
        Box y = button(yes, true, () -> {
            hide();
            ok.run();
        });
        (safe ? n : y).requestFocus();
    }

    /** Facts of a file: label and value per line; {@code value(i)} can be changed later (a folder's size). */
    public void info(CharSequence title, CharSequence[][] rows, CharSequence close) {
        open(title, null);
        Context c = getContext();
        for (CharSequence[] r : rows) {
            LinearLayout l = new LinearLayout(c);
            TextView k = Ui.line(c, r[0], 22, Ui.REGULAR, Ui.P.ptext2);
            l.addView(k, new LinearLayout.LayoutParams(Ui.px(260), -2));
            TextView v = new TextView(c);
            Ui.text(v, 22, Ui.MEDIUM, Ui.P.ptext1);
            v.setText(r[1]);
            l.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            ll.topMargin = Ui.px(14);
            mBody.addView(l, ll);
        }
        button(close, true, this::hide).requestFocus();
    }

    public void value(int row, CharSequence v) {
        if (row < 0 || row >= mBody.getChildCount()) return;
        View l = mBody.getChildAt(row);
        if (l instanceof LinearLayout && ((LinearLayout) l).getChildCount() > 1) ((TextView) ((LinearLayout) l).getChildAt(1)).setText(v);
    }

    /** Typed fields (a name, a server's details) with OK and Cancel. */
    public void form(CharSequence title, CharSequence text, Field[] fields, CharSequence ok, CharSequence cancel, Submit s) {
        open(title, text);
        Context c = getContext();
        for (Field f : fields) {
            TextView k = Ui.line(c, f.label, 20, Ui.MEDIUM, Ui.P.ptext2);
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-1, -2);
            kl.topMargin = Ui.px(22);
            mBody.addView(k, kl);
            EditText e = new EditText(c);
            Ui.text(e, 26, Ui.REGULAR, Ui.P.ptext1);
            e.setHintTextColor(Ui.P.ptext3);
            e.setSingleLine(true);
            e.setText(f.value);
            e.setHint(f.hint);
            e.setInputType(f.secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_FULLSCREEN);
            e.setPadding(Ui.px(20), Ui.px(14), Ui.px(20), Ui.px(14));
            e.setBackground(Ui.round(Ui.P.surface, 14));
            e.setOnFocusChangeListener((v, has) -> v.setBackground(Ui.round(has ? Ui.P.surface2 : Ui.P.surface, 14)));
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, -2);
            el.topMargin = Ui.px(8);
            mBody.addView(e, el);
            mFields.add(e);
        }
        button(cancel, false, this::hide);
        Box go = button(ok, true, () -> {
            String[] v = new String[mFields.size()];
            for (int i = 0; i < v.length; i++) v[i] = mFields.get(i).getText().toString();
            String err = s.submit(v);
            if (err != null) error(err);
        });
        if (!mFields.isEmpty()) {
            EditText last = mFields.get(mFields.size() - 1);
            last.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN);
            last.setOnEditorActionListener((v, id, ev) -> {
                if (id == EditorInfo.IME_ACTION_DONE) {
                    go.requestFocus();
                    return true;
                }
                return false;
            });
            mFields.get(0).requestFocus();
            EditText first = mFields.get(0);
            first.setSelection(first.getText().length());
        } else {
            go.requestFocus();
        }
    }

    /** Selects the part of the first field before the ending (a rename keeps ".mkv"). */
    public void selectStem(int end) {
        if (mFields.isEmpty()) return;
        EditText e = mFields.get(0);
        e.setSelection(0, Math.max(0, Math.min(end, e.getText().length())));
    }

    public void error(CharSequence e) {
        mError.setText(e);
        mError.setVisibility(e == null || e.length() == 0 ? GONE : VISIBLE);
    }

    /** A running job: its bar, a line under it, Cancel. */
    public void progress(CharSequence title, CharSequence cancel, Runnable onCancel) {
        open(title, " ");
        mBusy = true;
        mCancel = onCancel;
        mBar.setVisibility(VISIBLE);
        mBar.setIndeterminate(true);
        button(cancel, true, this::cancel).requestFocus();
    }

    public void update(int percent, CharSequence line) {
        mBar.setIndeterminate(percent < 0);
        if (percent >= 0) mBar.setProgress(percent);
        mText.setVisibility(VISIBLE);
        mText.setText(line);
    }

    public void cancel() {
        if (mCancel != null) mCancel.run();
        mCancel = null;
    }

    public void hide() {
        mBusy = false;
        mCancel = null;
        Sheet.give(mBack, lost);
        mBack = null;
        setVisibility(GONE);
        mBody.removeAllViews();
        mButtons.removeAllViews();
        mFields.clear();
    }

    public boolean owns(View focused) {
        for (View v = focused; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) if (v == this) return true;
        return false;
    }
}
