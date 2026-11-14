package org.lumen.projector.hal;

import android.os.HwBlob;
import android.os.HwParcel;
import android.os.IHwBinder;
import android.os.Process;
import android.os.RemoteException;

import org.lumen.hal.gmpf.GmpfWire;

/**
 * The gmpf transport of the Z9X: one HIDL interface (IGmpf or IGmpf2) over HidlCaller, keyed by its HalCodes. Each
 * typed call lays its arguments out as the vendor proxy does (u8 / i8 / bool one byte, a struct one buffer of its
 * size) and turns a bad reply into ReplyException. lazy: connect on first use and drop the binder after a failed
 * transaction (IGmpf2, no death recipient).
 */
final class HidlWire extends HidlCaller implements GmpfWire<Integer> {
    private static final int I32 = 4, I16 = 2;
    private static final int MOTION_SIZE = 14, MOTION_AXES = 6, MOTION_TYPE_AT = 12;

    private final String descriptor;
    private final String shortName;
    private final boolean lazy;

    HidlWire(String descriptor, boolean lazy) {
        super(descriptor);
        this.descriptor = descriptor;
        this.shortName = descriptor.substring(descriptor.indexOf("::") + 2);
        this.lazy = lazy;
    }

    boolean connect(IHwBinder.DeathRecipient death, long cookie) { return connectInternal(death, cookie); }

    @Override boolean onTransactFailed() { return lazy; }

    @Override public int code(Integer k) { return k; }

    @Override public String name(Integer k) { return shortName + " " + k; }

    @Override public void ready() throws RemoteException {
        if (lazy && !ensureConnected()) throw new RemoteException(descriptor + " not available");
    }

    @Override public boolean connected() { return isConnected(); }

    @Override public void drop() { dropConnection(); }

    /** The one transaction path of this transport: HidlCaller.call, with its bad reply as our ReplyException. */
    <T> T send(int code, HwParcel q, Ret<T> ret) throws RemoteException {
        try {
            return call(code, q, ret);
        } catch (HidlCaller.ReplyException e) {
            throw new org.lumen.projector.hal.ReplyException(e.getMessage());
        }
    }

    @Override public void u8(Integer k, int v) throws RemoteException { send(k, q().withI8(v).p, RET_VOID); }
    @Override public boolean u8RetBool(Integer k, int v) throws RemoteException { return send(k, q().withI8(v).p, RET_BOOL); }
    @Override public void i32(Integer k, int v) throws RemoteException { send(k, q().withI32(v).p, RET_VOID); }
    @Override public boolean i32RetBool(Integer k, int v) throws RemoteException { return send(k, q().withI32(v).p, RET_BOOL); }
    @Override public int i32RetI32(Integer k, int v) throws RemoteException { return send(k, q().withI32(v).p, RET_I32); }
    @Override public byte i32RetI8(Integer k, int v) throws RemoteException { return send(k, q().withI32(v).p, RET_I8); }
    @Override public boolean i32I32RetBool(Integer k, int a, int b) throws RemoteException {
        return send(k, q().withI32(a).withI32(b).p, RET_BOOL);
    }
    @Override public int i32I32RetI32(Integer k, int a, int b) throws RemoteException {
        return send(k, q().withI32(a).withI32(b).p, RET_I32);
    }
    @Override public boolean i32BoolRetBool(Integer k, int a, boolean b) throws RemoteException {
        return send(k, q().withI32(a).withBool(b).p, RET_BOOL);
    }
    @Override public int i32BoolRetI32(Integer k, int a, boolean b) throws RemoteException {
        return send(k, q().withI32(a).withBool(b).p, RET_I32);
    }
    @Override public boolean i32F32RetBool(Integer k, int a, float b) throws RemoteException {
        Req r = q().withI32(a);
        r.p.writeFloat(b);
        return send(k, r.p, RET_BOOL);
    }
    @Override public boolean i32x3RetBool(Integer k, int a, int b, int c) throws RemoteException {
        return send(k, q().withI32(a).withI32(b).withI32(c).p, RET_BOOL);
    }
    @Override public int i32x4RetI32(Integer k, int a, int b, int c, int d) throws RemoteException {
        return send(k, q().withI32(a).withI32(b).withI32(c).withI32(d).p, RET_I32);
    }
    @Override public void bool(Integer k, boolean v) throws RemoteException { send(k, q().withBool(v).p, RET_VOID); }
    @Override public boolean boolRetBool(Integer k, boolean v) throws RemoteException { return send(k, q().withBool(v).p, RET_BOOL); }
    @Override public int boolRetI32(Integer k, boolean v) throws RemoteException { return send(k, q().withBool(v).p, RET_I32); }
    @Override public boolean boolI32RetBool(Integer k, boolean a, int b) throws RemoteException {
        return send(k, q().withBool(a).withI32(b).p, RET_BOOL);
    }
    @Override public int boolI32I32RetI32(Integer k, boolean a, int b, int c) throws RemoteException {
        return send(k, q().withBool(a).withI32(b).withI32(c).p, RET_I32);
    }
    @Override public boolean retBool(Integer k) throws RemoteException { return send(k, request(), RET_BOOL); }
    @Override public int retI32(Integer k) throws RemoteException { return send(k, request(), RET_I32); }
    @Override public int retU8(Integer k) throws RemoteException { return send(k, request(), RET_U8); }
    @Override public byte retI8(Integer k) throws RemoteException { return send(k, request(), RET_I8); }

    @Override public byte strI32I32RetI8(Integer k, String s, int a, int b) throws RemoteException {
        HwParcel q = request();
        q.writeString(s);
        q.writeInt32(a);
        q.writeInt32(b);
        return send(k, q, RET_I8);
    }

    @Override public byte strRetI8(Integer k, String s) throws RemoteException {
        HwParcel q = request();
        q.writeString(s);
        return send(k, q, RET_I8);
    }

    /** connectProjectorFocusManager / setProjectorFocusListener(pid, callback): never a null callback. */
    @Override public void focusCallback(Integer k) throws RemoteException {
        IHwBinder cb = HidlFocusCallback.get();
        if (cb == null) throw new RemoteException("callback must be non-null");
        HwParcel q = request();
        q.writeInt32(Process.myPid());
        q.writeStrongBinder(cb);
        send(k, q, RET_VOID);
    }

    @Override public int[] i32Struct(Integer k, int n) throws RemoteException {
        return send(k, request(), r -> {
            int ret = r.readInt32();
            HwBlob b = r.readBuffer((long) n * I32);
            int[] o = new int[n + 1];
            o[0] = ret;
            for (int i = 0; i < n; i++) o[i + 1] = b.getInt32((long) i * I32);
            return o;
        });
    }

    @Override public int[] boolStruct(Integer k, int n) throws RemoteException {
        return send(k, request(), r -> ints(k, r, n, null));
    }

    @Override public float[] boolFloats(Integer k, int n, Check<float[]> check) throws RemoteException {
        return send(k, request(), r -> {
            ok(k, r);
            HwBlob b = r.readBuffer((long) n * I32);
            float[] v = new float[n];
            for (int i = 0; i < n; i++) v[i] = b.getFloat((long) i * I32);
            check.check(v);
            return v;
        });
    }

    @Override public int[] i32BoolStruct(Integer k, int a, int n, Check<int[]> check) throws RemoteException {
        return send(k, q().withI32(a).p, r -> ints(k, r, n, check));
    }

    @Override public int[] i32I32BoolBytes(Integer k, int a, int b, int n, Check<int[]> check) throws RemoteException {
        return send(k, q().withI32(a).withI32(b).p, r -> {
            ok(k, r);
            HwBlob blob = r.readBuffer(n);
            int[] v = new int[n];
            for (int i = 0; i < n; i++) v[i] = blob.getInt8(i);
            check.check(v);
            return v;
        });
    }

    @Override public boolean i32I32BytesRetBool(Integer k, int a, int b, byte[] v) throws RemoteException {
        HwBlob blob = new HwBlob(v.length);
        for (int i = 0; i < v.length; i++) blob.putInt8(i, v[i]);
        HwParcel q = q().withI32(a).withI32(b).p;
        q.writeBuffer(blob);
        return send(k, q, RET_BOOL);
    }

    @Override public boolean i32StructRetBool(Integer k, int a, int[] fields) throws RemoteException {
        HwBlob blob = new HwBlob(fields.length * I32);
        for (int i = 0; i < fields.length; i++) blob.putInt32((long) i * I32, fields[i]);
        HwParcel q = q().withI32(a).p;
        q.writeBuffer(blob);
        return send(k, q, RET_BOOL);
    }

    /** MsensorDataStruct, 14 bytes: int16 acc x, y, z and gyro x, y, z, u8 type, 1 pad. */
    @Override public int[] motion(Integer k) throws RemoteException {
        return send(k, request(), r -> {
            HwBlob b = r.readBuffer(MOTION_SIZE);
            int[] o = new int[MOTION_AXES + 1];
            for (int i = 0; i < MOTION_AXES; i++) o[i] = b.getInt16((long) i * I16);
            o[MOTION_AXES] = b.getInt8(MOTION_TYPE_AT) & 0xff;
            return o;
        });
    }

    /** A false boolean return fails the reply. */
    private static void ok(int code, HwParcel r) {
        if (!r.readBool()) throw new IllegalStateException(code + " returned false");
    }

    private static int[] ints(int code, HwParcel r, int n, Check<int[]> check) {
        ok(code, r);
        HwBlob b = r.readBuffer((long) n * I32);
        int[] v = new int[n];
        for (int i = 0; i < n; i++) v[i] = b.getInt32((long) i * I32);
        if (check != null) check.check(v);
        return v;
    }

    private Req q() { return new Req(request()); }

    /** A request under construction: the arguments in call order. */
    private static final class Req {
        final HwParcel p;
        Req(HwParcel p) { this.p = p; }
        Req withI8(int v) { p.writeInt8((byte) v); return this; }
        Req withI32(int v) { p.writeInt32(v); return this; }
        Req withBool(boolean v) { p.writeBool(v); return this; }
    }
}
