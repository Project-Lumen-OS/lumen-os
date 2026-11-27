// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * settings.txt of a manual report: every setting an HDMI-CEC, ARC / eARC or sound problem depends on, in one place
 * (evleo790 LR-9MEQZP had no CEC setting at all: its log helper did not answer, so hdmi.txt had no dump either).
 * <ul>
 *   <li>the rows of Settings.Global, Secure and System whose key names HDMI, CEC, ARC, eARC, sound or volume;</li>
 *   <li>the framework's HDMI-CEC settings (the "CEC settings:" block of dumpsys hdmi_control: every HdmiCecConfig
 *       value with its default);</li>
 *   <li>the projector app's own switches (its shared preferences files about CEC, sound, HDMI and power: night
 *       guard, the output choice, the one-time steps).</li>
 * </ul>
 * Plain Java: the Android side reads the rows and files and hands them in; the Scrubber runs over the result.
 */
final class SettingsDump {
    /** A settings key is kept when it names one of these (ARC / eARC only as a whole word between underscores). */
    private static final Pattern KEY = Pattern.compile("(?i)hdmi|cec|(^|_)e?arc(_|$)|audio|sound|surround|volume"
            + "|spdif|dolby|dts|passthrough|mute|standby|one_touch");
    /** A shared preferences file of the app is kept when its name says it is about these. */
    private static final Pattern PREFS = Pattern.compile("(?i)cec|audio|sound|hdmi|arc|power|output|speaker|eq");
    private static final Pattern PREF_LINE = Pattern.compile(
            "<(boolean|int|long|float|string)\\s+name=\"([^\"]*)\"(?:\\s+value=\"([^\"]*)\"\\s*/>|\\s*>([^<]*)</string>)");
    /** Upper bound of one preferences file in characters. */
    static final int PREFS_CAP = 64 << 10;

    private SettingsDump() {}

    static boolean keepKey(String key) {
        return key != null && KEY.matcher(key).find();
    }

    static boolean keepPrefsFile(String fileName) {
        return fileName != null && fileName.endsWith(".xml") && PREFS.matcher(fileName).find();
    }

    /**
     * One settings table: its kept rows sorted by key as {@code key=value}; {@code rows} null = the table could not be
     * read ({@code why} says why).
     */
    static String table(String title, List<String[]> rows, String why) {
        StringBuilder sb = new StringBuilder("== ").append(title).append(" (keys about HDMI, CEC, ARC, eARC and sound)\n");
        if (rows == null) return sb.append("(not readable").append(why == null ? "" : ": " + why).append(")\n").toString();
        List<String> kept = new ArrayList<>();
        for (String[] r : rows) {
            if (r == null || r.length < 2 || !keepKey(r[0])) continue;
            kept.add(r[0] + "=" + (r[1] == null ? "(null)" : r[1]));
        }
        Collections.sort(kept);
        for (String k : kept) sb.append(k).append('\n');
        if (kept.isEmpty()) sb.append("(no such key among ").append(rows.size()).append(" rows)\n");
        return sb.toString();
    }

    /** The "CEC settings:" block of a dumpsys hdmi_control text, null when the text has none. */
    static String cecSettings(String hdmiControl) {
        if (hdmiControl == null) return null;
        String[] lines = hdmiControl.split("\n", -1);
        StringBuilder sb = null;
        for (String line : lines) {
            if (sb == null) {
                if (line.trim().equals("CEC settings:")) sb = new StringBuilder();
                continue;
            }
            if (!line.startsWith(" ") || line.trim().isEmpty()) break;
            sb.append(line.trim()).append('\n');
        }
        return sb == null || sb.length() == 0 ? null : sb.toString();
    }

    /** The CEC part: the block of the dump, or a line saying where it was not found. */
    static String cecPart(String hdmiControl) {
        StringBuilder sb = new StringBuilder("== HDMI-CEC settings of the framework (dumpsys hdmi_control)\n");
        String block = cecSettings(hdmiControl);
        if (block != null) return sb.append(block).toString();
        return sb.append(hdmiControl == null ? "(dumpsys hdmi_control not readable)\n" : "(no CEC settings block in the dump)\n")
                .toString();
    }

    /** One shared preferences file as {@code key=value} lines in file order, at most {@link #PREFS_CAP} characters. */
    static String prefs(String fileName, String xml) {
        StringBuilder sb = new StringBuilder("[").append(fileName).append("]\n");
        if (xml == null) return sb.append("(not readable)\n").toString();
        Matcher m = PREF_LINE.matcher(xml.length() > PREFS_CAP ? xml.substring(0, PREFS_CAP) : xml);
        int n = 0;
        while (m.find()) {
            String v = m.group(3) != null ? m.group(3) : m.group(4);
            sb.append(m.group(2)).append('=').append(v).append('\n');
            n++;
        }
        if (n == 0) sb.append("(empty)\n");
        return sb.toString();
    }
}
