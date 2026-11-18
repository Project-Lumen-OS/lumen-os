/*
 * HbAudioRules: the HB decisions of the CEC System Audio of an output choice (cec/HbSystemAudio, through lumen-audio
 * SoundbarMode), kept free of Android classes so test/audio/run.sh checks them on the host. The 3.5 mm output the HB
 * connects itself is lumen-audio JackOutput (HbWiredJack). HB-only file, no HAL access. The path switch and the
 * chip levels (the earlier ARC volume spike rules) are lumen-audio PathSwitch / StockVolume, the same on every model.
 */
package org.lumen.projector.audio;

import org.lumen.audio.SoundbarMode;

public final class HbAudioRules {
    private HbAudioRules() {}

    // ------------------------------------------------------------------ CEC System Audio / volume control
    // One rule on every projector (apps/core/lumen-audio SoundbarMode, also the X50 AudioRules). 2026-11-01 hb-t12: with
    // "Speaker" chosen every volume key went to the LG soundbar as <User Control Pressed>: a local output turns CEC volume
    // control off. 2026-11-14: choosing the soundbar asks again while System Audio is on (SoundbarMode.requestOnChoice).
    // 2026-11-15: Speaker leaves System Audio and ARC alone, as stock.

    /** System Audio for a choice (AudioOutputKind name): Soundbar (ARC) and Auto on, anything else (Speaker too) null. */
    public static Boolean systemAudioFor(String kind) {
        return SoundbarMode.systemAudioFor(kind);
    }

    /** A System Audio change: CEC on, the System Audio Control setting on, an audio system known, a mode that differs. */
    public static boolean systemAudioChange(Boolean want, boolean cecOn, boolean controlOn, boolean avrKnown, boolean modeOn) {
        return SoundbarMode.systemAudioChange(want, cecOn, controlOn, avrKnown, modeOn);
    }

    /** CEC volume control for a choice: off for a local output, the soundbar switch for Soundbar / Auto, null = leave it. */
    public static Boolean cecVolumeFor(String kind, Boolean controlOn) {
        return SoundbarMode.cecVolumeFor(kind, controlOn);
    }

    /** Set CEC volume control: only for a known wanted value that differs from a known current one. */
    public static boolean cecVolumeChange(Boolean want, Boolean current) {
        return SoundbarMode.cecVolumeChange(want, current);
    }
}
