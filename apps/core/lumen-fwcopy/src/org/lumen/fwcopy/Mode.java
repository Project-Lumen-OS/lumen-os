// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

/** How a copy runs: DRY reads and hashes everything a real copy reads and reports every write as WOULD. */
public enum Mode {
    DRY,
    REAL
}
