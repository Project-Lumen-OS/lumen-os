// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.hal;

import android.os.HwBinder;
import android.os.HwParcel;
import android.os.IHwBinder;
import android.os.IHwInterface;
import android.util.Log;

import org.lumen.hal.gmpf.TvEventSink;

/**
 * Java xgimi.hardware.gmpf@1.0::IGmTvCallback of the HIDL projectors (Z9X, Play 6), the one object registered with
 * IGmpf 702 setGmTvListener(pid, cb) as stock libgmpftvmanager_hidl does. gmTvEvent = transaction 1: token, i32 type,
 * hidl_string msg (BnHwGmTvCallback::_hidl_gmTvEvent of xgimi.hardware.gmpf@1.0.so); the reply Status(0) + bool(true)
 * goes out at once, also on a parse error; then the event goes to the sink, which must only post it. Event 5
 * "picturemode:N" is the picture mode change of the AI picture quality (org.lumen.aipq.AiPq). Hash chain read from
 * the same library (IGmTvCallback::getHashChain, 0xb64a0 and 0xb6540).
 */
final class HidlTvCallback extends HwBinder implements IHwInterface {
    static final String DESCRIPTOR = "xgimi.hardware.gmpf@1.0::IGmTvCallback";
    private static final String TAG = "LumenGmTv";
    static final int GM_TV_EVENT = 1;
    private static final byte[] HASH_SELF = HidlFocusCallback.hex(
            "e0e732ce0a370d085e5fa65314da361e3e090f528884aa1e631ed8f045556c44");

    private static HidlTvCallback sCallback;
    private static volatile TvEventSink sSink;

    static synchronized void attach(TvEventSink sink) {
        sSink = sink;
        if (sCallback == null) sCallback = new HidlTvCallback();
    }

    static synchronized HidlTvCallback get() {
        if (sCallback == null) sCallback = new HidlTvCallback();
        return sCallback;
    }

    @Override
    public void onTransact(int code, HwParcel req, HwParcel reply, int flags) {
        if (code != GM_TV_EVENT) {
            HidlFocusCallback.answerIBase(code, req, reply, DESCRIPTOR, HASH_SELF);
            return;
        }
        int type = -1;
        String msg = null;
        boolean parsed = false;
        try {
            req.enforceInterface(DESCRIPTOR);
            type = req.readInt32();
            msg = req.readString();
            parsed = true;
        } catch (Throwable t) {
            Log.w(TAG, "gmTvEvent parse: " + t);
        }
        try {
            reply.writeStatus(HwParcel.STATUS_SUCCESS);
            reply.writeBool(true);
            reply.send();
        } catch (Throwable t) {
            Log.w(TAG, "gmTvEvent reply: " + t);
        }
        TvEventSink s = sSink;
        if (parsed && s != null) {
            try { s.onTvEvent(type, msg); } catch (Throwable t) { Log.w(TAG, "sink: " + t); }
        }
    }

    @Override public IHwInterface queryLocalInterface(String descriptor) {
        return DESCRIPTOR.equals(descriptor) || HidlFocusCallback.IBASE.equals(descriptor) ? this : null;
    }
    @Override public IHwBinder asBinder() { return this; }
    @Override public boolean linkToDeath(IHwBinder.DeathRecipient recipient, long cookie) { return true; }
    @Override public boolean unlinkToDeath(IHwBinder.DeathRecipient recipient) { return true; }
}
