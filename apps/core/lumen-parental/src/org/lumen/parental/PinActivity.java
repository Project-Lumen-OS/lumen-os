// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.parental;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * The stock child lock password screen: four presses of the remote's arrow keys, each shown as its arrow. Modes: check
 * (then an optional launch), set twice, change (check, then set), power on (no way back but the password). After a wrong
 * password OK opens the forgotten password question (stock 找回密码 / 答题解锁), whose right answer sets a new one.
 */
public final class PinActivity extends Activity {
    static final String EXTRA_MODE = "mode", EXTRA_THEN = "then";
    static final int VERIFY = 0, SET = 1, CHANGE = 2, BOOT = 3;
    /** Stock MAX_ERROR_COUNT: after three wrong tries the screen points at the question. */
    static final int MAX_ERRORS = 3;

    private int mode, stage, errors;
    private String keys = "", first;
    private TextView title, hint;
    private LinearLayout slots;
    private Quiz quiz;
    private final Random random = new Random();

    static void verifyThen(Context c, Intent then) {
        c.startActivity(new Intent(c, PinActivity.class).putExtra(EXTRA_MODE, VERIFY).putExtra(EXTRA_THEN, then)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static void boot(Activity a) {
        a.startActivity(new Intent(a, PinActivity.class).putExtra(EXTRA_MODE, BOOT));
    }

    static Intent forResult(Context c, int mode) { return new Intent(c, PinActivity.class).putExtra(EXTRA_MODE, mode); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getIntExtra(EXTRA_MODE, VERIFY);
        stage = mode == SET ? SET : VERIFY;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Look.BG);
        title = Look.text(this, "", 26, Look.TEXT, true);
        hint = Look.text(this, "", 15, Look.SUB, false);
        slots = new LinearLayout(this);
        slots.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Look.dp(this, 28);
        lp.bottomMargin = Look.dp(this, 22);
        root.addView(slots, lp);
        root.addView(hint, new LinearLayout.LayoutParams(-2, -2));
        setContentView(root);
        show();
    }

    private void show() {
        quiz = null;
        title.setText(stage == SET ? (first == null ? R.string.lumen_lp_pin_set : R.string.lumen_lp_pin_again) : R.string.lumen_lp_pin_enter);
        if (hint.getTag() == null) hint.setText(R.string.lumen_lp_pin_hint);
        hint.setTag(null);
        slots.removeAllViews();
        for (int i = 0; i < PinCode.LENGTH; i++) {
            ImageView v = new ImageView(this);
            v.setBackground(Look.round(Look.CARD, Look.dp(this, 14)));
            int pad = Look.dp(this, 14);
            v.setPadding(pad, pad, pad, pad);
            if (i < keys.length()) {
                v.setImageResource(arrow(keys.charAt(i)));
                v.setColorFilter(Look.FOCUS);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Look.dp(this, 64), Look.dp(this, 64));
            lp.leftMargin = lp.rightMargin = Look.dp(this, 8);
            slots.addView(v, lp);
        }
    }

    private static int arrow(char c) {
        switch (c) {
            case PinCode.UP: return org.lumen.icons.R.drawable.lumen_ic_key_up;
            case PinCode.DOWN: return org.lumen.icons.R.drawable.lumen_ic_key_down;
            case PinCode.LEFT: return org.lumen.icons.R.drawable.lumen_ic_key_left;
            default: return org.lumen.icons.R.drawable.lumen_ic_key_right;
        }
    }

    private void note(int res) {
        hint.setText(res);
        hint.setTag(Boolean.TRUE);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (quiz != null) return quizKey(code) || super.onKeyDown(code, e);
        char k = PinCode.letter(code);
        if (k != 0) {
            keys += k;
            show();
            if (keys.length() == PinCode.LENGTH) slots.postDelayed(this::entered, 150);
            return true;
        }
        if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && stage == VERIFY) {
            askQuiz();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK && mode == BOOT) return true;
        return super.onKeyDown(code, e);
    }

    private void entered() {
        String k = keys;
        keys = "";
        ParentalState s = Parental.load(this);
        if (stage == VERIFY) {
            if (!s.pin(k)) {
                errors++;
                note(errors >= MAX_ERRORS ? R.string.lumen_lp_pin_forgot : R.string.lumen_lp_pin_wrong);
                show();
                return;
            }
            if (mode == CHANGE) {
                stage = SET;
                show();
                return;
            }
            done(s);
            return;
        }
        if (first == null) {
            first = k;
            show();
            return;
        }
        if (!first.equals(k)) {
            first = null;
            note(R.string.lumen_lp_pin_mismatch);
            show();
            return;
        }
        s.setPin(k);
        Parental.save(this, s);
        done(s);
    }

    private void done(ParentalState s) {
        if (mode == BOOT) Parental.bootUnlocked(this);
        Intent then = getIntent().getParcelableExtra(EXTRA_THEN);
        setResult(RESULT_OK);
        finish();
        if (then != null) {
            try {
                startActivity(then);
            } catch (RuntimeException ignored) {
                // the app went away meanwhile
            }
        }
        if (mode == BOOT && ParentalRules.controlled(s)) UsageService.start(this);
    }

    private TextView[] answers;
    private int pick;

    private void askQuiz() {
        quiz = Quiz.next(random);
        if (hint.getTag() == null) hint.setText("");
        hint.setTag(null);
        title.setText(getString(R.string.lumen_lp_quiz, quiz.a, quiz.b));
        slots.removeAllViews();
        answers = new TextView[quiz.choices.length];
        for (int i = 0; i < answers.length; i++) {
            answers[i] = Look.button(this, Integer.toString(quiz.choices[i]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = lp.rightMargin = Look.dp(this, 8);
            slots.addView(answers[i], lp);
        }
        pick = 0;
        answers[0].requestFocus();
    }

    private boolean quizKey(int code) {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            pick = Math.floorMod(pick + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1), answers.length);
            answers[pick].requestFocus();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (quiz.right(quiz.choices[pick])) {
                stage = SET;
                first = null;
                show();
            } else {
                note(R.string.lumen_lp_quiz_wrong);
                askQuiz();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            show();
            return true;
        }
        return false;
    }
}
