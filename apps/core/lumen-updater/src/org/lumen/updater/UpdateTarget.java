// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * What one projector model gives the shared updater (2026-11-13: one updater for every model, the model is
 * data). Every field is a value; the ways an update is installed (InstallKind, InstallWay), the install checks (InstallChecks),
 * the channel rules (ChannelRule) and the version schemes (VersionScheme) are written once in this module and a
 * model only names them. Each updater recipe supplies one {@code Target.SPEC} (the app's
 * src/org/lumen/updater/Target.java): the Z9X, the Play 6, and the neutral one of the HB and the X50 family, whose
 * facts come from the image's ro.lumen.ota.* properties. README.md has the table of every model.
 */
public final class UpdateTarget {
    /** The project's repository and its download and guide addresses. */
    public static final String REPO = "Project-Lumen-OS/lumen-os";
    public static final String PROJECT_URL = "https://github.com/" + REPO;
    public static final String DOWNLOADS = PROJECT_URL + "/releases/download/";

    /** Property namespace of the image's version facts and manifest URL: ro.z9x (Z9X) or ro.lumen. */
    final String props;
    /** The wire id a manifest must carry, fixed, or read from {@link #deviceProp} when null. */
    final String device, deviceProp;
    final VersionScheme versions;
    final ChannelRule channels;
    /** ChannelRule.FIXED: the manifest without an https image URL, the stable file that has a beta channel and its
     *  beta URL (null = no beta channel), the file name of a URL that is no manifest name. */
    final String defaultManifest, stableFile, betaUrl, fallbackFile;
    /** Install guide folder (lang.md#section is added); null = the image's ro.lumen.ota.guide or the device's. */
    final String guide;
    final OtaDir dir;
    final InstallKind install;
    /** The checks before an install: fixed, or picked by the image property {@link #gateProp}. */
    final InstallChecks checks;
    final String gateProp;
    /** A DSU trial hands the update screen over to the in-trial installer and does no update work. */
    final boolean dsuHandover;
    /** Without a wire id the update screen (and the TvSettings row) is hidden. */
    final boolean hideWithoutDevice;
    /** The wire ids whose payloads use compressed snapshots (devices/&lt;codename&gt;/ota.conf vabc=yes); every other one
     *  uses legacy snapshots. Preflight STORAGE wants the vendor's Virtual A/B compression to be the same. */
    final Set<String> compressedSnapshots;

    private UpdateTarget(Builder b) {
        props = b.props;
        device = b.device;
        deviceProp = b.deviceProp;
        versions = b.versions;
        channels = b.channels;
        defaultManifest = b.defaultManifest;
        stableFile = b.stableFile;
        betaUrl = b.betaUrl;
        fallbackFile = b.fallbackFile;
        guide = b.guide;
        dir = b.dir;
        install = b.install;
        checks = b.checks;
        gateProp = b.gateProp;
        dsuHandover = b.dsuHandover;
        hideWithoutDevice = b.hideWithoutDevice;
        compressedSnapshots = Collections.unmodifiableSet(new TreeSet<>(b.compressedSnapshots));
    }

    /** The wire id of this image; "" = this image takes no update. */
    String device() { return device != null ? device : Ota.prop(deviceProp).trim(); }

    /** The payloads of this image use compressed snapshots (VABC). */
    boolean compressedSnapshots() { return compressedSnapshots.contains(device()); }

    /** The install checks of this image (an unknown boot gate name gets a list that always refuses). */
    InstallChecks checks() {
        if (gateProp == null) return checks;
        String g = Ota.prop(gateProp).trim();
        return g.isEmpty() ? checks : InstallChecks.named(g);
    }

    /** One section of this model's install guide. */
    String guide(String lang, String section) {
        if (guide != null) return guide + lang + ".md#" + section;
        String g = Ota.prop("ro.lumen.ota.guide").trim();
        while (g.endsWith("/")) g = g.substring(0, g.length() - 1);
        if (!g.startsWith("https://")) {
            String d = device();
            g = PROJECT_URL + "/blob/main/docs/install" + (d.matches("[a-z0-9_-]+") ? "/" + d : "");
        }
        return g + "/" + lang + ".md#" + section;
    }

    /** The values of one model; every field must be named (build() refuses a missing one). */
    public static final class Builder {
        private String props, device, deviceProp, defaultManifest = "", stableFile, betaUrl, fallbackFile = "", guide;
        private String gateProp;
        private VersionScheme versions;
        private ChannelRule channels;
        private OtaDir dir;
        private InstallKind install;
        private InstallChecks checks;
        private boolean dsuHandover, hideWithoutDevice;
        private Set<String> compressedSnapshots = Collections.emptySet();

        public Builder props(String ns) { props = ns; return this; }
        public Builder device(String id) { device = id; return this; }
        public Builder deviceFrom(String prop) { deviceProp = prop; return this; }
        public Builder versions(VersionScheme v) { versions = v; return this; }
        public Builder channels(ChannelRule r) { channels = r; return this; }
        public Builder defaultManifest(String url) { defaultManifest = url; return this; }
        public Builder beta(String stable, String url) { stableFile = stable; betaUrl = url; return this; }
        public Builder fallbackFile(String f) { fallbackFile = f; return this; }
        public Builder guide(String folder) { guide = folder; return this; }
        public Builder dir(OtaDir d) { dir = d; return this; }
        public Builder install(InstallKind k) { install = k; return this; }
        public Builder checks(InstallChecks c) { checks = c; return this; }
        public Builder checksFrom(String prop, InstallChecks byDefault) { gateProp = prop; checks = byDefault; return this; }
        public Builder dsuHandover(boolean on) { dsuHandover = on; return this; }
        public Builder hideWithoutDevice(boolean on) { hideWithoutDevice = on; return this; }
        public Builder compressedSnapshots(String... wireIds) {
            compressedSnapshots = new TreeSet<>(Arrays.asList(wireIds));
            return this;
        }

        public UpdateTarget build() {
            if (props == null || versions == null || channels == null || dir == null || install == null || checks == null
                    || (device == null) == (deviceProp == null)) {
                throw new IllegalStateException("UpdateTarget: a value is missing");
            }
            return new UpdateTarget(this);
        }
    }
}
