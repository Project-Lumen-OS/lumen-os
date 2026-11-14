package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.Hal;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;

import java.util.function.Consumer;

/** Smart autofocus and Autofocus now. */
final class HbFocusRows extends HbRows {
    HbFocusRows(HbMenuFeatures menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, GmpfClient g, Gmpf2Client g2) throws Exception {
        switch (feature) {
            case FOCUS_SMART:
                // stock FocusRepository.setSmartAutoFocus: FOCUS 85, then MOTION 4 (AF after moving); the user set it
                org.lumen.projector.power.StandbyController.noteUserMoveAf();
                g2.setSmartAutofocus(on);
                g.setMoveAf(on);
                return null;
            default:
                return OTHER;
        }
    }

    @Override
    boolean run(FeatureId action, Consumer<Result> cb) {
        if (FeatureId.FOCUS_AF_NOW != action) return false;
        Hal.requestAutofocus(app);
        Ui.main().post(() -> cb.accept(Result.done(menu.state(last()))));
        return true;
    }
}
