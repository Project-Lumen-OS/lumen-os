// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The small card in the corner: "X wants to show its screen" with Allow / Decline, or the PIN to type
 * on the phone. A system overlay window (TYPE_APPLICATION_OVERLAY; SYSTEM_ALERT_WINDOW is granted to
 * the platform-signed app), so it shows over any app without taking the screen. Main thread only.
 */
final class PromptOverlay {
    interface Answer {
        void onAnswer(boolean allow);
    }

    static final long ASK_TIMEOUT_MS = 30_000;
    static final long PIN_TIMEOUT_MS = 90_000;

    private final Context ctx;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private View shown;
    private Answer pending;
    private String lastHow = "";

    PromptOverlay(Context ctx) {
        this.ctx = ctx;
        this.wm = ctx.getSystemService(WindowManager.class);
    }

    boolean asking() {
        return pending != null;
    }

    /** How the last question ended: button, back, timeout, replaced (for the diagnostics). */
    String lastHow() {
        return lastHow;
    }

    void ask(String who, Answer answer) {
        hide();
        pending = answer;
        LinearLayout card = card();
        card.addView(text(ctx.getString(R.string.prompt_title, who), 20, true));
        card.addView(text(ctx.getString(R.string.prompt_hint), 15, false));
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(14), 0, 0);
        Button allow = button(R.string.prompt_allow, true);
        Button decline = button(R.string.prompt_decline, false);
        row.addView(allow);
        View gap = new View(ctx);
        row.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        row.addView(decline);
        card.addView(row);
        card.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                finish(false, "back");
                return true;
            }
            return false;
        });
        allow.setOnClickListener(v -> finish(true, "button"));
        decline.setOnClickListener(v -> finish(false, "button"));
        decline.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) finish(false, "back");
                return true;
            }
            return false;
        });
        allow.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) finish(false, "back");
                return true;
            }
            return false;
        });
        show(card, true);
        if (shown == null) {
            // the card could not be shown: answer now instead of leaving the sender waiting
            finish(false, "card not shown");
            return;
        }
        allow.requestFocus();
        main.postDelayed(timeout, ASK_TIMEOUT_MS);
    }

    void showPin(String who, String pin) {
        hide();
        LinearLayout card = card();
        card.addView(text(ctx.getString(R.string.pin_title, who), 18, false));
        TextView digits = text(spaced(pin), 44, true);
        digits.setLetterSpacing(0.08f);
        card.addView(digits);
        show(card, false);
        main.postDelayed(this::hide, PIN_TIMEOUT_MS);
    }

    /** A short note in the corner (for example after the session ended), gone after a few seconds. */
    void toast(String msg) {
        if (pending != null) return;
        hide();
        LinearLayout card = card();
        card.addView(text(msg, 18, false));
        show(card, false);
        main.postDelayed(this::hide, 4000);
    }

    void hide() {
        main.removeCallbacksAndMessages(null);
        if (shown != null) {
            if (shown.getParent() != null) {
                try {
                    wm.removeViewImmediate(shown);
                } catch (IllegalArgumentException ignored) {
                    // already gone
                }
            }
            shown = null;
        }
        Answer a = pending;
        pending = null;
        if (a != null) {
            lastHow = "replaced";
            a.onAnswer(false);
        }
    }

    private final Runnable timeout = () -> finish(false, "no answer in " + ASK_TIMEOUT_MS / 1000 + " s");

    private void finish(boolean allow, String how) {
        Answer a = pending;
        pending = null;
        lastHow = how;
        hide();
        if (a != null) a.onAnswer(allow);
    }

    private void show(View v, boolean focusable) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                (focusable ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = dp(40);
        lp.y = dp(40);
        lp.setTitle("LumenMiracastPrompt");
        try {
            wm.addView(v, lp);
            shown = v;
        } catch (RuntimeException e) {
            android.util.Log.w("LumenMiracast", "overlay: " + e);
            shown = null;
        }
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(22);
        l.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0181B20);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0x33FFFFFF);
        l.setBackground(bg);
        l.setMinimumWidth(dp(360));
        l.setFocusable(false);
        return l;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(bold ? 0xFFFFFFFF : 0xCCFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setMaxWidth(dp(460));
        return t;
    }

    private Button button(int label, boolean primary) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setMinWidth(dp(120));
        android.content.res.ColorStateList text = new android.content.res.ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}}, new int[] {0xFF101214, 0xFFFFFFFF});
        b.setTextColor(text);
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFFF2EEE6);
        on.setCornerRadius(dp(22));
        GradientDrawable off = new GradientDrawable();
        off.setColor(primary ? 0x33FFFFFF : 0x1FFFFFFF);
        off.setCornerRadius(dp(22));
        sl.addState(new int[] {android.R.attr.state_focused}, on);
        sl.addState(new int[] {}, off);
        b.setBackground(sl);
        b.setDefaultFocusHighlightEnabled(false);
        return b;
    }

    private static String spaced(String pin) {
        if (pin == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pin.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(pin.charAt(i));
        }
        return sb.toString();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics());
    }
}
