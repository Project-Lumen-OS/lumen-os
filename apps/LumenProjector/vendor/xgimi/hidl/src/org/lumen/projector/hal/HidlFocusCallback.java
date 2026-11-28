// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.hal;

import android.os.HwParcel;
import android.util.Log;

/**
 * Java xgimi.hardware.gmpf@1.0::IProjectorFocusCallback of the Z9X, the one object registered with the vendor
 * (strong static reference: the HAL keeps only a binder reference). focusEvent = transaction 1: token, i32 type,
 * hidl_string value; the reply Status(0) + bool(true) goes out at once (the vendor waits for it while it holds its
 * listener mutex), also on a parse error; then the event goes to the FocusEventSink, which must only post it. Never calls
 * the HAL, never blocks. IBase-reserved codes are answered by HidlCallback.
 */
final class HidlFocusCallback extends HidlCallback {
    static final String DESCRIPTOR = "xgimi.hardware.gmpf@1.0::IProjectorFocusCallback";
    private static final String TAG = "Z9xGmpf";

    private static HidlFocusCallback sCallback;
    private static volatile FocusEventSink sSink;

    /** The sink of the focus events; creates the callback object. */
    static synchronized void attach(FocusEventSink sink) {
        sSink = sink;
        if (sCallback == null) sCallback = new HidlFocusCallback();
    }

    static synchronized HidlFocusCallback get() {
        if (sCallback == null) sCallback = new HidlFocusCallback();
        return sCallback;
    }

    static final int FOCUS_EVENT = 1;
    private static final byte[] HASH_SELF = hex("657623bcdeadd6be8c60deb99c61eecef80732eebedd3dad9ae54949dbd2611e");

    private HidlFocusCallback() {
        super(TAG, DESCRIPTOR, HASH_SELF);
    }

    @Override
    public void onTransact(int code, HwParcel req, HwParcel reply, int flags) {
        if (code == FOCUS_EVENT) {
            int type = -1;
            String value = null;
            boolean parsed = false;
            try {
                req.enforceInterface(DESCRIPTOR);
                type = req.readInt32();
                value = req.readString();
                parsed = true;
            } catch (Throwable t) {
                Log.w(TAG, "focusEvent parse: " + t);
            }
            // Reply at once, also on a parse error: the vendor waits for Status + bool.
            try {
                reply.writeStatus(HwParcel.STATUS_SUCCESS);
                reply.writeBool(true);
                reply.send();
            } catch (Throwable t) {
                Log.w(TAG, "focusEvent reply: " + t);
            }
            if (parsed) {
                FocusEventSink s = sSink;
                if (s != null) {
                    try { s.onFocusEvent(type, value); } catch (Throwable t) { Log.w(TAG, "sink: " + t); }
                }
            }
            return;
        }
        answerIBase(code, req, reply);
    }
}
