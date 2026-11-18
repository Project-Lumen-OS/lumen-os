// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** Lumen OS 1.0.4: HDMI colour range of the current HDMI input (stock SignalEnum): 0 auto, 1 full, 2 limited. *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum HdmiRange {
    AUTO(0), FULL(1), LIMITED(2);
    public final int wire;
    HdmiRange(int v) { wire = v; }
}
