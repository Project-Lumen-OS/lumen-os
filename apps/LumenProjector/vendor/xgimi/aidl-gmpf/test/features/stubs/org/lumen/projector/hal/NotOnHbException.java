// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.hal;

/** Host stub for test/features: the exception HbFeatures.require throws (the real class is the same). */
public final class NotOnHbException extends android.os.RemoteException {
    NotOnHbException(String what) { super(what + ": not on the Z9X HB, nothing sent"); }
}
