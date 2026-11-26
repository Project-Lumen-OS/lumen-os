// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

/**
 * power.txt of a manual report: dumpsys power from the image's log helper (RootLogs) for sleep, standby and wake
 * problems (wakefulness, display power, the sleep timeout and settings, the last wake and sleep reasons). The blocks
 * that name apps one by one (the wake locks and the uid states with their package lists) are left out, like the logs of
 * apps the user installed; their headers stay with the count. Plain Java, host-tested in ReportTest.
 */
final class PowerDump {
    /** Upper bound of the text in characters. */
    static final int CAP = 192 << 10;
    /** Block headers whose indented lines name apps. */
    private static final String[] APP_BLOCKS = {"Wake Locks:", "Suspend Blockers:", "Uid states:", "Looper state:"};

    private PowerDump() {}

    /** The power.txt text; a missing dump is named. */
    static String text(String power) {
        if (power == null) return "== dumpsys power\n(not readable)\n";
        StringBuilder sb = new StringBuilder("== dumpsys power (the wake lock and uid lists left out)\n");
        int skipIndent = -1;
        long left = 0;
        for (String line : power.split("\n", -1)) {
            int indent = indent(line);
            if (skipIndent >= 0) {
                if (!line.trim().isEmpty() && indent > skipIndent) {
                    left++;
                    continue;
                }
                if (left > 0) sb.append(spaces(skipIndent + 2)).append("(").append(left).append(" lines left out)\n");
                skipIndent = -1;
                left = 0;
            }
            if (appBlock(line.trim())) skipIndent = indent;
            sb.append(line).append('\n');
        }
        if (skipIndent >= 0 && left > 0) sb.append(spaces(skipIndent + 2)).append("(").append(left).append(" lines left out)\n");
        return HdmiDump.cap(sb.toString().replaceAll("\n+$", "\n"), CAP);
    }

    private static boolean appBlock(String t) {
        for (String h : APP_BLOCKS) if (t.startsWith(h)) return true;
        return false;
    }

    private static int indent(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return i;
    }

    private static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }
}
