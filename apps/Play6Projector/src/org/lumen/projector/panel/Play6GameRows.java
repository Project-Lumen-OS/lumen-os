package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.projector.game.GameMode;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Gmpf2;

import static org.lumen.projector.utils.Play6HalReads.hdmiNow;

/** Game mode (HDMI only). */
final class Play6GameRows extends Play6Rows {
    Play6GameRows(Play6Features menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2) throws Exception {
        switch (feature) {
            case GAME_MODE: {
                Integer w = Play6MenuGates.GAME_MODE.wire(value);
                if (w == null) throw new IllegalArgumentException("game mode " + value);
                if (!hdmiNow(g, "game mode")) return "panel_game_hdmi_only";
                return GameMode.set(app, g, g2, w) ? null : "toast_not_ready";     // IGmpf2 34 / 35 (+ 648)
            }
            default:
                return OTHER;
        }
    }

}
