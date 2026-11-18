// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import android.os.SystemProperties;

import java.util.Collections;

/**
 * The neutral Target (apps/LumenUpdater) with the image properties of each model that ships it (test/ota/run.sh):
 * ro.lumen.ota.boot_gate picks the install checks (lumen_ota by default), and the X50 family (G0111 wire id x50,
 * G0100 x50max) reads its own channel pair on the tag x50: update-&lt;id&gt;-test.json (beta, what the beta images
 * read) beside update-&lt;id&gt;-nogms.json (stable), never the installer's update-&lt;id&gt;-beta.json and never another
 * model's manifest, and its storage check accepts the compressed snapshots its vendor declares (the HB's refuses
 * them). Any other X50 model (H0062, H0063) is the same code with its own image properties
 * (apps/core/lumen-updater/README.md).
 */
public final class NeutralTargetTest {
    private static int pass, fail;
    static final String R = "https://github.com/Project-Lumen-OS/lumen-os/releases/download/";
    static final String GUIDE = "https://github.com/Project-Lumen-OS/lumen-os/blob/main/docs/install/x50";
    /** ro.product.ab_ota_partitions of the X50 images (devices/g0111/image/x50_prop_pub.txt) */
    /** the XGIMI set of the X50 family (apps/core/lumen-fwcopy): the boot chain with the four of before */
    static final String X50_LIST = "boot,bootdata,dtbo,mboot,optee,owl,satf,tvconfig,tvservice,vbmeta_system,xgimiconfig";

    private static void eq(String name, Object want, Object got) {
        if (want == null ? got == null : want.equals(got)) pass++;
        else {
            fail++;
            System.out.println("FAIL " + name + ": want " + want + ", got " + got);
        }
    }

    private static String res(Preflight.Block b) { return b == null ? "ok" : b.res + "(" + b.arg + ")"; }

    private static void image(String device, String url, String vc, String gate) {
        SystemProperties.VALUES.clear();
        SystemProperties.DENY.clear();
        Ota.setBeta(null);
        SystemProperties.VALUES.put("ro.lumen.ota.device", device);
        SystemProperties.VALUES.put("ro.lumen.ota.manifest_url", url);
        SystemProperties.VALUES.put("ro.lumen.version_code", vc);
        SystemProperties.VALUES.put("ro.lumen.ota.guide", GUIDE);
        SystemProperties.VALUES.put("ro.vendor.build.version.incremental", "v1.2.64");
        if (gate != null) SystemProperties.VALUES.put("ro.lumen.ota.boot_gate", gate);
    }

    private static Preflight.Block app(String device, String channel, int vc) {
        return Preflight.applicable(1, device, channel, java.util.Arrays.asList("v1.2.64", "v1.2.54"), vc, 0);
    }

    public static void main(String[] a) {
        // test/ota/run.sh passes the wire ids of devices/*/ota.conf vabc=yes (the models with this recipe)
        eq("compressed snapshots = ota.conf vabc=yes", a.length == 1 ? a[0] : "?",
                String.join(",", Ota.T.compressedSnapshots));
        // the boot gate: lumen_ota without the property (HB, X50), the Z9X base's gate by its name, anything else refused
        image("hb", R + "z9x-hb/update-hb-nogms.json", "1001012", null);
        eq("no gate property", InstallChecks.LUMEN_GATE, Ota.T.checks());
        image("hb", R + "z9x-hb/update-hb-nogms.json", "1001012", "lumen_ota");
        eq("lumen_ota", InstallChecks.LUMEN_GATE, Ota.T.checks());
        image("x50", R + "x50/update-x50-test.json", "1001005", "z9x_ota");
        eq("z9x_ota", InstallChecks.Z9X_GATE, Ota.T.checks());
        image("x50", R + "x50/update-x50-test.json", "1001005", "z9x");
        eq("a model name is no gate", InstallChecks.UNKNOWN, Ota.T.checks());

        String[][] family = {{"G0111", "x50"}, {"G0100", "x50max"}};
        for (String[] m : family) {
            String code = m[0], dev = m[1];
            String beta = R + "x50/update-" + dev + "-test.json", stable = R + "x50/update-" + dev + "-nogms.json";
            image(dev, beta, "1001005", null);
            eq(code + " checks", InstallChecks.LUMEN_GATE, Ota.T.checks());
            eq(code + " beta image reads beta", beta, Ota.manifestUrl());
            eq(code + " beta switch", true, Ota.betaAvailable());
            eq(code + " beta on by default", true, Ota.betaOn());
            eq(code + " reads both", 2, Channels.read().length);
            eq(code + " stable first", stable, Channels.read()[0]);
            eq(code + " version code", 1001005, Ota.currentVersionCode());
            eq(code + " guide", GUIDE + "/ru.md#vbmeta", Ota.guide("ru", "vbmeta"));
            eq(code + " next beta", "ok", res(app(dev, dev + "-test", 1001006)));
            eq(code + " final", "ok", res(app(dev, dev + "-nogms", 1001099)));
            eq(code + " same beta", "uptodate()", res(app(dev, dev + "-test", 1001005)));
            eq(code + " installer channel never", "blk_channel(" + dev + "-beta)", res(app(dev, dev + "-beta", 1001006)));
            eq(code + " Z9X manifest", "err_signature()", res(app("z9x", dev + "-test", 1001106)));
            eq(code + " HB manifest", "err_signature()", res(app("hb", dev + "-test", 1001106)));
            for (String[] o : family) {
                if (o == m) continue;
                eq(code + " refuses the " + o[0] + " manifest", "err_signature()", res(app(o[1], o[1] + "-test", 1001006)));
                eq(code + " refuses the " + o[0] + " channel", "blk_channel(" + o[1] + "-test)", res(app(dev, o[1] + "-test", 1001006)));
            }
            SystemProperties.VALUES.put("ro.vendor.build.version.incremental", "v1.2.70");
            eq(code + " other stock firmware", "blk_vendor(v1.2.70)", res(app(dev, dev + "-test", 1001006)));
            // beta turned off: only the stable file, no downgrade
            Ota.setBeta(false);
            eq(code + " beta off reads stable", stable, Ota.manifestUrl());
            eq(code + " beta off refuses test", "blk_channel(" + dev + "-test)", res(app(dev, dev + "-test", 1001006)));
            // the unit of 2026-11-13: Lumen in slot b, slot a's vbmeta stock (flags 0): no update, the guide step
            SystemProperties.VALUES.put("ro.boot.slot_suffix", "_b");
            SystemProperties.VALUES.put("sys.z9x.vbmeta_other.flags", "0");
            eq(code + " stock vbmeta in slot a", "blk_vbmeta_title(A)", res(Preflight.vbmeta()));
            SystemProperties.VALUES.put("sys.z9x.vbmeta_other.flags", "3");
            eq(code + " flags 3 in slot a", "ok", res(Preflight.vbmeta()));
            // the storage check (STORAGE) with the vendor facts of the X50 Ultra on 1.0.10-beta5 (2026-11-14): compressed
            // snapshots are what its payloads use, so the check goes on to the partition list (no build.prop here)
            SystemProperties.VALUES.put("ro.virtual_ab.enabled", "true");
            SystemProperties.VALUES.put("ro.virtual_ab.compression.enabled", "true");
            SystemProperties.VALUES.put("ro.product.ab_ota_partitions", X50_LIST);
            eq(code + " compressed snapshots", "blk_partitions(no build list)", res(Preflight.storage()));
            eq(code + " its partition list", "ok", res(Preflight.partitions("", X50_LIST, X50_LIST, code)));
            eq(code + " the old list without the boot chain", "blk_partitions(no boot)",
                    res(Preflight.partitions("", "optee,owl,satf,xgimiconfig", "optee,owl,satf,xgimiconfig", code)));
            SystemProperties.VALUES.put("ro.virtual_ab.compression.enabled", "false");
            eq(code + " legacy snapshots", "blk_vab(compression off)", res(Preflight.storage()));
            SystemProperties.VALUES.remove("ro.virtual_ab.compression.enabled");
            eq(code + " compression unset", "blk_vab(compression off)", res(Preflight.storage()));
        }
        // the HB keeps legacy snapshots: compression on is refused, off goes on to the partition list
        image("hb", R + "z9x-hb/update-hb-nogms.json", "1001012", null);
        SystemProperties.VALUES.put("ro.virtual_ab.enabled", "true");
        SystemProperties.VALUES.put("ro.virtual_ab.compression.enabled", "false");
        eq("hb legacy snapshots", "blk_partitions(no build list)", res(Preflight.storage()));
        SystemProperties.VALUES.put("ro.virtual_ab.compression.enabled", "true");
        eq("hb compression on", "blk_vab(compression on)", res(Preflight.storage()));
        // a wire id outside the table (a later model before its ota.conf says vabc=yes) counts as legacy
        image("x50pro", R + "x50/update-x50pro-test.json", "1001006", null);
        SystemProperties.VALUES.put("ro.virtual_ab.enabled", "true");
        SystemProperties.VALUES.put("ro.virtual_ab.compression.enabled", "true");
        eq("unknown wire id compression on", "blk_vab(compression on)", res(Preflight.storage()));
        // an X50 image without a wire id (every X50 image up to 1.0.10-beta4) takes nothing
        image("", R + "x50/update-x50-test.json", "1001004", null);
        eq("no wire id: no manifest", "", Ota.manifestUrl());
        eq("no wire id: refused", "err_signature()", res(app("x50", "x50-test", 1001006)));
        System.out.println("neutral target tests passed=" + pass + " failed=" + fail);
        if (fail > 0) System.exit(1);
    }
}
