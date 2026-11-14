package org.lumen.projector.hal;

import android.os.RemoteException;

import org.lumen.hal.gmpf.GmpfWire;

/**
 * The gmpf transport of the Z9X HB: the allow-list Calls over AidlCaller, laid out as the stock Java proxies do (int32
 * per byte and boolean, structs as out parcelables after the return value, framed in parcelables as arguments). One
 * connection state for every client (one vendor process); the handshake connects it (GmpfClient.connect), every other
 * interface connects on its first call.
 */
final class AidlWire extends AidlCaller implements GmpfWire<GmpfCall> {
    /** The int arrays of MsensorDataStruct: at most this long, exactly MOTION_AXES used. */
    private static final int MOTION_ARRAY_MAX = 8, MOTION_AXES = 3;

    @Override public int code(GmpfCall k) { return k.code; }

    @Override public String name(GmpfCall k) { return k.iface + " " + k.code + " " + k.method; }

    @Override public void ready() {}

    @Override public boolean connected() { return isConnected(); }

    @Override public void drop() { dropConnection(); }

    @Override public void u8(GmpfCall k, int v) throws RemoteException { callU8(k, v); }
    @Override public boolean u8RetBool(GmpfCall k, int v) throws RemoteException { return callU8RetBool(k, v); }
    @Override public void i32(GmpfCall k, int v) throws RemoteException { callI32(k, v); }
    @Override public boolean i32RetBool(GmpfCall k, int v) throws RemoteException { return callI32RetBool(k, v); }
    @Override public int i32RetI32(GmpfCall k, int v) throws RemoteException { return callI32RetI32(k, v); }
    @Override public byte i32RetI8(GmpfCall k, int v) throws RemoteException { return call(k, o -> o.i32(v), RET_I8); }
    @Override public boolean i32I32RetBool(GmpfCall k, int a, int b) throws RemoteException { return callI32I32RetBool(k, a, b); }
    @Override public int i32I32RetI32(GmpfCall k, int a, int b) throws RemoteException { return callI32I32RetI32(k, a, b); }
    @Override public boolean i32BoolRetBool(GmpfCall k, int a, boolean b) throws RemoteException { return callI32BoolRetBool(k, a, b); }
    @Override public int i32BoolRetI32(GmpfCall k, int a, boolean b) throws RemoteException { return callI32BoolRetI32(k, a, b); }
    @Override public boolean i32F32RetBool(GmpfCall k, int a, float b) throws RemoteException { return callI32F32RetBool(k, a, b); }
    @Override public boolean i32x3RetBool(GmpfCall k, int a, int b, int c) throws RemoteException { return callI32x3RetBool(k, a, b, c); }
    @Override public int i32x4RetI32(GmpfCall k, int a, int b, int c, int d) throws RemoteException {
        return callI32x4RetI32(k, a, b, c, d);
    }
    @Override public void bool(GmpfCall k, boolean v) throws RemoteException { callBool(k, v); }
    @Override public boolean boolRetBool(GmpfCall k, boolean v) throws RemoteException { return callBoolRetBool(k, v); }
    @Override public int boolRetI32(GmpfCall k, boolean v) throws RemoteException { return callBoolRetI32(k, v); }
    @Override public boolean boolI32RetBool(GmpfCall k, boolean a, int b) throws RemoteException { return callBoolI32RetBool(k, a, b); }
    @Override public int boolI32I32RetI32(GmpfCall k, boolean a, int b, int c) throws RemoteException {
        return callBoolI32I32RetI32(k, a, b, c);
    }
    @Override public boolean retBool(GmpfCall k) throws RemoteException { return callRetBool(k); }
    @Override public int retI32(GmpfCall k) throws RemoteException { return callRetI32(k); }
    @Override public int retU8(GmpfCall k) throws RemoteException { return callRetU8(k); }
    @Override public byte retI8(GmpfCall k) throws RemoteException { return callRetI8(k); }

    @Override public byte strI32I32RetI8(GmpfCall k, String s, int a, int b) throws RemoteException {
        return call(k, o -> { o.str(s); o.i32(a); o.i32(b); }, RET_I8);
    }

    @Override public byte strRetI8(GmpfCall k, String s) throws RemoteException { return call(k, o -> o.str(s), RET_I8); }

    @Override public void focusCallback(GmpfCall k) throws RemoteException { callRegisterFocusCallback(k); }

    @Override public int[] i32Struct(GmpfCall k, int n) throws RemoteException {
        return call(k, null, r -> {
            int ret = r.i32();
            int[] f = r.out(q -> ints(q, n));
            int[] v = new int[n + 1];
            v[0] = ret;
            System.arraycopy(f, 0, v, 1, n);
            return v;
        });
    }

    @Override public int[] boolStruct(GmpfCall k, int n) throws RemoteException {
        return call(k, null, r -> {
            boolean ok = r.bool();
            int[] v = r.out(q -> ints(q, n));
            ok(k, ok);
            return v;
        });
    }

    @Override public float[] boolFloats(GmpfCall k, int n, Check<float[]> check) throws RemoteException {
        return call(k, null, r -> {
            boolean ok = r.bool();
            float[] v = r.out(q -> {
                float[] f = new float[n];
                for (int i = 0; i < n; i++) f[i] = q.f32();
                return f;
            });
            ok(k, ok);
            check.check(v);
            return v;
        });
    }

    @Override public int[] i32BoolStruct(GmpfCall k, int a, int n, Check<int[]> check) throws RemoteException {
        return call(k, o -> o.i32(a), r -> {
            boolean ok = r.bool();
            int[] v = r.out(q -> ints(q, n));
            ok(k, ok);
            check.check(v);
            return v;
        });
    }

    /** The out byte array: the request carries its length, the reply readByteArray of exactly that length. */
    @Override public int[] i32I32BoolBytes(GmpfCall k, int a, int b, int n, Check<int[]> check) throws RemoteException {
        return call(k, o -> { o.i32(a); o.i32(b); o.outBytes(n); }, r -> {
            boolean ok = r.bool();
            byte[] raw = new byte[n];
            r.bytes(raw);
            ok(k, ok);
            int[] v = new int[n];
            for (int i = 0; i < n; i++) v[i] = raw[i];
            check.check(v);
            return v;
        });
    }

    @Override public boolean i32I32BytesRetBool(GmpfCall k, int a, int b, byte[] v) throws RemoteException {
        return call(k, o -> { o.i32(a); o.i32(b); o.bytes(v); }, RET_BOOL);
    }

    /** The struct as an in parcelable after the int argument. */
    @Override public boolean i32StructRetBool(GmpfCall k, int a, int[] fields) throws RemoteException {
        return call(k, o -> {
            o.i32(a);
            o.object(w -> { for (int f : fields) w.i32(f); });
        }, RET_BOOL);
    }

    /** MsensorDataStruct {int[] accData, int[] angData, byte msensorType} as the returned parcelable. */
    @Override public int[] motion(GmpfCall k) throws RemoteException {
        return call(k, null, r -> r.object(q -> {
            int[] acc = q.ints(MOTION_ARRAY_MAX), ang = q.ints(MOTION_ARRAY_MAX);
            int type = q.i8();
            if (acc.length != MOTION_AXES || ang.length != MOTION_AXES) {
                throw new IllegalStateException("MsensorDataStruct arrays " + acc.length + " / " + ang.length);
            }
            return new int[] {acc[0], acc[1], acc[2], ang[0], ang[1], ang[2], type};
        }));
    }

    private static int[] ints(In q, int n) {
        int[] f = new int[n];
        for (int i = 0; i < n; i++) f[i] = q.i32();
        return f;
    }

    /** A false boolean return fails the reply (after the out struct was read, as the stock proxy reads it). */
    private static void ok(GmpfCall k, boolean ok) {
        if (!ok) throw new IllegalStateException(k.code + " returned false");
    }
}
