// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/** Host checks of the Wi-Fi Direct capability rule (P2pCapability): sh test/run_core.sh runs them. */
public final class CapabilityTest {
    private static int pass, fail;
    private static final long W = P2pCapability.PROOF_WINDOW_MS;

    private static void eq(String name, Object want, Object got) {
        boolean ok = want == null ? got == null : want.equals(got);
        if (ok) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name + ": want " + want + " got " + got);
        }
    }

    public static void main(String[] a) {
        P2pCapability c = new P2pCapability();
        eq("starts unknown", P2pVerdict.UNKNOWN, c.verdict());
        eq("nothing to wait for with Wi-Fi off", -1L, c.nextCheckIn(0));
        eq("Wi-Fi off decides nothing", P2pVerdict.UNKNOWN, c.check(10 * W));

        // a capable unit: Wi-Fi on, a call succeeds
        c = new P2pCapability();
        c.wifi(true, 1_000);
        eq("window counted from Wi-Fi on", W, c.nextCheckIn(1_000));
        eq("half the window left", W / 2, c.nextCheckIn(1_000 + W / 2));
        eq("not yet NO inside the window", P2pVerdict.UNKNOWN, c.check(1_000 + W - 1));
        eq("a successful call proves it", P2pVerdict.YES, c.proven("setDeviceName ok"));
        eq("proof is kept after the window", P2pVerdict.YES, c.check(1_000 + 10 * W));
        eq("nothing to wait for after a proof", -1L, c.nextCheckIn(1_000 + W));
        eq("a later BUSY does not take it back", P2pVerdict.YES, c.callFailed(2, "discoverPeers"));
        eq("an unsupported answer after a proof does not take it back", P2pVerdict.YES,
                c.callFailed(P2pCapability.REASON_UNSUPPORTED, "startListening"));
        eq("proof is not definitive", false, c.definitive());

        // a chip without Wi-Fi Direct: Wi-Fi on for the whole window, every call failing
        c = new P2pCapability();
        c.wifi(true, 0);
        c.callFailed(2, "setDeviceName");
        eq("BUSY alone decides nothing", P2pVerdict.UNKNOWN, c.verdict());
        eq("NO when the window ran out", P2pVerdict.NO, c.check(W));
        eq("a window NO is not definitive", false, c.definitive());
        eq("a later success turns it into YES", P2pVerdict.YES, c.proven("startListening ok"));

        // Wi-Fi off in between starts the window again
        c = new P2pCapability();
        c.wifi(true, 0);
        c.wifi(false, W / 2);
        eq("Wi-Fi off stops the window", -1L, c.nextCheckIn(W / 2));
        eq("no NO while Wi-Fi was off", P2pVerdict.UNKNOWN, c.check(W + 1));
        c.wifi(true, 2 * W);
        eq("window starts again at Wi-Fi on", P2pVerdict.UNKNOWN, c.check(3 * W - 1));
        eq("NO a full window after Wi-Fi came back", P2pVerdict.NO, c.check(3 * W));
        c = new P2pCapability();
        c.wifi(true, 0);
        c.wifi(true, W / 2);
        eq("a second Wi-Fi on keeps the first start", P2pVerdict.NO, c.check(W));

        // no framework at all
        c = new P2pCapability();
        eq("no manager is NO", P2pVerdict.NO, c.noFramework("no WifiP2pManager"));
        eq("no manager is definitive", true, c.definitive());
        eq("nothing proves it later", P2pVerdict.NO, c.proven("?"));
        c = new P2pCapability();
        c.wifi(true, 0);
        eq("P2P_UNSUPPORTED is NO", P2pVerdict.NO, c.callFailed(P2pCapability.REASON_UNSUPPORTED, "setWfdInfo"));
        eq("P2P_UNSUPPORTED is definitive", true, c.definitive());
        eq("the reason is kept", "setWfdInfo: unsupported", c.why());

        // the control provider follows the verdict
        eq("YES: provider on", Boolean.TRUE, P2pCapability.providerOn(P2pVerdict.YES));
        eq("NO: provider off", Boolean.FALSE, P2pCapability.providerOn(P2pVerdict.NO));
        eq("UNKNOWN: provider left as it is", null, P2pCapability.providerOn(P2pVerdict.UNKNOWN));
        eq("null: provider left as it is", null, P2pCapability.providerOn(null));

        System.out.println("capability: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
