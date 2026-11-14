// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Note;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.hal.AspectRatio;
import org.lumen.projector.hal.HdmiRange;

import java.util.function.Consumer;

import static org.lumen.projector.utils.Z9xHalReads.hdmiNow;

/** HDMI colour range, HDMI mode (EDID) and aspect ratio: HDMI on screen only, checked right before the write. */
final class Z9xHdmiRows extends Z9xRows {
    static final Result HDMI_ONLY = Result.refusedWith(Note.of("panel_game_hdmi_only"), null);

    Z9xHdmiRows(Z9xFeatures menu) { super(menu); }

    @Override boolean set(FeatureId f, String v, boolean confirmed, Consumer<Result> cb) {
        switch (f) {
            case HDMI_RANGE: {                                            // QPC:1126
                int i = Z9xMenuRules.indexOf(Z9xMenuRules.RANGE, v);
                if (i < 0) return false;
                final HdmiRange r = HdmiRange.values()[i];
                write("635 hdmi range " + r, (g, g2) -> {
                    if (!hdmiNow(app, g, "635")) return HDMI_ONLY;
                    if (!g.setHdmiRange(r)) throw new IllegalStateException("635 returned false");
                    return null;
                }, cb);
                return true;
            }
            case HDMI_EDID: {                                             // QPC:1140
                final int wire = PanelRules.edidWire("enhanced".equals(v) ? 1 : 0);
                write("660 edid " + wire, (g, g2) -> {
                    if (!hdmiNow(app, g, "660")) return HDMI_ONLY;
                    if (!g.setEdidVersion(wire)) throw new IllegalStateException("660 returned false");
                    return Result.done(null, Note.titled("hdmi_mode_note"));
                }, cb);
                return true;
            }
            case PICTURE_ASPECT: {                                        // QPC:1160
                int w = Z9xMenuRules.wireOf(Z9xMenuRules.ASPECT, Z9xMenuRules.ASPECT_WIRE, v);
                AspectRatio a = null;
                for (AspectRatio x : AspectRatio.values()) if (x.wire == w) a = x;
                if (a == null) return false;
                final AspectRatio fa = a;
                write("665 aspect " + a, (g, g2) -> {
                    if (!hdmiNow(app, g, "665")) return HDMI_ONLY;
                    g.setAspectRatio(fa);
                    return null;
                }, cb);
                return true;
            }
            default:
                return false;
        }
    }
}
