// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.miracast.core;

/** Highest picture the sink offers. */
public enum WfdQuality {
    P1080_60(1080, 60), P1080_30(1080, 30), P720_60(720, 60);

    public final int maxHeight, maxFps;

    WfdQuality(int h, int f) {
        maxHeight = h;
        maxFps = f;
    }

    /** The next choice of the casting screen's quality row (1080p 60, 1080p 30, 720p, then 1080p 60 again). */
    public WfdQuality next() {
        return step(1);
    }

    /** The choice d steps away (LEFT on the row: -1), round. */
    public WfdQuality step(int d) {
        WfdQuality[] all = values();
        return all[Math.floorMod(ordinal() + d, all.length)];
    }

    public static WfdQuality parse(String s) {
        for (WfdQuality q : values()) {
            if (q.name().equalsIgnoreCase(s)) return q;
        }
        return P1080_60;
    }
}
