// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.utils;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuState;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Value helpers shared by the device adapters of the menu. */
public final class MenuValues {
    private MenuValues() {}

    /** "on" or "off" as a boolean; any other token is an error. */
    public static boolean onOff(String v) {
        if ("on".equals(v)) return true;
        if ("off".equals(v)) return false;
        throw new IllegalArgumentException("not on/off: " + v);
    }

    /** Index of {@code token} in {@code tokens}, or -1. */
    public static int indexOf(String[] tokens, String token) {
        for (int i = 0; i < tokens.length; i++) if (tokens[i].equals(token)) return i;
        return -1;
    }

    /** The constant of {@code values} whose lower-case name is {@code token}, or null. */
    public static <E extends Enum<E>> E ofToken(E[] values, String token) {
        for (E e : values) if (e.name().toLowerCase(Locale.ROOT).equals(token)) return e;
        return null;
    }

    /** {@code base}, or {@code base_i} when another option already took it; the token is added to {@code seen}. */
    public static String uniqueToken(String base, int i, Set<String> seen) {
        String t = base;
        if (seen.contains(t)) t = t + "_" + i;
        seen.add(t);
        return t;
    }

    /** Runs one read; a failure leaves only that value out, never an invented one. */
    public static void read(MenuState.Builder b, StateRead r) {
        try {
            r.into(b);
        } catch (Exception e) {
            // left out: the row shows no value
        }
    }

    /** Copies the values of {@code from} into {@code to}. */
    public static void copyValues(MenuState from, MenuState.Builder to) {
        for (Map.Entry<String, String> e : from.asMap().entrySet()) to.put(FeatureId.of(e.getKey()), e.getValue());
    }
}
