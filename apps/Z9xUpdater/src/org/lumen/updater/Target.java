// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

/**
 * The Z9X (G0082) facts of the shared updater (apps/core/lumen-updater, UpdateTarget): the ro.z9x.* properties,
 * wire id z9x, the published no-Google edition on the channel tag z9x with its beta file beside it, the Z9X install
 * guide, update_engine into the other slot behind the Z9X boot gate (tools/ota/image/z9x_ota.sh).
 */
final class Target {
    private Target() {}

    static final String CHANNEL = UpdateTarget.DOWNLOADS + "z9x/";

    static final UpdateTarget SPEC = new UpdateTarget.Builder()
            .props("ro.z9x")
            .device("z9x")
            .versions(VersionScheme.LEGACY)
            .channels(ChannelRule.FIXED)
            // used only when ro.z9x.ota.manifest_url is missing or not https (every Lumen image sets it): the
            // published edition's manifest on the fixed pre-release tag z9x, as the images from 1.0.8 on read it
            // (releases/latest only up to 1.0.7). The tag holds no update-stable.json.
            .defaultManifest(CHANNEL + "update-public-nogms.json")
            // the beta channel of the published edition only (packages on the pre-release z9x-<version>)
            .beta("update-public-nogms.json", CHANNEL + "update-public-beta.json")
            .fallbackFile("update-stable.json")
            .guide(UpdateTarget.PROJECT_URL + "/blob/main/docs/install/z9x/")
            .dir(OtaDir.APP_DATA)
            .install(InstallKind.UPDATE_ENGINE)
            .checks(InstallChecks.Z9X_GATE)
            .dsuHandover(false)
            .importBridgeData(true)
            .hideWithoutDevice(false)
            .build();
}
