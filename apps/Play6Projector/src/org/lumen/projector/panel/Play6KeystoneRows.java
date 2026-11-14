package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Note;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.menu.ResultKind;
import org.lumen.projector.Hal;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Gmpf2;

import java.util.function.Consumer;

/** Real-time keystone, keystone at power on, Auto keystone now and the keystone reset. */
final class Play6KeystoneRows extends Play6Rows {
    Play6KeystoneRows(Play6Features menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2) throws Exception {
        switch (feature) {
            case KEYSTONE_REALTIME:
                g.setMoveAk(on);                                                     // 585
                return null;
            case KEYSTONE_ON_POWER:
                g.setPowerOnAk(on);                                                  // 552
                return null;
            default:
                return OTHER;
        }
    }

    @Override
    boolean run(FeatureId action, Consumer<Result> cb) {
        switch (action) {
            case KEYSTONE_AK_NOW:                                            // the menu closed first
                Hal.requestKeystone(app);                                            // 326 on p6-long
                Ui.main().post(() -> cb.accept(Result.done(menu.state())));
                return true;
            case KEYSTONE_RESTORE:                                           // asked by the menu
                write("207 keystone reset", r -> cb.accept(r.kind == ResultKind.DONE
                        ? Result.done(r.after, Note.of("panel_reset_keystone_done")) : r), (g, g2) -> {
                    Log.i(TAG, "keystone reset: 207(0), 130 -> " + g.resetKeystone());
                    return null;
                });
                return true;
            default:
                return false;
        }
    }

}
