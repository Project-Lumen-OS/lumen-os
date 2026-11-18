// SPDX-License-Identifier: Apache-2.0
package org.lumen.fwcopy;

/** How a copy runs: DRY reads and hashes everything a real copy reads and reports every write as WOULD. */
public enum Mode {
    DRY,
    REAL
}
