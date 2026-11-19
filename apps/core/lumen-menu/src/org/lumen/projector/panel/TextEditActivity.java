// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.projector.R;
import org.lumen.projector.ui.Theme;
import org.lumen.projector.ui.ToolActivity;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.TvFocus;

/**
 * The one text edit screen of the menu (the hotspot name and password; stock: a text dialog over the page): a card
 * with the title, one field and a line under it, over the dimmed picture, the keyboard (Lumen Keyboard) below. DONE
 * checks and saves (stock note "Текущая конфигурация сохранена"), a refused text says why under the field, BACK
 * leaves without saving. A tool screen (KeyTools): the menu steps aside while it is in front and comes back after.
 */
public final class TextEditActivity extends ToolActivity {
    private static final String TAG = "LumenTextEdit";
    static final String EXTRA_FIELD = "org.lumen.projector.extra.TEXT_FIELD";
    /** The dim over the picture (prototype hotspot_edit: rgba(0,0,0,.45)). */
    private static final int SCRIM = 0x73000000;
    private TextField field;
    private EditText edit;
    private TextView note;

    /** Opens the edit screen of {@code key} over what is on screen; false when it cannot start. */
    static boolean open(Context c, String key) {
        if (TextFields.of(key) == null) return false;
        try {
            c.startActivity(new Intent(c, TextEditActivity.class).putExtra(EXTRA_FIELD, key)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "open " + key + ": " + e);
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        field = TextFields.of(getIntent().getStringExtra(EXTRA_FIELD));
        if (field == null) {
            finish();
            return;
        }
        QpdStyle.theme(!LumenTheme.dark(this));
        setContentView(build());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        edit.requestFocus();
    }

    private int px(float design) { return Math.round(Theme.pxf(this, design)); }

    private TextView text(float size, int weight, int color) {
        TextView t = new TextView(this);
        t.setTypeface(QpdStyle.inter(this, weight, Math.round(size)));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Theme.pxf(this, size));
        t.setTextColor(color);
        return t;
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(SCRIM);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(QpdStyle.PANEL);
        bg.setCornerRadius(px(32));
        card.setBackground(bg);
        card.setPadding(px(40), px(34), px(40), px(30));
        TextView title = text(22, 600, QpdStyle.INK);
        title.setText(field.title());
        card.addView(title);
        edit = new EditText(this);
        edit.setTypeface(QpdStyle.inter(this, 400, 28));
        edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, Theme.pxf(this, 28));
        edit.setTextColor(QpdStyle.INK);
        edit.setHighlightColor(QpdStyle.SELECTED);
        GradientDrawable box = new GradientDrawable();
        box.setColor(QpdStyle.TILE);
        box.setCornerRadius(px(16));
        box.setStroke(px(2), QpdStyle.RING);
        edit.setBackground(box);
        GradientDrawable caret = new GradientDrawable();
        caret.setColor(QpdStyle.RING);
        caret.setSize(px(2), px(32));
        edit.setTextCursorDrawable(caret);
        edit.setPadding(px(24), px(16), px(24), px(16));
        edit.setSingleLine(true);
        edit.setFilters(field.filters(this));
        edit.setInputType(field.inputType());
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        String now = field.value(this);
        if (now != null) {
            edit.setText(now);
            edit.setSelection(now.length());
        }
        edit.setOnEditorActionListener((v, id, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (id != EditorInfo.IME_ACTION_DONE && !enter) return false;
            commit();
            return true;
        });
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void afterTextChanged(Editable s) { showHint(); }
        });
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fl.topMargin = px(22);
        card.addView(edit, fl);
        note = text(15, 400, QpdStyle.MUTE);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = px(14);
        card.addView(note, nl);
        showHint();
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(976), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cl.topMargin = px(150);
        root.addView(card, cl);
        return root;
    }

    /** The line under the field: the field's hint, or nothing. */
    private void showHint() {
        note.setTextColor(QpdStyle.MUTE);
        if (field.hint() != 0) note.setText(field.hint());
        note.setVisibility(field.hint() != 0 ? View.VISIBLE : View.GONE);
    }

    /** The line under the field says what is wrong (in the error colour), the text stays for a fix. */
    private void showProblem(int message) {
        note.setText(message);
        note.setTextColor(LumenTheme.palette(this).errorPanel);
        note.setVisibility(View.VISIBLE);
    }

    private void commit() {
        String t = edit.getText().toString();
        int problem = field.problem(t);
        if (problem != 0) {
            showProblem(problem);
            return;
        }
        if (!field.save(this, t)) {
            showProblem(field.failed());
            return;
        }
        if (field.saved() != 0) TvFocus.toast(this, field.saved(), false);
        finish();
    }

    /** The gear changes nothing on a text screen. */
    @Override
    public void onMenuKey(boolean held) {}
}
