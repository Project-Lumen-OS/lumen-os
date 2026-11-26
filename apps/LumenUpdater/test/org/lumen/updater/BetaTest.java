// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.os.SystemProperties;

import java.util.Collections;
import java.util.List;

/**
 * Host test of the "Beta updates" switch of the device-neutral updater (sh test/ota/run.sh). Fork of the Z9X
 * BetaTest with the HB facts: the channel pair is update-hb-nogms.json (stable) and update-hb-test.json (beta) on
 * the fixed pre-release tag z9x-hb; when the user never chose, a beta (or rc) build reads beta and a final build
 * stable, whichever of the two files the image names, and the user's choice wins either way. The X50 family shares
 * this recipe: its images name the beta file, its final builds still start on stable. With beta on a check reads both manifests and offers the higher version (the shared Channels of the
 * Z9X updater, which the HB recipe takes unchanged). Turning beta off never downgrades (Preflight.applicable,
 * version codes of decision). Properties come from the SystemProperties stand-in of test/ota/stubs.
 */
public final class BetaTest {
    private static int failed, passed;
    private static final String TAG_URL = "https://github.com/Project-Lumen-OS/lumen-os/releases/download/z9x-hb/";
    private static final String NOGMS = TAG_URL + "update-hb-nogms.json";
    private static final String TEST = TAG_URL + "update-hb-test.json";
    private static final List<String> ANY = Collections.emptyList();

    private static void eq(String what, Object want, Object got) {
        if (want == null ? got == null : want.equals(got)) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    private static String res(Preflight.Block b) { return b == null ? "ok" : b.res; }

    private static Preflight.Block app(String channel, int vc) {
        return Preflight.applicable(1, "hb", channel, ANY, vc, 0);
    }

    /** An HB image reading {@code url}, running {@code version}. */
    private static void image(String url, String version) {
        SystemProperties.VALUES.clear();
        SystemProperties.VALUES.put("ro.lumen.ota.device", "hb");
        if (url != null) SystemProperties.VALUES.put("ro.lumen.ota.manifest_url", url);
        SystemProperties.VALUES.put("ro.lumen.version", version);
        SystemProperties.VALUES.put("ro.lumen.version_code", String.valueOf(Ota.versionCode(version)));
        SystemProperties.VALUES.put("ro.vendor.build.version.incremental", "V1");
    }

    public static void main(String[] a) {
        image(NOGMS, "1.0.8-beta3");
        String[] c = Ota.channels(NOGMS);
        eq("pair from stable: stable", NOGMS, c == null ? null : c[0]);
        eq("pair from stable: beta", TEST, c == null ? null : c[1]);
        c = Ota.channels(TEST + "?x=1");
        eq("pair from test: stable", NOGMS, c == null ? null : c[0]);
        eq("pair from test: beta", TEST, c == null ? null : c[1]);
        eq("another file: no beta", null, Ota.channels(TAG_URL + "update-hb-lab.json"));
        eq("another device's file: no beta", null, Ota.channels(TAG_URL + "update-x50-nogms.json"));
        eq("beta build, public image: on by default", true, Ota.betaOn(NOGMS, null));
        eq("beta build, test image: on by default", true, Ota.betaOn(TEST, null));
        eq("test image turned off", NOGMS, Ota.manifestUrl(TEST, false));
        eq("public image turned on", TEST, Ota.manifestUrl(NOGMS, true));

        // a final build on a public image, never chosen: stable as before
        image(NOGMS, "1.0.8");
        Ota.setBeta(null);
        eq("final build, public image: off by default", false, Ota.betaOn(NOGMS, null));
        eq("final build, test image: still stable", false, Ota.betaOn(TEST, null));
        eq("final build reading the test file: reads stable", NOGMS, Ota.manifestUrl(TEST, null));
        eq("switch shown", true, Ota.betaAvailable());
        eq("reads stable", NOGMS, Ota.manifestUrl());
        eq("test manifest while off", "blk_channel", res(app("hb-test", Ota.versionCode("1.0.9-beta1"))));
        eq("older test manifest while off: up to date", "uptodate", res(app("hb-test", Ota.versionCode("1.0.8-beta9"))));
        Ota.setBeta(true);
        image(NOGMS, "1.0.8-beta3");
        eq("reads beta", TEST, Ota.manifestUrl());
        eq("USB file", "update-hb-test.json", Ota.channelFile());
        eq("beta newer", "ok", res(app("hb-test", 1000804)));
        eq("stable manifest while on (both read, the higher offered)", "ok", res(app("hb-nogms", 1000899)));
        eq("reads both", NOGMS + " " + TEST, String.join(" ", Channels.read()));
        eq("USB files", "update-hb-nogms.json update-hb-test.json", String.join(" ", Channels.files()));
        eq("another device's channel while on", "blk_channel", res(app("x50-nogms", 1000899)));

        // the tester's unit: a test image on 1.0.9-beta2 turns beta off, never a downgrade
        image(TEST, "1.0.9-beta2");
        Ota.setBeta(null);
        eq("test image reads beta", TEST, Ota.manifestUrl());
        Ota.setBeta(false);
        eq("turned off reads stable", NOGMS, Ota.manifestUrl());
        eq("older final", "uptodate", res(app("hb-nogms", Ota.versionCode("1.0.8"))));
        eq("older beta of the same version", "uptodate", res(app("hb-nogms", Ota.versionCode("1.0.9-beta1"))));
        eq("same build", "uptodate", res(app("hb-nogms", Ota.versionCode("1.0.9-beta2"))));
        eq("rc of the same version", "ok", res(app("hb-nogms", Ota.versionCode("1.0.9-rc1"))));
        eq("final of the same version", "ok", res(app("hb-nogms", Ota.versionCode("1.0.9"))));
        eq("newer beta while off", "blk_channel", res(app("hb-test", Ota.versionCode("1.0.9-beta3"))));

        // a beta build never chosen: the beta channel, so newer betas arrive; turning it off never downgrades
        image(NOGMS, "1.0.10-beta20");
        Ota.setBeta(null);
        eq("beta build reads beta", TEST, Ota.manifestUrl());
        eq("beta build reads both", NOGMS + " " + TEST, String.join(" ", Channels.read()));
        eq("beta build: next beta", "ok", res(app("hb-test", Ota.versionCode("1.0.10-beta21"))));
        eq("beta build: older final", "uptodate", res(app("hb-nogms", Ota.versionCode("1.0.9"))));
        image(NOGMS, "1.0.10-rc1");
        Ota.setBeta(null);
        eq("rc build reads beta", TEST, Ota.manifestUrl());

        // the X50 family on the same recipe: its images name update-x50-test.json
        String x50 = "https://github.com/Project-Lumen-OS/lumen-os/releases/download/x50/";
        image(x50 + "update-x50-test.json", "1.0.10-beta10");
        SystemProperties.VALUES.put("ro.lumen.ota.device", "x50");
        Ota.setBeta(null);
        eq("x50 beta: beta", true, Ota.betaOn());
        eq("x50 beta reads both", x50 + "update-x50-nogms.json " + x50 + "update-x50-test.json",
                String.join(" ", Channels.read()));
        SystemProperties.VALUES.put("ro.lumen.version", "1.0.10");
        SystemProperties.VALUES.put("ro.lumen.version_code", String.valueOf(Ota.versionCode("1.0.10")));
        eq("x50 final: stable", false, Ota.betaOn());
        eq("x50 final reads stable only", x50 + "update-x50-nogms.json", String.join(" ", Channels.read()));
        eq("x50 final: a beta manifest refused", "blk_channel",
                res(Preflight.applicable(1, "x50", "x50-test", ANY, Ota.versionCode("1.0.11-beta1"), 0)));
        Ota.setBeta(true);
        eq("x50 final turned to beta", x50 + "update-x50-nogms.json " + x50 + "update-x50-test.json",
                String.join(" ", Channels.read()));

        // the prerelease rule itself (VersionScheme.prerelease)
        String[][] pr = {{"1.0.10-beta21", "true"}, {"1.0.11-rc1", "true"}, {"x50-1.0.10-beta4", "true"},
                {"titanpro-1.0.10-beta3", "true"}, {" 1.0.10-beta2 ", "true"}, {"1.0.10", "false"}, {"1.0.9", "false"},
                {"titanpro-1.0.10", "false"}, {"hb-t1", "false"}, {"", "false"}, {"1.0.10-beta", "false"},
                {"1.0.10-beta0", "false"}, {"1.0.10-dev1", "false"}, {"beta1", "false"}};
        for (String[] v : pr) eq("prerelease " + v[0], Boolean.valueOf(v[1]), VersionScheme.prerelease(v[0]));
        eq("prerelease null", false, VersionScheme.prerelease(null));

        // fail closed: no device or no manifest URL = no switch, nothing read
        image(null, "1.0.8");
        eq("no URL: no switch", false, Ota.betaAvailable());
        eq("no URL: nothing read", "", Ota.manifestUrl());
        SystemProperties.VALUES.put("ro.lumen.ota.manifest_url", NOGMS);
        SystemProperties.VALUES.remove("ro.lumen.ota.device");
        eq("no device: no switch", false, Ota.betaAvailable());
        eq("no device: nothing read", "", Ota.manifestUrl());

        System.out.println("beta tests passed=" + passed + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
