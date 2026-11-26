// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

/** One check before an install (Preflight.run has the code of each). */
public enum Check {
    /** the other slot's vbmeta has verification off (the slot update_engine writes can start) */
    VBMETA,
    /** no update waits for its first start (sys.z9x.ota pending) */
    PENDING,
    /** Virtual A/B, compressed as this model's payloads, and this build's own partition list (lumen_ota gate) */
    STORAGE,
    /** a fresh answer of the lumen_ota boot gate */
    GATE_PROBE,
    /** the lumen_ota gate's state of the running slot */
    SLOT_STATE,
    /** the recovery trusts the Lumen certificate (Play 6) */
    RECOVERY_CERT,
    /** mains power or a well charged battery (Play 6) */
    POWER,
    /** not too hot */
    THERMAL,
    /** update_engine answers and no merge cleans up */
    ENGINE,
    /** the image names a boot gate this updater does not know: always refused */
    UNKNOWN_GATE
}
