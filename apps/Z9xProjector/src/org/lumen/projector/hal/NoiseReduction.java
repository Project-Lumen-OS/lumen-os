// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** Noise reduction 0 Off, 1 Low, 2 Medium, 3 High, 4 Auto (FS 3.5). *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum NoiseReduction {
    OFF(0), LOW(1), MEDIUM(2), HIGH(3), AUTO(4);
    public final int wire;
    NoiseReduction(int v) { wire = v; }
}
