// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What the report screen shows for a job state (plain Java, host tested): the screen, its actions in order and the one
 * that has the focus when the screen appears, and where BACK goes. The look is ReportActivity's (lumen-ui TwoPane).
 *   START     Send report (focus), Send automatically (switch), What is in a report (opens DETAILS), Close
 *   DETAILS   what a report holds and what is never sent, Back (focus)
 *   PROGRESS  collecting / sending with a bar, Cancel (focus)
 *   DONE      the report code, Done (focus)
 *   QUEUED    no connection: the projector sends it later, Close (focus)
 *   FAILED    why, Try again (focus), Close
 */
final class ReportView {
    enum Screen { START, DETAILS, PROGRESS, DONE, QUEUED, FAILED }

    enum Action { SEND, AUTO, DETAILS, CLOSE, BACK, CANCEL, DONE, RETRY }

    /** What BACK does on a screen: leave the screen, go back to START, or stop the running report first. */
    enum Back { LEAVE, START, CANCEL }

    final Screen screen;
    final List<Action> actions;
    final Action focus;

    private ReportView(Screen s, Action focus, Action... actions) {
        screen = s;
        this.actions = Collections.unmodifiableList(Arrays.asList(actions));
        this.focus = focus;
    }

    static ReportView of(ReportState st, boolean details) {
        switch (st == null ? ReportState.IDLE : st) {
            case COLLECTING:
            case UPLOADING:
                return new ReportView(Screen.PROGRESS, Action.CANCEL, Action.CANCEL);
            case DONE:
                return new ReportView(Screen.DONE, Action.DONE, Action.DONE);
            case QUEUED:
                return new ReportView(Screen.QUEUED, Action.CLOSE, Action.CLOSE);
            case FAILED:
                return new ReportView(Screen.FAILED, Action.RETRY, Action.RETRY, Action.CLOSE);
            case IDLE:
            default:
                return details
                        ? new ReportView(Screen.DETAILS, Action.BACK, Action.BACK)
                        : new ReportView(Screen.START, Action.SEND, Action.SEND, Action.AUTO, Action.DETAILS, Action.CLOSE);
        }
    }

    /**
     * BACK: the details page and every finished screen return to START (the code stays on it as the last report),
     * a running report is stopped (and START shows again), START itself leaves.
     */
    Back back() {
        switch (screen) {
            case PROGRESS: return Back.CANCEL;
            case START: return Back.LEAVE;
            default: return Back.START;
        }
    }

    /** The focus on START after coming back from {@code from}: the row that opened the details page, else Send. */
    static Action returnFocus(Screen from) {
        return from == Screen.DETAILS ? Action.DETAILS : Action.SEND;
    }

    /** The bar of the PROGRESS screen, 0..1: collecting fills the first 70 %, sending the rest. */
    static float progress(ReportState st, int step, int steps, long sent, long total) {
        if (st == ReportState.COLLECTING) {
            int n = Math.max(1, steps);
            int s = Math.max(1, Math.min(step, n));
            return 0.7f * (s - 1) / n;
        }
        if (st == ReportState.UPLOADING) {
            long t = Math.max(1, total);
            return 0.7f + 0.3f * Math.min(Math.max(0, sent), t) / t;
        }
        return 0f;
    }

    /** The shown step of COLLECTING, 1..steps. */
    static int step(int step, int steps) {
        int n = Math.max(1, steps);
        return Math.max(1, Math.min(step, n));
    }

    /** The last report the START screen names: the newer of the manual and the automatic one; null: none. */
    static boolean newerIsAuto(String manualCode, long manualTime, String autoCode, long autoTime) {
        boolean m = manualCode != null && manualTime > 0, a = autoCode != null && autoTime > 0;
        return a && (!m || autoTime > manualTime);
    }
}
