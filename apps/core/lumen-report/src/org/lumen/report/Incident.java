// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

/**
 * One incident an automatic report is about: the DropBox tag (or "bootreason", "unexpected_stop"), its time, kind and
 * process, and for an unexpected stop the plain text of unexpected_stop.txt ({@code detail}, else null).
 */
final class Incident {
    final String tag;
    final long time;
    final ReportKind kind;
    final String process;
    final String detail;

    Incident(String tag, long time, ReportKind kind, String process) {
        this(tag, time, kind, process, null);
    }

    Incident(String tag, long time, ReportKind kind, String process, String detail) {
        this.tag = tag;
        this.time = time;
        this.kind = kind;
        this.process = process;
        this.detail = detail;
    }

    /** One automatic report per key and day: the same crash of the same process is reported once. */
    String key() {
        return kind.wire + ":" + process;
    }

    @Override
    public String toString() {
        return tag + " " + process + " at " + time;
    }
}
