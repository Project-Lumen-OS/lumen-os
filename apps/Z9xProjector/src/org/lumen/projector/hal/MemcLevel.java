// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** MEMC (motion compensation) 0 Off, 1 Low, 2 Medium, 3 High (FS 3.4). *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum MemcLevel {
    OFF(0), LOW(1), MEDIUM(2), HIGH(3);
    public final int wire;
    MemcLevel(int v) { wire = v; }
}
