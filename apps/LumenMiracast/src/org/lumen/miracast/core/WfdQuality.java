// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/** Highest picture the sink offers. */
public enum WfdQuality {
    P1080_60(1080, 60), P1080_30(1080, 30), P720_60(720, 60);

    public final int maxHeight, maxFps;

    WfdQuality(int h, int f) {
        maxHeight = h;
        maxFps = f;
    }

    public static WfdQuality parse(String s) {
        for (WfdQuality q : values()) {
            if (q.name().equalsIgnoreCase(s)) return q;
        }
        return P1080_60;
    }
}
