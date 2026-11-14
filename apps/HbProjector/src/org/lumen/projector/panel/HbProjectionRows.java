package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.PutMode;

/** Projection mode (Auto, table, ceiling) and rear projection. */
final class HbProjectionRows extends HbRows {
    HbProjectionRows(HbMenuFeatures menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, GmpfClient g, Gmpf2Client g2) throws Exception {
        switch (feature) {
            case PROJECTION_MODE:
                return setProjection(value, g, g2);
            case PROJECTION_REAR: {
                PutMode cur = g.getPutMode();
                if (cur == null) throw new IllegalStateException("put mode unreadable");
                PutMode m = PutMode.fromWire(HbMenuGates.putModeForRear(on, cur.wire));
                if (m != null && m != cur) g.setPutMode(m);                     // DISPLAY 2 only, auto flip kept
                return null;
            }
            default:
                return OTHER;
        }
    }

    /**
     * HAL thread, stock ProjectionRepository: Auto = MOTION 8 on; Table = 8 off + put mode 0 / 2; Ceiling = 8 off +
     * put mode 1 / 3 + SYS 140 (the stock hoist dialog is the menu's confirmation of this choice).
     */
    private String setProjection(String value, GmpfClient g, Gmpf2Client g2) throws Exception {
        if (HbMenuGates.PROJECTION.wire(value) == null) throw new IllegalArgumentException("projection " + value);
        PutMode cur = g.getPutMode();
        Boolean auto = g.getAutoReverse();
        if (cur == null || auto == null) throw new IllegalStateException("put mode / auto flip unreadable");
        if ("auto".equals(value)) {
            if (!auto) g.setAutoReverse(true);
            return null;
        }
        PutMode m = PutMode.fromWire(HbMenuGates.putModeFor(value, cur.wire));
        if (m == null) throw new IllegalStateException("no put mode for " + value);
        if (m == cur) {
            if (auto) g.setAutoReverse(false);
            return null;
        }
        g.setAutoReverse(false);
        g.setPutMode(m);
        if ("ceiling".equals(value) && cur.wire != 1 && cur.wire != 3) {
            try { g2.disablePushPullScreenForCeiling(); } catch (Throwable t) { Log.w(TAG, "140(false): " + t); }
        }
        return null;
    }
}
