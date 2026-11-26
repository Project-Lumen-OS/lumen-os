// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.hal;

import android.os.RemoteException;

/** Thrown instead of an eye protection call while GmpfBase.nuiReaderReady() is false. Nothing was sent. */
public final class NuiNotReadyException extends RemoteException {
    NuiNotReadyException(String what) {
        super(what + " not sent: no NUI fifo reader (" + GmpfBase.NUI_READY_PROP + " != ready)");
    }
}
