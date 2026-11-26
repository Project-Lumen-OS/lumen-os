// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

/** What one check found (Newest.check, Checker.checkUsb). Plain Java. */
public final class CheckResult {
    public final CheckOutcome outcome;
    public final UpdateManifest manifest;
    public final String res, arg;
    /** The saved offer stayed: the manifest read was older than it (Newest.accept). */
    public final boolean kept;

    CheckResult(CheckOutcome o, UpdateManifest m, String res, String arg) { this(o, m, res, arg, false); }

    CheckResult(CheckOutcome o, UpdateManifest m, String res, String arg, boolean kept) {
        outcome = o;
        manifest = m;
        this.res = res;
        this.arg = arg;
        this.kept = kept;
    }
}
