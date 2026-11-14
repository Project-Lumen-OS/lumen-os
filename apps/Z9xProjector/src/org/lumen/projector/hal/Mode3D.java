// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** 3D (FS 3.6): enable3D 1 off, 2 side-by-side, 3 top-bottom; enable3DTo2D 2 / 3. *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum Mode3D {
    OFF(1), SIDE_BY_SIDE(2), TOP_BOTTOM(3);
    public final int wire;
    Mode3D(int v) { wire = v; }
}
