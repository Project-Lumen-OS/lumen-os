// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.util.ArrayList;
import java.util.List;

/** Host checks of the stage diagnostics and the Wi-Fi Direct rules. Run by test/run_core.sh. */
public final class DiagTest {
    private static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    static void eq(Object want, Object got, String what) {
        check(want == null ? got == null : want.equals(got), what + " want=" + want + " got=" + got);
    }

    public static void main(String[] a) {
        diag();
        approver();
        prompt();
        port();
        endReason();
        System.out.println("miracast diag: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void endReason() {
        eq(Diag.Stage.SOURCE_IP, EndReason.failedStage("source not found", false, false, false, false), "end: no source address");
        eq(Diag.Stage.RTSP_TCP, EndReason.failedStage("connect failed: ECONNREFUSED", false, false, false, false), "end: no RTSP port");
        eq(Diag.Stage.RTSP, EndReason.failedStage("source closed the connection", false, false, false, false), "end: source hung up before PLAY");
        eq(Diag.Stage.RTSP, EndReason.failedStage("SETUP refused 461", false, false, false, false), "end: refused SETUP");
        eq(Diag.Stage.RTSP, EndReason.failedStage("source silent (keep-alive timeout)", false, false, false, false), "end: no M1");
        eq(null, EndReason.failedStage("cancelled", false, false, false, false), "end: cancelled is not a failure");
        eq(null, EndReason.failedStage("back", true, false, false, false), "end: the user left before PLAY");
        eq(Diag.Stage.GROUP, EndReason.failedStage("wifi direct group gone", true, false, false, false), "end: group lost before PLAY");
        eq(Diag.Stage.MEDIA, EndReason.failedStage("no media for 15002 ms", true, true, false, false), "end: PLAY but no RTP");
        eq(Diag.Stage.MEDIA, EndReason.failedStage("back", true, true, false, false), "end: BACK on a black screen still names the stage");
        eq(Diag.Stage.FIRST_FRAME, EndReason.failedStage("source teardown", false, true, true, false), "end: RTP but no picture");
        eq(null, EndReason.failedStage("source teardown", false, true, true, true), "end: a normal session");
        eq(null, EndReason.failedStage("back", true, true, true, true), "end: BACK after mirroring");
        eq(Diag.Stage.MEDIA, EndReason.failedStage("no free RTP port", false, false, false, false), "end: no RTP port");
        eq(Diag.Stage.RTSP, EndReason.failedStage(null, false, false, false, false), "end: null reason");
    }

    static void diag() {
        long[] now = {1_000_000};
        List<String> lines = new ArrayList<>();
        Diag d = new Diag(() -> now[0], lines::add);
        eq("nothing yet", d.verdict(), "diag: empty");
        d.ok(Diag.Stage.SERVICE, "started");
        d.ok(Diag.Stage.P2P, "enabled");
        eq("waiting for NAME (Wi-Fi Direct name set)", d.verdict(), "diag: next setup stage named");
        d.ok(Diag.Stage.NAME, "Lumen");
        d.ok(Diag.Stage.WFD_IE, "0011 1c44 0032");
        d.ok(Diag.Stage.APPROVER, "attached");
        d.ok(Diag.Stage.VISIBLE, "listen");
        eq("ready, no sender has asked yet", d.verdict(), "diag: ready");
        eq("stage=VISIBLE status=OK detail=listen", lines.get(lines.size() - 1), "diag: logcat line format");
        eq(6, lines.size(), "diag: one line per report");
        // an attempt that stops at the source address
        now[0] += 5000;
        d.newAttempt("Galaxy S23 pbc");
        eq(1, d.attempt(), "diag: attempt counted");
        d.ok(Diag.Stage.ANSWER, "allowed");
        now[0] += 1200;
        d.ok(Diag.Stage.GROUP, "go=true iface=p2p-p2p0-0");
        eq(Diag.Stage.GROUP, d.furthest(), "diag: furthest");
        eq("waiting for SOURCE_IP (sender address found)", d.verdict(), "diag: waiting at the next stage");
        now[0] += 20000;
        d.fail(Diag.Stage.SOURCE_IP, "no DHCP lease, no ARP entry, sweep found nothing in 20 s");
        check(d.verdict().startsWith("stopped at SOURCE_IP"), "diag: verdict names the failed stage: " + d.verdict());
        String r = d.report();
        check(r.contains("FAIL SOURCE_IP"), "diag: report row for the failure\n" + r);
        check(r.contains("+21200ms"), "diag: attempt rows relative to the request\n" + r);
        check(r.contains("OK   VISIBLE"), "diag: setup rows kept\n" + r);
        // the next attempt starts clean but keeps the setup stages
        d.newAttempt("DESKTOP-1 pbc");
        eq(null, d.get(Diag.Stage.SOURCE_IP), "diag: attempt stages cleared");
        check(d.get(Diag.Stage.VISIBLE) != null, "diag: setup stages kept");
        eq(2, d.attempt(), "diag: second attempt");
        d.ok(Diag.Stage.ANSWER, "allowed");
        d.ok(Diag.Stage.GROUP, "go=false");
        d.ok(Diag.Stage.SOURCE_IP, "198.51.100.1 group owner");
        d.ok(Diag.Stage.RTSP_TCP, "198.51.100.1:7236");
        d.ok(Diag.Stage.RTSP, "M3 answered 16 of 27");
        d.ok(Diag.Stage.PLAYING, "session 6B8B4567");
        d.ok(Diag.Stage.SCREEN, "resumed");
        d.ok(Diag.Stage.MEDIA, "first RTP");
        d.ok(Diag.Stage.DECODER, "c2.mtk.avc.decoder");
        d.ok(Diag.Stage.FIRST_FRAME, "1920x1080");
        eq("mirroring", d.verdict(), "diag: mirroring");
        d.ok(Diag.Stage.END, "source teardown");
        eq("ended after FIRST_FRAME: source teardown", d.verdict(), "diag: end keeps the furthest stage");
        // a setup failure outranks the attempt
        d.fail(Diag.Stage.WFD_IE, "setWfdInfo failed (0)");
        check(d.verdict().startsWith("stopped at WFD_IE"), "diag: setup failure first: " + d.verdict());
        // a later OK of the same stage clears its failure
        d.ok(Diag.Stage.WFD_IE, "set again");
        eq("ended after FIRST_FRAME: source teardown", d.verdict(), "diag: recovered stage no longer failing");
        // the event ring is bounded, details are one line and clipped
        for (int i = 0; i < 200; i++) d.ok(Diag.Stage.MEDIA, "x\ny" + i);
        String rr = d.report();
        int recent = rr.substring(rr.indexOf("recent:")).split("\n").length - 1;
        eq(Diag.MAX_EVENTS, recent, "diag: event ring bounded");
        check(!lines.get(lines.size() - 1).contains("\n"), "diag: one line per event");
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 100; i++) big.append("0123456789");
        d.ok(Diag.Stage.MEDIA, big.toString());
        check(lines.get(lines.size() - 1).length() < 360, "diag: long detail clipped");
    }

    static void approver() {
        check(P2pRules.reattach(P2pRules.DETACH_REMOVE, true, false),
                "approver: the framework's removal after an answer is undone (next request asks again)");
        check(!P2pRules.reattach(P2pRules.DETACH_REMOVE, true, true), "approver: our own removal stays");
        check(P2pRules.reattach(P2pRules.DETACH_FAILURE, true, false), "approver: a failed add is retried");
        check(!P2pRules.reattach(P2pRules.DETACH_REPLACE, true, false), "approver: another app's wildcard is not fought");
        check(!P2pRules.reattach(P2pRules.DETACH_CLOSE, true, false), "approver: channel close handled by reopen");
        check(!P2pRules.reattach(P2pRules.DETACH_REMOVE, false, false), "approver: not while stopped");
        eq("remove", P2pRules.detachName(0), "approver: name 0");
        eq("close", P2pRules.detachName(3), "approver: name 3");
    }

    static void prompt() {
        check(P2pRules.ownPrompt(P2pRules.WPS_PBC), "prompt: push button gets our card");
        check(!P2pRules.ownPrompt(P2pRules.WPS_KEYPAD), "prompt: PIN entry to the framework");
        check(!P2pRules.ownPrompt(P2pRules.WPS_DISPLAY), "prompt: PIN display to the framework");
        check(P2pRules.ownPrompt(P2pRules.WPS_INVALID), "prompt: unknown method = push button");
        check(P2pRules.ownPrompt(-1), "prompt: missing config = push button");
        eq("pbc", P2pRules.wpsName(0), "prompt: pbc name");
        eq("pin-keypad", P2pRules.wpsName(2), "prompt: keypad name");
    }

    static void port() {
        eq(7236, P2pRules.controlPort(0, 0), "port: default 7236");
        eq(8554, P2pRules.controlPort(0, 8554), "port: from the request's WFD IE");
        eq(7250, P2pRules.controlPort(7250, 8554), "port: the group record wins");
        eq(7236, P2pRules.controlPort(70000, -1), "port: invalid values ignored");
    }
}
