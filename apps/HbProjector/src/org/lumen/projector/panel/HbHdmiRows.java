package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.AspectRatio;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.HdmiRange;
import org.lumen.projector.ui.Notify;

/** HDMI colour range, HDMI mode (EDID) and aspect ratio: HDMI on screen only, checked right before the write. */
final class HbHdmiRows extends HbRows {
    HbHdmiRows(HbMenuFeatures menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, GmpfClient g, Gmpf2Client g2) throws Exception {
        switch (feature) {
            case HDMI_RANGE: {
                Integer w = HbMenuGates.HDMI_RANGE.wire(value);
                if (w == null) throw new IllegalArgumentException("range " + value);
                if (!hdmiNow(g)) return "panel_game_hdmi_only";
                if (!g.setHdmiRange(HdmiRange.values()[w])) throw new IllegalStateException("TV 63 false");
                return null;
            }
            case HDMI_EDID: {
                Integer w = HbMenuGates.EDID.wire(value);
                if (w == null) throw new IllegalArgumentException("edid " + value);
                if (!hdmiNow(g)) return "panel_game_hdmi_only";
                if (!g.setEdidVersion(w)) throw new IllegalStateException("TV 40 false");
                Ui.main().post(() -> Notify.show(app, app.getString(R.string.hdmi_mode), app.getString(R.string.hdmi_mode_note)));
                return null;
            }
            case PICTURE_ASPECT: {
                Integer w = HbMenuGates.ASPECT.wire(value);
                if (w == null) throw new IllegalArgumentException("aspect " + value);
                if (!hdmiNow(g)) return "panel_game_hdmi_only";
                AspectRatio a = null;
                for (AspectRatio r : AspectRatio.values()) if (r.wire == w) a = r;
                if (a == null || !g.setAspectRatio(a)) throw new IllegalStateException("TV 35 " + w);
                return null;
            }
            default:
                return OTHER;
        }
    }

}
