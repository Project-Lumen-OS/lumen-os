package org.lumen.hal.gmpf;

import static org.lumen.hal.gmpf.GmpfOp.GET_CURRENT_ZOOM_LIMIT_STEP;
import static org.lumen.hal.gmpf.GmpfOp.SET_OPTICAL_ZOOM_LIMIT_STEP;

import android.os.RemoteException;

/**
 * The call layer under the shared gmpf clients: a device transport, the device's code table and its rules. A call
 * names its operation; key() gives that projector's code or throws the device's absent error, nothing sent.
 */
public abstract class GmpfCaller<K> {
    protected final GmpfWire<K> wire;
    private final GmpfCodes<K> codes;
    private final GmpfRules rules;

    protected GmpfCaller(GmpfWire<K> wire, GmpfCodes<K> codes, GmpfRules rules) {
        this.wire = wire;
        this.codes = codes;
        this.rules = rules;
    }

    /** The code of op on this projector; connects an interface that connects on first use. */
    protected final K key(GmpfOp op) throws RemoteException {
        K k = codes.key(op);
        if (k == null) throw rules.absent(op);
        wire.ready();
        return k;
    }

    protected final boolean has(GmpfOp op) { return codes.has(op); }

    /** The transport's name of op for messages; the stock method where the projector has no such call. */
    protected final String label(GmpfOp op) {
        K k = codes.key(op);
        return k == null ? op.method() : wire.name(k);
    }

    protected final GmpfRules rules() { return rules; }

    protected final void gate(GmpfGate gate, String what) throws RemoteException { rules.require(gate, what); }

    public boolean isConnected() { return wire.connected(); }

    /** Forgets the connection after a death notice (no vendor call). */
    public void dropConnection() { wire.drop(); }

    // ------------------------------------------------------------------ the optical zoom limit position
    // Its two calls are IGmpf2 on the Z9X and DISPLAY 190 / 189 next to the other zoom calls on the HB: whichever client
    // of a projector has them exposes these.

    /** GetCurrentZoomLimitStep() -> {ret, limit, digital}: 0 out of the limit zone, -1..-count inside it. */
    protected final int[] readZoomLimitStep() throws RemoteException {
        gate(GmpfGate.OPTICAL_ZOOM, "GetCurrentZoomLimitStep");
        int[] v = wire.i32Struct(key(GET_CURRENT_ZOOM_LIMIT_STEP), 2);
        ZoomGuard.noteLimitRead(v[1]);
        return v;
    }

    /**
     * SetOpticalZoomLimitStep(step), as stock ZoomUtils: the neighbour of a fresh limit read inside [-count, 0], at
     * optical index 0, inside a prepared session; ZoomGuard.checkLimitStep refuses (nothing sent) otherwise.
     */
    protected final int writeZoomLimitStep(int step) throws RemoteException {
        gate(GmpfGate.OPTICAL_ZOOM, "SetOpticalZoomLimitStep");
        K k = key(SET_OPTICAL_ZOOM_LIMIT_STEP);
        ZoomGuard.checkLimitStep(label(SET_OPTICAL_ZOOM_LIMIT_STEP), step);
        return wire.i32RetI32(k, step);
    }
}
