// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.parental;

/**
 * The parent page's writes, one key each (ParentalProvider "set", asked by the projector menu's Parental controls
 * page), and the state another app may read. Plain Java for the host tests. Every key but turning the lock off needs
 * the lock on (the stock page shows those rows only then); a value outside the stock choices changes nothing.
 */
public final class ParentalSettings {
    public static final String RANGE = "range", SESSION = "session", DAILY = "daily", BOOT_PIN = "boot_pin",
            BOOT_CHILD = "boot_child", CHILD = "child", ALLOW = "allow", DENY = "deny", LOCK_OFF = "lock_off";
    public static final String ON = "1", OFF = "0";

    private ParentalSettings() {}

    /** True when the state changed. */
    public static boolean apply(ParentalState s, String key, String value) {
        if (LOCK_OFF.equals(key)) {
            if (!s.lockOn) return false;
            ParentalRules.lockOff(s);
            return true;
        }
        if (!s.lockOn || key == null || value == null) return false;
        switch (key) {
            case RANGE:
                for (ControlRange r : ControlRange.values()) {
                    if (r.token.equals(value)) {
                        s.range = r;
                        return true;
                    }
                }
                return false;
            case SESSION: return minutes(ParentalState.SESSION_MIN, value, m -> s.sessionMin = m);
            case DAILY: return minutes(ParentalState.DAILY_MIN, value, m -> s.dailyMin = m);
            case BOOT_PIN: return flag(value, on -> s.bootPin = on);
            case BOOT_CHILD: return flag(value, on -> s.bootChild = on);
            case CHILD: return flag(value, on -> s.childMode = on);
            case ALLOW: return !value.trim().isEmpty() && s.allowed.add(value.trim());
            case DENY: return s.allowed.remove(value.trim());
            default: return false;
        }
    }

    private interface IntSet { void set(int v); }

    private interface FlagSet { void set(boolean v); }

    private static boolean minutes(int[] options, String value, IntSet set) {
        try {
            int v = Integer.parseInt(value);
            for (int o : options) {
                if (o == v) {
                    set.set(v);
                    return true;
                }
            }
        } catch (NumberFormatException ignored) {
            // not a stock choice
        }
        return false;
    }

    private static boolean flag(String value, FlagSet set) {
        if (!ON.equals(value) && !OFF.equals(value)) return false;
        set.set(ON.equals(value));
        return true;
    }

    /** The state without the password's salt and hash: what the provider hands out. */
    public static String shown(ParentalState s) {
        StringBuilder b = new StringBuilder();
        for (String l : s.encode().split("\n")) {
            if (!l.startsWith("salt=") && !l.startsWith("pin=")) b.append(l).append('\n');
        }
        return b.toString();
    }

    /** {@link #shown} read back (the lock as stated: the copy has no password of its own). */
    public static ParentalState read(String shown) {
        ParentalState s = ParentalState.decode(shown);
        s.lockOn = shown != null && ("\n" + shown).contains("\nlock=1\n");
        return s;
    }
}
