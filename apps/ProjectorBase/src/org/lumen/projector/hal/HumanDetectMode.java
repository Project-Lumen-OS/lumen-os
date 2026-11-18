// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** Eye protection mode (FS 5.1): 0 follow, 1 safe. The stock wrapper rejects >= 2. *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum HumanDetectMode {
    FOLLOW(0), SAFE(1);
    public final int wire;
    HumanDetectMode(int v) { wire = v; }
}
