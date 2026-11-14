package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.Hal;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Gmpf2;

import java.util.function.Consumer;

/** Smart autofocus and Autofocus now. */
final class Play6FocusRows extends Play6Rows {
    Play6FocusRows(Play6Features menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2) throws Exception {
        switch (feature) {
            case FOCUS_SMART:
                if (!g2.isIdentityOpen()) return "toast_not_ready";
                Hal.setSmartAutofocus(g, g2, on);                                    // IGmpf2 64, then 587
                return null;
            default:
                return OTHER;
        }
    }

    @Override
    boolean run(FeatureId action, Consumer<Result> cb) {
        if (FeatureId.FOCUS_AF_NOW != action) return false;                // the menu closed first
        Hal.requestAutofocus(app);                                           // 307(44)
        Ui.main().post(() -> cb.accept(Result.done(menu.state())));
        return true;
    }
}
