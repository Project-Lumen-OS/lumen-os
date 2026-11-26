// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

/**
 * The decisions of the power-on sound ({@link BootSound}) as plain Java: the switch, its first value on a unit, the
 * boot animation gate, when a wake plays, and the WAV the way the AOSP boot animation player (audioplay.cpp
 * parseClipBuf) reads it.
 */
public final class BootSoundRules {
    public static final String ON = "1", OFF = "0";
    /** Until this long after the kernel start the boot animation played the sound: a screen on then plays nothing. */
    static final long COLD_BOOT_MS = 120_000L;
    /** Two wake signals closer than this are one wake (SCREEN_ON and the standby exit of the same power on). */
    static final long REPEAT_MS = 20_000L;
    /** PCM 16 bit, 48 kHz, stereo, 2.0 to 3.5 s: the HAL's own rate, the length the boot animation part allows. */
    static final int RATE = 48_000, CHANNELS = 2, BITS = 16;
    static final double MIN_S = 2.0, MAX_S = 3.5;

    private BootSoundRules() {}

    /** The switch from its stored value: unset is on (stock CustomerEnvTbl power_on_music DEFAULT="1"). */
    public static boolean on(String stored) {
        return !OFF.equals(stored);
    }

    /** The value to store the first time: the unit's stock switch while Lumen stored none, else null (keep). */
    public static String adopt(String stored, Boolean vendor) {
        if (stored != null && !stored.isEmpty() || vendor == null) return null;
        return vendor ? ON : OFF;
    }

    /** The boot animation gate: the switch is on and the music is neither muted nor at 0. */
    public static String gate(boolean on, boolean muted, int index) {
        return on && !muted && index > 0 ? ON : OFF;
    }

    /** True when a wake plays the sound: switched on, booted long ago, no wake just before, audible, nothing playing. */
    public static boolean playOnWake(boolean on, boolean booted, long sinceBootMs, long sinceLastMs, boolean muted,
            int index, boolean musicActive) {
        return on && booted && sinceBootMs >= COLD_BOOT_MS && sinceLastMs >= REPEAT_MS && !muted && index > 0
                && !musicActive;
    }

    /**
     * The PCM of a RIFF WAVE: {data offset, data length, rate, channels}. Walks the chunks until "data" like
     * audioplay.cpp; throws IllegalArgumentException for anything but PCM 16 bit 48 kHz stereo of 2.0 to 3.5 s.
     */
    public static int[] pcm(byte[] w) {
        if (w == null || w.length < 12 || !tag(w, 0, "RIFF") || !tag(w, 8, "WAVE")) throw bad("not a riff/wave file");
        int pos = 12, fmt = -1;
        while (pos + 8 <= w.length) {
            int size = le(w, pos + 4, 4);
            pos += 8;
            if (tag(w, pos - 8, "fmt ")) {
                fmt = pos;
            } else if (tag(w, pos - 8, "data")) {
                if (fmt < 0) throw bad("no fmt chunk before data");
                if (size < 0 || size > w.length - pos) throw bad("data chunk runs past the file");
                int format = le(w, fmt, 2), ch = le(w, fmt + 2, 2), rate = le(w, fmt + 4, 4), bits = le(w, fmt + 14, 2);
                if (format != 1 || ch != CHANNELS || rate != RATE || bits != BITS) {
                    throw bad("format " + format + ", " + ch + " ch, " + rate + " Hz, " + bits + " bit");
                }
                double s = size / (double) (RATE * CHANNELS * BITS / 8);
                if (s < MIN_S || s > MAX_S) throw bad(s + " s");
                return new int[] {pos, size, rate, ch};
            }
            pos += size;
        }
        throw bad("no data chunk");
    }

    private static boolean tag(byte[] w, int at, String t) {
        for (int i = 0; i < 4; i++) if (w[at + i] != t.charAt(i)) return false;
        return true;
    }

    private static int le(byte[] w, int at, int n) {
        int v = 0;
        for (int i = n - 1; i >= 0; i--) v = v << 8 | (w[at + i] & 0xff);
        return v;
    }

    private static IllegalArgumentException bad(String why) {
        return new IllegalArgumentException("power-on sound: " + why);
    }
}
