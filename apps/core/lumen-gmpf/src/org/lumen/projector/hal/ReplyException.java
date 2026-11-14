package org.lumen.projector.hal;

import android.os.RemoteException;

/** The request reached the vendor (the transaction returned) but the reply was bad. Do not repeat one-shot calls. */
public class ReplyException extends RemoteException {
    ReplyException(String msg) { super(msg); }
}
