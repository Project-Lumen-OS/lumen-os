// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.parental;

import java.util.Random;

/** Host tests of the plain parts of lumen-parental: the state line format, the rules, the time limits, the password. */
public final class ParentalTest {
    private static int fails, oks;

    static void ok(String what, boolean v) {
        if (v) oks++; else { fails++; System.out.println("FAIL " + what); }
    }

    public static void main(String[] a) {
        ParentalState s = new ParentalState();
        ok("defaults are stock: lock off, child range, no limits", !s.lockOn && s.range == ControlRange.CHILD_ONLY
                && s.sessionMin == 0 && s.dailyMin == 0 && !s.bootPin && !s.hasPin());
        ok("stock session list", java.util.Arrays.equals(ParentalState.SESSION_MIN, new int[]{0, 20, 30, 40, 50, 60}));
        s.setPin("UDLR");
        ok("the password matches only itself", s.pin("UDLR") && !s.pin("UDRL") && !s.pin("UDL") && !s.pin(null));
        ok("only the hash is kept", !s.encode().contains("UDLR"));
        s.lockOn = true;
        s.childMode = true;
        s.allowed.add("com.kids.app");
        s.sessionMin = 20;
        s.dailyMin = 60;
        ParentalState d = ParentalState.decode(s.encode());
        ok("round trip", d.lockOn && d.childMode && d.allowed.contains("com.kids.app") && d.sessionMin == 20
                && d.dailyMin == 60 && d.pin("UDLR"));
        ok("a lock without a password reads as off", !ParentalState.decode("lock=1\n").lockOn);
        ok("a value off the lists reads as no limit", ParentalState.decode("session=33\ndaily=7\n").sessionMin == 0
                && ParentalState.decode("session=33\ndaily=7\n").dailyMin == 0);
        ok("garbage is ignored", ParentalState.decode("x\n=1\nday=abc\n") != null);

        ok("child mode shows only the picks and Home", ParentalRules.visible(d, "com.kids.app")
                && !ParentalRules.visible(d, "com.netflix") && ParentalRules.visible(d, ParentalRules.HOME));
        ok("child mode asks the password for others", ParentalRules.needsPin(d, "com.netflix") && !ParentalRules.needsPin(d, "com.kids.app"));
        d.childMode = false;
        ok("child range, child mode off: everything open", !ParentalRules.needsPin(d, "com.netflix") && ParentalRules.visible(d, "com.netflix"));
        d.range = ControlRange.GLOBAL;
        ok("global range: others ask, all shown", ParentalRules.needsPin(d, "com.netflix") && ParentalRules.visible(d, "com.netflix")
                && ParentalRules.needsPin(d, Parental.INPUT_PREFIX + "hdmi1"));
        ParentalRules.lockOff(d);
        ok("lock off drops every control", !ParentalRules.needsPin(d, "com.netflix") && !ParentalRules.controlled(d));

        ParentalState t = ParentalState.decode(s.encode());
        long now = 1_000_000L;
        LimitState st = LimitState.FREE;
        for (int i = 0; i < 19; i++) st = ParentalRules.tick(t, 20261119, now += 60_000L);
        ok("19 minutes of a 20 minute session: free", st == LimitState.FREE && t.usedToday == 19);
        st = ParentalRules.tick(t, 20261119, now += 60_000L);
        ok("minute 20: the 10 minute rest", st == LimitState.SESSION_REST && t.restUntil == now + 10 * 60_000L);
        ok("rest holds 10 minutes", ParentalRules.state(t, now + 9 * 60_000L) == LimitState.SESSION_REST
                && ParentalRules.state(t, now + 10 * 60_000L + 1) == LimitState.FREE);
        st = ParentalRules.tick(t, 20261119, now + 60_000L);
        ok("no counting during the rest", st == LimitState.SESSION_REST && t.usedToday == 20);
        now += 11 * 60_000L;
        for (int i = 0; i < 40; i++) st = ParentalRules.tick(t, 20261119, now += 60_000L);
        ok("the day's 60 minutes end the day", st == LimitState.DAILY_DONE || t.usedToday >= 40);
        while (st != LimitState.DAILY_DONE) st = ParentalRules.tick(t, 20261119, now += 11 * 60_000L);
        ok("daily done", ParentalRules.state(t, now) == LimitState.DAILY_DONE);
        ParentalRules.unlockLimit(t, now);
        ok("the password unlocks the limit", ParentalRules.state(t, now) == LimitState.FREE);
        ParentalRules.tick(t, 20261120, now + 60_000L);
        ok("a new day starts at zero", t.usedToday == 1 && t.day == 20261120);
        t.childMode = false;
        ok("child range outside child mode counts nothing", ParentalRules.tick(t, 20261120, now) == LimitState.FREE && t.usedToday == 1);

        ParentalState b = ParentalState.decode(s.encode());
        b.childMode = false;
        b.bootChild = true;
        ParentalRules.boot(b);
        ok("child mode at power on", b.childMode && b.usedSession == 0);

        ok("arrow keys only", PinCode.letter(19) == 'U' && PinCode.letter(22) == 'R' && PinCode.letter(23) == 0
                && !PinCode.complete("UDLX") && PinCode.complete("LLLL"));
        Random r = new Random(7);
        for (int i = 0; i < 50; i++) {
            Quiz q = Quiz.next(r);
            int right = 0;
            for (int c : q.choices) if (q.right(c)) right++;
            if (right != 1 || q.choices.length != 4) { ok("quiz has one right answer of four", false); break; }
        }
        ok("quiz ok", true);
        // the writes of the menu's page through ParentalProvider (ParentalSettings)
        ParentalState w = new ParentalState();
        ok("settings: nothing but the lock changes while it is off", !ParentalSettings.apply(w, ParentalSettings.CHILD, "1")
                && !w.childMode && !ParentalSettings.apply(w, ParentalSettings.LOCK_OFF, "1"));
        w.setPin("UUDD");
        w.lockOn = true;
        ok("settings: a stock session time", ParentalSettings.apply(w, ParentalSettings.SESSION, "30") && w.sessionMin == 30);
        ok("settings: a time off the stock list changes nothing", !ParentalSettings.apply(w, ParentalSettings.DAILY, "45")
                && w.dailyMin == 0 && !ParentalSettings.apply(w, ParentalSettings.SESSION, "x") && w.sessionMin == 30);
        ok("settings: the range by its token only", ParentalSettings.apply(w, ParentalSettings.RANGE, "global")
                && w.range == ControlRange.GLOBAL && !ParentalSettings.apply(w, ParentalSettings.RANGE, "nowhere")
                && w.range == ControlRange.GLOBAL);
        ok("settings: switches take 1 and 0 only", ParentalSettings.apply(w, ParentalSettings.BOOT_PIN, "1") && w.bootPin
                && !ParentalSettings.apply(w, ParentalSettings.BOOT_CHILD, "yes") && !w.bootChild);
        ok("settings: apps of child mode", ParentalSettings.apply(w, ParentalSettings.ALLOW, "com.kids")
                && w.allowed.contains("com.kids") && ParentalSettings.apply(w, ParentalSettings.DENY, "com.kids")
                && w.allowed.isEmpty() && !ParentalSettings.apply(w, ParentalSettings.DENY, "com.kids"));
        String shown = ParentalSettings.shown(w);
        ok("settings: the shown state has no password", !("\n" + shown).contains("\nsalt=")
                && !("\n" + shown).contains("\npin=") && !shown.contains(w.pinHash) && !shown.contains(w.salt));
        ParentalState back = ParentalSettings.read(shown);
        ok("settings: the shown state reads back with the lock", back.lockOn && back.sessionMin == 30
                && back.range == ControlRange.GLOBAL && back.bootPin && !back.hasPin());
        ok("settings: turning the lock off drops every control", ParentalSettings.apply(w, ParentalSettings.LOCK_OFF, "1")
                && !w.lockOn && !ParentalSettings.read(ParentalSettings.shown(w)).lockOn);
        System.out.println("ParentalTest: " + oks + " ok, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
