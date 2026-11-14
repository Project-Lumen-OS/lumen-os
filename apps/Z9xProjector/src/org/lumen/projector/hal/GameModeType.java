// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** Game mode type (DS / QS gmuiapi DisplayManager.setGameMode): 0 manual, 1 auto. *
 * <p>Values of Gmpf2Client (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum GameModeType {
    MANUAL(0), AUTO(1);
    final int wire;
    GameModeType(int v) { wire = v; }
}
