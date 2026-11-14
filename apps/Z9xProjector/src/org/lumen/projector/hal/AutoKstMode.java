// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/**
 * newAutoKst modes used by stock (FS 2.1): 9 = settings button / panel tile ("misckey"),
 * 10 = retry from the "angle too large" notice of manual keystone, 11 = curtain re-fit
 * (event 120 dialog OK). Never 24 (tilt-shift only) or any factory mode.
 *
 * <p>The Z9X values of GmpfClient (each device app has its own value list of this name).
 */
public enum AutoKstMode {
    MISCKEY(9), RETRY_ANGLE(10), CURTAIN_REFIT(11);
    final int wire;
    AutoKstMode(int v) { wire = v; }
}
