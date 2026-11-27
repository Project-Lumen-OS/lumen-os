// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.hal;

import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import org.lumen.hal.AidlTransport;
import org.lumen.hal.HashRule;
import org.lumen.hal.gmpf.GmpfWire;
import org.lumen.hal.gmpf.TvEventSink;

/**
 * The gmpf transport of the Z9X HB on the shared lumen-hal AidlTransport (the one raw Binder path of every AIDL family):
 * the allow-list Calls (GmpfCall, devices/goat_elegant/research/allowed.tsv, tools/devices/check_aidl_hal.py rules 1 to
 * 7) laid out as the stock Java proxies do (com.xgimi.middle.jar, xgimi.hardware.gmpf.I<Name>$Stub$Proxy: int32 per
 * byte and boolean, structs as out parcelables after the return value, framed in parcelables as arguments).
 *
 * The vendor process /vendor/bin/hw/xgimi.hardware.gmpf-service registers 14 stable AIDL services
 * "xgimi.hardware.gmpf.I<Name>/default" (V1, hash per firmware: GmpfHash, 908de964 of V6.16.24 anywhere, 715affd0
 * only on v6.17.12). The HB rules over the shared transport: a call the vendor received but did not answer as asked
 * (unimplemented, an exception code, a reply that does not parse, a closed interface) is a ReplyException (delivered:
 * one-shot callers never repeat it), a lost one a RemoteException (the binder is dropped, the next call reconnects); a
 * failed guard call closes that interface for the process; every interface is linked to death, the first notice drops
 * them all, opens the guards again and tells the death listener once. NotOnHbException: a feature the HB does not have,
 * nothing sent. One connection state for every client (one vendor process); the handshake connects SYSTEM, POWER and
 * FOCUS (GmpfClient.connect), every other interface connects on its first call.
 */
final class AidlWire extends AidlTransport<GmpfCall> implements GmpfWire<GmpfCall> {
    static final String TAG = "Z9xGmpf";
    /** xgimi.hardware.gmpf V1; the accepted interface hashes per firmware are GmpfHash.RULE. */
    static final int VERSION = 1;
    private static final String PKG = "xgimi.hardware.gmpf.";
    /** The guard rule (GmpfHash): V61624 on any firmware, V61712 only on v6.17.12. */
    private static final HashRule RULE = new HashRule(GmpfHash.V61624, GmpfHash.V61712_FIRMWARE, GmpfHash.V61712);
    private static final GmpfIface[] HANDSHAKE = {GmpfIface.SYSTEM, GmpfIface.POWER, GmpfIface.FOCUS};
    static final ProxyRet<Void> RET_VOID = r -> null;
    static final ProxyRet<Boolean> RET_BOOL = In::bool;
    static final ProxyRet<Byte> RET_I8 = In::i8;
    static final ProxyRet<Integer> RET_I32 = In::i32;
    /** AIDL byte reply as 0..255 (the vendor's u8 values). */
    static final ProxyRet<Integer> RET_U8 = r -> r.i8() & 0xff;

    /** Receives (type, value) of IProjectorFocusCallback.focusEvent on a binder thread: only post. */
    interface FocusSink { void onFocusEvent(int type, String value); }

    private static volatile FocusSink sFocusSink;
    private static volatile Runnable sDeathListener;

    AidlWire() {
        super(TAG, VERSION, RULE, GmpfCall.GET_INTERFACE_VERSION, GmpfCall.GET_INTERFACE_HASH);
    }

    @Override protected RemoteException replyError(String msg) { return new ReplyException(msg); }
    @Override protected boolean closesOnFailedGuard(RemoteException e) { return e instanceof ReplyException; }
    @Override protected boolean watchesDeath() { return true; }
    @Override protected void vendorDied() {
        Runnable l = sDeathListener;
        if (l != null) l.run();
    }

    static void setFocusSink(FocusSink s) {
        sFocusSink = s;
    }

    /** Connect and verify the three handshake interfaces (SYSTEM, POWER, FOCUS). Never throws, never on the main thread. */
    boolean connectHandshake(Runnable deathListener) {
        sDeathListener = deathListener;
        if (Looper.getMainLooper().isCurrentThread()) {
            Log.e(TAG, "handshake refused: called on the main thread");
            return false;
        }
        boolean ok = true;
        for (GmpfIface i : HANDSHAKE) {
            try {
                require(i);
            } catch (Throwable t) {
                ok = false;
            }
        }
        return ok;
    }

    /** SYSTEM, POWER and FOCUS connected and verified. */
    boolean isConnected() {
        for (GmpfIface i : HANDSHAKE) if (!connected(i)) return false;
        return true;
    }

    /** One call of the allow list with the stock proxy layout (the clients' own parcelable calls). */
    <T> T send(GmpfCall c, ProxyArgs a, ProxyRet<T> r) throws RemoteException {
        return proxyCall(c, a, r);
    }

    /** An AIDL byte argument: the Z9X u8 values, 0..127 only (nothing larger is ever sent). */
    private static byte u8(int v) {
        if (v < 0 || v > 127) throw new IllegalArgumentException("byte argument " + v + " not in 0..127");
        return (byte) v;
    }

    /** The int arrays of MsensorDataStruct: at most this long, exactly MOTION_AXES used. */
    private static final int MOTION_ARRAY_MAX = 8, MOTION_AXES = 3;

    @Override public int code(GmpfCall k) { return k.code; }

    @Override public String name(GmpfCall k) { return k.iface + " " + k.code + " " + k.method; }

    @Override public void ready() {}

    @Override public boolean connected() { return isConnected(); }

    @Override public void drop() { forgetAll(); }

    @Override public void u8(GmpfCall k, int v) throws RemoteException { final byte b = u8(v); proxyCall(k, o -> o.i8(b), RET_VOID); }
    @Override public boolean u8RetBool(GmpfCall k, int v) throws RemoteException { final byte b = u8(v); return proxyCall(k, o -> o.i8(b), RET_BOOL); }
    @Override public void i32(GmpfCall k, int v) throws RemoteException { proxyCall(k, o -> o.i32(v), RET_VOID); }
    @Override public boolean i32RetBool(GmpfCall k, int v) throws RemoteException { return proxyCall(k, o -> o.i32(v), RET_BOOL); }
    @Override public int i32RetI32(GmpfCall k, int v) throws RemoteException { return proxyCall(k, o -> o.i32(v), RET_I32); }
    @Override public byte i32RetI8(GmpfCall k, int v) throws RemoteException { return proxyCall(k, o -> o.i32(v), RET_I8); }
    @Override public void i32I32(GmpfCall k, int a, int b) throws RemoteException { proxyCall(k, o -> { o.i32(a); o.i32(b); }, RET_VOID); }
    @Override public boolean i32I32RetBool(GmpfCall k, int a, int b) throws RemoteException { return proxyCall(k, o -> { o.i32(a); o.i32(b); }, RET_BOOL); }
    @Override public int i32I32RetI32(GmpfCall k, int a, int b) throws RemoteException { return proxyCall(k, o -> { o.i32(a); o.i32(b); }, RET_I32); }
    @Override public boolean i32BoolRetBool(GmpfCall k, int a, boolean b) throws RemoteException { return proxyCall(k, o -> { o.i32(a); o.bool(b); }, RET_BOOL); }
    @Override public int i32BoolRetI32(GmpfCall k, int a, boolean b) throws RemoteException { return proxyCall(k, o -> { o.i32(a); o.bool(b); }, RET_I32); }
    @Override public boolean i32F32RetBool(GmpfCall k, int a, float b) throws RemoteException { return proxyCall(k, o -> { o.i32(a); o.f32(b); }, RET_BOOL); }
    @Override public boolean i32x3RetBool(GmpfCall k, int a, int b, int c) throws RemoteException { return proxyCall(k, o -> { o.i32(a); o.i32(b); o.i32(c); }, RET_BOOL); }
    @Override public int i32x4RetI32(GmpfCall k, int a, int b, int c, int d) throws RemoteException {
        return proxyCall(k, o -> { o.i32(a); o.i32(b); o.i32(c); o.i32(d); }, RET_I32);
    }
    @Override public void bool(GmpfCall k, boolean v) throws RemoteException { proxyCall(k, o -> o.bool(v), RET_VOID); }
    @Override public boolean boolRetBool(GmpfCall k, boolean v) throws RemoteException { return proxyCall(k, o -> o.bool(v), RET_BOOL); }
    @Override public int boolRetI32(GmpfCall k, boolean v) throws RemoteException { return proxyCall(k, o -> o.bool(v), RET_I32); }
    @Override public boolean boolI32RetBool(GmpfCall k, boolean a, int b) throws RemoteException { return proxyCall(k, o -> { o.bool(a); o.i32(b); }, RET_BOOL); }
    @Override public int boolI32I32RetI32(GmpfCall k, boolean a, int b, int c) throws RemoteException {
        return proxyCall(k, o -> { o.bool(a); o.i32(b); o.i32(c); }, RET_I32);
    }
    @Override public boolean retBool(GmpfCall k) throws RemoteException { return proxyCall(k, null, RET_BOOL); }
    @Override public int retI32(GmpfCall k) throws RemoteException { return proxyCall(k, null, RET_I32); }
    @Override public int retU8(GmpfCall k) throws RemoteException { return proxyCall(k, null, RET_U8); }
    @Override public byte retI8(GmpfCall k) throws RemoteException { return proxyCall(k, null, RET_I8); }

    @Override public byte strI32I32RetI8(GmpfCall k, String s, int a, int b) throws RemoteException {
        return proxyCall(k, o -> { o.str(s); o.i32(a); o.i32(b); }, RET_I8);
    }

    @Override public byte strRetI8(GmpfCall k, String s) throws RemoteException { return proxyCall(k, o -> o.str(s), RET_I8); }

    /** int(String): GmTvManager 163 getExtDecInfo. */
    @Override public int strRetI32(GmpfCall k, String s) throws RemoteException { return proxyCall(k, o -> o.str(s), RET_I32); }

    /** A parcelable returned after one int argument, n int words (bool fields are int words): TV 153 getHdmiHwConfig. */
    @Override public int[] i32RetObject(GmpfCall k, int a, int n) throws RemoteException {
        return proxyCall(k, o -> o.i32(a), r -> r.object(q -> ints(q, n)));
    }

    /** FOCUS 1 setFocusCallback(cb), the stock registration (ProjectorFocusManagerWrapper.initCallback), at every call. */
    @Override public void focusCallback(GmpfCall k) throws RemoteException {
        sendCallback(k, PKG + "IProjectorFocusCallback", (t, v) -> {
            FocusSink s = sFocusSink;
            if (s != null) s.onFocusEvent(t, v);
        });
    }

    /**
     * TV 1 setGmTvCallback(cb), the stock registration of the GmTvManager events (GmTvManagerWrapper.setGmTvListener):
     * event 5 "picturemode:N" drives the AI picture quality (org.lumen.aipq.AiPq).
     */
    @Override public void tvCallback(GmpfCall k, TvEventSink sink) throws RemoteException {
        sendCallback(k, PKG + "IGmTvCallback", sink == null ? null : sink::onTvEvent);
    }

    @Override public int[] i32Struct(GmpfCall k, int n) throws RemoteException {
        return proxyCall(k, null, r -> {
            int ret = r.i32();
            int[] f = r.out(q -> ints(q, n));
            int[] v = new int[n + 1];
            v[0] = ret;
            System.arraycopy(f, 0, v, 1, n);
            return v;
        });
    }

    @Override public int[] boolStruct(GmpfCall k, int n) throws RemoteException {
        return proxyCall(k, null, r -> {
            boolean ok = r.bool();
            int[] v = r.out(q -> ints(q, n));
            ok(k, ok);
            return v;
        });
    }

    @Override public float[] boolFloats(GmpfCall k, int n, Check<float[]> check) throws RemoteException {
        return proxyCall(k, null, r -> {
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
        return proxyCall(k, o -> o.i32(a), r -> {
            boolean ok = r.bool();
            int[] v = r.out(q -> ints(q, n));
            ok(k, ok);
            check.check(v);
            return v;
        });
    }

    /** The out byte array: the request carries its length, the reply readByteArray of exactly that length. */
    @Override public int[] i32I32BoolBytes(GmpfCall k, int a, int b, int n, Check<int[]> check) throws RemoteException {
        return proxyCall(k, o -> { o.i32(a); o.i32(b); o.outBytes(n); }, r -> {
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
        return proxyCall(k, o -> { o.i32(a); o.i32(b); o.bytes(v); }, RET_BOOL);
    }

    /** The struct as an in parcelable after the int argument. */
    @Override public boolean i32StructRetBool(GmpfCall k, int a, int[] fields) throws RemoteException {
        return proxyCall(k, o -> {
            o.i32(a);
            o.object(w -> { for (int f : fields) w.i32(f); });
        }, RET_BOOL);
    }

    /** MsensorDataStruct {int[] accData, int[] angData, byte msensorType} as the returned parcelable. */
    @Override public int[] motion(GmpfCall k) throws RemoteException {
        return proxyCall(k, null, r -> r.object(q -> {
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
