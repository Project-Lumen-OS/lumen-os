package org.lumen.hal.gmpf;

import android.os.RemoteException;

/** The device rules of the shared calls: feature gates, the error of an absent call, the device's value sets. */
public interface GmpfRules {
    /** Throws, before anything is sent, when this projector has the feature off. */
    void require(GmpfGate gate, String what) throws RemoteException;

    /** The error of a call this projector does not have (nothing is sent). */
    RemoteException absent(GmpfOp op);

    /** The EDID versions setEdidVersion may send. */
    int[] edidVersions();

    /** An optical zoom step needs a limit position read of 0 in the session, whatever the limit count says. */
    boolean stepNeedsLimitRead();
}
