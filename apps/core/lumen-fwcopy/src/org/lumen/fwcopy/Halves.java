// SPDX-License-Identifier: Apache-2.0
package org.lumen.fwcopy;

import java.io.IOException;

/** The platform's own boot record of the two halves (the boot HAL and the A/B block in misc). */
public interface Halves {
    /**
     * Mark half {@code half} (0 = a, 1 = b) unbootable the way the platform does (boot HAL setSlotAsUnbootable) and read
     * the record back: true only when it now says that half is not tried at boot.
     */
    boolean markUnbootable(int half) throws IOException;
}
