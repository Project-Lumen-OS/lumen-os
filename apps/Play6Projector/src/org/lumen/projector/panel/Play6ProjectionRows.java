package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.hal.Play6Values;

import java.util.function.Consumer;

/** Projection mode (Auto, table, ceiling) and the rear switch. */
final class Play6ProjectionRows extends Play6Rows {
    Play6ProjectionRows(Play6Features menu) { super(menu); }

    /** Stock sequence: Auto = 580(true); table / ceiling = 580(false) then 172 (a flip was asked by the menu). */
    void setProjection(String value, Play6MenuGates.Read r, Consumer<Result> cb) {
        final int cur = r.putMode;
        final boolean auto = r.autoReverse;
        if ("auto".equals(value)) {
            if (auto) { refuse(cb, null); return; }
            write("580 auto projection on", cb, (g, g2) -> { g.setAutoReverse(true); return null; });
            return;
        }
        final int target = Play6MenuGates.putModeFor(value, cur);
        if (target < 0) { refuse(cb, null); return; }
        if (target == cur) {
            if (auto) write("580 auto projection off", cb, (g, g2) -> { g.setAutoReverse(false); return null; });
            else refuse(cb, null);
            return;
        }
        write("172 put mode " + target, cb, (g, g2) -> {
            g.setAutoReverse(false);                                                 // 580(false)
            g.setPutMode(Play6Values.putMode(target));                               // 172
            return null;
        });
    }

    /** Rear switch: 172 alone, auto flip kept (stock ProjectionRepository.setRearProjection). */
    void setRear(boolean on, Play6MenuGates.Read r, Consumer<Result> cb) {
        final int target = Play6MenuGates.putModeForRear(on, r.putMode);
        if (target == r.putMode) { refuse(cb, null); return; }
        write("172 put mode " + target, cb, (g, g2) -> { g.setPutMode(Play6Values.putMode(target)); return null; });
    }
}
