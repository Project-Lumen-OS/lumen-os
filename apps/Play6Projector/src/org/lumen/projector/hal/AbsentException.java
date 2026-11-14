package org.lumen.projector.hal;

import android.os.RemoteException;

/** The method does not exist in the IGmpf2 of this vendor build: nothing was sent. */
public final class AbsentException extends RemoteException {
    AbsentException(String msg) { super(msg); }
}
