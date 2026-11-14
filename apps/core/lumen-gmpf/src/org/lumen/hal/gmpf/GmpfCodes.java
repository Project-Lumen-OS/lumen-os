package org.lumen.hal.gmpf;

import java.util.EnumMap;

/**
 * One projector's codes of the shared calls: each operation maps to the transaction key of that HAL (a generated
 * HalCodes constant, or an allow-list Call), or is absent: the call is not on that projector and sends nothing.
 * Every row names the code of the operation's own stock method (tools/hal/check_codes.py).
 */
public final class GmpfCodes<K> {
    private final EnumMap<GmpfOp, K> keys = new EnumMap<>(GmpfOp.class);

    public GmpfCodes<K> put(GmpfOp op, K key) {
        if (key == null || keys.put(op, key) != null) throw new IllegalArgumentException("code row " + op);
        return this;
    }

    /** The key of op, null when this projector has no such call. */
    public K key(GmpfOp op) { return keys.get(op); }

    public boolean has(GmpfOp op) { return keys.containsKey(op); }
}
