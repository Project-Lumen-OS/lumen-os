// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The CEC journal of hdmi.txt: every CEC message of the system log in order, the power polls counted.
 * The framework prints each message it sends or receives as {@code [S]:<Name> SD:OP:params} / {@code [R]:...} under
 * the tag HDMI (HdmiLogger.debug in HdmiCecController) once the image sets log.tag.HDMI=D before system_server starts
 * (image/lumen_report.rc); its own history (dumpsys hdmi_control) keeps only the last 250 messages, which a soundbar
 * that asks the power status every 2 s fills in about 4 minutes. The system log keeps them as long as its buffer
 * lasts, and here an unchanged question with its answer becomes one counted line until another message comes:
 * <pre>
 *   11-26 17:27:10.201 to 11-26 17:43:48.207 x500 [R]:&lt;Give Device Power Status&gt; 50:8F answer [S]:&lt;Report Power Status&gt; 05:90:00
 * </pre>
 * A changed answer (a device went to standby) is another pair and gets its own line. Lines of the tag
 * HdmiCecController (a failed send, an invalid message) and the other HDMI lines (hotplug, addresses) stay as they are.
 * Plain Java, fed with the kept logcat lines in their order (ReportCollector); the framework already hides OSD names
 * and vendor commands in these lines.
 */
final class CecJournal {
    /** The property that turns the framework's CEC lines on (read once by system_server). */
    static final String TAG_PROP = "log.tag.HDMI";
    /** Upper bound of journal lines; the newest are kept. */
    static final int MAX_LINES = 4000;
    private static final Pattern LINE = Pattern.compile(
            "^(\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3})\\s+(?:\\S+\\s+)?\\d+\\s+\\d+\\s+([VDIWEF]) (HDMI|HdmiCecController)\\s*: (.*)$");
    private static final Pattern MSG = Pattern.compile("^\\[[RS]\\]:<[^>]*> ([0-9A-F])([0-9A-F]):([0-9A-F]{2})\\b");
    private static final String ASK = "8F", ANSWER = "90";

    private final ArrayDeque<String> out = new ArrayDeque<>();
    /** Questions waiting for their answer, by asker and asked address. */
    private final Map<String, Line> asks = new HashMap<>();
    /** Poll pairs since the last other message, by question and answer text, in the order they began. */
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private int dropped, messages;
    private String first, last;

    private static final class Line {
        final String time, text;

        Line(String time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    private static final class Run {
        final Line ask, answer;
        String last;
        int count = 1;

        Run(Line ask, Line answer) {
            this.ask = ask;
            this.answer = answer;
            this.last = answer == null ? ask.time : answer.time;
        }
    }

    /** One logcat line (threadtime, with or without the uid column); lines of other tags are ignored. */
    void add(String logLine) {
        Matcher m = LINE.matcher(logLine);
        if (!m.matches()) return;
        String time = m.group(1), text = m.group(4);
        if (noise(text)) return;
        if (first == null) first = time;
        last = time;
        boolean plain = "HDMI".equals(m.group(3)) && "D".equals(m.group(2));
        Matcher c = plain ? MSG.matcher(text) : null;
        if (c == null || !c.find()) {
            flush();
            emit(time + " " + (plain ? text : m.group(2) + " " + m.group(3) + ": " + text));
            return;
        }
        messages++;
        Line l = new Line(time, text);
        String src = c.group(1), dst = c.group(2), op = c.group(3);
        if (ASK.equals(op)) {
            Line open = asks.put(src + dst, l);
            if (open != null) pair(open, null);
        } else if (ANSWER.equals(op) && asks.containsKey(dst + src)) {
            pair(asks.remove(dst + src), l);
        } else {
            flush();
            emit(time + " " + text);
        }
    }

    /**
     * HdmiCecConfig's debug lines of every setting read ("Getting CEC setting value 'x'." and "Reading 'x' shared
     * preference.", two per read, every 5 s on a Lumen unit) and the store line of a write: no CEC message, and in
     * LR-9MEQZP they were most of the journal. The change itself ("Updating CEC setting 'x' to 'v'.") stays.
     */
    static boolean noise(String text) {
        return text.startsWith("Getting CEC setting value ") || (text.startsWith("Reading '") && text.endsWith(" shared preference."))
                || (text.startsWith("Setting '") && text.endsWith(" shared pref."));
    }

    private void pair(Line ask, Line answer) {
        String key = ask.text + "\n" + (answer == null ? "" : answer.text);
        Run r = runs.get(key);
        if (r == null) {
            runs.put(key, new Run(ask, answer));
        } else {
            r.count++;
            r.last = answer == null ? ask.time : answer.time;
        }
    }

    /** Writes the open poll pairs (a question without an answer as such) before another message. */
    private void flush() {
        for (Line open : asks.values().toArray(new Line[0])) pair(open, null);
        asks.clear();
        for (Run r : runs.values()) {
            String answer = r.answer == null ? " (no answer)" : " answer " + r.answer.text;
            if (r.count == 1) {
                emit(r.ask.time + " " + r.ask.text + (r.answer == null ? answer : ""));
                if (r.answer != null) emit(r.answer.time + " " + r.answer.text);
            } else {
                emit(r.ask.time + " to " + r.last + " x" + r.count + " " + r.ask.text + answer);
            }
        }
        runs.clear();
    }

    private void emit(String s) {
        out.addLast(s);
        if (out.size() > MAX_LINES) {
            out.removeFirst();
            dropped++;
        }
    }

    /** Number of CEC messages seen (polls counted one by one). */
    int messages() {
        return messages;
    }

    /** The journal text with its header; {@code tagProp} is the value of log.tag.HDMI on the unit (null = unknown). */
    String text(String tagProp) {
        flush();
        StringBuilder sb = new StringBuilder();
        String prop = tagProp == null ? "unknown" : "\"" + tagProp + "\"";
        if (first == null) {
            return sb.append("(no CEC lines in the system log; ").append(TAG_PROP).append(" is ").append(prop)
                    .append(": the image sets D at boot, with any other value system_server prints none)\n").toString();
        }
        sb.append(messages).append(" CEC messages from ").append(first).append(" to ").append(last).append(", ")
                .append(TAG_PROP).append(" ").append(prop).append(", repeated power status questions counted\n");
        if (dropped > 0) sb.append("(").append(dropped).append(" older lines left out)\n");
        for (String s : out) sb.append(s).append('\n');
        return sb.toString();
    }
}
