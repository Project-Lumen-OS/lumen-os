// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.audio;

/** Host test of the HB CEC System Audio decisions (the volume rules moved to lumen-audio StockVolume, the jack to JackOutput). */
public final class HbAudioRulesTest {
    private static int fails, checks;

    private static void eq(String what, Object want, Object got) {
        checks++;
        if (want == null ? got != null : !want.equals(got)) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    public static void main(String[] a) {
        // 3. CEC System Audio follows the choice
        eq("Speaker = unchanged (stock, ARC stays up)", null, HbAudioRules.systemAudioFor("SPEAKER"));
        eq("Soundbar = on", Boolean.TRUE, HbAudioRules.systemAudioFor("ARC"));
        eq("Auto = on", Boolean.TRUE, HbAudioRules.systemAudioFor("AUTO"));
        eq("BT = unchanged", null, HbAudioRules.systemAudioFor("BT"));
        eq("wired = unchanged", null, HbAudioRules.systemAudioFor("WIRED"));
        eq("off when on", true, HbAudioRules.systemAudioChange(false, true, true, true, true));
        eq("off when off", false, HbAudioRules.systemAudioChange(false, true, true, true, false));
        eq("on when off", true, HbAudioRules.systemAudioChange(true, true, true, true, false));
        eq("CEC off", false, HbAudioRules.systemAudioChange(false, false, true, true, true));
        eq("control setting off: never touched", false, HbAudioRules.systemAudioChange(false, true, false, true, true));
        eq("no audio system", false, HbAudioRules.systemAudioChange(true, true, true, false, false));
        eq("unchanged choice", false, HbAudioRules.systemAudioChange(null, true, true, true, true));
        // 4. CEC volume control follows the output choice (tester 2026-11-01: Speaker, keys still went to the soundbar)
        eq("Speaker = CEC volume off", Boolean.FALSE, HbAudioRules.cecVolumeFor("SPEAKER", Boolean.TRUE));
        eq("BT = CEC volume off", Boolean.FALSE, HbAudioRules.cecVolumeFor("BT", Boolean.TRUE));
        eq("wired = CEC volume off", Boolean.FALSE, HbAudioRules.cecVolumeFor("WIRED", Boolean.TRUE));
        eq("USB = CEC volume off", Boolean.FALSE, HbAudioRules.cecVolumeFor("USB", null));
        eq("Soundbar = soundbar switch on", Boolean.TRUE, HbAudioRules.cecVolumeFor("ARC", Boolean.TRUE));
        eq("Soundbar, switch off = off", Boolean.FALSE, HbAudioRules.cecVolumeFor("ARC", Boolean.FALSE));
        eq("Auto = soundbar switch", Boolean.TRUE, HbAudioRules.cecVolumeFor("AUTO", Boolean.TRUE));
        eq("Auto, switch unknown = leave", null, HbAudioRules.cecVolumeFor("AUTO", null));
        eq("no choice = leave", null, HbAudioRules.cecVolumeFor(null, Boolean.TRUE));
        eq("volume off when on", true, HbAudioRules.cecVolumeChange(false, true));
        eq("volume off when off", false, HbAudioRules.cecVolumeChange(false, false));
        eq("volume on when off", true, HbAudioRules.cecVolumeChange(true, false));
        eq("volume current unknown", false, HbAudioRules.cecVolumeChange(false, null));
        eq("volume wanted unknown", false, HbAudioRules.cecVolumeChange(null, true));
        // 6. the 3.5 mm jack: lumen-audio JackOutput (kept connected, out of the default route unless chosen), checked there
        if (fails > 0) {
            System.out.println("HbAudioRulesTest: " + fails + " of " + checks + " checks failed");
            System.exit(1);
        }
        System.out.println("HbAudioRulesTest: " + checks + " checks passed");
    }
}
