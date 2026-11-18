// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.utils;

/** Text helpers of the menu. */
public final class Texts {
    private Texts() {}

    /**
     * Two sentences as one text: a space between them, none after a full-width stop (Chinese and Japanese write
     * sentences without a space). Null or empty parts are left out.
     */
    public static String sentences(CharSequence a, CharSequence b) {
        if (a == null || a.length() == 0) return b == null ? null : b.toString();
        if (b == null || b.length() == 0) return a.toString();
        char last = a.charAt(a.length() - 1);
        boolean wide = last == '\u3002' || last == '\uFF01' || last == '\uFF1F';
        return a + (wide ? "" : " ") + b;
    }
}
