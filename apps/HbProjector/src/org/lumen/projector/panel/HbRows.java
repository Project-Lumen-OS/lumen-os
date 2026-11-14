package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import android.content.Context;
import android.content.SharedPreferences;

import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.utils.HbHalReads;

import java.util.function.Consumer;

/**
 * One section of the HB adapter ({@link HbMenuFeatures}): writes its features with the HB stock sequences through the
 * adapter's HAL write (read back after every write). A section never sends a call for a feature of another section.
 */
abstract class HbRows {
    static final String TAG = HbMenuFeatures.TAG;
    /** {@link #writeHal}'s answer for a feature of another section (compared by identity, never a note name). */
    static final String OTHER = new String("other section");

    final HbMenuFeatures menu;
    final Context app;
    final SharedPreferences prefs;

    HbRows(HbMenuFeatures menu) {
        this.menu = menu;
        this.app = menu.app;
        this.prefs = menu.prefs;
    }

    /** HAL thread: one write with the HB rules; null = done, a note name = refused, {@link #OTHER} = not this section. */
    String writeHal(FeatureId feature, String value, boolean on, GmpfClient g, Gmpf2Client g2) throws Exception { return OTHER; }

    /** Runs the action; false when it is not an action of this section. */
    boolean run(FeatureId action, Consumer<Result> cb) { return false; }

    void write(String what, Consumer<Result> cb, HbMenuFeatures.Write w) { menu.write(what, cb, w); }

    void refuse(Consumer<Result> cb, String reason) { HbMenuFeatures.refuse(cb, reason); }

    Snapshot last() { return menu.last(); }

    boolean hdmiNow(GmpfClient g) { return HbHalReads.hdmiNow(app, g); }
}
