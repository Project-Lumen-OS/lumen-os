/*
 * HbAudioRules: the decisions of the Z9X HB ARC volume spike fix (device tests,
 * section "ARC volume spike"), kept free of Android classes so test/audio/run.sh checks them on the host.
 * HB-only file, no HAL access. Used by the HB forks source/HdmiVolume and audio/AudioPathReporter and by
 * cec/HbSystemAudio.
 *
 * Paths are the vendor EN_XGIMI_AUDIO_PATH wire values of AudioPathReporter: 0 speaker, 2 ARC, 3 Bluetooth A2DP,
 * 4 wired, 6 USB, -1 unknown.
 */
package org.lumen.projector.audio;

public final class HbAudioRules {
    public static final int PATH_SPEAKER = 0;
    public static final int PATH_ARC = 2;
    public static final int PATH_BT = 3;
    /** The 3.5 mm jack (headphones / line out): stock threadInit maps WIRED_HEADSET / WIRED_HEADPHONE to 4. */
    public static final int PATH_WIRED = 4;
    /** Stock threadInit: 50 x 20 ms after the last change (libxgimiaudiopolicy.so @0x4680). */
    public static final long STOCK_SWITCH_MS = 1_000L;
    /** Lumen 1.0.6 debounce for the other switches (Bluetooth back to the speaker was silent for 2 s with 1 s). */
    public static final long SHORT_SWITCH_MS = 300L;
    /** A switch_start whose switch_end did not follow within this time gets its switch_end anyway (never stay muted). */
    public static final long SWITCH_END_SAFETY_MS = 6_000L;

    private HbAudioRules() {}

    /** What HdmiVolume does after a change of the routed or the confirmed vendor path, HDMI picture or not. */
    public enum VolumeStep {
        /** The speaker is both routed and confirmed: spk_volume follows STREAM_MUSIC. */
        FOLLOW,
        /** The output is not (or not yet) the confirmed speaker: send nothing (never on ARC). */
        HOLD
    }

    /** The media output is the projector's speaker: routed there (latest evaluation) AND confirmed by the vendor. */
    public static boolean speakerConfirmed(int target, int confirmed) {
        return target == PATH_SPEAKER && confirmed == PATH_SPEAKER;
    }

    /**
     * target: AudioPathReporter's latest routed path. confirmed: the path the vendor confirmed (ready + 60), -1 none.
     * 2026-11-01 (hb-t12 "sound to maximum by itself", hb_full.txt 16:52:10-16:53:35): the earlier rule followed the
     * music volume only while an HDMI picture was shown and put spk_volume=100 on the speaker otherwise (the Z9X 1.0.4
     * design, where AudioFlinger's software volume does the rest). On the HB the speaker then stayed at 100 while the
     * volume keys moved STREAM_MUSIC 10 -> 4 (no spk_volume, no mi_device_set line). Stock HB setDspVolumesToHal
     * (services.jar AudioService) writes spk_volume=N for DEVICE_OUT_SPEAKER on every music change, with or without
     * HDMI, and has no HDMI_ARC branch and no 100: so FOLLOW whenever the speaker is confirmed, HOLD otherwise.
     */
    public static VolumeStep volumeStep(int target, int confirmed) {
        return speakerConfirmed(target, confirmed) ? VolumeStep.FOLLOW : VolumeStep.HOLD;
    }

    /**
     * The spk_volume to send now, or -1 for nothing. speaker: the speaker is confirmed (volumeStep FOLLOW). music:
     * STREAM_MUSIC scaled to 0..100 (0 while muted), -1 unknown. sent: the value sent last, -1 none. force: send
     * even when unchanged (start of following: boot, wake, an HDMI picture, the speaker confirmed again; the audio HAL
     * may have lost its level). Never anything off the speaker, never a value other than the music level.
     */
    public static int speakerVolume(boolean speaker, int music, int sent, boolean force) {
        if (!speaker || music < 0 || music > 100) return -1;
        return force || music != sent ? music : -1;
    }

    /**
     * Before switch_end=0 (leaving Bluetooth, or the end of an ARC -> speaker switch; the HAL restores the speaker level
     * from the stored spk_volume): the value to store first, or -1 to keep the stored one. Always the music level, HDMI
     * or not (the 100 stored here before 2026-11-01 was the loud speaker after Speaker was chosen, 16:52:12).
     */
    public static int speakerSinkStore(int sent, int music) {
        return music >= 0 && music <= 100 && music != sent ? music : -1;
    }

    /**
     * The stock HAL mute around a path switch (setXgimiPathForDeviceDelay: xgimi_audiopath_switch_start=P at once,
     * switch_end=P after the 1 s wait) is sent for a switch to ARC, and from ARC back to the speaker: the two paths
     * whose switch_end this app sends. Not for Bluetooth (its own switch_end=3 since 1.0.6) and not for wired / USB
     * (the HAL's default sink is kept: their stored levels are 0, switch_start=P would zero them with no end).
     */
    public static boolean halMute(int prev, int next) {
        if (prev < 0 || next < 0 || prev == next) return false;
        return next == PATH_ARC || (prev == PATH_ARC && next == PATH_SPEAKER);
    }

    /** The wait before 59(P) after a change: stock 1 s around an ARC switch, 300 ms otherwise. */
    public static long switchDelayMs(int prev, int next) {
        return halMute(prev, next) ? STOCK_SWITCH_MS : SHORT_SWITCH_MS;
    }

    /** switch_end=P must store spk_volume first only for the speaker path (its stored level may be 0). */
    public static boolean storeSpeakerBeforeEnd(int path) {
        return path == PATH_SPEAKER;
    }

    /**
     * CEC System Audio for a sound output choice (AudioOutputChoice.Kind name): "SPEAKER" off (AOSP AudioService
     * forwards the volume keys over CEC whenever System Audio is on and the routed device is not an absolute volume
     * device, even the speaker), "ARC" and "AUTO" on (the soundbar back), anything else unchanged (null).
     */
    public static Boolean systemAudioFor(String kind) {
        if ("SPEAKER".equals(kind)) return Boolean.FALSE;
        if ("ARC".equals(kind) || "AUTO".equals(kind)) return Boolean.TRUE;
        return null;
    }

    /**
     * Send a System Audio change: only with CEC on, the framework's System Audio Control setting on (otherwise the
     * framework refuses to turn it on again, and turning it off could not be undone from the panel), an audio system
     * known, and the current mode different from the wanted one.
     */
    public static boolean systemAudioChange(Boolean want, boolean cecOn, boolean controlOn, boolean avrKnown, boolean modeOn) {
        if (want == null || !cecOn || !controlOn || !avrKnown) return false;
        return want != modeOn;
    }

    /**
     * CEC volume control (framework volume_control_enabled, AudioService mHdmiCecVolumeControlEnabled) for a sound
     * output choice (2026-11-01 tester, hb-t12: "Speaker" chosen since the boot, System Audio turned on at the boot by
     * the framework's auto initiation, every volume key went to the soundbar as &lt;User Control Pressed&gt;).
     * A local output ("SPEAKER", "BT", "WIRED", "USB") = off, so no volume key leaves over CEC whatever System Audio
     * does; "ARC" and "AUTO" = the CEC page's soundbar switch (system_audio_control, which CecSettings.setSoundbar
     * keeps equal to volume_control_enabled); null = leave it (unknown choice, or the soundbar switch unknown).
     */
    public static Boolean cecVolumeFor(String kind, Boolean controlOn) {
        if ("SPEAKER".equals(kind) || "BT".equals(kind) || "WIRED".equals(kind) || "USB".equals(kind)) return Boolean.FALSE;
        if ("ARC".equals(kind) || "AUTO".equals(kind)) return controlOn;
        return null;
    }

    /** Set CEC volume control: only for a known wanted value that differs from a known current one. */
    public static boolean cecVolumeChange(Boolean want, Boolean current) {
        return want != null && current != null && !want.equals(current);
    }

    /** Shortest gap between two automatic System Audio switch-offs (no ping-pong with a soundbar that asks again). */
    public static final long AUTO_OFF_GAP_MS = 30_000L;

    /**
     * Turn System Audio off again without a user choice (boot, wake, the soundbar or CecPolicy turned it on, ARC came
     * back): only with "Speaker" chosen, under the same conditions as a choice, and not within AUTO_OFF_GAP_MS of the
     * last automatic switch-off (sinceLastMs < 0 = never).
     */
    public static boolean autoSystemAudioOff(String kind, boolean cecOn, boolean controlOn, boolean avrKnown,
            boolean modeOn, long sinceLastMs) {
        if (!"SPEAKER".equals(kind)) return false;
        if (!systemAudioChange(Boolean.FALSE, cecOn, controlOn, avrKnown, modeOn)) return false;
        return sinceLastMs < 0 || sinceLastMs >= AUTO_OFF_GAP_MS;
    }

    // ------------------------------------------------------------------ the 3.5 mm jack (4PDA report 2026-11-10)
    //
    // The HB kernel reports no jack: no /sys/class/switch/h2w ("This kernel does not have wired headset support" at
    // every Lumen boot) and config_useDevInputEventForAudioJack=false in the stock framework-res as well, so neither
    // stock nor Lumen ever sees a plug. Stock lists "Headphones" anyway: GimiAudioService.getAudioDeviceForNewSDK
    // (ro.build.version.sdk 33, ro.vendor.xgimi.system.isatv unset) sets the headset bit whenever
    // getSupportWiredHeadphone() (feature support_wired_headphone not "false"; absent in the G0130 feature.xml) and the
    // Bluetooth bit always; the user picks the output (voice_output -> setAudioDeviceOn(4) -> FOR_MEDIA headphones).
    // Lumen's AOSP audio policy routes only to a connected device, so the HB app connects the wired headphone output
    // itself while it is the user's choice (AudioManager.setWiredDeviceConnectionState, as WiredAccessoryManager does
    // for a real jack) and removes it again when another output is chosen.

    /** What the HB app does with its wired headphone output on a sound output evaluation. */
    public enum WiredStep {
        /** "Headphones" is the choice and no wired output is listed: connect it (the next evaluation routes to it). */
        CONNECT,
        /** Another output is the choice and the listed wired output is the one this app connected: remove it. */
        DISCONNECT,
        /** Nothing to do. */
        NONE
    }

    /**
     * chosen: the stored AudioOutputChoice.Kind name; wiredListed: AudioManager lists a wired headphone / headset
     * output now; ours: this process connected it (a real jack, if a future kernel reports one, is never removed).
     */
    public static WiredStep wiredStep(String chosen, boolean wiredListed, boolean ours) {
        boolean want = "WIRED".equals(chosen);
        if (want) return wiredListed ? WiredStep.NONE : WiredStep.CONNECT;
        return ours && wiredListed ? WiredStep.DISCONNECT : WiredStep.NONE;
    }

    /**
     * Leaving the headphones for Auto: Auto removes the media role, and AOSP would then keep routing to the still
     * connected wired output, so the disconnect would come while music plays there and AudioService would send
     * ACTION_AUDIO_BECOMING_NOISY (apps pause). The speaker is made the preferred device first, the wired output goes,
     * and the evaluation that follows (devices removed) removes the role for Auto.
     */
    public static boolean speakerBeforeDisconnect(String chosen, WiredStep step) {
        return step == WiredStep.DISCONNECT && ("AUTO".equals(chosen) || chosen == null);
    }

    /** headphone_volume follows STREAM_MUSIC only while the 3.5 mm path is both routed and confirmed by the vendor. */
    public static boolean headphoneConfirmed(int target, int confirmed) {
        return target == PATH_WIRED && confirmed == PATH_WIRED;
    }

    /**
     * headphone_volume to send (stock AudioService setDspVolumesToHal: "headphone_volume=" + index for device 4 / 8),
     * or -1: nothing off the confirmed 3.5 mm path, an out of range level, or the value already sent (unless force).
     */
    public static int headphoneVolume(boolean wired, int music, int sent, boolean force) {
        return speakerVolume(wired, music, sent, force);
    }

    /**
     * switch_end=4 restores the headphone level the HAL stored from headphone_volume (zero from adev_open, like
     * spk_volume), so headphone_volume is stored first.
     */
    public static boolean storeHeadphoneBeforeEnd(int path) {
        return path == PATH_WIRED;
    }

    /**
     * After 59(P) the HAL's sink goes back to the speaker (stored spk_volume, then switch_end=0) when the last sink
     * this process selected was Bluetooth or the 3.5 mm jack and P is not that sink any more.
     */
    public static boolean speakerSinkAfter(int halSink, int target) {
        return (halSink == PATH_BT || halSink == PATH_WIRED) && target != halSink;
    }
}
