// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** Projector put mode (FS 2.9 VERIFIED ProjectionRepository): 0 desk front .. 3 ceiling rear. *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum PutMode {
    DESK_FRONT(0), CEILING_FRONT(1), DESK_REAR(2), CEILING_REAR(3);
    public final int wire;
    PutMode(int v) { wire = v; }
    public static PutMode fromWire(int v) {
        for (PutMode m : values()) if (m.wire == v) return m;
        return null;
    }
}
