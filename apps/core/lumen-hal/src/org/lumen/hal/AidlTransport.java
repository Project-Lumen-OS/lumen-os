package org.lumen.hal;

import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

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

    static final int META_VERSION = 0x00FFFFFF, META_HASH = 0x00FFFFFE;

    /** Binder and guard state of one interface, for the whole process (as the old static enum fields). */
    private static final class State {
        IBinder binder;
        boolean verified, refused;
    }

    private static final Map<String, State> STATES = new HashMap<>();
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

    /** The interface is reachable and through the guard (only the meta calls are sent). */
    protected final void require(Service s) throws RemoteException {
        need(s);
    }

    /** The result as the call log shows it (null = done; arrays element by element). */
    protected String show(Object v) {
        if (v == null) return "done";
        if (v instanceof float[]) return java.util.Arrays.toString((float[]) v);
        if (v instanceof int[]) return java.util.Arrays.toString((int[]) v);
        return String.valueOf(v);
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
            if (st.refused) throw new RemoteException(i.descriptor() + " closed by the version guard");
            if (st.binder == null || !st.binder.isBinderAlive()) {
                st.binder = null;
                st.verified = false;
                IBinder b;
                try {
                    b = ServiceManager.checkService(instance);
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
                    throw e;
                }
                String fw = SystemProperties.get(HashRule.FIRMWARE_PROP, "");
                if (v != version || !rule.accepted(h, fw)) {
                    st.refused = true;
                    st.binder = null;
                    Log.e(tag, "guard: " + i.descriptor() + " version " + v + " hash " + h + " on firmware " + fw
                            + ": closed, nothing sent");
                    throw new RemoteException(i.descriptor() + " version/hash mismatch");
                }
                st.verified = true;
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
            String args = argWords(data, start);
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
                throw new RemoteException(c.method() + " unimplemented");
            }
            try {
                reply.readException();
                T v = r.read(reply);
                logCall(c, args, show(v));
                return v;
            } catch (RuntimeException e) {
                logCall(c, args, "error " + e);
                throw new RemoteException("reply of " + c.method() + ": " + e);
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

    private static void drop(Service i, IBinder b) {
        synchronized (AidlTransport.class) {
            State st = state(i);
            if (st.binder == b) {
                st.binder = null;
                st.verified = false;
            }
        }
    }
}
