package org.lumen.projector.hal;

import android.os.RemoteException;

import org.lumen.hal.gmpf.GmpfGate;
import org.lumen.hal.gmpf.GmpfOp;
import org.lumen.hal.gmpf.GmpfRules;

/** The Z9X rules of the shared gmpf calls: no feature table, the three stock EDID values, the limit zone rule of 1.0.8. */
final class Z9xRules implements GmpfRules {
    static final Z9xRules RULES = new Z9xRules();
    private static final int[] EDID = {GmpfBase.EDID_14, GmpfBase.EDID_20, GmpfBase.EDID_21};

    private Z9xRules() {}

    @Override public void require(GmpfGate gate, String what) {}

    @Override public RemoteException absent(GmpfOp op) {
        return new RemoteException(op.method() + ": not on the Z9X, nothing sent");
    }

    @Override public int[] edidVersions() { return EDID; }

    @Override public boolean stepNeedsLimitRead() { return false; }
}
