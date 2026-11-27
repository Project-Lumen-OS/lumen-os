// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.cec;

/** Host test of cec/EarcRules (eARC the stock way). */
public final class EarcRulesTest {
    private static int fails, checks;

    private static void eq(String what, Object want, Object got) {
        checks++;
        if (want == null ? got != null : !want.equals(got)) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    public static void main(String[] a) {
        EarcStep none = EarcStep.NONE, con = EarcStep.CONNECT, dis = EarcStep.DISCONNECT;
        eq("eARC linked, nothing listed", con, EarcRules.step(3, false, false));
        eq("eARC linked, CEC ARC output listed", none, EarcRules.step(3, false, true));
        eq("eARC linked, ours listed", none, EarcRules.step(3, true, true));
        eq("eARC linked, ours removed by <Terminate ARC>", con, EarcRules.step(3, true, false));
        eq("eARC ended, ours", dis, EarcRules.step(1, true, true));
        eq("eARC off, ours already gone", dis, EarcRules.step(0, true, false));
        eq("plain ARC, never ours", none, EarcRules.step(1, false, true));
        eq("eARC pending (2), nothing listed", none, EarcRules.step(2, false, false));
        eq("read failed (-1)", none, EarcRules.step(-1, false, false));
        // ---- 2026-11-26 (x50-cec-live-1126): Slava's X50 going to sleep with the LG bar on eARC. 14:10:04.879 the
        // framework ends ARC for its standby (output removed), the link still reads 3: stock adds nothing (its
        // framework keeps the output while eARC), Lumen connected one at 04.943 and the vendor switched to ARC again
        eq("asleep, eARC linked, framework removed the output: nothing", none, EarcRules.step(3, true, false, false));
        eq("asleep, eARC linked, nothing ours: nothing", none, EarcRules.step(3, false, false, false));
        eq("asleep, link ended, ours: removed (nothing to switch on)", dis, EarcRules.step(0, true, false, false));
        eq("asleep, ARC listed: nothing", none, EarcRules.step(3, false, true, false));
        eq("awake, eARC linked, nothing listed: connect", con, EarcRules.step(3, false, false, true));
        eq("awake, ours removed by <Terminate ARC>: connect", con, EarcRules.step(3, true, false, true));
        eq("awake, link ended, ours: removed", dis, EarcRules.step(1, true, true, true));
        // ---- cec-fix-1127: the vendor's event 11 instead of a poll; no ARC restart; System Audio keeps CEC ARC's output
        eq("event: linked", 3, EarcRules.eventStatus(11, "eARCstate:1"));
        eq("event: ended", 0, EarcRules.eventStatus(11, "eARCstate:0"));
        eq("event: other type", -1, EarcRules.eventStatus(5, "eARCstate:1"));
        eq("event: other text", -1, EarcRules.eventStatus(11, "picturemode:3"));
        eq("event: no value", -1, EarcRules.eventStatus(11, "eARCstate"));
        eq("event: null", -1, EarcRules.eventStatus(11, null));
        eq("link end, System Audio on: CEC ARC keeps the output", none, EarcRules.afterLinkEnd(dis, true));
        eq("link end, System Audio off: ours removed", dis, EarcRules.afterLinkEnd(dis, false));
        eq("connect is not touched by the System Audio rule", con, EarcRules.afterLinkEnd(con, true));
        // ---- 2026-11-13: the digital output format again on an eARC link change
        eq("format: link up from ARC", true, EarcRules.formatAgain(1, 3));
        eq("format: link down to ARC", true, EarcRules.formatAgain(3, 1));
        eq("format: link down to none", true, EarcRules.formatAgain(3, 0));
        eq("format: no change", false, EarcRules.formatAgain(3, 3));
        eq("format: ARC to pending, no link involved", false, EarcRules.formatAgain(1, 2));
        eq("format: first read of the process", false, EarcRules.formatAgain(-1, 3));
        if (fails > 0) {
            System.out.println("EarcRulesTest: " + fails + " of " + checks + " checks failed");
            System.exit(1);
        }
        System.out.println("EarcRulesTest: " + checks + " checks passed");
    }
}
