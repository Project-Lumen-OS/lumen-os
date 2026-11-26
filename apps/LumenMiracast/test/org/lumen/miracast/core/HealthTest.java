// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.miracast.core;

/** Host checks of the Wi-Fi Direct link rule (P2pHealth) and the status it feeds: sh test/run_core.sh runs them. */
public final class HealthTest {
    private static int pass, fail;
    private static final int ERR = P2pHealth.REASON_ERROR, BUSY = P2pHealth.REASON_BUSY;
    private static final long M = 60_000L;

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
        P2pHealth h = new P2pHealth();
        eq("a new link has no fault", P2pHealth.Fault.NONE, h.fault());

        // BUSY: Wi-Fi is connecting, a wait and never a restart
        for (int i = 0; i < 10; i++) eq("BUSY never restarts " + i, P2pHealth.Action.NONE, h.failed(BUSY, i * M));
        eq("BUSY shows the wait for Wi-Fi", P2pHealth.Fault.WAITING_WIFI, h.fault());
        eq("BUSY is not lasting", false, P2pHealth.lasting(h.fault()));
        h.ok();
        eq("a success ends the wait", P2pHealth.Fault.NONE, h.fault());
        h.failed(BUSY, 0);
        h.wifiOff();
        eq("Wi-Fi off ends the wait", P2pHealth.Fault.NONE, h.fault());

        // a test Z9X: every call answers ERROR
        h = new P2pHealth();
        eq("one ERROR is not enough", P2pHealth.Action.NONE, h.failed(ERR, 0));
        eq("two ERRORs in a row reopen the channel", P2pHealth.Action.RESTART, h.failed(ERR, M));
        eq("the status says it restarts", P2pHealth.Fault.RESTARTING, h.fault());
        eq("restarting is not lasting", false, P2pHealth.lasting(h.fault()));
        eq("the count starts again after a restart", P2pHealth.Action.NONE, h.failed(ERR, 2 * M));
        eq("second restart", P2pHealth.Action.RESTART, h.failed(ERR, 3 * M));
        h.failed(ERR, 4 * M);
        eq("third restart", P2pHealth.Action.RESTART, h.failed(ERR, 5 * M));
        h.failed(ERR, 6 * M);
        eq("no fourth restart inside the window", P2pHealth.Action.NONE, h.failed(ERR, 7 * M));
        eq("then it does not answer", P2pHealth.Fault.NOT_ANSWERING, h.fault());
        eq("not answering is lasting", true, P2pHealth.lasting(h.fault()));
        h.failed(ERR, 8 * M);
        eq("still not answering inside the window", P2pHealth.Action.NONE, h.failed(ERR, 9 * M));
        eq("the oldest restart left the window: one more try", P2pHealth.Action.RESTART,
                h.failed(ERR, M + P2pHealth.RESTART_WINDOW_MS));
        eq("a new try shows the restart again", P2pHealth.Fault.RESTARTING, h.fault());
        h.ok();
        eq("a success after a restart clears it", P2pHealth.Fault.NONE, h.fault());

        // a success between two errors: no restart
        h = new P2pHealth();
        h.failed(ERR, 0);
        h.ok();
        eq("errors must come in a row", P2pHealth.Action.NONE, h.failed(ERR, M));
        eq("an unknown reason changes nothing", P2pHealth.Action.NONE, h.failed(7, M));
        eq("unsupported changes nothing here (P2pCapability decides)", P2pHealth.Action.NONE, h.failed(1, M));

        // the link died after an accepted connection
        h = new P2pHealth();
        eq("reopen at once", P2pHealth.Action.RESTART, h.lostAfterAccept());
        eq("the cause is the connection", P2pHealth.Fault.CONNECT_BROKEN, h.fault());
        eq("it is lasting", true, P2pHealth.lasting(h.fault()));
        h.ok();
        eq("a working link keeps the connection fault", P2pHealth.Fault.CONNECT_BROKEN, h.fault());
        for (int i = 0; i < 6; i++) h.lostAfterAccept();
        eq("accept losses are outside the restart budget", P2pHealth.Action.RESTART, h.lostAfterAccept());
        h = new P2pHealth();
        h.knownConnectBroken();
        eq("known from an earlier start", P2pHealth.Fault.CONNECT_BROKEN, h.fault());
        eq("and reported as such", true, h.connectBroken());

        // the status the settings screen, the quick menu and the casting screen show
        eq("ready", ReceiverStatus.READY, Control.status(true, true, false, "VISIBLE", "NONE"));
        eq("restarting reads as starting", ReceiverStatus.STARTING, Control.status(true, true, false, "STARTING", "RESTARTING"));
        eq("waiting for Wi-Fi reads as starting", ReceiverStatus.STARTING,
                Control.status(true, true, false, "STARTING", "WAITING_WIFI"));
        eq("not answering is a fault", ReceiverStatus.FAULT, Control.status(true, true, false, "STARTING", "NOT_ANSWERING"));
        eq("a broken connection is a fault while visible", ReceiverStatus.FAULT,
                Control.status(true, true, false, "VISIBLE", "CONNECT_BROKEN"));
        eq("off wins over a fault", ReceiverStatus.OFF, Control.status(false, true, false, "STARTING", "NOT_ANSWERING"));
        eq("a running session wins over a fault", ReceiverStatus.IN_USE,
                Control.status(true, true, true, "CONNECTED", "CONNECT_BROKEN"));
        eq("Wi-Fi off wins over a fault", ReceiverStatus.NO_WIFI, Control.status(true, true, false, "WIFI_OFF", "NOT_ANSWERING"));
        eq("no fault given = the old rule", ReceiverStatus.READY, Control.status(true, true, false, "VISIBLE", null));

        System.out.println("HealthTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
