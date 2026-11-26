// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.parental;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * The parent's settings and today's use, one line per key (no JSON so the host tests need no Android). Defaults are the
 * stock ones: lock off, range child mode only, no time limits, no password at power on.
 */
public final class ParentalState {
    /** Stock single session choices (SettingActivity.timeList: off, 20, 30, 40, 50, 60 min). */
    public static final int[] SESSION_MIN = {0, 20, 30, 40, 50, 60};
    /** Daily choices: off, 1, 1.5, 2, 3, 4 h (the stock list was not in the decompile). */
    public static final int[] DAILY_MIN = {0, 60, 90, 120, 180, 240};

    public boolean lockOn;
    public String salt, pinHash;
    public ControlRange range = ControlRange.CHILD_ONLY;
    public int sessionMin, dailyMin;
    public boolean bootPin, bootChild, childMode;
    public final Set<String> allowed = new TreeSet<>();
    /** Today's use: the day (yyyymmdd), minutes used today, minutes of the running session, rest end (ms). */
    public int day, usedToday, usedSession;
    public long restUntil;

    public boolean hasPin() { return salt != null && pinHash != null; }

    public void setPin(String keys) {
        if (!PinCode.complete(keys)) throw new IllegalArgumentException("pin");
        salt = PinCode.newSalt();
        pinHash = PinCode.hash(salt, keys);
    }

    public boolean pin(String keys) { return PinCode.matches(salt, pinHash, keys); }

    public String encode() {
        StringBuilder s = new StringBuilder();
        line(s, "lock", lockOn ? "1" : "0");
        if (hasPin()) {
            line(s, "salt", salt);
            line(s, "pin", pinHash);
        }
        line(s, "range", range.token);
        line(s, "session", Integer.toString(sessionMin));
        line(s, "daily", Integer.toString(dailyMin));
        line(s, "boot_pin", bootPin ? "1" : "0");
        line(s, "boot_child", bootChild ? "1" : "0");
        line(s, "child", childMode ? "1" : "0");
        line(s, "allowed", String.join(",", allowed));
        line(s, "day", Integer.toString(day));
        line(s, "used_today", Integer.toString(usedToday));
        line(s, "used_session", Integer.toString(usedSession));
        line(s, "rest_until", Long.toString(restUntil));
        return s.toString();
    }

    public static ParentalState decode(String text) {
        ParentalState p = new ParentalState();
        if (text == null) return p;
        for (String l : text.split("\n")) {
            int i = l.indexOf('=');
            if (i <= 0) continue;
            String k = l.substring(0, i), v = l.substring(i + 1);
            try {
                switch (k) {
                    case "lock": p.lockOn = "1".equals(v); break;
                    case "salt": p.salt = v; break;
                    case "pin": p.pinHash = v; break;
                    case "range": p.range = ControlRange.of(v); break;
                    case "session": p.sessionMin = choice(SESSION_MIN, Integer.parseInt(v)); break;
                    case "daily": p.dailyMin = choice(DAILY_MIN, Integer.parseInt(v)); break;
                    case "boot_pin": p.bootPin = "1".equals(v); break;
                    case "boot_child": p.bootChild = "1".equals(v); break;
                    case "child": p.childMode = "1".equals(v); break;
                    case "allowed":
                        for (String a : v.split(",")) if (!a.trim().isEmpty()) p.allowed.add(a.trim());
                        break;
                    case "day": p.day = Integer.parseInt(v); break;
                    case "used_today": p.usedToday = Math.max(0, Integer.parseInt(v)); break;
                    case "used_session": p.usedSession = Math.max(0, Integer.parseInt(v)); break;
                    case "rest_until": p.restUntil = Long.parseLong(v); break;
                    default: break;
                }
            } catch (NumberFormatException ignored) {
                // a broken value keeps its default
            }
        }
        if (!p.hasPin()) p.lockOn = false;
        return p;
    }

    public Set<String> allowedView() { return Collections.unmodifiableSet(allowed); }

    private static int choice(int[] options, int v) {
        for (int o : options) if (o == v) return v;
        return 0;
    }

    private static void line(StringBuilder s, String k, String v) { s.append(k).append('=').append(v).append('\n'); }
}
