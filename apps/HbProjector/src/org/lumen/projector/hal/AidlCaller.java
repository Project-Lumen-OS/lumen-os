/*
 * AidlCaller: the single transact path from HbProjector to XGIMI's vendor service on the Z9X HB (goat_elegant,
 * G0130). HB-only file (the design notes to 6), the HB twin of HidlCaller.
 *
 * The vendor process /vendor/bin/hw/xgimi.hardware.gmpf-service registers 14 stable AIDL services
 * "xgimi.hardware.gmpf.I<Name>/default" (V1, hash per firmware: GmpfHash). This class talks to nine of them with raw Binder
 * transactions laid out exactly as the stock Java proxies do it (com.xgimi.middle.jar,
 * xgimi.hardware.gmpf.I<Name>$Stub$Proxy):
 *   writeInterfaceToken(DESCRIPTOR) + arguments -> transact(code, data, reply, 0)
 *   -> false means "unimplemented"; else reply.readException() + return value + out parcelables.
 *
 * HARD ALLOWLIST: only the members of enum Call can ever be transacted. Every Call is a row of
 * devices/goat_elegant/research/allowed.tsv, of aidl_codes.tsv (same service, code and stub method) and not a
 * BANNED or REVIEW row of banned.tsv (tools/devices/check_aidl_hal.py, rules 1 to 7, with --dex in every build).
 * No IGmpf / IGmpf2 HIDL code number ever reaches this file. The typed clients (GmpfClient, Gmpf2Client over the
 * shared GmpfBase / Gmpf2Base, through AidlWire) fix or range-check every argument before anything is sent.
 *
 * VERSION GUARD (HbBoot rule): before the first real call on an interface, getInterfaceVersion must return 1 and
 * getInterfaceHash must return a hash GmpfHash.accepted for this unit's ro.vendor.build.version.incremental
 * (908de964 of V6.16.24 anywhere, 715affd0 only on v6.17.12). A mismatch or a failed meta call latches that
 * interface closed for the process: nothing else is sent to it, and every typed method on it throws ReplyException
 * at once.
 *
 * Connection: ServiceManager.checkService, never the blocking getService. Every connected interface gets a death
 * recipient; all 14 services live in one vendor process, so the first death notice drops every binder, resets
 * every guard (a restarted service runs the guard again) and calls the death listener once.
 *
 * Errors (as HidlCaller): calls on the main thread are refused before sending (RemoteException); a failure
 * inside transact is a RemoteException (not delivered; that binder is dropped, the next call reconnects);
 * transact returning false, an exception code in the reply or a reply that does not parse is a ReplyException
 * (delivered: one-shot callers never repeat it). NotOnHbException: a feature the HB does not have, nothing sent.
 *
 * Parcels are touched only here (check_aidl_hal rule 1): KstPoint and the clients see Wire.In / Wire.Out.
 */
package org.lumen.projector.hal;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;

abstract class AidlCaller {
    static final String TAG = "Z9xGmpf";

    /** xgimi.hardware.gmpf V1; the accepted interface hashes per firmware are GmpfHash's rule. */
    static final int VERSION = 1;
    /** The hash the guard accepted last (the callback stub answers it, as the stock stub of that firmware does). */
    private static volatile String sAcceptedHash = GmpfHash.V61624;
    private static final String PKG = "xgimi.hardware.gmpf.";

    // ---------------------------------------------------------------- errors (a bad reply is the shared ReplyException)
    /** A feature the HB does not have: nothing was sent. */
    public static final class NotOnHbException extends RemoteException {
        NotOnHbException(String what) { super(what + ": not on the Z9X HB, nothing sent"); }
    }

    // ---------------------------------------------------------------- connection state (one per process)
    private static Runnable sDeathListener;
    private static int sGeneration;

    /** Called by the vendor's death notice of any linked interface; only the first one of a generation acts. */
    private static final class Death implements IBinder.DeathRecipient {
        private final int generation;
        Death(int generation) { this.generation = generation; }
        @Override public void binderDied() { onVendorDied(generation); }
    }

    private static void onVendorDied(int generation) {
        Runnable l;
        synchronized (AidlCaller.class) {
            if (generation != sGeneration) return;            // a second notice of the same death, or stale
            sGeneration++;
            for (GmpfIface i : GmpfIface.values()) {
                i.binder = null;
                i.verified = false;
                i.refused = false;                             // a restarted service runs the guard again
                i.refusedLogged = false;
            }
            l = sDeathListener;
        }
        Log.w(TAG, "vendor gmpf service died: every interface dropped");
        if (l != null) {
            try { l.run(); } catch (Throwable t) { Log.w(TAG, "death listener: " + t); }
        }
    }

    static synchronized void setDeathListener(Runnable l) {
        sDeathListener = l;
    }

    /** checkService + linkToDeath, without waiting. Never throws. true when the interface has a live binder. */
    private static boolean connectIface(GmpfIface i) {
        synchronized (AidlCaller.class) {
            if (i.refused) return false;
            if (i.binder != null && i.binder.isBinderAlive()) return true;
            i.binder = null;
            i.verified = false;
            try {
                IBinder b = ServiceManager.checkService(i.instance);
                if (b == null) return false;
                b.linkToDeath(new Death(sGeneration), 0);
                i.binder = b;
                Log.i(TAG, "connected to " + i.instance);
                return true;
            } catch (Throwable t) {                          // RemoteException (already dead), SecurityException
                Log.w(TAG, i.instance + " not available: " + t);
                return false;
            }
        }
    }

    /** Connect and run the version guard of one interface. ReplyException when the guard refuses it. */
    private static void need(GmpfIface i) throws RemoteException {
        synchronized (AidlCaller.class) {
            if (i.refused) {
                if (!i.refusedLogged) {
                    i.refusedLogged = true;
                    Log.e(TAG, "guard: " + i.descriptor + " is closed for this process (version or hash)");
                }
                throw new ReplyException(i.descriptor + " refused earlier (version or hash guard)");
            }
            if (!connectIface(i)) throw new RemoteException(i.instance + " not available");
            if (i.verified) return;
            int version;
            String hash;
            try {
                version = sendCall(i, GmpfCall.GET_INTERFACE_VERSION, null, In::i32);
                hash = sendCall(i, GmpfCall.GET_INTERFACE_HASH, null, In::str);
            } catch (ReplyException e) {
                i.refused = true;
                i.binder = null;
                i.refusedLogged = true;
                Log.e(TAG, "guard: " + i.descriptor + " meta call failed, interface closed: " + e.getMessage());
                throw e;
            }
            String firmware = SystemProperties.get(GmpfHash.FIRMWARE_PROP, "");
            if (version != VERSION || !GmpfHash.accepted(hash, firmware)) {
                i.refused = true;
                i.binder = null;
                i.refusedLogged = true;
                String msg = "guard: " + i.descriptor + " version " + version + " hash " + hash + " on firmware "
                        + firmware + ", expected " + VERSION + " " + GmpfHash.V61624 + " (or " + GmpfHash.V61712
                        + " on " + GmpfHash.V61712_FIRMWARE + "); interface closed, nothing sent";
                Log.e(TAG, msg);
                throw new ReplyException(msg);
            }
            i.verified = true;
            sAcceptedHash = GmpfHash.answer(hash);
            Log.i(TAG, "guard: " + i.descriptor + " V" + version + " hash " + hash + " ok on " + firmware);
        }
    }

    /** Connect and verify the three handshake interfaces (SYSTEM, POWER, FOCUS). Never throws. */
    static boolean connectHandshake(Runnable deathListener) {
        setDeathListener(deathListener);
        boolean ok = true;
        for (GmpfIface i : new GmpfIface[] {GmpfIface.SYSTEM, GmpfIface.POWER, GmpfIface.FOCUS}) {
            try {
                need(i);
            } catch (Throwable t) {
                ok = false;
            }
        }
        return ok;
    }

    /** SYSTEM, POWER and FOCUS connected and verified. */
    public boolean isConnected() {
        synchronized (AidlCaller.class) {
            for (GmpfIface i : new GmpfIface[] {GmpfIface.SYSTEM, GmpfIface.POWER, GmpfIface.FOCUS}) {
                if (i.binder == null || !i.verified || !i.binder.isBinderAlive()) return false;
            }
            return true;
        }
    }

    /** Forget every binder after a death notice (no vendor call). The guards run again at the next connect. */
    public void dropConnection() {
        synchronized (AidlCaller.class) {
            for (GmpfIface i : GmpfIface.values()) {
                i.binder = null;
                i.verified = false;
            }
        }
    }

    // ---------------------------------------------------------------- wire
    /** Writes the arguments after the interface token. */
    interface Args { void write(Out o); }
    /** Reads the reply after readException. */
    interface Ret<T> { T read(In r); }
    /** One stable parcelable's fields, inside its size frame. */
    interface Writer { void write(Out o); }
    interface Reader<T> { T read(In r); }

    static final Ret<Void> RET_VOID = r -> null;
    static final Ret<Boolean> RET_BOOL = In::bool;
    static final Ret<Byte> RET_I8 = In::i8;
    static final Ret<Integer> RET_I32 = In::i32;
    /** AIDL byte reply as 0..255 (the vendor's u8 values). */
    static final Ret<Integer> RET_U8 = r -> r.i8() & 0xff;

    /**
     * The request after writeInterfaceToken, with the Java Parcel rules of the stock proxies
     *: byte = writeByte (one int32), boolean = writeBoolean (int32 0 / 1).
     */
    static final class Out {
        private final Parcel p;
        private Out(Parcel p) { this.p = p; }
        void i32(int v) { p.writeInt(v); }
        void bool(boolean v) { p.writeBoolean(v); }
        void i8(byte v) { p.writeByte(v); }
        void f32(float v) { p.writeFloat(v); }
        void str(String v) { p.writeString(v); }
        /** in byte[]: writeByteArray (length, then the bytes). */
        void bytes(byte[] v) { p.writeByteArray(v); }
        /** out byte[]: only the length the reply must fill (never null, never negative). */
        void outBytes(int length) {
            if (length < 0) throw new IllegalArgumentException("out array length " + length);
            p.writeInt(length);
        }
        /** in parcelable: writeTypedObject(x, 0), i.e. int32 1, then the framed parcelable. */
        void object(Writer w) {
            p.writeInt(1);
            framed(w);
        }
        /** writeTypedArray of n framed parcelables: int32 n, then per element int32 1 and the element. */
        void typedArray(int n, ElementWriter w) {
            p.writeInt(n);
            for (int k = 0; k < n; k++) {
                final int idx = k;
                p.writeInt(1);
                framed(o -> w.write(o, idx));
            }
        }
        /** Stable parcelable frame: int32 size (patched after the fields), the fields. */
        void framed(Writer w) {
            int start = p.dataPosition();
            p.writeInt(0);
            w.write(this);
            int end = p.dataPosition();
            p.setDataPosition(start);
            p.writeInt(end - start);
            p.setDataPosition(end);
        }
    }

    interface ElementWriter { void write(Out o, int index); }
    interface ElementReader { void read(In r, int index); }

    /** The reply after readException. Every parse error is an IllegalStateException (-> ReplyException). */
    static final class In {
        private final Parcel p;
        private In(Parcel p) { this.p = p; }
        int i32() { need(4); return p.readInt(); }
        boolean bool() { need(4); return p.readBoolean(); }
        byte i8() { need(4); return p.readByte(); }
        float f32() { need(4); return p.readFloat(); }
        String str() { return p.readString(); }
        /** out byte[]: readByteArray, the length must equal the array's. */
        void bytes(byte[] dst) {
            int pos = p.dataPosition();
            need(4);
            int n = p.readInt();
            if (n != dst.length) throw new IllegalStateException("byte array of " + n + ", expected " + dst.length);
            p.setDataPosition(pos);
            p.readByteArray(dst);
        }
        /** createIntArray: int32 n (negative = null: fails, more than max fails), then n int32. */
        int[] ints(int max) {
            int n = i32();
            if (n < 0 || n > max) throw new IllegalStateException("int array of " + n + " (at most " + max + ")");
            int[] a = new int[n];
            for (int k = 0; k < n; k++) a[k] = i32();
            return a;
        }
        /** Returned parcelable (readTypedObject): int32 non-zero, then the framed parcelable. Zero (null) fails. */
        <T> T object(Reader<T> r) {
            if (i32() == 0) throw new IllegalStateException("null parcelable in the reply");
            return framed(r);
        }
        /** Out parcelable after the return value: int32 non-zero, then readFromParcel. Zero fails. */
        <T> T out(Reader<T> r) {
            return object(r);
        }
        /** createTypedArray: int32 n (negative = null: fails), per element int32 present + framed element. */
        int typedArray(int max, ElementReader r) {
            int n = i32();
            if (n < 0 || n > max) throw new IllegalStateException("typed array of " + n + " (at most " + max + ")");
            for (int k = 0; k < n; k++) {
                if (i32() == 0) throw new IllegalStateException("null element " + k);
                final int idx = k;
                framed(x -> { r.read(x, idx); return null; });
            }
            return n;
        }
        /**
         * Stable parcelable frame: int32 size (at least 4), the fields, then the position moves to start + size
         * (fields of a newer version are skipped). Fields read past the frame fail (the vendor sent fewer).
         */
        <T> T framed(Reader<T> r) {
            int start = p.dataPosition();
            int size = i32();
            if (size < 4) throw new IllegalStateException("parcelable too small (" + size + ")");
            if (size > p.dataSize() - start) throw new IllegalStateException("parcelable size " + size + " past the reply");
            T v = r.read(this);
            if (p.dataPosition() - start > size) {
                throw new IllegalStateException("parcelable of " + size + " bytes is shorter than its fields");
            }
            p.setDataPosition(start + size);
            return v;
        }
        private void need(int n) {
            if (p.dataAvail() < n) throw new IllegalStateException("reply too short");
        }
    }

    // ---------------------------------------------------------------- calls
    /** Guarded call of one Call: verify its interface first, then send. Package-private: no raw code anywhere. */
    static <T> T call(GmpfCall c, Args a, Ret<T> r) throws RemoteException {
        if (c.iface == null) throw new IllegalStateException(c.method + " is a meta call");
        need(c.iface);
        return sendCall(c.iface, c, a, r);
    }

    /** The only transact. RemoteException: not delivered (the binder is dropped); ReplyException: delivered. */
    private static <T> T sendCall(GmpfIface i, GmpfCall c, Args a, Ret<T> r) throws RemoteException {
        if (c.iface != null && c.iface != i) throw new IllegalStateException(c.method + " is not on " + i.descriptor);
        if (Looper.getMainLooper().isCurrentThread()) {
            Log.e(TAG, i.descriptor + " " + c.method + " refused: called on the main thread");
            throw new RemoteException("HAL call on the main thread refused");
        }
        IBinder b;
        synchronized (AidlCaller.class) {
            // a real call goes only to a binder the guard passed: connectIface clears verified with every new
            // binder, and a refused interface has no binder (need() drops it), so a call that passed need()
            // before a vendor restart never reaches the new binder before or after its guard ran
            if (c.iface != null && (!i.verified || i.refused)) throw new RemoteException(i.descriptor + " not verified");
            b = i.binder;
        }
        if (b == null) throw new RemoteException(i.instance + " not connected");
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(i.descriptor);
            if (a != null) a.write(new Out(data));
            boolean done;
            try {
                done = b.transact(c.code, data, reply, 0);
            } catch (RemoteException e) {
                dropIfSame(i, b);
                throw e;
            } catch (RuntimeException e) {
                dropIfSame(i, b);
                throw new RemoteException(c.method + ": " + e);
            }
            if (!done) throw new ReplyException("Method " + c.method + " is unimplemented (code " + c.code + ")");
            try {
                reply.readException();
                return r.read(new In(reply));
            } catch (RuntimeException e) {
                throw new ReplyException("reply of " + i.descriptor + " " + c.method + ": " + e);
            }
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static void dropIfSame(GmpfIface i, IBinder b) {
        synchronized (AidlCaller.class) {
            if (i.binder == b) {
                i.binder = null;
                i.verified = false;
            }
        }
    }

    // ---------------------------------------------------------------- typed helpers (as HidlCaller)
    /** An AIDL byte argument: the Z9X u8 values, 0..127 only (nothing larger is ever sent). */
    private static byte u8(int v) {
        if (v < 0 || v > 127) throw new IllegalArgumentException("byte argument " + v + " not in 0..127");
        return (byte) v;
    }

    static void callU8(GmpfCall c, int v) throws RemoteException {
        final byte b = u8(v);
        call(c, o -> o.i8(b), RET_VOID);
    }

    static boolean callU8RetBool(GmpfCall c, int v) throws RemoteException {
        final byte b = u8(v);
        return call(c, o -> o.i8(b), RET_BOOL);
    }

    static void callI32(GmpfCall c, int v) throws RemoteException { call(c, o -> o.i32(v), RET_VOID); }

    static boolean callI32RetBool(GmpfCall c, int v) throws RemoteException { return call(c, o -> o.i32(v), RET_BOOL); }

    static int callI32RetI32(GmpfCall c, int v) throws RemoteException { return call(c, o -> o.i32(v), RET_I32); }

    static boolean callI32I32RetBool(GmpfCall c, int a, int b) throws RemoteException {
        return call(c, o -> { o.i32(a); o.i32(b); }, RET_BOOL);
    }

    static int callI32I32RetI32(GmpfCall c, int a, int b) throws RemoteException {
        return call(c, o -> { o.i32(a); o.i32(b); }, RET_I32);
    }

    static int callBoolI32I32RetI32(GmpfCall c, boolean a, int b, int d) throws RemoteException {
        return call(c, o -> { o.bool(a); o.i32(b); o.i32(d); }, RET_I32);
    }

    static boolean callI32x3RetBool(GmpfCall c, int a, int b, int d) throws RemoteException {
        return call(c, o -> { o.i32(a); o.i32(b); o.i32(d); }, RET_BOOL);
    }

    static boolean callI32BoolRetBool(GmpfCall c, int a, boolean b) throws RemoteException {
        return call(c, o -> { o.i32(a); o.bool(b); }, RET_BOOL);
    }

    static boolean callBoolI32RetBool(GmpfCall c, boolean a, int b) throws RemoteException {
        return call(c, o -> { o.bool(a); o.i32(b); }, RET_BOOL);
    }

    static int callI32BoolRetI32(GmpfCall c, int a, boolean b) throws RemoteException {
        return call(c, o -> { o.i32(a); o.bool(b); }, RET_I32);
    }

    static boolean callI32F32RetBool(GmpfCall c, int a, float f) throws RemoteException {
        return call(c, o -> { o.i32(a); o.f32(f); }, RET_BOOL);
    }

    static void callBool(GmpfCall c, boolean v) throws RemoteException { call(c, o -> o.bool(v), RET_VOID); }

    static boolean callBoolRetBool(GmpfCall c, boolean v) throws RemoteException { return call(c, o -> o.bool(v), RET_BOOL); }

    static int callBoolRetI32(GmpfCall c, boolean v) throws RemoteException { return call(c, o -> o.bool(v), RET_I32); }

    static int callI32x4RetI32(GmpfCall c, int a, int b, int d, int e) throws RemoteException {
        return call(c, o -> { o.i32(a); o.i32(b); o.i32(d); o.i32(e); }, RET_I32);
    }

    static boolean callRetBool(GmpfCall c) throws RemoteException { return call(c, null, RET_BOOL); }

    static int callRetI32(GmpfCall c) throws RemoteException { return call(c, null, RET_I32); }

    static int callRetU8(GmpfCall c) throws RemoteException { return call(c, null, RET_U8); }

    static byte callRetI8(GmpfCall c) throws RemoteException { return call(c, null, RET_I8); }

    // ---------------------------------------------------------------- the focus callback
    /** Receives (type, value) of IProjectorFocusCallback.focusEvent on a binder thread: only post. */
    interface FocusSink { void onFocusEvent(int type, String value); }

    private static volatile FocusSink sFocusSink;
    /** Strong static reference: the vendor keeps only a binder reference to our callback. */
    private static FocusCallback sCallback;

    static void setFocusSink(FocusSink s) {
        sFocusSink = s;
    }

    /** FOCUS 1 setFocusCallback(cb), the stock registration (ProjectorFocusManagerWrapper.initCallback). */
    static void callRegisterFocusCallback(GmpfCall c) throws RemoteException {
        final FocusCallback cb;
        synchronized (AidlCaller.class) {
            if (sCallback == null) sCallback = new FocusCallback();
            cb = sCallback;
        }
        call(c, o -> o.p.writeStrongBinder(cb), RET_VOID);
    }

    /**
     * Java xgimi.hardware.gmpf.IProjectorFocusCallback, answered as the stock $Stub (com.xgimi.middle.jar):
     * markVintfStability in the constructor (the vendor process may only call a VINTF binder), code 1
     * oneway focusEvent(int, String) with enforceInterface and enforceNoDataAvail, 0x00FFFFFF version 1,
     * 0x00FFFFFE the hash the guard accepted (that firmware's stub), everything else to Binder.onTransact.
     * Never calls the HAL, never blocks: the event goes to the sink, which posts it.
     */
    private static final class FocusCallback extends Binder implements IInterface {
        static final String DESCRIPTOR = PKG + "IProjectorFocusCallback";
        static final int FOCUS_EVENT = 1;
        static final int META_VERSION = 0x00FFFFFF;
        static final int META_HASH = 0x00FFFFFE;

        FocusCallback() {
            markVintfStability();
            attachInterface(this, DESCRIPTOR);
        }

        @Override public IBinder asBinder() { return this; }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == META_VERSION || code == META_HASH) {
                data.enforceInterface(DESCRIPTOR);
                reply.writeNoException();
                if (code == META_VERSION) reply.writeInt(VERSION); else reply.writeString(sAcceptedHash);
                return true;
            }
            if (code != FOCUS_EVENT) return super.onTransact(code, data, reply, flags);
            int type;
            String value;
            try {
                data.enforceInterface(DESCRIPTOR);
                type = data.readInt();
                value = data.readString();
                data.enforceNoDataAvail();
            } catch (Throwable t) {
                Log.w(TAG, "focusEvent parse: " + t);   // oneway: nothing to answer, the event is dropped
                return true;
            }
            FocusSink s = sFocusSink;
            if (s != null) {
                try { s.onFocusEvent(type, value); } catch (Throwable t) { Log.w(TAG, "sink: " + t); }
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- argument checks
    static int checkRange(String what, int v, int min, int max) {
        if (v < min || v > max) throw new IllegalArgumentException(what + " " + v + " not in " + min + ".." + max);
        return v;
    }
}
