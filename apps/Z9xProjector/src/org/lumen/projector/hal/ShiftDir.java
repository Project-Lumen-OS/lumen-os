// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** Picture shift direction for 141 translationKst (FS 2.5): 1 up, 2 down, 3 left, 4 right. *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum ShiftDir {
    UP(1), DOWN(2), LEFT(3), RIGHT(4);
    final int wire;
    ShiftDir(int v) { wire = v; }
}
