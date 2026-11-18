// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

/**
 * The facts of the two install ways (InstallKind) as the three updaters had them before the shared module: the
 * Z9X / HB update_engine download needs the metadata file and never skips a complete file; the Play 6 recovery
 * download needs no payload facts, skips a complete file, and installs after the restart. test/run.sh runs it on
 * every assembled updater (the package part on the Play 6 tree, where a whole package parses).
 */
public final class KindTest {
    private static int pass, fail;

    private static void eq(String name, Object want, Object got) {
        if (want.equals(got)) pass++;
        else {
            fail++;
            System.out.println("FAIL " + name + ": want " + want + ", got " + got);
        }
    }

    public static void main(String[] a) throws Exception {
        InstallKind u = InstallKind.UPDATE_ENGINE, r = InstallKind.RECOVERY;
        eq("update_engine payload facts", true, u.payloadFacts);
        eq("update_engine metadata completes", true, u.metadataCompletesDownload);
        eq("update_engine skips complete", false, u.skipsCompleteFile);
        eq("update_engine installs before restart", false, u.installsAfterRestart);
        eq("recovery payload facts", false, r.payloadFacts);
        eq("recovery metadata completes", false, r.metadataCompletesDownload);
        eq("recovery skips complete", true, r.skipsCompleteFile);
        eq("recovery installs after restart", true, r.installsAfterRestart);
        eq("ways", 2, InstallKind.values().length);
        if (Ota.T.install == InstallKind.RECOVERY) {
            String sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
            UpdateManifest.Pkg bare = UpdateManifest.Pkg.parse("{\"type\":\"full\",\"url\":\"https://x/a.zip\",\"size\":5,"
                    + "\"sha256\":\"" + sha + "\"}");
            UpdateManifest.Pkg meta = UpdateManifest.Pkg.parse("{\"type\":\"full\",\"url\":\"https://x/a.zip\",\"size\":5,"
                    + "\"sha256\":\"" + sha + "\",\"metadata_url\":\"https://x/m.bin\"}");
            eq("recovery package without metadata", false, r.needsMetadata(bare));
            eq("recovery package naming metadata", true, r.needsMetadata(meta));
            eq("update_engine always needs it", true, u.needsMetadata(bare));
        }
        System.out.println("kind tests passed=" + pass + " failed=" + fail);
        if (fail > 0) System.exit(1);
    }
}
