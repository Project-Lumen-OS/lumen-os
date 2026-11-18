// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** goToBootCompleted arguments: 0 early (stock XRMService.onCreate), 1 after the boot animation. *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum BootStage {
    EARLY(0), AFTER_BOOTANIM(1);
    final int wire;
    BootStage(int v) { wire = v; }
}
