// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

/**
 * The Play 6 (G0058) facts of the shared updater (apps/core/lumen-updater, UpdateTarget): the ro.lumen.*
 * properties, wire id play6, the play6 channel (no beta channel), the general install guide, a whole recovery
 * package on /cache installed by XGIMI's recovery after a restart (it trusts the Lumen certificate after the
 * one-time step, tools/play6/recovery_trust.py).
 */
final class Target {
    private Target() {}

    static final UpdateTarget SPEC = new UpdateTarget.Builder()
            .props("ro.lumen")
            .device("play6")
            .versions(VersionScheme.LEGACY)
            .channels(ChannelRule.FIXED)
            // a fixed release tag whose assets each Play 6 release replaces (never the Z9X "latest" release)
            .defaultManifest(UpdateTarget.DOWNLOADS + "play6/update-play6.json")
            .fallbackFile("update-play6.json")
            .guide(UpdateTarget.PROJECT_URL + "/blob/main/docs/install/")
            .dir(OtaDir.CACHE)
            .install(InstallKind.RECOVERY)
            .checks(InstallChecks.RECOVERY)
            .dsuHandover(false)
            .importBridgeData(true)
            .hideWithoutDevice(false)
            .build();
}
