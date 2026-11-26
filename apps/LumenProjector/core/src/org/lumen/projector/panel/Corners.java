// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

/**
 * Four keystone corners (TL, TR, BR, BL) in the panel space of the 4-point keystone, PANEL_W x PANEL_H: the value the
 * corner card (Play6KeystonePanel) moves. The family adapter reads and writes it through the card's KeystoneIo and
 * may extend it with its wire form (play6-hidl hal/Play6KstPoint); copy keeps that kind. set refuses a corner outside
 * the panel space.
 */
public class Corners {
    public static final int PANEL_W = 1920;
    public static final int PANEL_H = 1080;
    public static final int TL = 0, TR = 1, BR = 2, BL = 3;

    private final int[] x = new int[4];
    private final int[] y = new int[4];

    public int x(int corner) { return x[corner]; }

    public int y(int corner) { return y[corner]; }

    public Corners set(int corner, int px, int py) {
        if (corner < 0 || corner > 3) throw new IllegalArgumentException("corner " + corner);
        check("x", px, PANEL_W);
        check("y", py, PANEL_H);
        x[corner] = px;
        y[corner] = py;
        return this;
    }

    public Corners copy() {
        Corners p = blank();
        System.arraycopy(x, 0, p.x, 0, 4);
        System.arraycopy(y, 0, p.y, 0, 4);
        return p;
    }

    /** The raw values the HAL reported for this set, or "-" for a set built locally. */
    public String raw() { return "-"; }

    /** An empty set of the same kind as this one. */
    protected Corners blank() { return new Corners(); }

    private static void check(String what, int v, int max) {
        if (v < 0 || v > max) throw new IllegalArgumentException(what + " " + v + " not in 0.." + max);
    }
}
