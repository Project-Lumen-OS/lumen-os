package org.lumen.hal;

/**
 * lumen-hal: the argument range of one allow-listed call, checked by {@link AidlTransport} on the written argument words
 * before anything is sent (plain Java, host-tested). The same text is the "args:" column of the app's allow list
 * (devices/<c>/research/allowed_<app>.tsv) and of a per-app unban list (unbanned_<app>.tsv); tools/devices/check_aidl_hal.py
 * holds the enum Call text equal to those columns (rule 8), so a range is written once and enforced twice: by the build
 * check on the sources and by the transport on the device.
 *
 * Grammar: spec = alt (";" alt)*; alt = "-" (no argument word) | word ("," word)*; word = INT | INT ".." INT |
 * "f:" FLOAT (one float word, compared by its bits). One word per Parcel int32 slot: writeInt, writeBoolean (0 / 1) and
 * writeByte (sign-extended) each fill one, writeFloat one. Example: "5,10;11,10" = (5, 10) or (11, 10);
 * "1..5,f:0" = an int 1..5 followed by the float 0.0.
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
            String[] ws = alt.split(",", -1);
            if (ws.length != words.length) continue;
            boolean ok = true;
            for (int k = 0; k < ws.length && ok; k++) {
                long[] r = word(ws[k]);
                ok = r != null && words[k] >= r[0] && words[k] <= r[1];
            }
            if (ok) return true;
        }
        return false;
    }

    /** [min, max] of one word (a float word: its bits twice), null when malformed. */
    private static long[] word(String w) {
        try {
            if (w.startsWith("f:")) {
                float f = Float.parseFloat(w.substring(2));
                if (Float.isNaN(f) || Float.isInfinite(f)) return null;
                int b = Float.floatToIntBits(f);
                return new long[] {b, b};
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
}
