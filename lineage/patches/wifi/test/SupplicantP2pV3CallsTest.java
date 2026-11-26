// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package com.android.server.wifi.p2p;

/**
 * Host test of SupplicantP2pV3Calls (lineage/patches/wifi/0001): which AIDL V3 P2P calls go to the supplicant.
 * Run by lineage/patches/wifi/test/run.sh, which takes the class from the patch itself.
 */
public final class SupplicantP2pV3CallsTest {
    /** aidl_api/android.hardware.wifi.supplicant/3/.hash of hardware/interfaces (AOSP). */
    private static final String AOSP_FROZEN_V3 = "55b58c9bd6d40c1459073b5d03f4ede5cfc9a212";
    /** The MediaTek TV vendors' pre-freeze V3 (Z9X, X50 Ultra, TITAN, RS30 Ultra Max). */
    private static final String MTK_DRAFT = "4652398bc565e185ec0799d2a9ea9db1774710bc";
    private static final String[] CALLS = {
        SupplicantP2pV3Calls.CONNECT, SupplicantP2pV3Calls.FIND, SupplicantP2pV3Calls.EXT_LISTEN};
    private static int sChecks;

    public static void main(String[] args) {
        check(SupplicantP2pV3Calls.FROZEN_V3_HASH.equals(AOSP_FROZEN_V3), "the frozen V3 hash is AOSP's");
        check(SupplicantP2pV3Calls.CONNECT.equals("connectWithParams"), "connect name");
        check(SupplicantP2pV3Calls.FIND.equals("findWithParams"), "find name");
        check(SupplicantP2pV3Calls.EXT_LISTEN.equals("configureExtListenWithParams"), "listen name");

        noCalls(SupplicantP2pV3Calls.none(), "before setup");
        noCalls(SupplicantP2pV3Calls.of(1, null), "V1");
        noCalls(SupplicantP2pV3Calls.of(2, null), "V2");
        noCalls(SupplicantP2pV3Calls.of(2, AOSP_FROZEN_V3), "V2 never gets V3 calls, whatever the hash");
        noCalls(SupplicantP2pV3Calls.of(-1, AOSP_FROZEN_V3), "unknown version");
        noCalls(SupplicantP2pV3Calls.of(3, MTK_DRAFT), "MediaTek pre-freeze V3");
        noCalls(SupplicantP2pV3Calls.of(3, null), "V3 whose hash could not be read");
        noCalls(SupplicantP2pV3Calls.of(3, "notfrozen"), "V3 built from an unfrozen interface");
        noCalls(SupplicantP2pV3Calls.of(3, AOSP_FROZEN_V3.toUpperCase()), "hash compared exactly");
        noCalls(SupplicantP2pV3Calls.of(3, AOSP_FROZEN_V3 + " "), "hash compared exactly, no trim");
        noCalls(SupplicantP2pV3Calls.of(4, "0000000000000000000000000000000000000000"), "V4 of an unknown hash");
        allCalls(SupplicantP2pV3Calls.of(3, AOSP_FROZEN_V3), "frozen V3");
        allCalls(SupplicantP2pV3Calls.of(4, AOSP_FROZEN_V3), "frozen V3 hash, any version from 3 up");

        // a frozen V3 supplicant that still answers one call as unimplemented: only that call goes off
        SupplicantP2pV3Calls c = SupplicantP2pV3Calls.of(3, AOSP_FROZEN_V3);
        check(!c.dropIfUnimplemented(SupplicantP2pV3Calls.FIND, "Method connectWithParams is unimplemented."),
                "the message of another call drops nothing");
        check(c.has(SupplicantP2pV3Calls.FIND), "find stays after another call's message");
        check(!c.dropIfUnimplemented(SupplicantP2pV3Calls.FIND, "Failed transaction"), "a dead binder drops nothing");
        check(!c.dropIfUnimplemented(SupplicantP2pV3Calls.FIND, null), "no message drops nothing");
        check(!c.dropIfUnimplemented(SupplicantP2pV3Calls.FIND, "Method findWithParams is unimplemented"),
                "the proxy's exact text only");
        check(c.has(SupplicantP2pV3Calls.FIND), "find stays after foreign errors");
        check(c.dropIfUnimplemented(SupplicantP2pV3Calls.FIND, "Method findWithParams is unimplemented."),
                "the proxy's unimplemented text drops the call");
        check(!c.has(SupplicantP2pV3Calls.FIND), "find is off after unimplemented");
        check(c.has(SupplicantP2pV3Calls.CONNECT) && c.has(SupplicantP2pV3Calls.EXT_LISTEN), "the others stay");
        check(c.dropIfUnimplemented(SupplicantP2pV3Calls.EXT_LISTEN,
                "Method configureExtListenWithParams is unimplemented."), "listen drops");
        check(c.dropIfUnimplemented(SupplicantP2pV3Calls.CONNECT, "Method connectWithParams is unimplemented."),
                "connect drops");
        noCalls(c, "all three dropped");
        check(c.dropIfUnimplemented(SupplicantP2pV3Calls.CONNECT, "Method connectWithParams is unimplemented."),
                "a second unimplemented answer is still recognised (the caller then uses the V2 call)");
        noCalls(c, "dropping twice adds nothing back");

        String d = SupplicantP2pV3Calls.of(3, MTK_DRAFT).toString();
        check(d.contains(MTK_DRAFT) && d.contains("V2 calls") && d.contains("V3"), "log names hash and choice: " + d);
        String f = SupplicantP2pV3Calls.of(3, AOSP_FROZEN_V3).toString();
        check(f.contains("frozen V3") && f.contains("connectWithParams"), "log of a frozen supplicant: " + f);
        System.out.println("SupplicantP2pV3CallsTest OK (" + sChecks + " checks)");
    }

    private static void noCalls(SupplicantP2pV3Calls c, String what) {
        for (String call : CALLS) {
            check(!c.has(call), what + ": no " + call);
        }
    }

    private static void allCalls(SupplicantP2pV3Calls c, String what) {
        for (String call : CALLS) {
            check(c.has(call), what + ": " + call);
        }
    }

    private static void check(boolean ok, String what) {
        sChecks++;
        if (!ok) {
            System.out.println("FAIL " + what);
            System.exit(1);
        }
    }
}
