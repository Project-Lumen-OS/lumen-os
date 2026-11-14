package org.lumen.hal.gmpf;

import static org.lumen.hal.gmpf.utils.Args.checkRange;

/**
 * The optical zoom session guard of every Z9X family projector (stock ZoomStateView / ZoomUtils, zoom audit F1 / F2):
 * a lens step is sent only inside a prepared session (setKstPrepare(true, 2), zoomStart) with a range and a step read
 * of the session, as the neighbour of that read; each step uses the read up. The limit position beyond optical index
 * 0 is entered and left only as the neighbour of a fresh limit read, at optical index 0, inside [-count, 0] of the
 * limit count. Process-wide: the IGmpf and IGmpf2 clients of one app share it.
 */
public final class ZoomGuard {
    /** Sanity bound of the optical range (OpticalZoomPanel.MAX_POSITIONS). */
    public static final int MAX_OPTICAL_STEP = 64;
    /** Sanity bound of the limit count (1 expected). */
    public static final int MAX_LIMIT_STEPS = 4;
    private static final int NONE = -1;
    private static final int LIM_UNKNOWN = Integer.MIN_VALUE;

    private static final Object LOCK = new Object();
    private static boolean sPrepared, sSession;
    private static int sOptMin = NONE, sOptMax = NONE, sOptCur = NONE;
    private static int sLimTotal = NONE, sLimCur = LIM_UNKNOWN;

    private ZoomGuard() {}

    /** After setKstPrepare(enter, 2) was sent (leaving: before it is sent). */
    public static void prepare(boolean enter) {
        synchronized (LOCK) {
            sPrepared = enter;
            if (!enter) clear();
        }
    }

    /** Before zoomStart is sent. */
    public static void clearReads() {
        synchronized (LOCK) { clear(); }
    }

    /** After zoomStart was sent. */
    public static void sessionOpened() {
        synchronized (LOCK) { sSession = true; }
    }

    /** Before zoomFinish is sent. */
    public static void sessionClosed() {
        synchronized (LOCK) {
            sSession = false;
            clear();
        }
    }

    /** After the home walk: the lens is somewhere new. */
    public static void noteHome() {
        synchronized (LOCK) {
            sOptCur = NONE;
            sLimCur = LIM_UNKNOWN;
        }
    }

    public static void noteLimitTotal(int count) {
        synchronized (LOCK) { sLimTotal = sSession && count >= 0 && count <= MAX_LIMIT_STEPS ? count : NONE; }
    }

    public static void noteRange(int opticalMin, int opticalMax) {
        synchronized (LOCK) {
            boolean ok = sSession && opticalMin >= 0 && opticalMax > opticalMin && opticalMax <= MAX_OPTICAL_STEP;
            sOptMin = ok ? opticalMin : NONE;
            sOptMax = ok ? opticalMax : NONE;
        }
    }

    public static void noteStep(int optical) {
        synchronized (LOCK) { sOptCur = sSession ? optical : NONE; }
    }

    public static void noteLimitRead(int limit) {
        synchronized (LOCK) { sLimCur = sSession ? limit : LIM_UNKNOWN; }
    }

    /**
     * Throws (nothing may be sent) unless step is a lens step allowed now; uses the step read up. what names the call
     * in the message. strictLimit: the limit position must have been read as 0 in the session, else only a non-zero
     * read or an unread count refuse.
     */
    public static void checkOpticalStep(String what, int step, boolean strictLimit) {
        synchronized (LOCK) {
            if (!sPrepared || !sSession) throw new IllegalStateException(what + " outside a prepared zoom session");
            if (sOptMax < 0 || sOptCur < 0) throw new IllegalStateException(what + " without a range and a fresh step read");
            checkRange("optical zoom step", step, sOptMin, sOptMax);
            if (Math.abs(step - sOptCur) != 1) {
                throw new IllegalArgumentException(what + "(" + step + ") is not next to the read position " + sOptCur);
            }
            boolean inLimit = strictLimit ? sLimCur != 0
                    : (sLimCur != LIM_UNKNOWN && sLimCur != 0) || (sLimTotal > 0 && sLimCur == LIM_UNKNOWN);
            if (inLimit) {
                throw new IllegalStateException(what + " while the lens may be in the limit zone (limit read "
                        + (sLimCur == LIM_UNKNOWN ? "none" : String.valueOf(sLimCur)) + ", count " + sLimTotal + ")");
            }
            sOptCur = NONE;
        }
    }

    /** Throws (nothing may be sent) unless step is a limit position step allowed now; uses both reads up. */
    public static void checkLimitStep(String what, int step) {
        checkRange("zoom limit step", step, -MAX_LIMIT_STEPS, 0);
        synchronized (LOCK) {
            if (!sPrepared || !sSession) throw new IllegalStateException(what + " outside a prepared zoom session");
            if (sLimTotal <= 0 || sLimCur == LIM_UNKNOWN) {
                throw new IllegalStateException(what + " without a limit count and a limit read");
            }
            if (sOptCur != 0) throw new IllegalStateException(what + " away from optical index 0 (step read " + sOptCur + ")");
            checkRange("zoom limit step", step, -sLimTotal, 0);
            if (Math.abs(step - sLimCur) != 1) {
                throw new IllegalArgumentException(what + "(" + step + ") is not next to the limit position " + sLimCur);
            }
            sLimCur = LIM_UNKNOWN;
            sOptCur = NONE;
        }
    }

    private static void clear() {
        sOptMin = sOptMax = sOptCur = NONE;
        sLimTotal = NONE;
        sLimCur = LIM_UNKNOWN;
    }
}
