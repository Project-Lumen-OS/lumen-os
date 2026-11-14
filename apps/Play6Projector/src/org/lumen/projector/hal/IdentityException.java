package org.lumen.projector.hal;

import android.os.RemoteException;

/** The IGmpf2 identity guard is closed for this connection: nothing was sent. */
public final class IdentityException extends RemoteException {
    IdentityException(String msg) { super(msg); }
}
