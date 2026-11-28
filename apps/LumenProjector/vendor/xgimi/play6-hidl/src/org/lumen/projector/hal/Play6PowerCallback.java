// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
/*
 * Play6PowerCallback: our Java xgimi.hardware.gmpf@1.0::IPowerCallback for IGmpf 235 / 242 on the XGIMI
 * Play 6, built like the Z9X HidlFocusCallback (stock analysis 1.1 and 6.1 B1;
 * a project decision reversed: Lumen registers with the vendor power manager, as every stock process that
 * shows the power state does).
 *
 * powerEvent = transaction 1: token, i32 type, hidl_string value. The stock proxy
 * (BpHwPowerCallback::_hidl_powerEvent, transact(1, ..., flags 0)) makes it a two-way call with a void
 * result, and the stock stub writes only the status: the reply here is Status(0) and nothing else, sent
 * at once, also on a parse error. The callback never calls the HAL and never blocks; it only hands the
 * event to the Sink, which must just post it. The IBase-reserved codes are answered like the hidl-gen Java
 * IBase.Stub; getHashChain returns the IPowerCallback hash recorded offline from the stock v1.7.116
 * library (stock analysis, plan 2.1: 9f7cbc16...94ab) and the IBase hash as element 1.
 *
 * Event types (libxgimi.so POWER_XEVENT, stock analysis 1.2): 0 adapter, 1 battery level or a low-battery
 * string, 3 power source type, 4 stand level, 5 power saving, 6 usage statistics, 7 power source number.
 */
package org.lumen.projector.hal;

import android.os.HwParcel;
import android.util.Log;

public final class Play6PowerCallback extends HidlCallback {
    private static final String TAG = "P6Power";
    public static final String IPOWER_CALLBACK = "xgimi.hardware.gmpf@1.0::IPowerCallback";

    static final int POWER_EVENT = 1;

    /** Recorded offline (plan 2.1): IPowerCallback, then IBase. */
    private static final byte[] HASH_SELF = hex("9f7cbc169c8bb9d1478335b858d370a51ab61d9425c901ab85bc645b4e0e94ab");

    /** Receives power events. Called on the hwbinder thread AFTER the reply was sent: only post. */
    public interface Sink {
        void onPowerEvent(int type, String value);
    }

    private final Sink sink;

    /** The caller keeps a strong reference (the HAL holds only a binder reference to this object). */
    public Play6PowerCallback(Sink sink) {
        super(TAG, IPOWER_CALLBACK, HASH_SELF);
        this.sink = sink;
    }

    @Override
    public void onTransact(int code, HwParcel req, HwParcel reply, int flags) {
        if (code == POWER_EVENT) {
            int type = -1;
            String value = null;
            boolean parsed = false;
            try {
                req.enforceInterface(IPOWER_CALLBACK);
                type = req.readInt32();
                value = req.readString();
                parsed = true;
            } catch (Throwable t) {
                Log.w(TAG, "powerEvent parse: " + t);
            }
            // Reply at once, also on a parse error: Status only (void result, as the stock stub).
            try {
                reply.writeStatus(HwParcel.STATUS_SUCCESS);
                reply.send();
            } catch (Throwable t) {
                Log.w(TAG, "powerEvent reply: " + t);
            }
            if (parsed && sink != null) {
                try { sink.onPowerEvent(type, value); } catch (Throwable t) { Log.w(TAG, "sink: " + t); }
            }
            return;
        }
        answerIBase(code, req, reply);
    }
}
