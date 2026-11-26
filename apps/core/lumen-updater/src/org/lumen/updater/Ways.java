// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

/** The one instance of each InstallWay (InstallWay.of). */
final class Ways {
    private Ways() {}

    static final InstallWay UPDATE_ENGINE = new ViaUpdateEngine();
    static final InstallWay RECOVERY = new ViaRecovery();
}
