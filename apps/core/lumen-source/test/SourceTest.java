// SPDX-License-Identifier: Apache-2.0
package org.lumen.source;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/** Host test of the pure Source key rules (gsi-stock analysis). */
public final class SourceTest {
    private static int pass, fail;
    private static final int H = SourceCycle.HOME, N = SourceCycle.NONE;

    public static void main(String[] a) throws Exception {
        TreeMap<String, SourceRule> models = models(new File(System.getProperty("gsi"), "devices"));
        ok(models.size() == 9, "9 projector models carry source.tsv: " + models.keySet());
        for (String c : models.keySet()) {
            SourceRule r = models.get(c);
            boolean play6 = c.equals("dragon_healthy");
            ok(r.homeAfterLast == !play6, c + ": after the last port " + (play6 ? "HDMI 1 again" : "Home") + " as stock");
            ok(r.osdMs == 3000, c + ": the card stays 3000 ms (xhplayer SIGNAL_DIALOG_SHOW_TIME)");
            if (play6) onePort(c, r); else twoPorts(c, r);
            quickPresses(c, r, play6 ? new int[] {1} : new int[] {1, 2});
        }
        badData();
        text();
        System.out.println((fail == 0 ? "ok   " : "FAIL ") + "SourceTest " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    /** Z9X, HB, X50 family: Home -> first connected -> next -> Home, no skipping on HDMI. */
    private static void twoPorts(String c, SourceRule r) {
        int[] p = {1, 2};
        eq(SourceCycle.next(r, p, set(1, 2), H), 1, c + ": Home, both connected -> HDMI 1");
        eq(SourceCycle.next(r, p, set(2), H), 2, c + ": Home, only HDMI 2 connected -> HDMI 2 (skips the empty one)");
        eq(SourceCycle.next(r, p, set(), H), 1, c + ": Home, nothing connected -> HDMI 1 anyway");
        eq(SourceCycle.next(r, p, set(1, 2), 1), 2, c + ": HDMI 1 -> HDMI 2");
        eq(SourceCycle.next(r, p, set(1), 1), 2, c + ": HDMI 1 -> HDMI 2 also when HDMI 2 is empty (no skipping on HDMI)");
        eq(SourceCycle.next(r, p, set(1, 2), 2), H, c + ": HDMI 2 -> Home");
        eq(SourceCycle.next(r, p, set(), 2), H, c + ": HDMI 2 -> Home with nothing connected");
        eq(SourceCycle.next(r, new int[] {2}, set(2), 2), N, c + ": one registered port and on it -> no step");
        eq(SourceCycle.next(r, new int[0], set(), H), N, c + ": no input registered -> no step");
        eq(SourceCycle.next(r, new int[] {1, 2, 3}, set(3), 2), 3, c + ": a third port would come before Home");
    }

    /** Play 6: one HDMI port. */
    private static void onePort(String c, SourceRule r) {
        int[] p = {1};
        eq(SourceCycle.next(r, p, set(1), H), 1, c + ": Home -> HDMI");
        eq(SourceCycle.next(r, p, set(), H), 1, c + ": Home -> HDMI with nothing connected");
        eq(SourceCycle.next(r, p, set(1), 1), N, c + ": on HDMI the key does nothing (only one port)");
        eq(SourceCycle.next(r, new int[] {1, 2}, set(), 2), 1, c + ": the Play 6 rule wraps to HDMI 1 after the last port");
    }

    /** Presses while the card is up go on from the last step; the stale screen value is never used then. */
    private static void quickPresses(String c, SourceRule r, int[] p) {
        SourcePress s = new SourcePress();
        Set<Integer> all = set(p);
        if (p.length == 2) {
            int[] go = new int[3], shown = new int[3];
            for (int i = 0; i < 3; i++) {
                SourceStep st = s.press(1000 + i * 250, r, p, all, H);     // the screen still says Home
                go[i] = st.go;
                shown[i] = st.shown;
            }
            eq(Arrays.toString(go), "[1, 2, 0]", c + ": three quick presses from Home switch HDMI 1, HDMI 2, Home");
            eq(Arrays.toString(shown), "[1, 2, 0]", c + ": the card names each step");
            ok(s.burst(1500 + r.osdMs - 1, r) && !s.burst(1500 + r.osdMs, r), c + ": the card's time counts from the last press");
            SourceStep later = s.press(1500 + r.osdMs + 10, r, p, all, 2);
            eq(later.from, 2, c + ": after the card is gone the next press starts from the screen");
            eq(later.go, H, c + ": ... and goes on from there (HDMI 2 -> Home)");
            SourcePress t = new SourcePress();
            StringBuilder seq = new StringBuilder();
            for (int i = 0; i < 6; i++) seq.append(t.press(i * 100, r, p, all, i < 3 ? H : 1).go).append(' ');
            eq(seq.toString().trim(), "1 2 0 1 2 0", c + ": six quick presses go round twice, whatever the screen says meanwhile");
        } else {
            SourceStep a = s.press(0, r, p, all, H), b = s.press(300, r, p, all, H), d = s.press(600, r, p, all, H);
            eq(a.go + " " + b.go + " " + d.go, "1 -1 -1", c + ": Home -> HDMI, then quick presses stay on HDMI");
            eq(b.shown + " " + d.shown, "1 1", c + ": the card keeps naming HDMI");
        }
    }

    private static void badData() {
        String[] bad = {"after_last\thome\n", "osd_ms\t3000\n", "after_last\tnext\nosd_ms\t3000\n",
                "after_last\thome\nosd_ms\t50\n", "after_last\thome\nosd_ms\t3000\nskip\tyes\n", "after_last home\n"};
        for (String b : bad) {
            boolean threw = false;
            try {
                SourceRule.parse(b);
            } catch (RuntimeException e) {
                threw = true;
            }
            ok(threw, "bad data refused: " + b.replace("\n", "|").replace("\t", " "));
        }
        SourceRule r = SourceRule.parse("# x\nafter_last\tfirst\twhy\nosd_ms\t2500\n");
        ok(!r.homeAfterLast && r.osdMs == 2500, "comments and the evidence column are read");
    }

    private static void text() {
        eq(OsdText.line("HDMI 1", "PlayStation 5"), "HDMI 1 \u00b7 PlayStation 5", "port and device");
        eq(OsdText.line("HDMI 2", null), "HDMI 2", "no device: the port alone");
        eq(OsdText.line("HDMI 2", "  "), "HDMI 2", "an empty name is no name");
        for (String ph : new String[] {"Playback_1", "Playback 2", "Recorder_1", "TV", "Audio System", "Tuner_3"}) {
            eq(OsdText.line("HDMI 1", ph), "HDMI 1", "a framework placeholder is no name: " + ph);
        }
        eq(OsdText.line("HDMI 1", "Xbox"), "HDMI 1 \u00b7 Xbox", "a short real name stays");
        eq(OsdText.line("Console", "console"), "Console", "the same word once");
        ok(!OsdText.SEP.contains("-") && !OsdText.SEP.contains("\u2013") && !OsdText.SEP.contains("\u2014"),
                "no dash in the card");
    }

    private static TreeMap<String, SourceRule> models(File devices) throws Exception {
        TreeMap<String, SourceRule> out = new TreeMap<>();
        File[] ds = devices.listFiles();
        if (ds == null) return out;
        for (File d : ds) {
            File f = new File(d, "source.tsv");
            if (f.isFile()) out.put(d.getName(), SourceRule.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
        }
        return out;
    }

    private static Set<Integer> set(int... v) {
        Set<Integer> s = new HashSet<>();
        for (int x : v) s.add(x);
        return s;
    }

    private static void eq(Object got, Object want, String what) {
        ok(String.valueOf(got).equals(String.valueOf(want)), what + " (got " + got + ", want " + want + ")");
    }

    private static void ok(boolean c, String what) {
        if (c) pass++;
        else {
            fail++;
            System.out.println("FAIL " + what);
        }
    }
}
