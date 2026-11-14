// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** HDMI aspect ratio, platform values because mt5877 is GMPF2 (QS VERIFIED): 0 4:3, 1 16:9, 2 Auto, 4 Original.
 *
 * <p>The Z9X values of GmpfClient (each device app has its own value list of this name).
 */
public enum AspectRatio {
    R4_3(0), R16_9(1), AUTO(2), ORIGINAL(4);
    public final int wire;
    AspectRatio(int v) { wire = v; }
}
