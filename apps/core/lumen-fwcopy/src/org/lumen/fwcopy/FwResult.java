// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** How a copy ended. The other half is usable for Lumen OS only after {@link #verified}. */
public final class FwResult {
    /** the plan's copies are all in the other half and read back equal (or there was nothing to copy) */
    public final boolean verified;
    /** where it stopped (plan, consent, unbootable, source, io, verify), null when verified */
    public final String step, reason;
    /** a byte of the other half was written (it stays unbootable after a failure) */
    public final boolean written;
    /** a real run (false: the dry run, nothing written) */
    public final boolean real;
    /** an earlier copy had not finished (journal) */
    public final boolean resumed;
    public final List<String> copied;

    private FwResult(boolean verified, String step, String reason, boolean written, boolean real, boolean resumed, List<String> copied) {
        this.verified = verified; this.step = step; this.reason = reason; this.written = written; this.real = real;
        this.resumed = resumed; this.copied = Collections.unmodifiableList(new ArrayList<>(copied));
    }

    static FwResult done(boolean real, boolean resumed, List<String> copied) {
        return new FwResult(true, null, null, real && !copied.isEmpty(), real, resumed, copied);
    }

    static FwResult stop(String step, String reason, boolean written, boolean real, boolean resumed) {
        return new FwResult(false, step, reason, written, real, resumed, Collections.emptyList());
    }

    @Override public String toString() {
        return verified ? (real ? "verified" : "dry run") + ": " + (copied.isEmpty() ? "nothing to copy" : String.join(", ", copied))
                : "stopped at " + step + ": " + reason + (written ? " (the other half was written and stays unbootable)" : " (nothing was written)");
    }
}
