// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** manualFocus arguments allowed by the project rules: {17, 0, 1, 2} only. *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum ManualFocus {
    /** Overlay opened (stock FocusAdjust sends 17 on open). */
    ENTER(17),
    /** Continuous move, stock "RIGHT" (stock DPAD_RIGHT down). */
    STOCK_RIGHT(0),
    /** Continuous move, stock "LEFT" (stock DPAD_LEFT down). */
    STOCK_LEFT(1),
    /** Stop the motor. */
    STOP(2);
    final byte wire;
    ManualFocus(int v) { wire = (byte) v; }
}
