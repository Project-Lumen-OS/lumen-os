// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

/** The ordered checks before an install, by what publishes the facts they read. The first refusal wins. */
public enum InstallChecks {
    /** tools/ota/image/z9x_ota.sh (the Z9X, and the X50 family whose image keeps the Z9X base's gate) */
    Z9X_GATE("z9x_ota", Check.VBMETA, Check.PENDING, Check.THERMAL, Check.ENGINE),
    /** tools/devices/ota/image/lumen_ota.sh (the HB) */
    LUMEN_GATE("lumen_ota", Check.VBMETA, Check.STORAGE, Check.GATE_PROBE, Check.SLOT_STATE, Check.THERMAL, Check.ENGINE),
    /** the Play 6 recovery install (lumen_recovery.sh publishes the certificate state) */
    RECOVERY("recovery", Check.RECOVERY_CERT, Check.POWER, Check.THERMAL),
    /** an image property that names no known gate */
    UNKNOWN("", Check.UNKNOWN_GATE);

    final String id;
    final Check[] list;

    InstallChecks(String id, Check... list) {
        this.id = id;
        this.list = list;
    }

    /** The checks of a gate name (ro.lumen.ota.boot_gate: z9x_ota or lumen_ota); UNKNOWN for any other value. */
    static InstallChecks named(String id) {
        for (InstallChecks c : values()) {
            if (!c.id.isEmpty() && c.id.equals(id)) return c;
        }
        return UNKNOWN;
    }
}
