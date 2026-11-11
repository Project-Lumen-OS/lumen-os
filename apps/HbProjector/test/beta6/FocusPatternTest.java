package org.lumen.projector;

/** Host test of FocusPattern against the stock HB manual-focus chart (SystemUI cp01_focus_bk, 1920x1080). */
public final class FocusPatternTest {
    static int n, bad;
    static void eq(String what, Object got, Object want) {
        n++;
        if (!want.equals(got)) { bad++; System.out.println("FAIL " + what + ": got " + got + ", want " + want); }
    }
    static void near(String what, float got, float want) {
        n++;
        if (Math.abs(got - want) > 0.51f) { bad++; System.out.println("FAIL " + what + ": got " + got + ", want " + want); }
    }

    public static void main(String[] a) {
        eq("white", FocusPattern.BACKGROUND, 0xFFFFFFFF);
        eq("black", FocusPattern.INK, 0xFF000000);
        float[][] w = FocusPattern.wheels(1920, 1080);
        eq("five wheels", w.length, 5);
        float[][] want = {{0, 0, 230}, {1920, 0, 230}, {0, 1080, 230}, {1920, 1080, 230}, {960, 540, 170}};
        String[] nm = {"top-left", "top-right", "bottom-left", "bottom-right", "centre"};
        for (int i = 0; i < 5; i++) for (int k = 0; k < 3; k++) near(nm[i] + " " + k, w[i][k], want[i][k]);
        float[][] w4 = FocusPattern.wheels(3840, 2160);
        near("4k corner r", w4[0][2], 460);
        near("4k centre x", w4[4][0], 1920);
        float[] l = FocusPattern.line(1920, 1080);
        near("line top", l[1], 852);
        near("line bottom", l[3], 856);
        near("line left", l[0], 0);
        near("line right", l[2], 1920);
        // black and white alternate evenly over the full circle
        eq("spokes", FocusPattern.SPOKES, 60);
        near("sweep", FocusPattern.wedgeSweep(), 3f);
        near("wedge 0", FocusPattern.wedgeStart(0), 0);
        near("wedge 1", FocusPattern.wedgeStart(1), 6);
        near("last wedge ends before 360", FocusPattern.wedgeStart(FocusPattern.SPOKES - 1) + FocusPattern.wedgeSweep(), 357);
        boolean threw = false;
        try { FocusPattern.wedgeStart(FocusPattern.SPOKES); } catch (IllegalArgumentException e) { threw = true; }
        eq("wedge out of range", threw, true);
        threw = false;
        try { FocusPattern.wheels(0, 1080); } catch (IllegalArgumentException e) { threw = true; }
        eq("empty view", threw, true);
        System.out.println("FocusPatternTest: " + (n - bad) + "/" + n + " passed");
        if (bad != 0) System.exit(1);
    }
}
