package org.lumen.keyboard;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.inputmethod.leanback.R;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Keyboard languages: the keyboard's settings screen (Settings, Device Preferences, Keyboard, "Lumen Keyboard
 * settings", and a long OK on the language key). One row per language with a page; OK turns it on or off; the
 * last one stays on. Remote only: D-pad up and down, OK, BACK. Rows and switches look like the Lumen menu's: the
 * focused row is the accent colour, the switch on the right is the Material Symbols toggle of apps/core/lumen-icons.
 */
public final class LanguagesActivity extends Activity {
    private static final int ACCENT = 0xFF3B78E7;
    private static final int TEXT = 0xFFF1F3F4;
    private static final int MUTED = 0xB3F1F3F4;
    /** The Lumen menu switch (QpdStyle): white track on a focused row, a pale outline when off. */
    private static final int SWITCH_FOCUSED = 0xF2FFFFFF;
    private static final int SWITCH_OFF = 0x8CFFFFFF;

    private InputMethodInfo mMe;
    private final Set<KeyboardLanguage> mOn = EnumSet.noneOf(KeyboardLanguage.class);
    private TextView mNote;
    private final ImageView[] mMarks = new ImageView[KeyboardLanguage.values().length];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF16181C));
        mMe = ImeLanguages.self(this);
        if (mMe != null) mOn.addAll(ImeLanguages.on(this, mMe));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(96), dp(32), dp(96), dp(24));
        col.addView(text(getString(R.string.lumen_languages_title), 26, TEXT, true));
        TextView hint = text(getString(R.string.lumen_languages_hint), 16, MUTED, false);
        hint.setPadding(0, dp(6), 0, dp(16));
        hint.setMaxWidth(dp(760));
        col.addView(hint);

        Locale ui = getResources().getConfiguration().getLocales().get(0);
        View first = null;
        for (KeyboardLanguage l : KeyboardLanguage.values()) {
            View row = row(l, ui);
            col.addView(row);
            if (first == null) first = row;
        }
        mNote = text("", 15, MUTED, false);
        mNote.setPadding(dp(4), dp(8), 0, 0);
        col.addView(mNote);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
        if (first != null) first.requestFocus();
    }

    private View row(KeyboardLanguage l, Locale ui) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(20), dp(6), dp(12), dp(6));
        r.setFocusable(true);
        r.setBackground(rowBackground());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(560), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        r.setLayoutParams(lp);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        String own = LanguagePolicy.ownName(l, ui);
        // a right to left name (Arabic) in a left to right UI: a left to right mark keeps it at the row's start
        boolean ltrUi = android.text.TextUtils.getLayoutDirectionFromLocale(ui) == View.LAYOUT_DIRECTION_LTR;
        names.addView(text(ltrUi ? "\u200E" + own : own, 18, TEXT, false));
        Locale shown = l == KeyboardLanguage.LATIN ? LanguagePolicy.latinLocale(ui) : new Locale(l.tag);
        String inUi = shown.getDisplayLanguage(ui);
        if (!inUi.isEmpty() && !inUi.equalsIgnoreCase(own)) names.addView(text(inUi, 13, MUTED, false));
        r.addView(names, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView mark = new ImageView(this);
        mark.setDuplicateParentStateEnabled(true);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        r.addView(mark, new LinearLayout.LayoutParams(dp(44), dp(44)));
        mMarks[l.ordinal()] = mark;
        showMark(l);

        r.setOnClickListener(v -> toggle(l));
        return r;
    }

    private void toggle(KeyboardLanguage l) {
        if (mMe == null) return;
        if (mOn.contains(l)) {
            if (!LanguagePolicy.canTurnOff(mOn, l)) {
                mNote.setText(R.string.lumen_languages_last);
                return;
            }
            mOn.remove(l);
        } else {
            mOn.add(l);
        }
        mNote.setText("");
        ImeLanguages.set(this, mMe, mOn);
        showMark(l);
    }

    private void showMark(KeyboardLanguage l) {
        ImageView m = mMarks[l.ordinal()];
        boolean on = mOn.contains(l);
        m.setImageDrawable(toggle(on));
        m.setContentDescription(getString(on ? R.string.btn_on : R.string.btn_off));
    }

    /** The switch: accent track with a white knob when on, accent knob on a white track in the focused row. */
    private Drawable toggle(boolean on) {
        Drawable icon = getDrawable(on ? R.drawable.lumen_ic_toggle_on : R.drawable.lumen_ic_toggle_off).mutate();
        icon.setTintList(focusedOr(SWITCH_FOCUSED, on ? ACCENT : SWITCH_OFF));
        if (!on) return icon;
        Drawable knob = getDrawable(R.drawable.lumen_toggle_knob).mutate();
        knob.setTintList(focusedOr(ACCENT, Color.WHITE));
        return new LayerDrawable(new Drawable[]{knob, icon});
    }

    private static ColorStateList focusedOr(int focused, int other) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focused, other});
    }

    private StateListDrawable rowBackground() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, round(ACCENT));
        s.addState(new int[0], round(0x14FFFFFF));
        return s;
    }

    private GradientDrawable round(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(12));
        return g;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        // an Arabic name in a left to right UI starts at the left like the other rows
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
