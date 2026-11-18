// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.Hal;
import org.lumen.projector.power.StandbyController;

import java.util.function.Consumer;

/** Smart autofocus, autofocus after moving and at power on, Autofocus now. */
final class Z9xFocusRows extends Z9xRows {
    Z9xFocusRows(Z9xFeatures menu) { super(menu); }

    @Override boolean set(FeatureId f, String v, boolean confirmed, Consumer<Result> cb) {
        final boolean on = "on".equals(v);
        switch (f) {
            case FOCUS_SMART:                                             // QPC:929: 91 and 587 (stock one switch)
                StandbyController.noteUserMoveAf();
                write("IGmpf2 91 + 587 smart AF " + on, (g, g2) -> { g2.setSmartAutofocus(on); g.setMoveAf(on); return null; }, cb);
                return true;
            case FOCUS_ON_MOVE:
                StandbyController.noteUserMoveAf();
                write("587 AF after moving " + on, (g, g2) -> { g.setMoveAf(on); return null; }, cb);
                return true;
            case FOCUS_ON_POWER:
                write("302 AF at power-on " + on, (g, g2) -> { g.setPowerOnAf(on); return null; }, cb);
                return true;
            default:
                return false;
        }
    }

    @Override boolean run(FeatureId a, Consumer<Result> cb) {
        if (FeatureId.FOCUS_AF_NOW != a) return false;
        Hal.requestAutofocus(app);
        cb.accept(Result.done(null));
        return true;
    }
}
