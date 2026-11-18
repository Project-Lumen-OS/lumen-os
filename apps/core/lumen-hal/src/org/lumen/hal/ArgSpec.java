package org.lumen.hal;

/**
 * lumen-hal: the argument range of one allow-listed call, checked by {@link AidlTransport} on the written argument words
 * before anything is sent (plain Java, host-tested). The same text is the "args:" column of the app's allow list
 * (devices/<c>/research/allowed_<app>.tsv) and of a per-app unban list (unbanned_<app>.tsv); tools/devices/check_aidl_hal.py
 * holds the enum Call text equal to those columns (rule 8), so a range is written once and enforced twice: by the build
 * check on the sources and by the transport on the device.
 *
 * Grammar: spec = alt (";" alt)*; alt = "-" (no argument word) | word ("," word)*; word = INT | INT ".." INT |
 * INT ("|" INT)+ (one of these values) | "f:" FLOAT (one float word, compared by its bits) | "f:" FLOAT ".." FLOAT (one
 * float word between two floats that are both zero or more: the bits of such floats grow with the value) | "d:" FLOAT
 * ".." FLOAT (one Parcel.writeDouble: two words, the low half first as the parcel stores it, read back as a double
 * that must be finite and within the range; the bounds may be negative) | "s:" INT
 * (one Parcel.writeString of at most INT chars, never null) | "b" N ":" INT ".." INT (N = 1..4: the word's N low bytes,
 * little endian as Parcel packs a byte[], each within the range 0..255, the bytes above them zero). One word per Parcel
 * int32 slot: writeInt, writeBoolean (0 / 1) and writeByte (sign-extended) each fill one, writeFloat one, writeDouble
 * two; a string fills
 * its length word, then its UTF-16 chars and the closing zero padded to whole words; writeByteArray fills its length
 * word and one word per four bytes (the last one zero padded), so a 7-byte array of 0..100 is "7,b4:0..100,b3:0..100".
 * Example: "5,10;11,10" = (5, 10) or (11, 10); "1..5,f:0" = an int 1..5 followed by the float 0.0; "5..6,f:0.5..45" =
 * 5 or 6, then a float from 0.5 to 45; "1..99,s:512" = an int 1..99, then a string of at most 512 chars; "80|90|100" =
 * one of three values; "d:-0.02..0.02" = one double from -0.02 to 0.02.
 */
public final class ArgSpec {
    private ArgSpec() {}

    /** null when spec is well formed, else why not. */
    public static String error(String spec) {
        if (spec == null || spec.isEmpty()) return "empty argument spec";
        for (String alt : spec.split(";", -1)) {
            if (alt.equals("-")) continue;
            if (alt.isEmpty()) return "empty alternative in " + spec;
            for (String w : alt.split(",", -1)) {
                if (word(w) == null) return "bad word '" + w + "' in " + spec;
            }
        }
        return null;
    }

    /** True when the words are one of the alternatives of a well-formed spec (a malformed spec matches nothing). */
    public static boolean matches(String spec, int[] words) {
        if (words == null || error(spec) != null) return false;
        for (String alt : spec.split(";", -1)) {
            if (alt.equals("-")) {
                if (words.length == 0) return true;
                continue;
            }
            if (matchesAlt(alt.split(",", -1), words)) return true;
        }
        return false;
    }

    /** One alternative against all the words: every spec word takes its own words, none may be left over. */
    private static boolean matchesAlt(String[] ws, int[] words) {
        int at = 0;
        for (String w : ws) {
            if (at >= words.length) return false;
            long[] r = word(w);
            if (w.startsWith("s:")) {
                int chars = words[at];
                if (chars < 0 || chars > r[1]) return false;
                at += 1 + stringWords(chars);
                continue;
            }
            if (w.startsWith("d:")) {
                if (at + 1 >= words.length) return false;
                double d = Double.longBitsToDouble((words[at] & 0xffffffffL) | ((long) words[at + 1] << 32));
                double[] dr = doubles(w);
                if (Double.isNaN(d) || Double.isInfinite(d) || d < dr[0] || d > dr[1]) return false;
                at += 2;
                continue;
            }
            if (!accepts(w, words[at])) return false;
            at++;
        }
        return at == words.length;
    }

    /** The int32 words of a string's chars after its length word: (chars + 1) UTF-16 units, padded to 4 bytes. */
    static int stringWords(int chars) {
        return ((chars + 1) * 2 + 3) / 4;
    }

    /** True when the well-formed word spec {@code w} accepts the argument word {@code v}. */
    private static boolean accepts(String w, int v) {
        if (w.indexOf('|') >= 0) {
            for (String one : w.split("\\|", -1)) if (Integer.parseInt(one) == v) return true;
            return false;
        }
        if (w.startsWith("b")) {
            int n = w.charAt(1) - '0';
            long[] r = word(w);
            for (int i = 0; i < 4; i++) {
                int b = (v >>> (8 * i)) & 0xff;
                if (i < n ? b < r[0] || b > r[1] : b != 0) return false;
            }
            return true;
        }
        long[] r = word(w);
        return v >= r[0] && v <= r[1];
    }

    /**
     * [min, max] of one word (a float word: its bits twice; a string word: 0 and its most chars; a byte word: its byte
     * range), null when malformed.
     */
    private static long[] word(String w) {
        try {
            if (w.startsWith("s:")) {
                int max = Integer.parseInt(w.substring(2));
                return max > 0 ? new long[] {0, max} : null;
            }
            if (w.indexOf('|') >= 0) {
                String[] vs = w.split("\\|", -1);
                long lo = Long.MAX_VALUE, hi = Long.MIN_VALUE;
                for (String one : vs) {
                    if (one.isEmpty() || one.indexOf("..") >= 0) return null;
                    long x = Integer.parseInt(one);
                    lo = Math.min(lo, x);
                    hi = Math.max(hi, x);
                }
                return new long[] {lo, hi};
            }
            if (w.length() > 3 && w.charAt(0) == 'b' && w.charAt(2) == ':') {
                int n = w.charAt(1) - '0';
                int dots = w.indexOf("..");
                if (n < 1 || n > 4 || dots < 0) return null;
                long a = Integer.parseInt(w.substring(3, dots)), b = Integer.parseInt(w.substring(dots + 2));
                return a >= 0 && a <= b && b <= 255 ? new long[] {a, b} : null;
            }
            if (w.startsWith("d:")) {
                return doubles(w) != null ? new long[] {0, 0} : null;
            }
            if (w.startsWith("f:")) {
                String f = w.substring(2);
                int dots = f.indexOf("..");
                if (dots < 0) {
                    int b = bits(f);
                    return new long[] {b, b};
                }
                int a = bits(f.substring(0, dots)), b = bits(f.substring(dots + 2));
                return a >= 0 && b >= 0 && a <= b ? new long[] {a, b} : null;   // -0.0 and negatives carry the sign bit
            }
            int dots = w.indexOf("..");
            if (dots < 0) {
                long v = Integer.parseInt(w);
                return new long[] {v, v};
            }
            long a = Integer.parseInt(w.substring(0, dots)), b = Integer.parseInt(w.substring(dots + 2));
            return a <= b ? new long[] {a, b} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** [min, max] of a "d:" word, null when malformed (no range, a bound not finite, min above max). */
    private static double[] doubles(String w) {
        try {
            String d = w.substring(2);
            int dots = d.indexOf("..", 1);                 // from 1: the first bound may start with a minus
            if (dots < 0) return null;
            double a = Double.parseDouble(d.substring(0, dots)), b = Double.parseDouble(d.substring(dots + 2));
            if (Double.isNaN(a) || Double.isNaN(b) || Double.isInfinite(a) || Double.isInfinite(b) || a > b) return null;
            return new double[] {a, b};
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    /** The bits of one finite float written in the spec. */
    private static int bits(String s) {
        if (s.isEmpty()) throw new NumberFormatException("empty float");
        float f = Float.parseFloat(s);
        if (Float.isNaN(f) || Float.isInfinite(f)) throw new NumberFormatException(s);
        return Float.floatToIntBits(f);
    }
}
