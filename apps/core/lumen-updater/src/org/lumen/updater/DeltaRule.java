// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import java.util.Collection;

/**
 * Which package of a verified manifest this unit downloads, and when a delta gives way to the full package.
 * <p>
 * A delta only changes the blocks that differ from its source build, so it installs only on a system that is byte for
 * byte that build. It is taken when the running build is exactly its source (the same build_id and, when the entry names
 * one, the same version_code), never for a USB stick install (the stick is the way back and holds the full package) and
 * never again once it failed on this build (its sha256 is refused). Everything else takes the full package.
 * <p>
 * A delta that fails (its sha256 after the download's retries, its signed metadata, a 404 of its file, update_engine
 * refusing it or ending with an error a full package may not have) is refused and the full package of the same
 * manifest becomes the offer ({@link Newest#deltaFailed}). Plain Java: host test DeltaTest.
 */
final class DeltaRule {
    private DeltaRule() {}

    /** The package to download for the running build {@code buildId} / {@code versionCode} (null: none). */
    static UpdateManifest.Pkg choose(UpdateManifest m, String buildId, int versionCode, boolean usb,
            Collection<String> refused) {
        if (!usb) {
            for (UpdateManifest.Pkg d : m.deltas()) {
                if (fits(d, buildId, versionCode) && !refused.contains(d.sha256)) return d;
            }
        }
        return m.full();
    }

    /** The running build is exactly the delta's source. */
    static boolean fits(UpdateManifest.Pkg d, String buildId, int versionCode) {
        return d.isDelta() && !buildId.isEmpty() && buildId.equals(d.fromBuildId)
                && (d.fromVersionCode == 0 || d.fromVersionCode == versionCode);
    }

    /**
     * An install of a delta ended with update_engine's {@code code}: true when the full package is tried instead. Not
     * for success, a cancel by the user, missing space (the full package needs more) or a downgrade (the same for both);
     * a negative code is ours (no answer from update_engine) and leaves the error as it is.
     */
    static boolean applyFailed(int code) {
        return code > 0 && code != Ota.ERR_UPDATED_BUT_NOT_ACTIVE && code != Ota.ERR_USER_CANCELED
                && code != Ota.ERR_NOT_ENOUGH_SPACE && code != Ota.ERR_TIMESTAMP;
    }
}
