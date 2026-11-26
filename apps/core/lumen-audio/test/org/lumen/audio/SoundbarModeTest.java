// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import org.lumen.testkit.Check;

import java.io.File;

/**
 * Host checks of SoundbarMode (CEC System Audio and CEC volume control for a sound output choice) with the soundbar
 * logs it replays, then the wiring: the HB and X50 choices ask SoundbarMode.requestOnChoice, both rule classes delegate
 * to SoundbarMode instead of a copy. Argument: the gsi folder.
 */
public final class SoundbarModeTest {
    private static final Check C = new Check("lumen-audio soundbar");

    public static void main(String[] args) throws Exception {
        rules();
        replays();
        wiring(new File(args[0]));
        C.done();
    }

    private static void rules() {
        C.eq("Speaker = unchanged (stock: setForceUse only, ARC stays up)", null, SoundbarMode.systemAudioFor("SPEAKER"));
        C.eq("Soundbar = on", Boolean.TRUE, SoundbarMode.systemAudioFor("ARC"));
        C.eq("Auto = on", Boolean.TRUE, SoundbarMode.systemAudioFor("AUTO"));
        C.eq("Bluetooth = unchanged", null, SoundbarMode.systemAudioFor("BT"));
        C.eq("wired = unchanged", null, SoundbarMode.systemAudioFor("WIRED"));
        C.eq("USB = unchanged", null, SoundbarMode.systemAudioFor("USB"));
        C.eq("no choice = unchanged", null, SoundbarMode.systemAudioFor(null));
        C.ok("change: off when on", SoundbarMode.systemAudioChange(false, true, true, true, true));
        C.ok("change: off when off, nothing", !SoundbarMode.systemAudioChange(false, true, true, true, false));
        C.ok("change: on when off", SoundbarMode.systemAudioChange(true, true, true, true, false));
        C.ok("change: on when on, nothing", !SoundbarMode.systemAudioChange(true, true, true, true, true));
        C.ok("change: CEC off", !SoundbarMode.systemAudioChange(true, false, true, true, false));
        C.ok("change: soundbar switch off", !SoundbarMode.systemAudioChange(true, true, false, true, false));
        C.ok("change: no audio system", !SoundbarMode.systemAudioChange(true, true, true, false, false));
        C.ok("change: unknown wanted", !SoundbarMode.systemAudioChange(null, true, true, true, true));

        // a user's choice: the soundbar is asked also while System Audio is on, the other choices only on a change
        C.ok("choice Soundbar, System Audio on: asked again", SoundbarMode.requestOnChoice("ARC", true, true, true, true));
        C.ok("choice Soundbar, System Audio off: asked", SoundbarMode.requestOnChoice("ARC", true, true, true, false));
        C.ok("choice Soundbar, CEC off: never", !SoundbarMode.requestOnChoice("ARC", false, true, true, true)
                && !SoundbarMode.requestOnChoice("ARC", false, true, true, false));
        C.ok("choice Soundbar, soundbar switch off: never", !SoundbarMode.requestOnChoice("ARC", true, false, true, true)
                && !SoundbarMode.requestOnChoice("ARC", true, false, true, false));
        C.ok("choice Soundbar, no audio system: never", !SoundbarMode.requestOnChoice("ARC", true, true, false, true)
                && !SoundbarMode.requestOnChoice("ARC", true, true, false, false));
        C.ok("choice Auto, System Audio on: nothing (Auto does not pull the soundbar off a Bluetooth source)",
                !SoundbarMode.requestOnChoice("AUTO", true, true, true, true));
        C.ok("choice Auto, System Audio off: asked", SoundbarMode.requestOnChoice("AUTO", true, true, true, false));
        C.ok("choice Speaker, System Audio on: nothing (ARC stays up)", !SoundbarMode.requestOnChoice("SPEAKER", true, true, true, true));
        C.ok("choice Speaker, System Audio off: nothing", !SoundbarMode.requestOnChoice("SPEAKER", true, true, true, false));
        for (String k : new String[] {"BT", "WIRED", "USB", null}) {
            C.ok("choice " + k + ": never", !SoundbarMode.requestOnChoice(k, true, true, true, true)
                    && !SoundbarMode.requestOnChoice(k, true, true, true, false));
        }

        C.eq("CEC volume: Speaker off", Boolean.FALSE, SoundbarMode.cecVolumeFor("SPEAKER", Boolean.TRUE));
        C.eq("CEC volume: Bluetooth off", Boolean.FALSE, SoundbarMode.cecVolumeFor("BT", Boolean.TRUE));
        C.eq("CEC volume: wired off", Boolean.FALSE, SoundbarMode.cecVolumeFor("WIRED", Boolean.TRUE));
        C.eq("CEC volume: USB off", Boolean.FALSE, SoundbarMode.cecVolumeFor("USB", null));
        C.eq("CEC volume: Soundbar = the switch", Boolean.TRUE, SoundbarMode.cecVolumeFor("ARC", Boolean.TRUE));
        C.eq("CEC volume: Soundbar, switch off", Boolean.FALSE, SoundbarMode.cecVolumeFor("ARC", Boolean.FALSE));
        C.eq("CEC volume: Auto, switch unknown = leave", null, SoundbarMode.cecVolumeFor("AUTO", null));
        C.eq("CEC volume: no choice = leave", null, SoundbarMode.cecVolumeFor(null, Boolean.TRUE));
        C.ok("CEC volume change: differs", SoundbarMode.cecVolumeChange(false, true) && SoundbarMode.cecVolumeChange(true, false));
        C.ok("CEC volume change: same or unknown", !SoundbarMode.cecVolumeChange(false, false)
                && !SoundbarMode.cecVolumeChange(false, null) && !SoundbarMode.cecVolumeChange(null, true));
    }

    /**
     * The soundbar's System Audio flag against its input, as the HB and X50 rigs logged it (2026-11-14). The model: an
     * LG soundbar plays ARC only on its TV input; its own BT key broadcasts System Audio off; a request switches it back.
     */
    private static void replays() {
        // HB 18:41:53 / 18:45:58: SK8 BT key -> flag off, Soundbar chosen -> asked -> TV input again
        Bar sk8 = new Bar();
        sk8.btKey();
        sk8.choose("ARC");
        C.ok("HB 18:45: Soundbar after the BT key, flag off: back on ARC", sk8.onArc);
        // beta14 report (the log before the reboot is lost): the flag on while the soundbar listens to another input,
        // Soundbar chosen. Before the fix nothing was sent and only the boot's System Audio initiation asked again
        Bar b14 = new Bar();
        b14.flag = true;
        b14.onArc = false;
        b14.choose("ARC");
        C.ok("beta14: Soundbar with the flag on and the soundbar on Bluetooth: asked, back on ARC", b14.onArc && b14.asked == 1);
        // Auto keeps a soundbar the user moved to Bluetooth there while the flag is on
        Bar auto = new Bar();
        auto.flag = true;
        auto.onArc = false;
        auto.choose("AUTO");
        C.ok("Auto with the flag on: nothing asked", auto.asked == 0 && !auto.onArc);
        // X50 23:17:22 / 23:17:51: SQ80R BT input -> flag off, a request -> back
        Bar sq80 = new Bar();
        sq80.btKey();
        C.ok("X50 23:17: the BT input clears the flag", !sq80.flag && !sq80.onArc);
        sq80.choose("ARC");
        C.ok("X50 23:17: Soundbar asks, the soundbar answers on", sq80.flag && sq80.onArc);
        // HB 2026-11-15 (Slava, Kodi playing): Speaker, then the soundbar. Up to beta15 Speaker ended ARC and the soundbar
        // choice initiated it again under a running stream: the SK8 stayed silent until the stream restarted. Stock and
        // now: Speaker keeps ARC, the soundbar choice only asks (no new ARC initiation), Auto asks nothing.
        Bar k = new Bar();
        k.choose("SPEAKER");
        C.ok("HB 11-15: Speaker keeps System Audio and ARC", k.flag && k.onArc && k.asked == 0);
        k.choose("ARC");
        C.ok("HB 11-15: Speaker -> Soundbar: no ARC initiation under the stream", k.onArc && k.arcInits == 0);
        k.choose("SPEAKER");
        k.choose("AUTO");
        C.ok("HB 11-15: Speaker -> Auto: no ARC initiation", k.onArc && k.arcInits == 0);
    }

    /** An LG soundbar as the rigs showed it, driven by SoundbarMode.requestOnChoice like HbSystemAudio / X50 AudioOutput. */
    private static final class Bar {
        boolean flag = true, onArc = true;
        int asked, arcInits;

        void btKey() { onArc = false; flag = false; }

        void choose(String kind) {
            if (!SoundbarMode.requestOnChoice(kind, true, true, true, flag)) return;
            Boolean want = SoundbarMode.systemAudioFor(kind);
            asked++;
            flag = want;
            if (want && !onArc) arcInits++;
            onArc = want;
        }
    }

    private static String read(File g, String rel) throws Exception { return Check.read(new File(g, rel)); }

    private static void wiring(File g) throws Exception {
        String hb = read(g, "apps/HbProjector/src/org/lumen/projector/cec/HbSystemAudio.java");
        C.ok("HB: a choice asks SoundbarMode.requestOnChoice", Check.countCode(hb, "SoundbarMode.requestOnChoice(kind, cecOn, controlOn, avr, modeOn)") == 1);
        C.ok("HB: no own change rule for a choice", Check.countCode(hb, "HbAudioRules.systemAudioChange(") == 0);
        String x = read(g, "apps/X50Projector/src/org/lumen/x50/projector/AudioOutput.java");
        C.ok("X50: a choice asks SoundbarMode.requestOnChoice", Check.countCode(x, "SoundbarMode.requestOnChoice(kind,") == 1);
        C.ok("X50: no own change rule for a choice", Check.countCode(x, "AudioRules.systemAudioChange(") == 0);
        String[][] rules = {
                {"apps/HbProjector/src/org/lumen/projector/audio/HbAudioRules.java", "HB"},
                {"apps/X50Projector/src/org/lumen/x50/projector/AudioRules.java", "X50"}};
        for (String[] r : rules) {
            String s = read(g, r[0]);
            C.ok(r[1] + " rules delegate System Audio and CEC volume to SoundbarMode",
                    Check.countCode(s, "return SoundbarMode.systemAudioFor(kind);") == 1
                            && Check.countCode(s, "return SoundbarMode.systemAudioChange(want, cecOn, controlOn, avrKnown, modeOn);") == 1
                            && Check.countCode(s, "return SoundbarMode.cecVolumeFor(kind, controlOn);") == 1
                            && Check.countCode(s, "return SoundbarMode.cecVolumeChange(want, current);") == 1);
            C.ok(r[1] + " rules: no automatic System Audio off (Speaker leaves it, as stock)",
                    Check.countCode(s, "autoSystemAudioOff") == 0 && Check.countCode(s, "AUTO_OFF_GAP_MS") == 0);
            C.ok(r[1] + " rules: no copy of the mode rule", Check.countCode(s, "return want != modeOn;") == 0);
        }
    }
}
