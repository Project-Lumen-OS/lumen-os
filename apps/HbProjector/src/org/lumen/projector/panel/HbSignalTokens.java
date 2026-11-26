// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import org.lumen.projector.hal.PictureMode;

/**
 * The HB menu's tokens (HbMenuGates, as HbMenuFeatures maps its read) for the signal panel's picture mode and motion
 * smoothing rows. A user picture mode has no fixed name, so its row stays hidden.
 */
public final class HbSignalTokens implements SignalMenuTokens {
    @Override public String pictureMode(int wire) {
        PictureMode pm = PictureMode.fromWire(wire);
        return pm == null ? null : HbMenuGates.PICTURE_MODE.token(pm.wire);
    }

    @Override public String memc(int wire) { return HbMenuGates.MEMC.token(wire); }
}
