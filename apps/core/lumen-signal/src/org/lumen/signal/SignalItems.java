// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

import java.util.EnumSet;
import java.util.Set;

/**
 * The HAL items of one model: devices/&lt;c&gt;/signal.tsv, in the app as assets/lumen_signal/items.tsv. One row per
 * item (column 1 = SignalItem token, then the vendor call it uses and a note), '#' starts a comment. An unknown token
 * is skipped here and refused by the host test. Pure.
 */
public final class SignalItems {
    public static final String ASSET = "lumen_signal/items.tsv";

    private SignalItems() {}

    public static Set<SignalItem> parse(String tsv) {
        Set<SignalItem> out = EnumSet.noneOf(SignalItem.class);
        if (tsv == null) return out;
        for (String line : tsv.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("item\t")) continue;
            SignalItem i = SignalItem.of(t.split("\t", 2)[0].trim());
            if (i != null && (i.hal || i.bridge)) out.add(i);
        }
        return out;
    }

    /**
     * What the panel reads on this model: the model's HAL items the reader has, the model's bridge items (the image
     * ships the bridge, no reader part), plus the Android items.
     */
    public static Set<SignalItem> shown(Set<SignalItem> model, Set<SignalItem> reader) {
        Set<SignalItem> out = EnumSet.noneOf(SignalItem.class);
        for (SignalItem i : SignalItem.values()) {
            if (i.bridge ? model.contains(i) : !i.hal || (model.contains(i) && reader.contains(i))) out.add(i);
        }
        return out;
    }

    /**
     * The receiver's port of each HDMI input: the call column of the hdmi_format row names them, "ports=1:2,2:0" (HDMI 1
     * is MStar port 2). Index = the HDMI input (1..n), value = the MStar port, -1 = unknown (its rows stay hidden).
     */
    public static int[] ports(String tsv) {
        int[] out = new int[PORTS_MAX + 1];
        java.util.Arrays.fill(out, -1);
        if (tsv == null) return out;
        for (String line : tsv.split("\n")) {
            String[] c = line.split("\t");
            if (c.length < 2 || !SignalItem.HDMI_FORMAT.token.equals(c[0].trim())) continue;
            java.util.regex.Matcher m = PORTS.matcher(c[1]);
            if (!m.find()) continue;
            for (String pair : m.group(1).split(",")) {
                String[] kv = pair.split(":");
                int in = Integer.parseInt(kv[0]), mstar = Integer.parseInt(kv[1]);
                if (in >= 1 && in <= PORTS_MAX && mstar >= 0 && mstar <= MSTAR_MAX) out[in] = mstar;
            }
        }
        return out;
    }

    /** The MStar port of HDMI {@code input} in {@code ports}, -1 = none. */
    public static int port(int[] ports, int input) {
        return ports == null || input < 1 || input >= ports.length ? -1 : ports[input];
    }

    private static final int PORTS_MAX = 4, MSTAR_MAX = 3;
    private static final java.util.regex.Pattern PORTS =
            java.util.regex.Pattern.compile("\\bports=((?:\\d:\\d)(?:,\\d:\\d)*)\\b");
}
