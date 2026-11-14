package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.hal.Play6Gmpf2;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Values;

import java.util.function.Consumer;

import static org.lumen.projector.utils.Play6HalReads.hdmiNow;

/** HDMI colour range, aspect ratio and HDMI mode: HDMI on screen only, checked right before the write. */
final class Play6HdmiRows extends Play6Rows {
    Play6HdmiRows(Play6Features menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2) throws Exception {
        switch (feature) {
            case HDMI_RANGE: {
                Integer w = Play6MenuGates.HDMI_RANGE.wire(value);
                if (w == null) throw new IllegalArgumentException("range " + value);
                if (!hdmiNow(g, "635")) return "panel_game_hdmi_only";
                if (!g.setHdmiRange(Play6Values.hdmiRange(w))) throw new IllegalStateException("635 returned false");
                return null;
            }
            case PICTURE_ASPECT: {
                Integer w = Play6MenuGates.ASPECT.wire(value);
                if (w == null) throw new IllegalArgumentException("aspect " + value);
                if (!hdmiNow(g, "665")) return "panel_game_hdmi_only";
                if (w == Play6MenuGates.ASPECT_ORIGINAL && !Play6MenuGates.originalFits(g.getHdmiInfo())) return "";
                if (!g.setAspectRatio(Play6Values.aspectRatio(w))) throw new IllegalStateException("665 returned false");
                return null;
            }
            default:
                return OTHER;
        }
    }

    /** HDMI mode (asked by the menu: the source reconnects): 660, HDMI on screen only. */
    void setEdid(String value, Play6MenuGates.Read r, Consumer<Result> cb) {
        Integer v = Play6MenuGates.EDID.wire(value);
        if (v == null || v.equals(r.edid)) { refuse(cb, null); return; }
        write("660 edid " + v, cb, (g, g2) -> {
            if (!hdmiNow(g, "660")) return "panel_game_hdmi_only";
            if (!g.setEdidVersion(v)) throw new IllegalStateException("660 returned false");
            return null;
        });
    }
}
