package org.lumen.projector.hal;

import android.os.HwBlob;
import android.os.HwParcel;
import android.os.RemoteException;
import android.util.Log;

import org.lumen.hal.gmpf.GmpfWire;

/**
 * The IGmpf2 transport of the Play 6: the Z9X HIDL transport (lazy, a failed transaction drops the binder) behind the
 * identity guard. Before the first call of a connection the guard reads IBase getHashChain of IGmpf2/default and opens
 * only for a hash of Gmpf2Vendor, with that build's code table; any other hash or an unreadable chain closes it until
 * the next connection, a call that was not delivered leaves it unknown. Every call checks the guard, then sends the
 * code of the open build (AbsentException where that build has no such method). No property or switch opens it.
 */
final class Play6Gmpf2Wire implements GmpfWire<Integer> {
    static final String IGMPF2 = "xgimi.hardware.gmpf@1.0::IGmpf2";
    private static final String IBASE = "android.hidl.base@1.0::IBase";
    private static final String TAG = "Z9xGmpf";
    private static final int CHAIN_HEADER = 16, CHAIN_SIZE_AT = 8, HASH_SIZE = 32, CHAIN_MAX = 8;

    private final HidlWire hidl = new HidlWire(IGMPF2, true);
    private volatile Gmpf2Identity identity = Gmpf2Identity.UNKNOWN;
    private volatile Gmpf2Vendor vendor;
    /** Element 0 of the last chain read (hex), or why it could not be read. */
    private volatile String lastLiveHash;

    /** Connects lazily and opens the guard for this connection, or throws; nothing else was sent. */
    synchronized void need() throws RemoteException {
        if (!hidl.connected()) identity = Gmpf2Identity.UNKNOWN;
        hidl.ready();
        if (identity == Gmpf2Identity.OPEN) return;
        if (identity == Gmpf2Identity.CLOSED) throw new IdentityException("IGmpf2 identity guard closed (live " + lastLiveHash + ")");
        String[] chain;
        try {
            chain = readHashChain();
        } catch (ReplyException e) {
            lastLiveHash = "unreadable";
            identity = Gmpf2Identity.CLOSED;
            Log.e(TAG, "IGmpf2 identity guard CLOSED: hash chain unreadable (" + e.getMessage() + ")");
            throw new IdentityException("IGmpf2 hash chain unreadable");
        }
        lastLiveHash = chain.length > 0 ? chain[0] : "empty";
        Gmpf2Vendor v = chain.length > 0 ? Gmpf2Vendor.of(chain[0]) : null;
        if (v != null) {
            vendor = v;
            identity = Gmpf2Identity.OPEN;
            Log.i(TAG, "IGmpf2 identity guard open (hash " + chain[0].substring(0, 8) + " = recorded " + v.label + ")");
            return;
        }
        identity = Gmpf2Identity.CLOSED;
        Log.e(TAG, "IGmpf2 identity guard CLOSED: live " + lastLiveHash);
        throw new IdentityException("IGmpf2 hash " + lastLiveHash + " is not a recorded one");
    }

    boolean isOpen() { return identity == Gmpf2Identity.OPEN && hidl.connected(); }

    /** The live chain (element 0 IGmpf2, element 1 IBase), read again; never opens the guard. */
    String[] liveHashChain() throws RemoteException {
        hidl.ready();
        return readHashChain();
    }

    /** IBase getHashChain(): Status + hidl_vec<uint8_t[32]> (16-byte header, then the embedded buffer). */
    private String[] readHashChain() throws RemoteException {
        HwParcel q = new HwParcel();
        q.writeInterfaceToken(IBASE);
        return hidl.send(HalCodes.IBASE_GET_HASH_CHAIN, q, r -> {
            HwBlob vec = r.readBuffer(CHAIN_HEADER);
            int n = vec.getInt32(CHAIN_SIZE_AT);
            if (n < 1 || n > CHAIN_MAX) throw new IllegalStateException("hash chain of " + n + " elements");
            HwBlob data = r.readEmbeddedBuffer((long) n * HASH_SIZE, vec.handle(), 0, true);
            String[] out = new String[n];
            for (int i = 0; i < n; i++) {
                byte[] h = new byte[HASH_SIZE];
                data.copyToInt8Array((long) i * HASH_SIZE, h, HASH_SIZE);
                out[i] = hex(h);
            }
            return out;
        });
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
        return sb.toString();
    }

    /** The guard, then the code of the open build. */
    private int c(int k) throws RemoteException {
        need();
        Gmpf2Vendor v = vendor;
        if (identity != Gmpf2Identity.OPEN || v == null) throw new IdentityException("IGmpf2 identity guard not open");
        int code = v.code(k);
        if (code == Gmpf2Vendor.ABSENT) throw new AbsentException("IGmpf2 " + k + " does not exist on vendor " + v.label);
        return code;
    }

    @Override public int code(Integer k) { return k; }
    @Override public String name(Integer k) { return hidl.name(k); }
    @Override public void ready() throws RemoteException { need(); }
    @Override public boolean connected() { return hidl.connected(); }
    @Override public void drop() {
        identity = Gmpf2Identity.UNKNOWN;
        hidl.drop();
    }

    @Override public void u8(Integer k, int v) throws RemoteException { hidl.u8(c(k), v); }
    @Override public boolean u8RetBool(Integer k, int v) throws RemoteException { return hidl.u8RetBool(c(k), v); }
    @Override public void i32(Integer k, int v) throws RemoteException { hidl.i32(c(k), v); }
    @Override public boolean i32RetBool(Integer k, int v) throws RemoteException { return hidl.i32RetBool(c(k), v); }
    @Override public int i32RetI32(Integer k, int v) throws RemoteException { return hidl.i32RetI32(c(k), v); }
    @Override public byte i32RetI8(Integer k, int v) throws RemoteException { return hidl.i32RetI8(c(k), v); }
    @Override public boolean i32I32RetBool(Integer k, int a, int b) throws RemoteException { return hidl.i32I32RetBool(c(k), a, b); }
    @Override public int i32I32RetI32(Integer k, int a, int b) throws RemoteException { return hidl.i32I32RetI32(c(k), a, b); }
    @Override public boolean i32BoolRetBool(Integer k, int a, boolean b) throws RemoteException { return hidl.i32BoolRetBool(c(k), a, b); }
    @Override public int i32BoolRetI32(Integer k, int a, boolean b) throws RemoteException { return hidl.i32BoolRetI32(c(k), a, b); }
    @Override public boolean i32F32RetBool(Integer k, int a, float b) throws RemoteException { return hidl.i32F32RetBool(c(k), a, b); }
    @Override public boolean i32x3RetBool(Integer k, int a, int b, int d) throws RemoteException { return hidl.i32x3RetBool(c(k), a, b, d); }
    @Override public int i32x4RetI32(Integer k, int a, int b, int d, int e) throws RemoteException {
        return hidl.i32x4RetI32(c(k), a, b, d, e);
    }
    @Override public void bool(Integer k, boolean v) throws RemoteException { hidl.bool(c(k), v); }
    @Override public boolean boolRetBool(Integer k, boolean v) throws RemoteException { return hidl.boolRetBool(c(k), v); }
    @Override public int boolRetI32(Integer k, boolean v) throws RemoteException { return hidl.boolRetI32(c(k), v); }
    @Override public boolean boolI32RetBool(Integer k, boolean a, int b) throws RemoteException { return hidl.boolI32RetBool(c(k), a, b); }
    @Override public int boolI32I32RetI32(Integer k, boolean a, int b, int d) throws RemoteException {
        return hidl.boolI32I32RetI32(c(k), a, b, d);
    }
    @Override public boolean retBool(Integer k) throws RemoteException { return hidl.retBool(c(k)); }
    @Override public int retI32(Integer k) throws RemoteException { return hidl.retI32(c(k)); }
    @Override public int retU8(Integer k) throws RemoteException { return hidl.retU8(c(k)); }
    @Override public byte retI8(Integer k) throws RemoteException { return hidl.retI8(c(k)); }
    @Override public byte strI32I32RetI8(Integer k, String s, int a, int b) throws RemoteException { return hidl.strI32I32RetI8(c(k), s, a, b); }
    @Override public byte strRetI8(Integer k, String s) throws RemoteException { return hidl.strRetI8(c(k), s); }
    @Override public void focusCallback(Integer k) throws RemoteException { hidl.focusCallback(c(k)); }
    @Override public int[] i32Struct(Integer k, int n) throws RemoteException { return hidl.i32Struct(c(k), n); }
    @Override public int[] boolStruct(Integer k, int n) throws RemoteException { return hidl.boolStruct(c(k), n); }
    @Override public float[] boolFloats(Integer k, int n, Check<float[]> check) throws RemoteException {
        return hidl.boolFloats(c(k), n, check);
    }
    @Override public int[] i32BoolStruct(Integer k, int a, int n, Check<int[]> check) throws RemoteException {
        return hidl.i32BoolStruct(c(k), a, n, check);
    }
    @Override public int[] i32I32BoolBytes(Integer k, int a, int b, int n, Check<int[]> check) throws RemoteException {
        return hidl.i32I32BoolBytes(c(k), a, b, n, check);
    }
    @Override public boolean i32I32BytesRetBool(Integer k, int a, int b, byte[] v) throws RemoteException {
        return hidl.i32I32BytesRetBool(c(k), a, b, v);
    }
    @Override public boolean i32StructRetBool(Integer k, int a, int[] fields) throws RemoteException {
        return hidl.i32StructRetBool(c(k), a, fields);
    }
    @Override public int[] motion(Integer k) throws RemoteException { return hidl.motion(c(k)); }

    /** A float32 reply (no shared call returns one). */
    float retF32(int k) throws RemoteException { return hidl.send(c(k), hidl.request(), HwParcel::readFloat); }
}
