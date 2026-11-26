// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;

import org.lumen.projector.R;

import java.util.function.Consumer;

/**
 * The search of the quick menu editor's All settings list (2026-11-24): the menu's one text screen
 * (TextEditActivity, the keyboard below it) with the words typed so far; DONE hands the text to the editor, BACK
 * leaves the list as it was. Nothing is stored.
 */
final class QuickSearch {
    private static String now;
    private static Consumer<String> then;

    private QuickSearch() {}

    /** Opens the text screen over the menu; {@code done} gets the text on the main thread. False when it cannot start. */
    static boolean open(Context c, String text, Consumer<String> done) {
        now = text;
        then = done;
        if (TextEditActivity.open(c, TextFields.QUICK_SEARCH)) return true;
        then = null;
        return false;
    }

    static TextField field() {
        return new TextField() {
            @Override public int title() { return R.string.lumen_qe_search_title; }

            @Override public int hint() { return R.string.lumen_qe_search_hint; }

            @Override public int inputType() { return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; }

            @Override public String value(Context c) { return now == null ? "" : now; }

            @Override public int problem(String text) { return 0; }

            @Override public boolean save(Context c, String text) {
                Consumer<String> to = then;
                then = null;
                now = text;
                if (to != null) new Handler(Looper.getMainLooper()).post(() -> to.accept(text));
                return true;
            }

            @Override public int saved() { return 0; }
        };
    }
}
