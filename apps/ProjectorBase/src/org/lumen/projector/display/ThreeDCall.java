// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.display;

import org.lumen.menu.ThreeDMode;
import org.lumen.projector.hal.Mode3D;

/**
 * The stock call of each 3D mode (PictureRepository.setImage3D): enable3D(1 | 2 | 3) for Off, Side by side, Top and
 * bottom, enable3DTo2D(2 | 3) for the two "as 2D" modes. Plain Java, so the host test pins it (test/threed).
 */
public final class ThreeDCall {
    private ThreeDCall() {}

    /** The enable3D argument of {@code m}, null for an "as 2D" mode or a mode the Z9X stock does not have. */
    public static Mode3D enable3D(ThreeDMode m) {
        if (m == null || m.to2d) return null;
        for (Mode3D x : Mode3D.values()) if (x.wire == m.format) return x;
        return null;
    }

    /** For an "as 2D" mode: true = enable3DTo2D(3) top and bottom, false = (2) side by side. Null otherwise. */
    public static Boolean to2dTopBottom(ThreeDMode m) {
        if (m == null || !m.to2d) return null;
        return m.format == Mode3D.TOP_BOTTOM.wire;
    }
}
