// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** Sound enhancement (QS: Harman = 2, DTS Virtual:X = 5; never 3/7/100). *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum SoundProcess {
    HARMAN(2), DTS_VIRTUAL_X(5);
    public final int wire;
    SoundProcess(int v) { wire = v; }
}
