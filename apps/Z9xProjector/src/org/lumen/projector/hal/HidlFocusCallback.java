package org.lumen.projector.hal;

import android.os.HwBinder;
import android.os.HwBlob;
import android.os.HwParcel;
import android.os.IHwBinder;
import android.os.IHwInterface;
import android.os.Process;
import android.util.Log;

import java.util.ArrayList;

/**
 * Java xgimi.hardware.gmpf@1.0::IProjectorFocusCallback of the Z9X, the one object registered with the vendor
 * (strong static reference: the HAL keeps only a binder reference). focusEvent = transaction 1: token, i32 type,
 * hidl_string value; the reply Status(0) + bool(true) goes out at once (the vendor waits for it while it holds its
 * listener mutex), also on a parse error; then the event goes to the FocusEventSink, which must only post it. Never calls
 * the HAL, never blocks. IBase-reserved codes are answered like the hidl-gen Java IBase.Stub.
 */
final class HidlFocusCallback extends HwBinder implements IHwInterface {
    static final String DESCRIPTOR = "xgimi.hardware.gmpf@1.0::IProjectorFocusCallback";
    static final String IBASE = "android.hidl.base@1.0::IBase";
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
    static final int HIDL_DESCRIPTOR_CHAIN = 0x0f43484e;        // 256067662 interfaceChain
    static final int HIDL_DEBUG = 0x0f444247;                   // 256131655 debug
    static final int HIDL_GET_DESCRIPTOR = 0x0f445343;          // 256136003 interfaceDescriptor
    static final int HIDL_HASH_CHAIN = 0x0f485348;              // 256398152 getHashChain
    static final int HIDL_SET_HAL_INSTRUMENTATION = 0x0f494e54; // 256462420 oneway
    static final int HIDL_PING = 0x0f504e47;                    // 256921159 ping
    static final int HIDL_GET_REF_INFO = 0x0f524546;            // 257049926 getDebugInfo
    static final int HIDL_SYSPROPS_CHANGED = 0x0f535953;        // 257120595 oneway

    private static final byte[] HASH_SELF = hex("657623bcdeadd6be8c60deb99c61eecef80732eebedd3dad9ae54949dbd2611e");
    private static final byte[] HASH_BASE = hex("ec7fd79ed02dfa85bc499426adae3ebe23ef0524f3cd6957139324b83b18ca4c");

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
        try {
            switch (code) {
                case HIDL_DESCRIPTOR_CHAIN: {
                    req.enforceInterface(IBASE);
                    ArrayList<String> chain = new ArrayList<>();
                    chain.add(DESCRIPTOR);
                    chain.add(IBASE);
                    reply.writeStatus(HwParcel.STATUS_SUCCESS);
                    reply.writeStringVector(chain);
                    reply.send();
                    break;
                }
                case HIDL_GET_DESCRIPTOR: {
                    req.enforceInterface(IBASE);
                    reply.writeStatus(HwParcel.STATUS_SUCCESS);
                    reply.writeString(DESCRIPTOR);
                    reply.send();
                    break;
                }
                case HIDL_PING: {
                    req.enforceInterface(IBASE);
                    reply.writeStatus(HwParcel.STATUS_SUCCESS);
                    reply.send();
                    break;
                }
                case HIDL_HASH_CHAIN: {
                    req.enforceInterface(IBASE);
                    reply.writeStatus(HwParcel.STATUS_SUCCESS);
                    HwBlob vec = new HwBlob(16);              // hidl_vec<uint8_t[32]>
                    vec.putInt32(8, 2);                       // mSize
                    vec.putBool(12, false);                   // mOwnsBuffer
                    HwBlob data = new HwBlob(2 * 32);
                    data.putInt8Array(0, HASH_SELF);
                    data.putInt8Array(32, HASH_BASE);
                    vec.putBlob(0, data);                     // mBuffer
                    reply.writeBuffer(vec);
                    reply.send();
                    break;
                }
                case HIDL_GET_REF_INFO: {                      // DebugInfo{i32 pid; u64 ptr; i32 arch}
                    req.enforceInterface(IBASE);
                    reply.writeStatus(HwParcel.STATUS_SUCCESS);
                    HwBlob info = new HwBlob(24);
                    info.putInt32(0, Process.myPid());
                    info.putInt64(8, 0L);
                    info.putInt32(16, 0);                     // Architecture.UNKNOWN
                    reply.writeBuffer(info);
                    reply.send();
                    break;
                }
                case HIDL_DEBUG: {
                    req.enforceInterface(IBASE);
                    req.readNativeHandle();
                    req.readStringVector();
                    reply.writeStatus(HwParcel.STATUS_SUCCESS);
                    reply.send();
                    break;
                }
                case HIDL_SET_HAL_INSTRUMENTATION:
                case HIDL_SYSPROPS_CHANGED:
                default:
                    // oneway IBase calls, linkToDeath/unlinkToDeath and unknown codes: no reply
                    break;
            }
        } catch (Throwable t) {
            Log.w(TAG, "IBase code " + code + ": " + t);
        }
    }

    @Override public IHwInterface queryLocalInterface(String descriptor) {
        return DESCRIPTOR.equals(descriptor) || IBASE.equals(descriptor) ? this : null;
    }
    @Override public IHwBinder asBinder() { return this; }
    @Override public boolean linkToDeath(IHwBinder.DeathRecipient recipient, long cookie) { return true; }
    @Override public boolean unlinkToDeath(IHwBinder.DeathRecipient recipient) { return true; }

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }
}
