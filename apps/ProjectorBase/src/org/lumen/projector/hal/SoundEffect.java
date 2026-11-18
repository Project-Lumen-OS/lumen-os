// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/**
 * Sound mode (QS SoundEnum VERIFIED): 3 AI, 20 Standard, 1 Movie, 2 Music, 12 Sports, 4 Karaoke.
 * v6.3.1 (stock analysis, decoded TAS5805M tables): only 1/2/12/4 select a real G0082 amp
 * table (movie/music/sport/ktv_5805); 20 is "TI58XX mode err" (nothing written, INIT stays after
 * a reboot) and is never sent.
 * v6.4 AI sound (stock analysis, libxgimi Msrv::setSoundEffectsMode @0x159cb74): 61(3) saves
 * AUTO in the vendor DB and stores property xgimi.pkg.effect.mod (or 1) as the pending sub-mode;
 * the vendor "aud2_effect" thread then writes that real table (1/2/12/4). Our AiSoundEngine picks
 * the sub-mode per foreground app with 63 {@link #setAutoSoundEffect}. STANDARD stays here only so
 * a read-back can be recognised.
 *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum SoundEffect {
    AI(3), STANDARD(20), MOVIE(1), MUSIC(2), SPORTS(12), KARAOKE(4);
    public final int wire;
    SoundEffect(int v) { wire = v; }
    public static SoundEffect fromWire(int v) {
        for (SoundEffect m : values()) if (m.wire == v) return m;
        return null;
    }
}
