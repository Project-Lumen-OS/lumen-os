// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
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
 * A Java HIDL callback object registered with the XGIMI vendor (the HIDL projectors' focus and TV callbacks, the Play 6
 * power and music player callbacks; the HAL keeps only a binder reference, so its owner keeps a strong one). The
 * subclass answers its event transaction; the IBase-reserved codes are answered here like the hidl-gen Java
 * IBase.Stub (its descriptor, its hash chain over IBase), and the object is its own local interface.
 */
abstract class HidlCallback extends HwBinder implements IHwInterface {
    static final String IBASE = "android.hidl.base@1.0::IBase";
    static final int HIDL_DESCRIPTOR_CHAIN = 0x0f43484e;        // 256067662 interfaceChain
    static final int HIDL_DEBUG = 0x0f444247;                   // 256131655 debug
    static final int HIDL_GET_DESCRIPTOR = 0x0f445343;          // 256136003 interfaceDescriptor
    static final int HIDL_HASH_CHAIN = 0x0f485348;              // 256398152 getHashChain
    static final int HIDL_SET_HAL_INSTRUMENTATION = 0x0f494e54; // 256462420 oneway
    static final int HIDL_PING = 0x0f504e47;                    // 256921159 ping
    static final int HIDL_GET_REF_INFO = 0x0f524546;            // 257049926 getDebugInfo
    static final int HIDL_SYSPROPS_CHANGED = 0x0f535953;        // 257120595 oneway

    private static final byte[] HASH_BASE = hex("ec7fd79ed02dfa85bc499426adae3ebe23ef0524f3cd6957139324b83b18ca4c");

    private final String tag, descriptor;
    private final byte[] hashSelf;

    /** descriptor: the callback interface; hashSelf: its hash, recorded from the vendor library. */
    HidlCallback(String tag, String descriptor, byte[] hashSelf) {
        this.tag = tag;
        this.descriptor = descriptor;
        this.hashSelf = hashSelf;
    }

    /** Every code that is not the subclass's event: the IBase-reserved ones, oneway and unknown codes get no reply. */
    final void answerIBase(int code, HwParcel req, HwParcel reply) {
        try {
            switch (code) {
                case HIDL_DESCRIPTOR_CHAIN: {
                    req.enforceInterface(IBASE);
                    ArrayList<String> chain = new ArrayList<>();
                    chain.add(descriptor);
                    chain.add(IBASE);
                    reply.writeStatus(HwParcel.STATUS_SUCCESS);
                    reply.writeStringVector(chain);
                    reply.send();
                    break;
                }
                case HIDL_GET_DESCRIPTOR: {
                    req.enforceInterface(IBASE);
                    reply.writeStatus(HwParcel.STATUS_SUCCESS);
                    reply.writeString(descriptor);
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
                    data.putInt8Array(0, hashSelf);
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
            Log.w(tag, "IBase code " + code + ": " + t);
        }
    }

    @Override public IHwInterface queryLocalInterface(String d) {
        return descriptor.equals(d) || IBASE.equals(d) ? this : null;
    }
    @Override public IHwBinder asBinder() { return this; }
    @Override public boolean linkToDeath(IHwBinder.DeathRecipient recipient, long cookie) { return true; }
    @Override public boolean unlinkToDeath(IHwBinder.DeathRecipient recipient) { return true; }

    static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }
}
