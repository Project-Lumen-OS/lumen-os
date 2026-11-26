// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;

import org.lumen.parental.PinCode;
import org.lumen.parental.Quiz;
import org.lumen.projector.R;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.TextRow;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The child lock password as a page of the menu (stock: four presses of the remote's arrow keys): check, set twice,
 * or change (check, then set). After a wrong password OK asks the forgotten password question (stock 找回密码 /
 * 答题解锁), whose right answer lets a new password be set. BACK leaves the page (nothing changes).
 */
final class PinPage {
    static final int VERIFY = 0, SET = 1, CHANGE = 2;
    /** Stock MAX_ERROR_COUNT: after three wrong tries the hint points at the question. */
    static final int MAX_ERRORS = 3;

    /** The password store (ParentalPage: Lumen Home through ParentalClient), answers on the main thread. */
    interface Store {
        void check(String keys, Consumer<Boolean> ok);

        void set(String keys, Consumer<Boolean> ok);
    }

    private final Context app;
    private final int mode;
    private final Store store;
    private final Runnable done;
    /** Repaints the page with the focus on that row. */
    private final IntConsumer repaint;
    private final Random random = new Random();
    final Page page;
    private final PinRow pin;
    private final TextRow hint;
    private int stage, errors;
    private String first;
    private boolean busy;

    PinPage(Context c, int mode, Store store, Runnable done, IntConsumer repaint) {
        app = c.getApplicationContext();
        this.mode = mode;
        this.store = store;
        this.done = done;
        this.repaint = repaint;
        stage = mode == SET ? SET : VERIFY;
        page = new Page("");
        pin = new PinRow(app, new PinRow.Keys() {
            @Override public void key(char k) { pressed(k); }

            @Override public void ok() { if (stage == VERIFY && !busy) quiz(null); }
        });
        hint = new TextRow(app, s(R.string.lumen_lp_pin_hint));
        entry(R.string.lumen_lp_pin_hint);
    }

    private String s(int res) { return app.getString(res); }

    /** The password entry: its title for the stage, the slots, a hint line. */
    private void entry(int note) {
        page.clear();
        page.setTitle(s(stage == SET ? (first == null ? R.string.lumen_lp_pin_set : R.string.lumen_lp_pin_again)
                : R.string.lumen_lp_pin_enter));
        pin.setEntered("");
        page.add(pin);
        hint.setText(s(note));
        page.add(hint);
        page.setInitialFocus(0);
        repaint.accept(0);
    }

    private void pressed(char k) {
        if (busy) return;
        String keys = pin.entered() + k;
        pin.setEntered(keys);
        if (keys.length() < PinCode.LENGTH) return;
        busy = true;
        pin.postDelayed(() -> entered(keys), 150);
    }

    private void entered(String keys) {
        if (stage == VERIFY) {
            store.check(keys, ok -> {
                busy = false;
                if (!ok) {
                    errors++;
                    entry(errors >= MAX_ERRORS ? R.string.lumen_lp_pin_forgot : R.string.lumen_lp_pin_wrong);
                } else if (mode == CHANGE) {
                    stage = SET;
                    entry(R.string.lumen_lp_pin_hint);
                } else {
                    done.run();
                }
            });
            return;
        }
        if (first == null) {
            first = keys;
            busy = false;
            entry(R.string.lumen_lp_pin_hint);
            return;
        }
        if (!first.equals(keys)) {
            first = null;
            busy = false;
            entry(R.string.lumen_lp_pin_mismatch);
            return;
        }
        store.set(keys, ok -> {
            busy = false;
            if (ok) done.run();
            else {
                first = null;
                entry(R.string.lumen_lp_pin_hint);
            }
        });
    }

    /** The forgotten password question: three answers, the right one lets a new password be set. */
    private void quiz(Integer wrongNote) {
        Quiz q = Quiz.next(random);
        page.clear();
        page.setTitle(app.getString(R.string.lumen_lp_quiz, q.a, q.b));
        if (wrongNote != null) page.add(new TextRow(app, s(wrongNote)));
        for (int choice : q.choices) {
            page.add(new NavRow(app, Integer.toString(choice), () -> {
                if (q.right(choice)) {
                    stage = SET;
                    first = null;
                    entry(R.string.lumen_lp_pin_hint);
                } else {
                    quiz(R.string.lumen_lp_quiz_wrong);
                }
            }).setChevron(false));
        }
        int at = wrongNote != null ? 1 : 0;
        page.setInitialFocus(at);
        repaint.accept(at);
    }
}
