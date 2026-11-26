// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.util.regex.Pattern;

/**
 * hdmi.txt of a manual report: what an HDMI-CEC or soundbar problem needs. system_server logs a CEC message only at
 * debug level (HdmiLogger [R] / [S], on in Lumen images by log.tag.HDMI), so a source's &lt;Standby&gt; or &lt;Active
 * Source&gt;, a &lt;Feature Abort&gt; of the projector, the System Audio flag, ARC and the power status the framework
 * believes are only in its dump. Two parts:
 * <ul>
 *   <li>dumpsys hdmi_control as it is (devices, power status, CEC settings, the last CEC messages with times);</li>
 *   <li>the lines of dumpsys audio about HDMI, ARC, System Audio, absolute volume and the media route (the whole dump
 *       also lists the apps that played sound, which the report keeps out).</li>
 * </ul>
 * Between them the CEC journal of the system log (CecJournal): every CEC message since the log buffer begins, which
 * the dump's 250 entries do not reach once a soundbar polls the power status.
 * Plain Java: the Android side hands in the two dumps of the image's log helper (RootLogs; null = no helper answer)
 * and the journal text, and adds the result to the zip.
 */
final class HdmiDump {
    /** Upper bound of each part in characters. */
    static final int CAP = 384 << 10;
    /**
     * A line of dumpsys audio is kept when it names one of these. ARC only as a word: a package name such as
     * ...katniss.search must not keep an app's playback line.
     */
    private static final Pattern AUDIO_KEYS = Pattern.compile("(?i)hdmi|\\be?arc\\b|system ?audio|absolute volume"
            + "|force ?use|forced|preferred device|devices for attributes|audio routes|spdif");

    private HdmiDump() {}

    /** The hdmi.txt text: the three parts with their headers, each capped, a missing part named. */
    static String text(String hdmiControl, String journal, String audio) {
        StringBuilder sb = new StringBuilder();
        sb.append("== dumpsys hdmi_control\n");
        sb.append(hdmiControl == null ? "(not readable)\n" : cap(hdmiControl));
        sb.append("\n== CEC messages of the system log (CecJournal)\n");
        sb.append(journal == null ? "(not read)\n" : cap(journal));
        sb.append("\n== dumpsys audio (HDMI, ARC, System Audio and media route lines)\n");
        sb.append(audio == null ? "(not readable)\n" : cap(audioLines(audio)));
        return sb.toString();
    }

    /** The lines of a dumpsys audio text that name HDMI, ARC, System Audio, absolute volume or forced routing. */
    static String audioLines(String audio) {
        StringBuilder sb = new StringBuilder();
        for (String line : audio.split("\n", -1)) {
            if (AUDIO_KEYS.matcher(line).find()) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** A part cut at {@link #CAP} characters with a line saying so. */
    static String cap(String s) {
        return cap(s, CAP);
    }

    /** A text cut at {@code max} characters with a line saying so. */
    static String cap(String s, int max) {
        if (s.length() <= max) return s.endsWith("\n") ? s : s + "\n";
        return s.substring(0, max) + "\n(cut at " + max + " characters of " + s.length() + ")\n";
    }
}
