// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

/**
 * The neutral facts of the shared updater (apps/core/lumen-updater, UpdateTarget):
 * the values come from the image's ro.lumen.* properties, one table is keyed by wire id (below). The HB (goat_elegant)
 * and the X50 family (g0111, g0100, h0062, h0063) ship it; their images differ only in their properties:
 * ro.lumen.ota.device (wire id, empty = no update and no settings row), ro.lumen.ota.manifest_url (the channel;
 * update-&lt;device&gt;-nogms.json and -test.json form a stable / beta pair, any other file is read alone),
 * ro.lumen.ota.guide, ro.lumen.version_code, and ro.lumen.ota.boot_gate (the boot gate the image carries and so
 * the install checks: lumen_ota = tools/devices/ota/image/lumen_ota.sh, the default; z9x_ota = the Z9X base's
 * gate tools/ota/image/z9x_ota.sh). One fact is no image property, because the images already installed must
 * read it from an updater update: the wire ids whose payloads use compressed snapshots, as their vendor declares
 * (devices/&lt;codename&gt;/ota.conf vabc=yes, test/ota/run.sh compares).
 */
final class Target {
    private Target() {}

    static final UpdateTarget SPEC = new UpdateTarget.Builder()
            .props("ro.lumen")
            .deviceFrom("ro.lumen.ota.device")
            .versions(VersionScheme.O1)
            .channels(ChannelRule.PAIR)
            .dir(OtaDir.APP_DATA)
            .install(InstallKind.UPDATE_ENGINE)
            .checksFrom("ro.lumen.ota.boot_gate", InstallChecks.LUMEN_GATE)
            .dsuHandover(true)
            .hideWithoutDevice(true)
            .compressedSnapshots("x50", "x50max")
            .build();
}
