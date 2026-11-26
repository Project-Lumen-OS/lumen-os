// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.hal;

import android.os.RemoteException;

/** A feature the HB does not have: nothing was sent. */
public final class NotOnHbException extends RemoteException {
    NotOnHbException(String what) { super(what + ": not on the Z9X HB, nothing sent"); }
}
