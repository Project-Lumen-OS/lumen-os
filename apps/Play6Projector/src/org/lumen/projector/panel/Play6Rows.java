package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import android.content.Context;
import android.content.SharedPreferences;

import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Gmpf2;

import java.util.function.Consumer;

/**
 * One section of the Play 6 adapter ({@link Play6Features}): writes its features with the calls of the old Play 6 panel
 * through the adapter's p6-hal write (read back after every write). A section never sends a call for a feature of
 * another section.
 */
abstract class Play6Rows {
    static final String TAG = Play6Features.TAG;
    /** {@link #writeHal}'s answer for a feature of another section (compared by identity, never a note name). */
    static final String OTHER = new String("other section");

    final Play6Features menu;
    final Context app;
    final SharedPreferences prefs;

    Play6Rows(Play6Features menu) {
        this.menu = menu;
        this.app = menu.app;
        this.prefs = menu.prefs;
    }

    /** p6-hal: one write with the Play 6 rules; null = done, a note name ("" = none) = refused, {@link #OTHER} = not this section. */
    String writeHal(FeatureId feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2) throws Exception { return OTHER; }

    /** Runs the action; false when it is not an action of this section. */
    boolean run(FeatureId action, Consumer<Result> cb) { return false; }

    void write(String what, Consumer<Result> cb, Play6Features.Write w) { menu.write(what, cb, w); }

    void refuse(Consumer<Result> cb, String reason) { menu.refuse(cb, reason); }
}
