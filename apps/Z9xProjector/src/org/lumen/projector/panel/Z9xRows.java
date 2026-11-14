// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import android.content.Context;
import android.content.SharedPreferences;

import org.lumen.menu.ProjectorFeatures.Result;

import java.util.function.Consumer;

/**
 * One section of the Z9X adapter ({@link Z9xFeatures}): writes its features with the 1.0.10 quick panel sequences
 * through the adapter's HAL write (read back after every write). A section never sends a call for a feature of
 * another section.
 */
abstract class Z9xRows {
    static final String TAG = Z9xFeatures.TAG;

    final Z9xFeatures menu;
    final Context app;
    final SharedPreferences prefs;

    Z9xRows(Z9xFeatures menu) {
        this.menu = menu;
        this.app = menu.app;
        this.prefs = menu.prefs;
    }

    /** Writes {@code f}; false when it is not a feature (or a token) of this section. */
    abstract boolean set(FeatureId f, String v, boolean confirmed, Consumer<Result> cb);

    /** Runs the action {@code a}; false when it is not an action of this section. */
    boolean run(FeatureId a, Consumer<Result> cb) { return false; }

    void write(String what, Z9xFeatures.Write w, Consumer<Result> cb) { menu.write(what, w, cb); }

    String text(String name, Object... args) { return menu.text(name, args); }
}
