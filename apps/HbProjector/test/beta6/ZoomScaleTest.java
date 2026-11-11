package org.lumen.projector.panel;

import java.util.Arrays;

/** Host test of ZoomScale against the stock HB dial (ZoomStateView) and the G0130 device table. */
public final class ZoomScaleTest {
    static int n, bad;
    static void eq(String what, Object got, Object want) {
        n++;
        boolean ok = (got instanceof int[] && want instanceof int[]) ? Arrays.equals((int[]) got, (int[]) want)
                : want == null ? got == null : want.equals(got);
        if (!ok) {
            bad++;
            System.out.println("FAIL " + what + ": got " + (got instanceof int[] ? Arrays.toString((int[]) got) : got)
                    + ", want " + (want instanceof int[] ? Arrays.toString((int[]) want) : want));
        }
    }

    public static void main(String[] a) {
        // G0130 table: 97 optical 0..12, 63(12,10) = 1 (device tests 2.8, zoombug 2026-10-31): 14 positions
        ZoomScale s = new ZoomScale(12, 1);
        eq("positions", s.positions(), 14);
        eq("last", s.last(), 13);
        // every position: the stock reads (98, 190) -> progress -> Size N of 14
        eq("p(190 -1)", s.progress(0, -1), 0);
        eq("size biggest", s.sizeNumber(0), 14);
        for (int c = 0; c <= 12; c++) {
            eq("p(98 " + c + ")", s.progress(c, 0), c + 1);
            eq("size 98 " + c, s.sizeNumber(c + 1), 13 - c);
        }
        eq("size smallest is 1", s.sizeNumber(s.last()), 1);
        // reads outside the table
        eq("98 13", s.progress(13, 0), -1);
        eq("98 -1", s.progress(-1, 0), -1);
        eq("190 -2", s.progress(0, -2), -1);
        eq("190 +1", s.progress(0, 1), -1);
        eq("190 with 98", s.progress(3, -1), -1);
        // every press, as stock setZoom$lambda$2 (smaller) / $4 (bigger)
        eq("smaller at 0", s.move(0, 1), new int[] {189, 0});
        eq("bigger at 1", s.move(1, -1), new int[] {189, -1});
        eq("bigger at 0 = end", s.move(0, -1), null);
        eq("smaller at last = end", s.move(13, 1), null);
        for (int p = 1; p < 13; p++) eq("smaller at " + p, s.move(p, 1), new int[] {95, p});       // 98 p-1 -> p
        for (int p = 2; p <= 13; p++) eq("bigger at " + p, s.move(p, -1), new int[] {95, p - 2});  // 98 p-1 -> p-2
        // walking the whole range smaller reaches Size 1, bigger reaches Size 14
        int p = 0;
        for (int i = 0; i < 20; i++) { int[] m = s.move(p, 1); if (m == null) break; p++; }
        eq("walk smaller ends at", s.sizeNumber(p), 1);
        for (int i = 0; i < 20; i++) { int[] m = s.move(p, -1); if (m == null) break; p--; }
        eq("walk bigger ends at", s.sizeNumber(p), 14);

        // a unit that reports one position more than it reaches (tester: "of 15", 1 never reachable)
        ZoomScale t = new ZoomScale(13, 1);
        eq("t positions", t.positions(), 15);
        eq("t refuse 95(13) at p 13", t.refusedAtEnd(13, 1), true);
        eq("t positions after", t.positions(), 14);
        eq("t now smallest", t.sizeNumber(13), 1);
        ZoomScale u = new ZoomScale(12, 2);
        eq("u positions", u.positions(), 15);
        eq("u refuse 189(-2) at p 1", u.refusedAtEnd(1, -1), true);
        eq("u limit after", u.limit(), 1);
        eq("u p of 190 -1", u.progress(0, -1), 0);
        eq("u biggest", u.sizeNumber(0), 14);
        // a refusal inside the range changes nothing
        ZoomScale v = new ZoomScale(12, 1);
        eq("v middle refusal", v.refusedAtEnd(5, 1), false);
        eq("v middle refusal bigger", v.refusedAtEnd(5, -1), false);
        eq("v positions kept", v.positions(), 14);
        // bounds
        eq("limit out of range = 0", new ZoomScale(12, 9).limit(), 0);
        eq("limit -1 = 0", new ZoomScale(12, -1).limit(), 0);
        boolean threw = false;
        try { new ZoomScale(0, 1); } catch (IllegalArgumentException e) { threw = true; }
        eq("max 0 refused", threw, true);

        System.out.println("ZoomScaleTest: " + (n - bad) + "/" + n + " passed");
        if (bad != 0) System.exit(1);
    }
}
