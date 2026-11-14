package org.lumen.projector.hal;

import android.os.RemoteException;

import org.lumen.hal.gmpf.GmpfGate;
import org.lumen.hal.gmpf.GmpfOp;
import org.lumen.hal.gmpf.GmpfRules;

/** The Play 6 rules of the shared gmpf calls: no feature table (Play6Codes decides), its two EDID values, no optical zoom. */
final class Play6Rules implements GmpfRules {
    static final Play6Rules RULES = new Play6Rules();
    private static final int[] EDID = {Play6Gmpf.EDID_V14, Play6Gmpf.EDID_V20};

    private Play6Rules() {}

    @Override public void require(GmpfGate gate, String what) {}

    @Override public RemoteException absent(GmpfOp op) {
        return new RemoteException(op.method() + ": not on the Play 6, nothing sent");
    }

    @Override public int[] edidVersions() { return EDID; }

    @Override public boolean stepNeedsLimitRead() { return false; }
}
