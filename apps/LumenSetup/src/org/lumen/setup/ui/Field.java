// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.ui;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * Text field for passwords, SSIDs and names: 84 high, radius 16, surface fill, the card focus ring
 * when focused, a 3 px error line before the ring after a wrong entry ({@link #setError(boolean)}), system IME.
 */
public class Field extends EditText {
    private final Ring mRing;
    /** The text kept after a wrong password: shown selected, the first key typed replaces it all. */
    private String mKept;
    private int mReselects;

    public Field(Context c, CharSequence hint, boolean password) {
        super(c);
        setHint(hint);
        setSingleLine(true);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.pxf(30));
        setTypeface(Ui.font(Ui.W_REGULAR, 30));
        setTextColor(Ui.TEXT);
        setHintTextColor(Ui.TEXT3);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        int in = Ui.px(Ring.INSET);
        setPadding(in + Ui.px(28), 0, in + Ui.px(28), 0);
        setMinHeight(Ui.px(84) + 2 * in);
        setHighlightColor(org.lumen.ui.Palette.alpha(Ui.ACCENT, 0.4f));
        mRing = new Ring(Ui.SURFACE, Ui.P.dark ? 0 : Ui.FIELD_LINE, org.lumen.ui.Tokens.R_FIELD);
        setBackground(mRing);
        setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        setPassword(password, false);
        addTextChangedListener(new TextWatcher() {
            private String mTyped;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // the first edit of a kept password: what was typed (a delete types nothing)
                mTyped = mKept == null ? null : count > 0 ? s.subSequence(start, start + count).toString() : "";
            }

            @Override
            public void afterTextChanged(Editable e) {
                String typed = mTyped;
                mTyped = null;
                if (mKept == null || typed == null || e.toString().equals(mKept)) return;
                mKept = null;   // whatever the keyboard did with the selection, the old text goes
                setSelectAllOnFocus(false);
                if (!typed.equals(e.toString())) e.replace(0, e.length(), typed);
            }
        });
    }

    /**
     * Shows {@code text} selected (a password that was wrong): typing replaces it, a delete clears it, also with a
     * keyboard that moves the cursor to the end when it opens (the selection is put back a few times until the user
     * types, and the first edit drops the old text either way).
     */
    public void keepSelected(String text) {
        mKept = null;
        setText(text);
        if (text == null || text.isEmpty()) return;
        mKept = text;
        mReselects = 0;
        setSelectAllOnFocus(true);
        selectAll();
    }

    @Override
    protected void onSelectionChanged(int start, int end) {
        super.onSelectionChanged(start, end);
        if (mKept == null || (start == 0 && end == length()) || mReselects >= 3) return;
        mReselects++;
        post(() -> {
            if (mKept != null && getText().toString().equals(mKept)) selectAll();
        });
    }

    public void setPassword(boolean password, boolean visible) {
        int sel = getSelectionEnd();
        if (password) {
            setInputType(InputType.TYPE_CLASS_TEXT | (visible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        } else {
            setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        setTypeface(Ui.font(Ui.W_REGULAR, 30));
        if (sel >= 0 && sel <= length()) setSelection(sel);
    }

    /** The error line around the field (wrong password): stays until the next {@code setError(false)}. */
    public void setError(boolean e) {
        mRing.setError(e);
    }

    public void showIme() {
        requestFocus();
        post(() -> {
            InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    public void hideIme() {
        InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }
}
