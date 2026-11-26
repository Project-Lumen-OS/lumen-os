// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

/** The Z9X menu's tokens (Z9xMenuRules) for the signal panel's picture mode and motion smoothing rows. */
public final class Z9xSignalTokens implements SignalMenuTokens {
    @Override public String pictureMode(int wire) { return Z9xMenuRules.pmToken(wire); }

    @Override public String memc(int wire) {
        return wire >= 0 && wire < Z9xMenuRules.MEMC.length ? Z9xMenuRules.MEMC[wire] : null;
    }
}
