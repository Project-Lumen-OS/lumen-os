// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

/** Runs the plain-JVM tests: sh tools/run_tests.sh */
public final class AllTests {
    public static void main(String[] a) throws Exception {
        IntentGuardTest.run();
        HeroArtTest.run();
        ResolutionTest.run();
        GfxTest.run();
        CecNamesTest.run();
        TmdbTest.run();
        MirrorTileTest.run();
        ProjectorTargetTest.run();
        System.out.println("tests passed=" + T.passed + " failed=" + T.failed);
        System.exit(T.failed == 0 ? 0 : 1);
    }
}
