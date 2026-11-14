// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.utils;

import org.lumen.projector.hal.KstPoint;

/** Keystone corner checks shared by the Z9X and HB menus. */
public final class KstFrames {
    /** Panel px a corner may be off and still count as the full frame. */
    private static final int FULL_FRAME_TOLERANCE = 4;

    private KstFrames() {}

    /** 186 corners within a few panel px of the full frame. */
    public static boolean isFullFrame(KstPoint p) {
        if (p == null) return false;
        KstPoint f = KstPoint.fullFrame();
        for (int c = KstPoint.TL; c <= KstPoint.BL; c++) {
            if (Math.abs(p.x(c) - f.x(c)) > FULL_FRAME_TOLERANCE || Math.abs(p.y(c) - f.y(c)) > FULL_FRAME_TOLERANCE) return false;
        }
        return true;
    }
}
