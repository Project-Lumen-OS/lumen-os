// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

import java.util.ArrayList;
import java.util.List;

/**
 * The CEC message history of {@code dumpsys hdmi_control} (HdmiCecController keeps the last 250 messages and 5 V
 * events): the one place an app can see a received CEC message on Android 14, read with DUMP (SourceFollow). Lines:
 * {@code [R] time=2026-11-26 11:32:34 message=<InActive Source> 80:9D:10:00}, {@code [S] ... (ACK)},
 * {@code [H] time=... hotplug port=1 connected=false}. {@link #fresh} gives what came since the previous read.
 */
public final class CecLog {
    /** One history line: received 'R', sent 'S' or a 5 V change 'H'. */
    public static final class Entry {
        public final char dir;
        /** Message header and opcode, -1 when the line has none (a 5 V event, a polling message). */
        public final int initiator, destination, opcode;
        public final int[] params;
        /** 5 V event: the port and whether a device is plugged in now. */
        public final int port;
        public final boolean connected;

        Entry(char dir, int initiator, int destination, int opcode, int[] params, int port, boolean connected) {
            this.dir = dir;
            this.initiator = initiator;
            this.destination = destination;
            this.opcode = opcode;
            this.params = params;
            this.port = port;
            this.connected = connected;
        }
    }

    /** The history lines of the previous read, null before the first read of a viewing. */
    private List<String> last;

    /** Every history entry of a dump, oldest first. */
    public static List<Entry> entries(String dump) {
        List<Entry> out = new ArrayList<>();
        for (String l : lines(dump)) out.add(parse(l));
        return out;
    }

    /** One history line, null for any other line. */
    public static Entry parse(String line) {
        String l = line.trim();
        if (l.length() < 4 || l.charAt(0) != '[' || l.charAt(2) != ']' || !l.startsWith(" time=", 3)) return null;
        char dir = l.charAt(1);
        if (dir == 'H') {
            int p = l.indexOf("hotplug port=");
            int c = l.indexOf(" connected=");
            if (p < 0 || c < p) return null;
            try {
                int port = Integer.parseInt(l.substring(p + 13, c));
                return new Entry(dir, -1, -1, -1, new int[0], port, l.startsWith("true", c + 11));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (dir != 'R' && dir != 'S') return null;
        int m = l.indexOf("message=<");
        int close = m < 0 ? -1 : l.indexOf("> ", m);
        if (close < 0) return null;
        String hex = l.substring(close + 2);
        int sp = hex.indexOf(' ');
        if (sp >= 0) hex = hex.substring(0, sp);
        String[] b = hex.split(":");
        int[] v = new int[b.length];
        try {
            for (int i = 0; i < b.length; i++) v[i] = Integer.parseInt(b[i], 16);
        } catch (NumberFormatException e) {
            return null;
        }
        int[] params = new int[Math.max(0, v.length - 2)];
        System.arraycopy(v, Math.min(2, v.length), params, 0, params.length);
        return new Entry(dir, v[0] >> 4, v[0] & 0xF, v.length > 1 ? v[1] : -1, params, -1, false);
    }

    /** A new viewing: the next read is the baseline again. */
    public void reset() {
        last = null;
    }

    /**
     * The entries added to the history since the previous read (the first read of a viewing is the baseline: none).
     * The history only drops its oldest lines and appends new ones, so the new lines follow the longest tail of the
     * previous read that starts the new one. {@code dump} null (the read failed): nothing, the baseline stays.
     */
    public List<Entry> fresh(String dump) {
        List<Entry> out = new ArrayList<>();
        if (dump == null) return out;
        List<String> now = lines(dump);
        List<String> prev = last;
        last = now;
        if (prev == null) return out;
        int keep = 0;
        for (int drop = 0; drop <= prev.size(); drop++) {
            int n = prev.size() - drop;
            if (n <= now.size() && prev.subList(drop, prev.size()).equals(now.subList(0, n))) {
                keep = n;
                break;
            }
        }
        for (String l : now.subList(keep, now.size())) out.add(parse(l));
        return out;
    }

    private static List<String> lines(String dump) {
        List<String> out = new ArrayList<>();
        if (dump == null) return out;
        for (String l : dump.split("\n")) if (parse(l) != null) out.add(l.trim());
        return out;
    }
}
