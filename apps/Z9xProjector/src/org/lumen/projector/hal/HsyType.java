// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** HSY colour correction: what one 7-colour array holds (stock ColorCorrection: 0 hue, 1 saturation, 2 lightness). *
 * <p>Values of Gmpf2Client (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum HsyType {
    HUE(0), SATURATION(1), LIGHTNESS(2);
    final int wire;
    HsyType(int v) { wire = v; }
}
