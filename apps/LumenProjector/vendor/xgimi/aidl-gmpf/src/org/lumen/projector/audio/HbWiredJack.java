// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
/*
 * HbWiredJack: the 3.5 mm jack of the HB as a sound output (2026-11-10, 4PDA report: an HDMI stick played only on the
 * projector speaker, never on a soundbar or headphones on the jack, and the output menu offered only Auto / Projector /
 * Bluetooth). HB-only file, no HAL access: the HB adapter of lumen-audio JackOutput, which holds the rule.
 *
 * Why the row was missing: AudioOutputChoice listed "Headphones" only while AudioManager lists a wired output, and on
 * the HB nothing ever connects one. The kernel has no h2w switch ("This kernel does not have wired headset support" in
 * every Lumen boot log) and the stock framework-res has config_useDevInputEventForAudioJack=false too, so the stock
 * system never sees a plug either. Stock lists the row anyway (GimiAudioService.getAudioDeviceForNewSDK: the headset bit
 * whenever support_wired_headphone is not "false") and routes media there when the user picks it
 * (setAudioDeviceOn(4) -> setForceUse(FOR_MEDIA, FORCE_HEADPHONES) -> libxgimiaudiopolicy -> 59(4) and
 * xgimi_audiopath_switch_end=4), never connecting or disconnecting anything.
 *
 * Lumen: JackOutput connects the wired headphone output once and keeps it (2026-11-15: removing it when the user left
 * Headphones broadcast ACTION_HEADSET_PLUG state 0 and Kodi paused), out of the default media route unless it is the
 * choice. AudioOutputChoice makes it the preferred media device while chosen, the shared path switch reports path 4
 * (59(4)) and stores headphone_volume before switch_end=4 (lumen-audio PathSwitch / StockVolume), and the HDMI input
 * sound, which the vendor DSP plays on the reported path, follows. Auto never picks it (AutoOutput, jack not sensed).
 *
 * Thread: z9x-audioout (AudioOutputChoice.apply).
 */
package org.lumen.projector.audio;

import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;

import org.lumen.audio.JackOutput;
import org.lumen.projector.hal.HbFeatures;

public final class HbWiredJack {
    /** The HB kernel senses no plug: the wired output is never an Auto candidate (AutoOutput). */
    static final boolean SENSED = false;

    private HbWiredJack() {}

    /** The projector has the jack: the row is always listed (stock getSupportWiredHeadphone). */
    static boolean offered() {
        try {
            return HbFeatures.on(HbFeatures.F.WIRED_HEADPHONE);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Start of an evaluation (lumen-audio JackOutput.keep); true when it connected the output now. */
    public static boolean keep(AudioManager am, AudioProductStrategy media, AudioOutputKind chosen, String why) {
        return offered() && JackOutput.keep(am, media, chosen == AudioOutputKind.WIRED, why);
    }

    /** End of an evaluation, after the preferred device (lumen-audio JackOutput.settle). */
    public static void settle(AudioManager am, AudioProductStrategy media, AudioOutputKind chosen, String why) {
        if (offered()) JackOutput.settle(am, media, chosen == AudioOutputKind.WIRED, why);
    }
}
