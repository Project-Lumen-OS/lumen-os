// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.util.Locale;

/**
 * WFD video and audio format lists (WFD 1.0 6.1.3, 6.1.4): what the sink offers in the M3 reply and
 * what the source chose in M4. Tables follow the spec's CEA / VESA / HH bit order.
 */
public final class WfdFormats {
    private WfdFormats() {}

    public static final int TABLE_CEA = 0, TABLE_VESA = 1, TABLE_HH = 2;

    /** {width, height, fps, interlaced(0/1)} by bit index. */
    static final int[][] CEA = {
        {640, 480, 60, 0}, {720, 480, 60, 0}, {720, 480, 60, 1}, {720, 576, 50, 0}, {720, 576, 50, 1},
        {1280, 720, 30, 0}, {1280, 720, 60, 0}, {1920, 1080, 30, 0}, {1920, 1080, 60, 0}, {1920, 1080, 60, 1},
        {1280, 720, 25, 0}, {1280, 720, 50, 0}, {1920, 1080, 25, 0}, {1920, 1080, 50, 0}, {1920, 1080, 50, 1},
        {1280, 720, 24, 0}, {1920, 1080, 24, 0},
    };
    static final int[][] VESA = {
        {800, 600, 30, 0}, {800, 600, 60, 0}, {1024, 768, 30, 0}, {1024, 768, 60, 0}, {1152, 864, 30, 0},
        {1152, 864, 60, 0}, {1280, 768, 30, 0}, {1280, 768, 60, 0}, {1280, 800, 30, 0}, {1280, 800, 60, 0},
        {1360, 768, 30, 0}, {1360, 768, 60, 0}, {1366, 768, 30, 0}, {1366, 768, 60, 0}, {1280, 1024, 30, 0},
        {1280, 1024, 60, 0}, {1400, 1050, 30, 0}, {1400, 1050, 60, 0}, {1440, 900, 30, 0}, {1440, 900, 60, 0},
        {1600, 900, 30, 0}, {1600, 900, 60, 0}, {1600, 1200, 30, 0}, {1600, 1200, 60, 0}, {1680, 1024, 30, 0},
        {1680, 1024, 60, 0}, {1680, 1050, 30, 0}, {1680, 1050, 60, 0}, {1920, 1200, 30, 0}, {1920, 1200, 60, 0},
    };
    static final int[][] HH = {
        {800, 480, 30, 0}, {800, 480, 60, 0}, {854, 480, 30, 0}, {854, 480, 60, 0}, {864, 480, 30, 0},
        {864, 480, 60, 0}, {640, 360, 30, 0}, {640, 360, 60, 0}, {960, 540, 30, 0}, {960, 540, 60, 0},
        {848, 480, 30, 0}, {848, 480, 60, 0},
    };

    /** H.264 level bits (WFD): 3.1, 3.2, 4, 4.1, 4.2. Max macroblocks per second for each. */
    static final int[] LEVEL_BIT = {0x01, 0x02, 0x04, 0x08, 0x10};
    static final long[] LEVEL_MBPS = {108000, 216000, 245760, 245760, 522240};
    public static final int PROFILE_CBP = 0x01, PROFILE_CHP = 0x02;

    /** One video mode chosen by the source. */
    public static final class Video {
        public final int table, index, width, height, fps, profile, level;
        public final boolean interlaced;

        Video(int table, int index, int[] row, int profile, int level) {
            this.table = table;
            this.index = index;
            width = row[0];
            height = row[1];
            fps = row[2];
            interlaced = row[3] != 0;
            this.profile = profile;
            this.level = level;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%dx%d%s%d %s", width, height, interlaced ? "i" : "p", fps,
                    profile == PROFILE_CHP ? "CHP" : profile == PROFILE_CBP ? "CBP" : "p" + profile);
        }
    }

    /** One audio mode chosen by the source. */
    public static final class Audio {
        public static final int LPCM = 1, AAC = 2, AC3 = 3;
        public final int codec, sampleRate, channels;

        public Audio(int codec, int sampleRate, int channels) {
            this.codec = codec;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }

        @Override
        public String toString() {
            return (codec == LPCM ? "LPCM" : codec == AAC ? "AAC" : codec == AC3 ? "AC3" : "?") + " " + sampleRate + "/" + channels;
        }
    }

    // ------------------------------------------------------------------ sink offer (M3)

    private static boolean fits(int[] row, WfdQuality q) {
        if (row[3] != 0) return false;                          // no interlaced modes
        int maxH = q.maxHeight == 1080 ? 1200 : q.maxHeight;     // 1200-line VESA modes are scaled down
        if (row[1] > maxH) return false;
        if (row[1] > 720 && row[2] > q.maxFps) return false;
        return mbps(row) <= LEVEL_MBPS[levelIndex(q)];
    }

    static long mbps(int[] row) {
        long mbW = (row[0] + 15) / 16, mbH = (row[1] + 15) / 16;
        return mbW * mbH * row[2];
    }

    private static int levelIndex(WfdQuality q) {
        return q == WfdQuality.P1080_60 ? 4 : 3;   // 4.2 for 1080p60, else 4.1
    }

    static long mask(int[][] table, WfdQuality q) {
        long m = 0;
        for (int i = 0; i < table.length; i++) {
            if (fits(table[i], q)) m |= 1L << i;
        }
        return m;
    }

    /** Value of wfd_video_formats in the M3 reply, e.g. "40 00 02 10 0001ffeb 1fffffff 00000fff 00 0000 0000 00 none none, 01 10 ...". */
    public static String sinkVideoFormats(WfdQuality q) {
        long cea = mask(CEA, q), vesa = mask(VESA, q), hh = mask(HH, q);
        int nativeByte = nativeIndex(q) << 3 | TABLE_CEA;
        int level = LEVEL_BIT[levelIndex(q)];
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%02x 00 ", nativeByte));
        int[] profiles = {PROFILE_CHP, PROFILE_CBP};
        for (int i = 0; i < profiles.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format(Locale.ROOT, "%02x %02x %08x %08x %08x 00 0000 0000 00 none none",
                    profiles[i], level, cea, vesa, hh));
        }
        return sb.toString();
    }

    /** CEA index of the native (preferred) mode: the best one allowed. */
    static int nativeIndex(WfdQuality q) {
        switch (q) {
            case P1080_30: return 7;
            case P720_60: return 6;
            default: return 8;
        }
    }

    /** Value of wfd_audio_codecs in the M3 reply. LPCM 44.1 and 48 kHz stereo, AAC 48 kHz stereo. */
    public static String sinkAudioCodecs(boolean lpcm, boolean aac) {
        StringBuilder sb = new StringBuilder();
        if (lpcm) sb.append("LPCM 00000003 00");
        if (aac) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("AAC 00000001 00");
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    // ------------------------------------------------------------------ source choice (M4)

    /**
     * The mode chosen in an M4 wfd_video_formats value; null for "none" or a value that names no
     * single known mode. Tolerates upper/lower case hex and several codec entries (takes the first).
     */
    public static Video parseVideo(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none")) return null;
        String first = s.split(",")[0].trim();
        String[] t = first.split("\\s+");
        // native pref profile level cea vesa hh latency minslice sliceenc frc maxh maxv
        if (t.length < 7) return null;
        try {
            int profile = Integer.parseInt(t[2], 16);
            int level = Integer.parseInt(t[3], 16);
            long cea = Long.parseLong(t[4], 16), vesa = Long.parseLong(t[5], 16), hh = Long.parseLong(t[6], 16);
            Video r = pick(TABLE_CEA, CEA, cea, profile, level);
            if (r == null) r = pick(TABLE_VESA, VESA, vesa, profile, level);
            if (r == null) r = pick(TABLE_HH, HH, hh, profile, level);
            return r;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Video pick(int table, int[][] rows, long mask, int profile, int level) {
        if (mask == 0) return null;
        for (int i = 0; i < rows.length; i++) {   // the lowest set bit; a source sends exactly one
            if ((mask & (1L << i)) != 0) return new Video(table, i, rows[i], profile, level);
        }
        return null;
    }

    /** The codec chosen in an M4 wfd_audio_codecs value; null for "none" or unknown. */
    public static Audio parseAudio(String v) {
        if (v == null) return null;
        String first = v.trim().split(",")[0].trim();
        String[] t = first.split("\\s+");
        if (t.length < 2) return null;
        long modes;
        try {
            modes = Long.parseLong(t[1], 16);
        } catch (NumberFormatException e) {
            return null;
        }
        switch (t[0].toUpperCase(Locale.ROOT)) {
            case "LPCM":
                // bit0 44.1 kHz stereo, bit1 48 kHz stereo
                return new Audio(Audio.LPCM, (modes & 0x2) != 0 ? 48000 : 44100, 2);
            case "AAC": {
                // bit0 2ch, bit1 4ch, bit2 6ch, bit3 8ch, all 48 kHz
                int ch = (modes & 0x8) != 0 ? 8 : (modes & 0x4) != 0 ? 6 : (modes & 0x2) != 0 ? 4 : 2;
                return new Audio(Audio.AAC, 48000, ch);
            }
            case "AC3":
                return new Audio(Audio.AC3, 48000, 2);
            default:
                return null;
        }
    }
}
