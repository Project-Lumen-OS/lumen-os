// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.List;

/** ElderColorRule over a recording port: the stock dialog's call order, the mutex, the reads, TemperatureText. */
public final class ElderColorRuleTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    /** A vendor that keeps the switch and the strength and records every call. */
    static final class Port implements ElderColorRule.Port {
        int tone, level;
        final List<String> calls = new ArrayList<>();
        @Override public int elderTone() { calls.add("179"); return tone; }
        @Override public boolean elderToneSet(boolean on) { calls.add("180(" + (on ? 1 : 0) + ")"); tone = on ? 1 : 0; return true; }
        @Override public int elderLevel() { calls.add("181"); return level; }
        @Override public boolean elderLevelSet(int l) { calls.add("182(" + l + ")"); level = l; return true; }
    }

    public static void main(String[] a) throws Exception {
        check(java.util.Arrays.equals(ElderColorRule.TOKENS, new String[]{"off", "low", "medium", "high"}), "tokens off low medium high");
        check(ElderColorRule.MAX_LEVEL == 2, "strengths 0..2");
        Port p = new Port();
        check("off".equals(ElderColorRule.read(p)) && p.calls.equals(List.of("179")), "off: the switch alone is read " + p.calls);
        p.calls.clear();
        check("medium".equals(ElderColorRule.set(p, "medium")), "medium set and read back");
        check(p.calls.equals(List.of("179", "180(1)", "182(1)", "179", "181")), "off -> medium: switch on, then strength 1, then the read " + p.calls);
        p.calls.clear();
        check("high".equals(ElderColorRule.set(p, "high")) && p.calls.equals(List.of("179", "182(2)", "179", "181")),
                "on -> high: the switch is not sent again " + p.calls);
        p.calls.clear();
        check("off".equals(ElderColorRule.set(p, "off")) && p.calls.equals(List.of("179", "180(0)", "179")),
                "on -> off: only the switch, no strength " + p.calls);
        p.calls.clear();
        check("off".equals(ElderColorRule.set(p, "off")) && p.calls.equals(List.of("179", "179")), "off -> off: nothing sent " + p.calls);
        p.tone = 1; p.level = 7;
        check(ElderColorRule.read(p) == null, "a strength out of range reads as no value");
        boolean threw = false;
        try { ElderColorRule.set(p, "max"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "an unknown token throws, nothing sent");
        for (int m : new int[]{6, 25, 5, 29}) check(!ElderColorRule.allowed(m), "mode " + m + " refuses senior colour (office, performance, colour accuracy)");
        for (int m : new int[]{1, 9, 10, 26, 0}) check(ElderColorRule.allowed(m), "mode " + m + " takes senior colour");
        check(ElderColorRule.officeMode(6) && ElderColorRule.officeMode(25) && !ElderColorRule.officeMode(5), "office modes 6 and 25");
        check("lumen_m_elder_mode_lock".equals(ElderColorRule.NOTE), "the lock note");
        check("41 \u00B0C".equals(TemperatureText.of(41)) && "200 \u00B0C".equals(TemperatureText.of((byte) 200))
                && TemperatureText.of((Integer) null) == null, "temperature text: unsigned byte, none without a read");
        System.out.println("ElderColorRuleTest: " + pass + " passed, " + fail + " failed");
        if (fail != 0) System.exit(1);
    }
}
