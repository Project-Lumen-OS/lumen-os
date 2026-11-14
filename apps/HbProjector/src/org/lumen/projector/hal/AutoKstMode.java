// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/**
 * newAutoKst modes of the Z9X (FS 2.1): 9 misckey, 10 retry from the angle notice, 11 curtain re-fit; HB hb-t7:
 * 12 = the "Continue" of the stock curtain align hint window (AuxiliaryCurtainAlignDialog, 2026-10-30).
 *
 * <p>The HB values of GmpfClient (each device app has its own value list of this name).
 */
public enum AutoKstMode {
    MISCKEY(9), RETRY_ANGLE(10), CURTAIN_REFIT(11), CURTAIN_ALIGN(12);
    final int wire;
    AutoKstMode(int v) { wire = v; }
}
