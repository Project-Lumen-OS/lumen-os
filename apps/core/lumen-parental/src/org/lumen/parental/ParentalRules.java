// SPDX-License-Identifier: Apache-2.0
package org.lumen.parental;

/**
 * What the child lock decides, plain Java: which apps Home shows and which launches ask the password, and the time limits
 * (stock ChildModeTimingService: a session over its limit needs a 10 minute rest, the daily time ends the day).
 */
public final class ParentalRules {
    /** Stock: "单次使用超过限制，需休息10分钟". */
    public static final int REST_MIN = 10;
    /** Packages that always stay reachable: Lumen Home itself and the parent page's host. */
    public static final String HOME = "org.lumen.home";

    private ParentalRules() {}

    /** True while the controls act: the lock is on and either child mode runs or the range is everywhere. */
    public static boolean controlled(ParentalState s) {
        return s.lockOn && (s.childMode || s.range == ControlRange.GLOBAL);
    }

    /** Home shows the app: everything outside child mode, in child mode only the parent's picks. */
    public static boolean visible(ParentalState s, String key) {
        return !s.lockOn || !s.childMode || HOME.equals(key) || s.allowed.contains(key);
    }

    /** Opening the app (or input, key "input:ID") asks the password first. */
    public static boolean needsPin(ParentalState s, String key) {
        if (!s.lockOn || HOME.equals(key) || s.allowed.contains(key)) return false;
        return s.childMode || s.range == ControlRange.GLOBAL;
    }

    /** One minute of use at day {@code day} and time {@code now}; returns the limit state after it. */
    public static LimitState tick(ParentalState s, int day, long now) {
        if (s.day != day) {
            s.day = day;
            s.usedToday = 0;
            s.usedSession = 0;
        }
        if (!controlled(s)) return LimitState.FREE;
        LimitState before = state(s, now);
        if (before != LimitState.FREE) return before;
        s.usedToday++;
        s.usedSession++;
        if (s.sessionMin > 0 && s.usedSession >= s.sessionMin) {
            s.usedSession = 0;
            s.restUntil = now + REST_MIN * 60_000L;
        }
        return state(s, now);
    }

    public static LimitState state(ParentalState s, long now) {
        if (!controlled(s)) return LimitState.FREE;
        if (s.dailyMin > 0 && s.usedToday >= s.dailyMin) return LimitState.DAILY_DONE;
        if (s.restUntil > now) return LimitState.SESSION_REST;
        return LimitState.FREE;
    }

    /** The parent's password ends a rest or the day's limit: the controls pause until the next session. */
    public static void unlockLimit(ParentalState s, long now) {
        s.restUntil = 0;
        s.usedSession = 0;
        if (s.dailyMin > 0 && s.usedToday >= s.dailyMin) s.usedToday = s.dailyMin - 1;
    }

    /** At power on: child mode as the parent chose, a fresh session. */
    public static void boot(ParentalState s) {
        if (s.lockOn && s.bootChild) s.childMode = true;
        s.usedSession = 0;
    }

    /** Closing the lock drops every control (stock 关闭儿童锁: time limits, power on password). */
    public static void lockOff(ParentalState s) {
        s.lockOn = false;
        s.childMode = false;
        s.restUntil = 0;
    }
}
