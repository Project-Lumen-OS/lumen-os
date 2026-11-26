// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hal;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * lumen-hal: the one raw Binder path from a Lumen app to XGIMI's stable AIDL vendor services
 * "xgimi.hardware.gmpf.I*Manager/default" (format aidl). Transactions laid out as the stock Java proxies:
 * writeInterfaceToken + arguments -> transact -> readException + value. Taken from the two transports of
 * apps/X50EyeGuard and apps/X50Projector (Gmpf), which were the same code (2026-11-10).
 *
 * Allow list: an app subclasses this class once (its Gmpf) with its own enum Call implementing {@link Method}. That enum
 * is the app's allow list (= devices/<c>/research/allowed_<app>.tsv, checked by tools/devices/check_aidl_hal.py on the
 * sources and the dex) and the only type {@link #call} accepts (the type parameter), so nothing outside the list can be
 * sent. The subclass fixes every value a setter may carry; this class adds no call of its own beyond the two meta calls
 * of the guard. A Call with an argument range (Method.args, ArgSpec) is also refused here when its written words fall
 * outside that range (t20: the calls the owner unbanned for one app carry one each, stock analysis
 * unbanned_x50projector.tsv).
 *
 * Guard: before the first call on an interface getInterfaceVersion must be the app's version and getInterfaceHash a
 * hash its {@link HashRule} accepts on the running stock firmware (the base hash anywhere, an extra hash only on its
 * own firmware); a mismatch closes that interface for the process (a model whose hash differs, or a later firmware,
 * gets nothing). checkService only (never the blocking getService); calls on the main thread are refused. Every call gets a
 * paced INFO line under the app's tag (HalLog).
 *
 * Callbacks (ai-mode-stock): {@link #registerCallback} sends the stock setXxxCallback(cb) of a Call of the list with the
 * process's one callback object of a vendor callback interface (IGmTvCallback: the GmTvManager events of the stock AI
 * picture quality), once per vendor binder, so a vendor restart gets it again. {@link #sendCallback} sends it on every
 * call (the HB stock wrappers register again at each connect).
 *
 * Family rules (the HB, 2026-11-25): a family whose callers must tell a delivered failure from a lost one returns its own
 * {@link #replyError} (unimplemented, a bad reply, a refused interface), closes an interface whose guard call failed
 * after delivery ({@link #closesOnFailedGuard}), and links every binder to death ({@link #watchesDeath}): the first
 * death notice of a vendor generation drops every interface, opens the closed ones again (a restarted service runs the
 * guard again) and calls {@link #vendorDied}. Calls with stable parcelables use {@link #proxyCall}. The defaults keep
 * the plain path of the X50 family.
 */
public abstract class AidlTransport<C extends Enum<C> & AidlTransport.Method> {
    /** A vendor interface: the app's enum Iface implements it. */
    public interface Service {
        String descriptor();
    }

    /**
     * One transaction of the app's allow list: its enum Call implements it (iface null = the two meta calls).
     * args(): the argument range ({@link ArgSpec}, the allow list's "args:" column), or null for a call without one;
     * a call with a range is refused before the transact when its written argument words are outside it.
     */
    public interface Method {
        Service iface();
        int code();
        String method();
        default String args() { return null; }
    }

    protected interface Args { void write(Parcel p); }
    protected interface Ret<T> { T read(Parcel p); }
    /** Arguments that carry a binder object: the call log names it instead of reading words over the object. */
    private interface BinderArgs extends Args { }

    /** A vendor event (type, message) of a registered callback, on a binder thread: it may only post. */
    public interface EventSink { void onEvent(int type, String msg); }

    static final int META_VERSION = 0x00FFFFFF, META_HASH = 0x00FFFFFE;

    /** Binder and guard state of one interface, for the whole process (as the old static enum fields). */
    private static final class State {
        IBinder binder;
        boolean verified, refused;
        String hash = "";
        /** The callback descriptors registered on this binder (empty again once the binder is replaced). */
        final Set<String> callbacks = new HashSet<>();
    }

    private static final Map<String, State> STATES = new HashMap<>();
    /** The vendor generation: a death notice of an older one is stale. */
    private static int sGeneration;
    private static final Map<String, EventCallback> CALLBACKS = new HashMap<>();
    private static final HalLog LOG = new HalLog();

    private final String tag;
    private final int version;
    private final HashRule rule;
    private final C getVersion, getHash;

    protected AidlTransport(String tag, int version, HashRule rule, C getVersion, C getHash) {
        if (getVersion.iface() != null || getVersion.code() != META_VERSION || !"getInterfaceVersion".equals(getVersion.method())
                || getHash.iface() != null || getHash.code() != META_HASH || !"getInterfaceHash".equals(getHash.method())) {
            throw new IllegalArgumentException("the guard needs getInterfaceVersion 0x00FFFFFF and getInterfaceHash 0x00FFFFFE");
        }
        this.tag = tag;
        this.version = version;
        if (rule == null) throw new IllegalArgumentException("the guard needs a hash rule");
        this.rule = rule;
        this.getVersion = getVersion;
        this.getHash = getHash;
    }

    // ---------------------------------------------------------------- the subclass's entries
    /** One call of the allow list, off the main thread, through the guard. */
    protected final <T> T call(C c, Args a, Ret<T> r) throws RemoteException {
        if (Looper.getMainLooper().isCurrentThread()) throw new RemoteException("vendor call on the main thread refused");
        if (c.iface() == null) throw new IllegalStateException(c.method() + " is a meta call of the guard");
        IBinder b = need(c.iface());
        return deliver(b, c.iface(), c, a, r);
    }

    /** One call whose arguments and reply use the stock proxy layout of stable parcelables ({@link Out}, {@link In}). */
    protected final <T> T proxyCall(C c, ProxyArgs a, ProxyRet<T> r) throws RemoteException {
        return call(c, a == null ? null : p -> a.write(new Out(p)), p -> r.read(new In(p)));
    }

    /** The interface has a live binder that passed the guard (no vendor call). */
    protected final boolean connected(Service s) {
        synchronized (AidlTransport.class) {
            State st = state(s);
            return st.binder != null && st.verified && st.binder.isBinderAlive();
        }
    }

    /** Forget every binder (no vendor call): the guards run again at the next call, closed interfaces stay closed. */
    protected final void forgetAll() {
        synchronized (AidlTransport.class) {
            for (State st : STATES.values()) {
                st.binder = null;
                st.verified = false;
                st.callbacks.clear();
            }
        }
    }

    /** The error of a call the vendor received but did not answer as asked (unimplemented, bad reply, guard). */
    protected RemoteException replyError(String msg) {
        return new RemoteException(msg);
    }

    /** True: a guard call that failed this way closes the interface for the process (until a vendor death). */
    protected boolean closesOnFailedGuard(RemoteException e) {
        return false;
    }

    /** True: every new binder is linked to death (see the class comment). */
    protected boolean watchesDeath() {
        return false;
    }

    /** The first death notice of a vendor generation, after every interface was dropped (binder thread: only post). */
    protected void vendorDied() {
    }

    /** The interface is reachable and through the guard (only the meta calls are sent). */
    protected final void require(Service s) throws RemoteException {
        need(s);
    }

    /** The getInterfaceHash answer the guard accepted for {@code s} (the interface is reached first, as require). */
    protected final String guardedHash(Service s) throws RemoteException {
        need(s);
        synchronized (AidlTransport.class) {
            return state(s).hash;
        }
    }

    /**
     * Call c with one argument, the process's one callback object of the vendor interface descriptor (the stock
     * setXxxCallback(cb)), whose code 1 event(int, String) goes to sink. Sent once per vendor binder: true when sent
     * now, false when this binder already has it (only the sink moves). Off the main thread, through the guard.
     */
    protected final boolean registerCallback(C c, String descriptor, EventSink sink) throws RemoteException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new RemoteException("callback registration on the main thread refused");
        IBinder b = need(c.iface());
        final EventCallback cb;
        synchronized (AidlTransport.class) {
            EventCallback x = callback(c, descriptor, sink);
            if (state(c.iface()).callbacks.contains(descriptor)) return false;
            cb = x;
        }
        BinderArgs a = p -> p.writeStrongBinder(cb);
        deliver(b, c.iface(), c, a, p -> null);
        synchronized (AidlTransport.class) {
            State st = state(c.iface());
            if (st.binder == b) st.callbacks.add(descriptor);
        }
        return true;
    }

    /** As registerCallback, but sent on every call, also to a binder that has it already. */
    protected final void sendCallback(C c, String descriptor, EventSink sink) throws RemoteException {
        if (Looper.getMainLooper().isCurrentThread()) throw new RemoteException("callback registration on the main thread refused");
        IBinder b = need(c.iface());
        final EventCallback cb;
        synchronized (AidlTransport.class) {
            cb = callback(c, descriptor, sink);
        }
        BinderArgs a = p -> p.writeStrongBinder(cb);
        deliver(b, c.iface(), c, a, p -> null);
    }

    /** The process's one callback object of the descriptor, its sink, version and accepted hash set (lock held). */
    private EventCallback callback(C c, String descriptor, EventSink sink) {
        EventCallback x = CALLBACKS.get(descriptor);
        if (x == null) {
            x = new EventCallback(tag, descriptor);
            CALLBACKS.put(descriptor, x);
        }
        x.sink = sink;
        x.version = version;
        x.hash = state(c.iface()).hash;
        return x;
    }

    /** The result as the call log shows it (null = done; arrays element by element). */
    protected String show(Object v) {
        if (v == null) return "done";
        if (v instanceof float[]) return java.util.Arrays.toString((float[]) v);
        if (v instanceof int[]) return java.util.Arrays.toString((int[]) v);
        return String.valueOf(v);
    }

    // ---------------------------------------------------------------- the stock proxy layout of stable parcelables
    /*
     * For a subclass whose calls carry stable parcelables (the HB, from its AidlCaller, 2026-11-25): byte = writeByte
     * (one int32), boolean = writeBoolean (int32 0 / 1), in parcelable = int32 1 + the framed fields, out parcelable
     * after the return value. Every parse error is an IllegalStateException, the reply error of proxyCall. Only
     * proxyCall makes Out and In, so the subclass never holds a Parcel.
     */
    /** Writes the arguments after the interface token. */
    public interface ProxyArgs { void write(Out o); }
    /** Reads the reply after readException. */
    public interface ProxyRet<T> { T read(In r); }
    /** One stable parcelable's fields, inside its size frame. */
    public interface Writer { void write(Out o); }
    public interface Reader<T> { T read(In r); }
    public interface ElementWriter { void write(Out o, int index); }
    public interface ElementReader { void read(In r, int index); }

    /** The request after writeInterfaceToken. */
    public static final class Out {
        private final Parcel p;
        private Out(Parcel p) { this.p = p; }
        public void i32(int v) { p.writeInt(v); }
        public void bool(boolean v) { p.writeBoolean(v); }
        public void i8(byte v) { p.writeByte(v); }
        public void f32(float v) { p.writeFloat(v); }
        public void str(String v) { p.writeString(v); }
        /** in byte[]: writeByteArray (length, then the bytes). */
        public void bytes(byte[] v) { p.writeByteArray(v); }
        /** out byte[]: only the length the reply must fill (never null, never negative). */
        public void outBytes(int length) {
            if (length < 0) throw new IllegalArgumentException("out array length " + length);
            p.writeInt(length);
        }
        /** in parcelable: writeTypedObject(x, 0), i.e. int32 1, then the framed parcelable. */
        public void object(Writer w) {
            p.writeInt(1);
            framed(w);
        }
        /** writeTypedArray of n framed parcelables: int32 n, then per element int32 1 and the element. */
        public void typedArray(int n, ElementWriter w) {
            p.writeInt(n);
            for (int k = 0; k < n; k++) {
                final int idx = k;
                p.writeInt(1);
                framed(o -> w.write(o, idx));
            }
        }
        /** Stable parcelable frame: int32 size (patched after the fields), the fields. */
        public void framed(Writer w) {
            int start = p.dataPosition();
            p.writeInt(0);
            w.write(this);
            int end = p.dataPosition();
            p.setDataPosition(start);
            p.writeInt(end - start);
            p.setDataPosition(end);
        }
    }

    /** The reply after readException. Every parse error is an IllegalStateException. */
    public static final class In {
        private final Parcel p;
        private In(Parcel p) { this.p = p; }
        public int i32() { need(4); return p.readInt(); }
        public boolean bool() { need(4); return p.readBoolean(); }
        public byte i8() { need(4); return p.readByte(); }
        public float f32() { need(4); return p.readFloat(); }
        public String str() { return p.readString(); }
        /** out byte[]: readByteArray, the length must equal the array's. */
        public void bytes(byte[] dst) {
            int pos = p.dataPosition();
            need(4);
            int n = p.readInt();
            if (n != dst.length) throw new IllegalStateException("byte array of " + n + ", expected " + dst.length);
            p.setDataPosition(pos);
            p.readByteArray(dst);
        }
        /** createIntArray: int32 n (negative = null: fails, more than max fails), then n int32. */
        public int[] ints(int max) {
            int n = i32();
            if (n < 0 || n > max) throw new IllegalStateException("int array of " + n + " (at most " + max + ")");
            int[] a = new int[n];
            for (int k = 0; k < n; k++) a[k] = i32();
            return a;
        }
        /** Returned parcelable (readTypedObject): int32 non-zero, then the framed parcelable. Zero (null) fails. */
        public <T> T object(Reader<T> r) {
            if (i32() == 0) throw new IllegalStateException("null parcelable in the reply");
            return framed(r);
        }
        /** Out parcelable after the return value: int32 non-zero, then readFromParcel. Zero fails. */
        public <T> T out(Reader<T> r) {
            return object(r);
        }
        /** createTypedArray: int32 n (negative = null: fails), per element int32 present + framed element. */
        public int typedArray(int max, ElementReader r) {
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
        public <T> T framed(Reader<T> r) {
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

    // ---------------------------------------------------------------- transport
    private static State state(Service s) {
        State st = STATES.get(s.descriptor());
        if (st == null) {
            st = new State();
            STATES.put(s.descriptor(), st);
        }
        return st;
    }

    private IBinder need(Service i) throws RemoteException {
        synchronized (AidlTransport.class) {
            State st = state(i);
            String instance = i.descriptor() + "/default";
            if (st.refused) throw replyError(i.descriptor() + " closed by the version guard");
            if (st.binder == null || !st.binder.isBinderAlive()) {
                st.binder = null;
                st.verified = false;
                st.callbacks.clear();
                IBinder b;
                try {
                    b = ServiceManager.checkService(instance);
                    if (b != null && watchesDeath()) b.linkToDeath(new Death(this, sGeneration), 0);
                } catch (Throwable t) {
                    throw new RemoteException(instance + ": " + t);
                }
                if (b == null) throw new RemoteException(instance + " not available");
                st.binder = b;
            }
            if (!st.verified) {
                int v;
                String h;
                try {
                    v = deliver(st.binder, i, getVersion, null, Parcel::readInt);
                    h = deliver(st.binder, i, getHash, null, Parcel::readString);
                } catch (RemoteException e) {
                    st.binder = null;
                    if (closesOnFailedGuard(e)) st.refused = true;
                    throw e;
                }
                String fw = SystemProperties.get(HashRule.FIRMWARE_PROP, "");
                if (v != version || !rule.accepted(h, fw)) {
                    st.refused = true;
                    st.binder = null;
                    Log.e(tag, "guard: " + i.descriptor() + " version " + v + " hash " + h + " on firmware " + fw
                            + ": closed, nothing sent");
                    throw replyError(i.descriptor() + " version/hash mismatch");
                }
                st.verified = true;
                st.hash = h;
            }
            return st.binder;
        }
    }

    private <T> T deliver(IBinder b, Service i, C c, Args a, Ret<T> r) throws RemoteException {
        if (c.iface() != null && c.iface() != i) throw new IllegalStateException(c.method() + " is not on " + i.descriptor());
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(i.descriptor());
            int start = data.dataPosition();
            if (a != null) a.write(data);
            String args = a instanceof BinderArgs ? "(binder)" : argWords(data, start);
            String spec = c.args();
            if (spec != null && !ArgSpec.matches(spec, words(data, start))) {
                logCall(c, args, "refused: outside " + spec);
                throw new IllegalArgumentException(c.method() + " " + args + " is outside its allowed arguments " + spec);
            }
            boolean done;
            try {
                done = b.transact(c.code(), data, reply, 0);
            } catch (RuntimeException e) {
                drop(i, b);
                logCall(c, args, "error " + e);
                throw new RemoteException(c.method() + ": " + e);
            } catch (RemoteException e) {
                drop(i, b);
                logCall(c, args, "error " + e);
                throw e;
            }
            if (!done) {
                logCall(c, args, "unimplemented");
                throw replyError(c.method() + " unimplemented");
            }
            try {
                reply.readException();
                T v = r.read(reply);
                logCall(c, args, show(v));
                return v;
            } catch (RuntimeException e) {
                logCall(c, args, "error " + e);
                throw replyError("reply of " + c.method() + ": " + e);
            }
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    // ---------------------------------------------------------------- the INFO line of every call (HalLog paces it)
    private void logCall(C c, String args, String result) {
        String line = LOG.line(c.method(), args, result, SystemClock.elapsedRealtime());
        if (line != null) Log.i(tag, line);
    }

    /** The argument words of a call (written after the interface token), at most HalLog.MAX_ARGS of them. */
    private static String argWords(Parcel p, int start) {
        int end = p.dataPosition();
        if ((end - start) % 4 != 0 || (end - start) / 4 > HalLog.MAX_ARGS) return HalLog.args(null);
        int[] w = new int[(end - start) / 4];
        p.setDataPosition(start);
        for (int k = 0; k < w.length; k++) w[k] = p.readInt();
        p.setDataPosition(end);
        return HalLog.args(w);
    }

    /** Every argument word written after the interface token (null when the bytes are not whole int32 words). */
    private static int[] words(Parcel p, int start) {
        int end = p.dataPosition();
        if ((end - start) % 4 != 0) return null;
        int[] w = new int[(end - start) / 4];
        p.setDataPosition(start);
        for (int k = 0; k < w.length; k++) w[k] = p.readInt();
        p.setDataPosition(end);
        return w;
    }

    /** The vendor's death notice of a linked binder: only the first one of a generation acts. */
    private static final class Death implements IBinder.DeathRecipient {
        private final AidlTransport<?> owner;
        private final int generation;
        Death(AidlTransport<?> owner, int generation) {
            this.owner = owner;
            this.generation = generation;
        }
        @Override public void binderDied() { owner.died(generation); }
    }

    private void died(int generation) {
        synchronized (AidlTransport.class) {
            if (generation != sGeneration) return;
            sGeneration++;
            for (State st : STATES.values()) {
                st.binder = null;
                st.verified = false;
                st.refused = false;
                st.callbacks.clear();
            }
        }
        Log.w(tag, "vendor service died: every interface dropped");
        try { vendorDied(); } catch (Throwable t) { Log.w(tag, "death listener: " + t); }
    }

    private static void drop(Service i, IBinder b) {
        synchronized (AidlTransport.class) {
            State st = state(i);
            if (st.binder == b) {
                st.binder = null;
                st.verified = false;
                st.callbacks.clear();
            }
        }
    }

    /**
     * The process's one Java object of a vendor callback interface, answered as the stock $Stub (com.xgimi.middle.jar,
     * the same shape as the HB AidlCaller callbacks): markVintfStability in the constructor (the vendor process may only
     * call a VINTF binder), code 1 event(int, String) with enforceInterface and enforceNoDataAvail, 0x00FFFFFF the app's
     * version, 0x00FFFFFE the hash the guard accepted, everything else to Binder.onTransact. A two-way event gets the
     * empty success reply. Never calls the vendor, never blocks: the event goes to the sink, which posts it.
     */
    private static final class EventCallback extends Binder implements IInterface {
        static final int EVENT = 1;
        private final String tag;
        private final String descriptor;
        volatile EventSink sink;
        volatile int version;
        volatile String hash = "";

        EventCallback(String tag, String descriptor) {
            this.tag = tag;
            this.descriptor = descriptor;
            markVintfStability();
            attachInterface(this, descriptor);
        }

        @Override public IBinder asBinder() { return this; }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == META_VERSION || code == META_HASH) {
                data.enforceInterface(descriptor);
                reply.writeNoException();
                if (code == META_VERSION) reply.writeInt(version); else reply.writeString(hash);
                return true;
            }
            if (code != EVENT) return super.onTransact(code, data, reply, flags);
            int type;
            String msg;
            try {
                data.enforceInterface(descriptor);
                type = data.readInt();
                msg = data.readString();
                data.enforceNoDataAvail();
            } catch (Throwable t) {
                Log.w(tag, descriptor + " event parse: " + t);   // the event is dropped
                return true;
            }
            if ((flags & FLAG_ONEWAY) == 0 && reply != null) reply.writeNoException();
            EventSink s = sink;
            if (s != null) {
                try { s.onEvent(type, msg); } catch (Throwable t) { Log.w(tag, descriptor + " sink: " + t); }
            }
            return true;
        }
    }
}
