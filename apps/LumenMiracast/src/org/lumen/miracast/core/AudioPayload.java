// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/**
 * Audio payload helpers for the two WFD audio formats.
 *
 * LPCM (WFD 1.0 table 5.x, carried as MPEG private stream 1, stream type 0x83): each PES payload
 * starts with a 4-byte header: sub_stream_id 0xA0, number_of_frame_header, reserved, then
 * quantization (2 bits, 0 = 16 bit), sampling frequency (3 bits, 1 = 44.1 kHz, 2 = 48 kHz) and
 * channels (3 bits, 1 = stereo). Samples follow as big-endian 16-bit, interleaved.
 *
 * AAC: ADTS frames (stream type 0x0F), several per PES; MediaCodec gets the raw frames and an
 * AudioSpecificConfig built from the first header.
 */
public final class AudioPayload {
    private AudioPayload() {}

    /** Parsed LPCM header, or null when the 4 bytes are not a WFD LPCM header we can play. */
    public static final class LpcmHeader {
        public final int sampleRate, channels;

        LpcmHeader(int sampleRate, int channels) {
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    public static LpcmHeader lpcmHeader(byte[] d, int off, int len) {
        if (len < 4 || (d[off] & 0xff) != 0xA0) return null;
        int b = d[off + 3] & 0xff;
        int quant = (b >> 6) & 3, freq = (b >> 3) & 7, ch = b & 7;
        if (quant != 0) return null;
        int rate = freq == 1 ? 44100 : freq == 2 ? 48000 : 0;
        if (rate == 0 || ch != 1) return null;
        return new LpcmHeader(rate, 2);
    }

    /**
     * Copies the LPCM samples after the 4-byte header into {@code out} as little-endian 16-bit (what
     * AudioTrack wants). Returns the number of bytes written (a multiple of 4: whole stereo frames).
     */
    public static int lpcmToLittleEndian(byte[] d, int off, int len, byte[] out) {
        int n = len - 4;
        if (n <= 0) return 0;
        n &= ~3;
        if (n > out.length) n = out.length & ~3;
        int s = off + 4;
        for (int i = 0; i < n; i += 2) {
            out[i] = d[s + i + 1];
            out[i + 1] = d[s + i];
        }
        return n;
    }

    private static final int[] ADTS_RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000,
        12000, 11025, 8000, 7350};

    /** One ADTS frame header. */
    public static final class Adts {
        public final int profile, rateIndex, sampleRate, channels, headerLen, frameLen;

        Adts(int profile, int rateIndex, int channels, int headerLen, int frameLen) {
            this.profile = profile;
            this.rateIndex = rateIndex;
            this.sampleRate = ADTS_RATES[rateIndex];
            this.channels = channels;
            this.headerLen = headerLen;
            this.frameLen = frameLen;
        }

        /** MPEG-4 AudioSpecificConfig (2 bytes) for MediaCodec csd-0. */
        public byte[] audioSpecificConfig() {
            int objectType = profile + 1;   // ADTS profile 1 = AAC LC = object type 2
            int v = (objectType << 11) | (rateIndex << 7) | (channels << 3);
            return new byte[] {(byte) (v >> 8), (byte) v};
        }
    }

    /** ADTS header at d[off], or null when there is no valid sync word / length. */
    public static Adts adts(byte[] d, int off, int len) {
        if (len < 7) return null;
        if ((d[off] & 0xff) != 0xFF || (d[off + 1] & 0xf0) != 0xF0) return null;
        boolean protectionAbsent = (d[off + 1] & 1) != 0;
        int profile = (d[off + 2] >> 6) & 3;
        int rateIndex = (d[off + 2] >> 2) & 0xf;
        int channels = ((d[off + 2] & 1) << 2) | ((d[off + 3] >> 6) & 3);
        int frameLen = ((d[off + 3] & 3) << 11) | ((d[off + 4] & 0xff) << 3) | ((d[off + 5] >> 5) & 7);
        int headerLen = protectionAbsent ? 7 : 9;
        if (rateIndex >= ADTS_RATES.length || frameLen < headerLen || channels == 0) return null;
        return new Adts(profile, rateIndex, channels, headerLen, frameLen);
    }
}
