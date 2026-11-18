// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** Colour space (FS 3.5): 4 Native, 1 BT.2020, 7 Adobe RGB, 2 DCI-P3, 3 Rec.709. *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum ColorSpace {
    NATIVE(4), BT2020(1), ADOBE_RGB(7), DCI_P3(2), REC709(3);
    public final int wire;
    ColorSpace(int v) { wire = v; }
}
