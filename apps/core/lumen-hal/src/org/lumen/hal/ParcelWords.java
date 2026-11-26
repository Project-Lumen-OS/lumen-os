// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hal;

import java.nio.charset.StandardCharsets;

/**
 * lumen-hal: the int32 words android.os.Parcel lays down for the stock AIDL proxies' array and string arguments, so a
 * transport writes them with writeInt one word at a time and the bytes equal the stock ones (plain Java, host-tested;
 * Parcel stores int32 in the device's little-endian order). writeByteArray: the length, then the bytes four to a word,
 * the last word zero padded (null: -1). writeString (String16 on Android 14): the length in UTF-16 units, then the
 * units two to a word and a 0 unit, zero padded (null: -1). The same words are what {@link ArgSpec} checks.
 */
public final class ParcelWords {
    private ParcelWords() {}

    /** The words of Parcel.writeByteArray(b). */
    public static int[] byteArray(byte[] b) {
        if (b == null) return new int[] {-1};
        int[] w = new int[1 + (b.length + 3) / 4];
        w[0] = b.length;
        for (int i = 0; i < b.length; i++) w[1 + i / 4] |= (b[i] & 0xff) << (8 * (i % 4));
        return w;
    }

    /** The words of Parcel.writeString(s) (String16: length, UTF-16LE units, a 0 unit, padding). */
    public static int[] string16(String s) {
        if (s == null) return new int[] {-1};
        byte[] u = s.getBytes(StandardCharsets.UTF_16LE);
        int units = u.length / 2;
        byte[] withEnd = new byte[u.length + 2];
        System.arraycopy(u, 0, withEnd, 0, u.length);
        int[] packed = byteArray(withEnd);
        packed[0] = units;
        return packed;
    }

    /**
     * The bytes of a byte array read back word by word: {@code words} holds the length word at {@code at}, then the
     * packed bytes. Null when the length is -1 (a null array) or the words end early.
     */
    public static byte[] bytes(int[] words, int at) {
        if (words == null || at < 0 || at >= words.length || words[at] < 0) return null;
        int n = words[at];
        if (words.length < at + 1 + (n + 3) / 4) return null;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (words[at + 1 + i / 4] >>> (8 * (i % 4)));
        return b;
    }

    /** How many words a byte array of {@code n} bytes fills, its length word included. */
    public static int byteArrayWords(int n) { return 1 + (n + 3) / 4; }

    /** The parts one after another (the argument words of one call). */
    public static int[] concat(int[]... parts) {
        int n = 0;
        for (int[] p : parts) n += p.length;
        int[] out = new int[n];
        int k = 0;
        for (int[] p : parts) {
            System.arraycopy(p, 0, out, k, p.length);
            k += p.length;
        }
        return out;
    }
}
