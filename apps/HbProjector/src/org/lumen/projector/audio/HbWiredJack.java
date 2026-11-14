/*
 * HbWiredJack: the 3.5 mm jack of the HB as a sound output (2026-11-10, 4PDA report: an HDMI stick played only on the
 * projector speaker, never on a soundbar or headphones on the jack, and the output menu offered only Auto / Projector /
 * Bluetooth). HB-only file, no HAL access; the decisions are HbAudioRules.wiredStep / speakerBeforeDisconnect (host
 * tested in test/audio).
 *
 * Why the row was missing: AudioOutputChoice listed "Headphones" only while AudioManager lists a wired output, and on
 * the HB nothing ever connects one. The kernel has no h2w switch ("This kernel does not have wired headset support" in
 * every Lumen boot log) and the stock framework-res has config_useDevInputEventForAudioJack=false too, so the stock
 * system never sees a plug either. Stock lists the row anyway (GimiAudioService.getAudioDeviceForNewSDK: the headset bit
 * whenever support_wired_headphone is not "false") and routes media there when the user picks it
 * (setAudioDeviceOn(4) -> setForceUse(FOR_MEDIA, FORCE_HEADPHONES) -> libxgimiaudiopolicy -> 59(4) and
 * xgimi_audiopath_switch_end=4).
 *
 * Lumen: while "Headphones" is the stored choice, this class connects the wired headphone output the AOSP way
 * (AudioManager.setWiredDeviceConnectionState, what WiredAccessoryManager sends for a real jack; MODIFY_AUDIO_ROUTING,
 * held by the system uid app). AudioOutputChoice then makes it the preferred media device, the media routing moves to
 * it, AudioPathReporter reports path 4 (59(4)) and stores headphone_volume before switch_end=4 (HbHeadphoneVolume), and
 * the HDMI input sound, which the vendor DSP plays on the reported path, follows. Another choice removes the output
 * again (only the one this process connected), the speaker made preferred first when the choice is Auto so the removal
 * never sends ACTION_AUDIO_BECOMING_NOISY. The connection lives in AudioService memory (it survives an audio server
 * restart there), so it is made again after every app start while the choice is stored.
 *
 * Thread: z9x-audioout (AudioOutputChoice.apply). Log tag Z9xAudioOut (same as AudioOutputChoice).
 */
package org.lumen.projector.audio;

import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;

import org.lumen.projector.hal.HbFeatures;

final class HbWiredJack {
    private static final String TAG = "Z9xAudioOut";
    /** This process connected the wired output (and has not removed it since). */
    private static volatile boolean sOurs;

    private HbWiredJack() {}

    /** The projector has the jack: the row is always listed (stock getSupportWiredHeadphone). */
    static boolean offered() {
        try {
            return HbFeatures.on(HbFeatures.F.WIRED_HEADPHONE);
        } catch (Throwable t) {
            return false;
        }
    }

    /** A wired headphone / headset output is listed now. */
    static boolean listed(AudioManager am) {
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET) return true;
        }
        return false;
    }

    /** The step for this evaluation (HbAudioRules.wiredStep); NONE where the projector has no jack row. */
    static WiredOutputStep step(AudioManager am, AudioOutputKind chosen) {
        if (!offered() && !sOurs) return WiredOutputStep.NONE;
        return HbAudioRules.wiredStep(chosen.name(), listed(am), sOurs);
    }

    /** Connects (true) or removes (false) the wired headphone output. Returns whether AudioService took the call. */
    static boolean connect(AudioManager am, boolean on, String why) {
        try {
            AudioDeviceAttributes jack = new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_OUTPUT,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "");
            am.setWiredDeviceConnectionState(jack, on ? 1 : 0);
            sOurs = on;
            Log.i(TAG, why + ": 3.5 mm output " + (on ? "connected (Headphones chosen)" : "removed (another output chosen)"));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, why + ": 3.5 mm output " + (on ? "connect" : "remove") + ": " + t);
            return false;
        }
    }
}
