// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** "Ultra 120 Hz" output timing (DS VERIFIED stock newsettings): 4 = on (1080p120), 6 = off (4K60). *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum OutputTiming {
    ULTRA_120_ON(4), ULTRA_120_OFF(6);
    final int wire;
    OutputTiming(int v) { wire = v; }
}
