// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/**
 * HDMI aspect ratio: 0 4:3, 1 16:9, 2 Auto, 4 Original; 5 21:9 and 6 32:9 only from the game bar (stock HB xhplayer
 * GmDisplayManager.getVideoRatioFromCommonVal on a GMPF2 platform: common 6 / 7 -> 5 / 6, stock analysis).
 *
 * <p>The HB values of GmpfClient (each device app has its own value list of this name).
 */
public enum AspectRatio {
    R4_3(0), R16_9(1), AUTO(2), ORIGINAL(4), R21_9(5), R32_9(6);
    public final int wire;
    AspectRatio(int v) { wire = v; }
}
