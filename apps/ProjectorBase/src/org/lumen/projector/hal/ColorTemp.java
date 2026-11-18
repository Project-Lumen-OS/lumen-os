// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** Colour temperature = DLP lumens mode; Z9X offers ONLY 16 and 17 (7/8/15 are max/min/test, FS 3.4).
 *
 * <p>The Z9X values of GmpfClient (each device app has its own value list of this name).
 */
public enum ColorTemp {
    D65(16), COLOR_TEMP_1(17);
    public final int wire;
    ColorTemp(int v) { wire = v; }
}
