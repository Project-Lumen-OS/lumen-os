package org.lumen.projector.hal;

import android.os.RemoteException;

import org.lumen.hal.gmpf.GmpfGate;
import org.lumen.hal.gmpf.GmpfOp;
import org.lumen.hal.gmpf.GmpfRules;

/**
 * The HB rules of the shared gmpf calls: every gate is a row of the feature table (HbFeatures, off wins), a call the
 * HB does not have throws NotOnHbException, the two stock G0130 EDID values, and an optical
 * zoom step only after a limit position read of 0 (hb-t6).
 */
final class HbRules implements GmpfRules {
    static final HbRules RULES = new HbRules();
    private static final int[] EDID = {GmpfBase.EDID_14, GmpfBase.EDID_20};

    private HbRules() {}

    @Override public void require(GmpfGate gate, String what) throws RemoteException {
        HbFeatures.require(feature(gate), what);
    }

    @Override public RemoteException absent(GmpfOp op) { return new AidlCaller.NotOnHbException(op.method()); }

    @Override public int[] edidVersions() { return EDID; }

    @Override public boolean stepNeedsLimitRead() { return true; }

    private static HbFeatures.F feature(GmpfGate gate) {
        switch (gate) {
            case AUTO_KEYSTONE: return HbFeatures.F.AUTO_KEYSTONE;
            case CURTAIN_FIT: return HbFeatures.F.CURTAIN_FIT;
            case OBSTACLE_AVOID: return HbFeatures.F.OBSTACLE_AVOID;
            case FIT_SCREEN: return HbFeatures.F.FIT_SCREEN;
            case ZOOM_SHIFT: return HbFeatures.F.ZOOM_SHIFT;
            case OPTICAL_ZOOM: return HbFeatures.F.OPTICAL_ZOOM;
            case OFFICE_MODE: return HbFeatures.F.OFFICE_MODE;
            case COLOR_TEMP: return HbFeatures.F.COLOR_TEMP;
            case BOOST: return HbFeatures.F.BOOST;
            case ROTATION: return HbFeatures.F.ROTATION;
            default: throw new IllegalArgumentException("no HB feature for " + gate);
        }
    }
}
