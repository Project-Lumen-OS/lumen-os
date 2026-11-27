// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.os.SystemProperties;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Host test of the delta choice and the fall back to the full package (DeltaRule, Newest.deltaFailed, Newest.accept,
 * Newest.downloadNewest) on the Z9X updater (test/run.sh): which package a unit on a given build downloads, and what
 * happens to the offer when a delta fails at download, check or install. No network, no device.
 */
public final class DeltaTest {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }

    static final String B4 = "lumen-1.1.0-beta4-20261124anp", B5 = "lumen-1.1.0-beta5-20261126anp";
    static final int VC4 = 1010004, VC5 = 1010005;
    static final String SHA_FULL = rep('a'), SHA_INC = rep('b'), SHA_OLD = rep('c'), SHA_BAD = rep('d');

    static String rep(char c) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 64; i++) b.append(c);
        return b.toString();
    }

    static String pkg(String type, String sha, String extra) {
        String u = "https://github.com/Project-Lumen-OS/lumen-os/releases/download/z9x-1.1.0-beta7/" + sha.charAt(0);
        return "{\"type\":\"" + type + "\",\"url\":\"" + u + ".zip\",\"size\":1000,\"sha256\":\"" + sha + "\","
                + "\"payload_offset\":41,\"payload_size\":900,\"metadata_url\":\"" + u + ".bin\",\"metadata_size\":30,"
                + "\"payload_properties\":[\"FILE_SIZE=900\"]" + extra + "}";
    }

    static String full() { return pkg("full", SHA_FULL, ""); }

    static String inc(String sha, String from, int vc) {
        return pkg("delta", sha, ",\"from_build_id\":\"" + from + "\",\"from_version\":\"1.1.0-beta4\""
                + (vc != 0 ? ",\"from_version_code\":" + vc : ""));
    }

    static UpdateManifest manifest(String packages, String incremental) throws Exception {
        return new UpdateManifest("{\"schema\":1,\"device\":\"z9x\",\"channel\":\"public-beta\",\"version\":\"1.1.0-beta7\","
                + "\"version_code\":1010007,\"build_id\":\"lumen-1.1.0-beta7-20261126anp\",\"packages\":[" + packages + "]"
                + (incremental == null ? "" : ",\"incremental\":[" + incremental + "]") + "}");
    }

    static String type(UpdateManifest.Pkg p) {
        return p == null ? "null" : p.isDelta() ? "delta:" + p.sha256.charAt(0) : "full";
    }

    static final Set<String> NONE = Collections.emptySet();

    static void choice() throws Exception {
        UpdateManifest m = manifest(inc(SHA_OLD, B5, 0) + "," + full(), inc(SHA_INC, B4, VC4));
        check("incremental parsed", m.incremental.size() == 1 && m.incremental.get(0).fromVersionCode == VC4
                && m.incremental.get(0).fromVersion.equals("1.1.0-beta4"));
        check("source build takes its delta", type(DeltaRule.choose(m, B4, VC4, false, NONE)).equals("delta:b"));
        check("same build id, other version code: full", type(DeltaRule.choose(m, B4, VC5, false, NONE)).equals("full"));
        check("1.0.9 form delta in packages (no version code): taken",
                type(DeltaRule.choose(m, B5, VC5, false, NONE)).equals("delta:c"));
        check("unknown build: full", type(DeltaRule.choose(m, "lumen-1.1.0-beta3-x", VC4, false, NONE)).equals("full"));
        check("no build id: full", type(DeltaRule.choose(m, "", VC4, false, NONE)).equals("full"));
        UpdateManifest anon = manifest(pkg("delta", SHA_OLD, "") + "," + full(), null);
        check("delta without a source, unit without a build id: full",
                type(DeltaRule.choose(anon, "", 0, false, NONE)).equals("full"));
        check("USB stick: always full", type(DeltaRule.choose(m, B4, VC4, true, NONE)).equals("full"));
        Set<String> refused = new TreeSet<>(Collections.singleton(SHA_INC));
        check("refused delta: full", type(DeltaRule.choose(m, B4, VC4, false, refused)).equals("full"));
        check("a refused delta does not refuse another one",
                type(DeltaRule.choose(m, B5, VC5, false, refused)).equals("delta:c"));
        UpdateManifest noFull = manifest(inc(SHA_OLD, B5, 0), inc(SHA_INC, B4, VC4));
        check("no full package, the delta fits: delta", type(DeltaRule.choose(noFull, B4, VC4, false, NONE)).equals("delta:b"));
        check("no full package, nothing fits: null", DeltaRule.choose(noFull, B4, VC5, false, NONE) == null);
        // an incremental entry this updater cannot use is skipped, the manifest and its full package stay
        UpdateManifest bad = manifest(full(), inc(SHA_BAD, B4, VC4).replace("\"url\":\"https://", "\"url\":\"http://")
                + "," + pkg("full", SHA_BAD, "") + "," + inc(SHA_BAD, "", 0) + ",{\"type\":\"delta\"}");
        check("malformed incremental entries skipped", bad.incremental.isEmpty() && bad.full() != null);
        check("malformed incremental: full", type(DeltaRule.choose(bad, B4, VC4, false, NONE)).equals("full"));
        UpdateManifest old = manifest(full(), null);
        check("manifest without incremental", old.incremental.isEmpty() && old.deltas().isEmpty()
                && type(DeltaRule.choose(old, B4, VC4, false, NONE)).equals("full"));
        check("deltas: incremental first", m.deltas().size() == 2 && m.deltas().get(0).sha256.equals(SHA_INC));
    }

    static void codes() {
        int[] keep = {0, 52, 48, 60, 51, -1, -2};
        for (int c : keep) check("no fall back on code " + c, !DeltaRule.applyFailed(c));
        int[] fall = {1, 7, 9, 15, 20, 26, 41};
        for (int c : fall) check("fall back on code " + c, DeltaRule.applyFailed(c));
    }

    static void fallBack() throws Exception {
        UpdateManifest m = manifest(full(), inc(SHA_INC, B4, VC4));
        NewestTest.Mem o = new NewestTest.Mem();
        o.setOffer(m.json, m.incremental.get(0).json, false, "");
        o.files.add("delta.zip");
        o.setState(UpdateState.INSTALLING);
        check("delta failed: true", Newest.deltaFailed(o, "update_engine code 20", UpdateState.AVAILABLE));
        check("delta refused", o.refused.contains(SHA_INC) && o.refused.size() == 1);
        check("full package is the offer", UpdateManifest.Pkg.parse(o.pkg).sha256.equals(SHA_FULL)
                && o.manifest.equals(m.json) && !o.usb);
        check("delta files gone", o.files.isEmpty());
        check("state as asked", o.state == UpdateState.AVAILABLE);
        check("full package failing: no fall back", !Newest.deltaFailed(o, "sha256", UpdateState.DOWNLOADING)
                && o.state == UpdateState.AVAILABLE && UpdateManifest.Pkg.parse(o.pkg).sha256.equals(SHA_FULL));
        UpdateManifest noFull = manifest(inc(SHA_OLD, B5, 0), null);
        NewestTest.Mem n = new NewestTest.Mem();
        n.setOffer(noFull.json, noFull.packages.get(0).json, false, "");
        n.setState(UpdateState.INSTALLING);
        check("no full package: the error stays", !Newest.deltaFailed(n, "code 20", UpdateState.AVAILABLE)
                && n.refused.isEmpty() && n.state == UpdateState.INSTALLING);
        NewestTest.Mem e = new NewestTest.Mem();
        check("no offer: nothing", !Newest.deltaFailed(e, "x", UpdateState.AVAILABLE) && e.pkg.isEmpty());
    }

    /** Newest.accept on the real running build (the Z9X target's properties) and the download switch to full. */
    static void acceptAndDownload() throws Exception {
        SystemProperties.VALUES.put("ro.z9x.build_id", B4);
        SystemProperties.VALUES.put("ro.z9x.version_code", String.valueOf(VC4));
        SystemProperties.VALUES.put("ro.z9x.version", "1.1.0-beta4");
        UpdateManifest m = manifest(full(), inc(SHA_INC, B4, VC4));
        NewestTest.Mem o = new NewestTest.Mem();
        Newest.accept(o, m, false, "", UpdateState.IDLE, VC4, 1);
        check("accept: the delta is the offer", UpdateManifest.Pkg.parse(o.pkg).sha256.equals(SHA_INC)
                && o.state == UpdateState.AVAILABLE);
        Newest.accept(o, m, true, "/mnt/usb", UpdateState.IDLE, VC4, 2);
        check("accept from USB: full", UpdateManifest.Pkg.parse(o.pkg).sha256.equals(SHA_FULL) && o.usb);
        o.clearOffer();
        Newest.accept(o, m, false, "", UpdateState.IDLE, VC5, 3);
        check("accept, other version code: full", UpdateManifest.Pkg.parse(o.pkg).sha256.equals(SHA_FULL));
        o.clearOffer();
        Newest.accept(o, m, false, "", UpdateState.IDLE, VC4, 4);
        // the delta download fails its sha256: the worker downloads the full package in the same run
        StringBuilder got = new StringBuilder();
        boolean ok = Newest.downloadNewest(o, j -> {
            try {
                UpdateManifest.Pkg p = UpdateManifest.Pkg.parse(j);
                got.append(type(p)).append(' ');
                o.files.add(p.fileName());
                if (!p.isDelta()) return true;
                Newest.deltaFailed(o, "sha256", UpdateState.DOWNLOADING);   // what UpdateService.downloadOnce does
                return false;
            } catch (Exception x) {
                return false;
            }
        });
        check("download: delta then full in one run", ok && got.toString().equals("delta:b full ")
                && o.state == UpdateState.DOWNLOADED);
        check("download: full is the offer, delta refused", UpdateManifest.Pkg.parse(o.pkg).sha256.equals(SHA_FULL)
                && o.refused.contains(SHA_INC));
        // the next check of the same manifest keeps the full package (the refused delta is not offered again)
        Newest.accept(o, m, false, "", UpdateState.DOWNLOADED, VC4, 5);
        check("recheck after the fall back: full stays downloaded", UpdateManifest.Pkg.parse(o.pkg).sha256.equals(SHA_FULL)
                && o.state == UpdateState.DOWNLOADED);
    }

    public static void main(String[] a) throws Exception {
        choice();
        codes();
        fallBack();
        acceptAndDownload();
        System.out.println("delta passed=" + passed + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
