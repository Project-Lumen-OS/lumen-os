// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/**
 * HDMI game mode option (DS, gamemode_version=3): 0 basic, 1 basic+HFR, 2 top speed+HFR,
 * 3 top speed. Stock picks 1/2 only when support_hfr and the current option is already 1 or 2.
 *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum GameModeOption {
    BASIC(0), BASIC_HFR(1), TOP_SPEED_HFR(2), TOP_SPEED(3);
    public final int wire;
    GameModeOption(int v) { wire = v; }
    public boolean isTopSpeed() { return this == TOP_SPEED || this == TOP_SPEED_HFR; }
    public static GameModeOption fromWire(int v) {
        for (GameModeOption m : values()) if (m.wire == v) return m;
        return null;
    }
}
