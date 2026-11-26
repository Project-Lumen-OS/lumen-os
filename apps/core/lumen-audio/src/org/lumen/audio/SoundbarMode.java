// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

/**
 * SoundbarMode: CEC System Audio and CEC volume control for a sound output choice, one rule on every projector whose
 * output choice drives them (Z9X HB cec/HbSystemAudio, X50 AudioOutput). Plain Java, host tested (test/run.sh).
 *
 * Choices are the AudioOutputKind names: SPEAKER, ARC (the soundbar), AUTO, BT, WIRED, USB. System Audio: the soundbar
 * and Auto on, every other choice unchanged. CEC volume control: off for a local output, the CEC page's soundbar switch
 * for the soundbar and Auto. Nothing is sent without CEC on, the soundbar switch on and an audio system known.
 *
 * Speaker leaves System Audio and ARC alone, as stock (GimiAudioService.setAudioDeviceOn only sets FOR_MEDIA; no app of
 * the stock image calls setSystemAudioMode). Lumen 1.0.10 up to beta15 turned System Audio off for Speaker, which ends
 * ARC; the soundbar or Auto chosen later initiated ARC again while media played, and the LG SK8 stayed silent until the
 * stream restarted (HB rig 2026-11-15: Speaker -> ARC silent, BT -> ARC with ARC up all the time fine, Kodi resume
 * fine). The volume keys that once reached the soundbar while Speaker was chosen (hb-t12) stay local through CEC
 * volume control off, which needs no System Audio change.
 *
 * The soundbar choice always asks (HB rig 2026-11-14, LG SK8 on ARC, Lumen 1.0.10-beta14: no ARC sound after the
 * soundbar was chosen again, back only after a reboot). An LG soundbar switched to its Bluetooth input leaves ARC and
 * comes back only on a &lt;System Audio Mode Request&gt; from the TV: SK8 18:41:53 &lt;Set System Audio Mode&gt; off and
 * &lt;Terminate ARC&gt; on its BT key, 18:45:58 back on our request; SQ80R on the X50 rig 23:17:22 off on its BT input,
 * 23:17:51 back on a request. The framework's System Audio flag is only what the soundbar broadcast last, not the input
 * it listens to. The choice asked only while that flag was off, so a soundbar left on another input with the flag on
 * stayed silent until the boot's System Audio initiation asked again. HdmiTvClient.setSystemAudioMode always starts
 * SystemAudioActionFromTv, which sends the request whatever the flag says (Lumen HB 1.0.10-beta14 and stock G0130
 * services.jar), the soundbar answers &lt;Set System Audio Mode&gt; on and ARC is initiated again when it is down.
 */
public final class SoundbarMode {
    public static final String SPEAKER = "SPEAKER", SOUNDBAR = "ARC", AUTO = "AUTO", BT = "BT", WIRED = "WIRED", USB = "USB";

    private SoundbarMode() {}

    /** System Audio for a choice: the soundbar and Auto on, anything else unchanged (null), Speaker too (stock). */
    public static Boolean systemAudioFor(String kind) {
        if (SOUNDBAR.equals(kind) || AUTO.equals(kind)) return Boolean.TRUE;
        return null;
    }

    /** A System Audio change: a known wanted mode that differs, with CEC on, the soundbar switch on, an audio system known. */
    public static boolean systemAudioChange(Boolean want, boolean cecOn, boolean controlOn, boolean avrKnown, boolean modeOn) {
        if (want == null || !cecOn || !controlOn || !avrKnown) return false;
        return want != modeOn;
    }

    /**
     * The user chose {@code kind}: send setSystemAudioMode(systemAudioFor(kind)) now. A change as systemAudioChange, and
     * for the soundbar also while System Audio is on: the request brings the soundbar back to its TV input.
     */
    public static boolean requestOnChoice(String kind, boolean cecOn, boolean controlOn, boolean avrKnown, boolean modeOn) {
        if (systemAudioChange(systemAudioFor(kind), cecOn, controlOn, avrKnown, modeOn)) return true;
        return SOUNDBAR.equals(kind) && cecOn && controlOn && avrKnown;
    }

    /** CEC volume control for a choice: off for a local output, the soundbar switch for the soundbar and Auto, null = leave. */
    public static Boolean cecVolumeFor(String kind, Boolean controlOn) {
        if (SPEAKER.equals(kind) || BT.equals(kind) || WIRED.equals(kind) || USB.equals(kind)) return Boolean.FALSE;
        if (SOUNDBAR.equals(kind) || AUTO.equals(kind)) return controlOn;
        return null;
    }

    /** Set CEC volume control: only a known wanted value that differs from a known current one. */
    public static boolean cecVolumeChange(Boolean want, Boolean current) {
        return want != null && current != null && !want.equals(current);
    }
}
