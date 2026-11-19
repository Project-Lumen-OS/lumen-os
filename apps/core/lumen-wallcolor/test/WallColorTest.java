// SPDX-License-Identifier: Apache-2.0
package org.lumen.wallcolor;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Host tests of the pure wall colour classes: vendor messages, the stock flow and its HAL order, the pattern, the models. */
public final class WallColorTest {
    private static int fails, checks;

    private static void eq(String what, Object want, Object got) {
        checks++;
        if (!String.valueOf(want).equals(String.valueOf(got))) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    private static void near(String what, double want, double got) {
        checks++;
        if (Math.abs(want - got) > 0.011) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    /** Records every view and HAL call in order; the HAL answers with the next scripted value (true by default). */
    static final class Rec implements WallColorView, WallColorHal, WallColorFlow.Delay {
        final List<String> log = new ArrayList<>();
        final List<Boolean> answers = new ArrayList<>();
        final List<Runnable> later = new ArrayList<>();
        @Override public void call(WallColorStep c, Done d) { log.add("hal " + c); d.done(answers.isEmpty() || answers.remove(0)); }
        @Override public void post(Runnable r, long ms) { log.add("later " + ms); later.add(r); }
        @Override public void closeSystemDialogs() { log.add("closeDialogs"); }
        @Override public void showIntro() { log.add("intro"); }
        @Override public void hideIntro() { log.add("-intro"); }
        @Override public void showPattern(float a) { log.add("pattern " + a); }
        @Override public void patternAlpha(float a) { log.add("alpha " + a); }
        @Override public void closePattern(boolean fade) { log.add("-pattern " + (fade ? "fade" : "now")); }
        @Override public void showResult() { log.add("result"); }
        @Override public void hideResult() { log.add("-result"); }
        @Override public void showSaveAsk() { log.add("ask"); }
        @Override public void hideSaveAsk() { log.add("-ask"); }
        @Override public void toast(WallColorMessage m) { log.add("toast " + m); }
        String take() { String s = String.join(", ", log); log.clear(); return s; }
    }

    private static WallColorEvent ev(String json) { return WallColorEvent.parse(json); }

    // the vendor's own formats (libxgimi AC_Process strings), filled as the vendor prints them
    static final String SHOW = "{\"cmd\":\"show\",\"x\":0,\"y\":0,\"w\":1920,\"h\":1080,\"trans\":100,\"points\":[]}";
    static final String SHOW_HALF = "{\"cmd\":\"show\",\"x\":0,\"y\":0,\"w\":1920,\"h\":1080,\"trans\":50,\"points\":[]}";
    static final String SHOW_PTS = "{\"cmd\":\"show_detect\",\"x\":10,\"y\":20,\"w\":1800,\"h\":1000,\"trans\":140,\"points\":[[10,20],[1810,20],[10,1020],[1810,1020]]}";
    static final String CLOSE = "{\"cmd\":\"close\"}";
    static final String OK0 = "{\"cmd\":\"result\",\"status\":true,\"code\":0}";
    static final String OK1 = "{\"cmd\":\"result\",\"status\":true,\"code\":1}";
    static final String OK100 = "{\"cmd\":\"result\",\"status\":true,\"code\":100}";

    static String fail(int code) { return "{\"cmd\":\"result\",\"status\":false,\"code\":" + code + "}"; }

    public static void main(String[] a) throws Exception {
        // 1. messages
        WallColorEvent e = ev(SHOW);
        eq("show cmd", "show", e.cmd); near("show alpha", 1.0, e.alpha()); eq("show points", false, e.points);
        e = ev(SHOW_PTS);
        eq("detect cmd", "show_detect", e.cmd); near("trans clamped", 1.0, e.alpha()); eq("detect points", true, e.points);
        eq("half", "0.5", ev(SHOW_HALF).alpha());
        eq("close", "close", ev(CLOSE).cmd);
        e = ev(OK0); eq("ok status", true, e.status); eq("ok code", 0, e.code);
        e = ev(fail(3)); eq("fail status", false, e.status); eq("fail code", 3, e.code);
        eq("spaces", 4, ev("{ \"cmd\" : \"result\" , \"status\" : false , \"code\" : 4 }").code);
        eq("no cmd", null, ev("{\"status\":true}"));
        eq("garbage", null, ev("not json"));
        eq("null", null, ev(null));
        near("negative trans", 0.0, ev("{\"cmd\":\"show\",\"trans\":-5}").alpha());

        // 2. the stock flow
        Rec r = new Rec();
        WallColorFlow f = new WallColorFlow(r, r, r);
        f.open(); eq("open", "intro", r.take());
        f.start(); eq("start order", "-intro, hal AE_OFF, hal START", r.take());
        f.onEvent(ev(SHOW)); eq("show", "closeDialogs, pattern 1.0", r.take());
        f.onEvent(ev(SHOW_HALF)); eq("show again", "closeDialogs, alpha 0.5", r.take());
        f.onEvent(ev(CLOSE)); eq("close", "-pattern fade", r.take());
        f.onEvent(ev(CLOSE)); eq("close twice", "", r.take());
        f.onEvent(ev(OK0)); eq("result", "closeDialogs, result", r.take());
        f.focusMatched(); f.focusOriginal(); f.focusMatched();
        eq("focus applies", "hal USE_MATCHED, hal USE_ORIGINAL, hal USE_MATCHED", r.take());
        f.choose(); eq("ok closes", "-result", r.take());
        f.focusOriginal(); eq("no bar no call", "", r.take());
        f.onEvent(ev(OK0)); r.take();
        f.backOnResult(); eq("back asks", "ask", r.take());
        f.backOnResult(); eq("ask once", "", r.take());
        f.backOnSaveAsk(); eq("back on ask", "-ask", r.take());
        f.backOnResult(); f.save(false); eq("save original", "ask, -ask, -result, hal USE_ORIGINAL", r.take());
        f.onEvent(ev(OK0)); f.backOnResult(); r.take();
        f.save(true); eq("save matched", "-ask, -result, hal USE_MATCHED", r.take());
        f.onEvent(ev(OK0)); r.take(); f.home(); eq("home keeps matched", "-result, hal USE_MATCHED", r.take());
        f.home(); eq("home idle", "", r.take());
        // close while the bar is up waits 100 ms (stock)
        f.onEvent(ev(SHOW)); f.onEvent(ev(OK0)); r.take();
        f.onEvent(ev(CLOSE)); eq("close after result", "later 100", r.take());
        r.later.remove(0).run(); eq("delayed close", "-pattern fade", r.take());
        f.choose(); r.take();
        // screen off / on: everything closes, an open question keeps the matched colour, no bar while off
        f.onEvent(ev(SHOW)); f.onEvent(ev(OK0)); f.backOnResult(); r.take();
        f.screenOff(); eq("screen off", "-pattern now, -ask, -result, hal USE_MATCHED", r.take());
        f.onEvent(ev(OK0)); eq("no bar while off", "closeDialogs", r.take());
        f.screenOn(); eq("screen on idle", "", r.take());
        // failures: the stock text per code
        int[] codes = {1, 2, 3, 4, 5, 6, 7, 8, 12, 9, 0, 55};
        String[] want = {"CAMERA", "START_FAILED", "DISTANCE", "LIGHT_CHANGED", "CUSTOM_COLOUR", "PERFORMANCE",
                "AUTO_BRIGHTNESS", "MODE_3D", "BIG_SCREEN", "OUT_OF_RANGE", "OUT_OF_RANGE", "OUT_OF_RANGE"};
        for (int i = 0; i < codes.length; i++) {
            f.onEvent(ev(fail(codes[i])));
            eq("fail " + codes[i], "toast " + want[i], r.take());
        }
        f.onEvent(ev(SHOW)); r.take(); f.onEvent(ev(fail(100))); eq("mutex closes", "-pattern now", r.take());
        f.onEvent(ev(OK1)); f.onEvent(ev(OK100)); eq("true 1 / 100 quiet", "", r.take());
        f.onEvent(ev("{\"cmd\":\"result\",\"status\":true,\"code\":7}")); eq("true other", "toast OUT_OF_RANGE", r.take());
        f.open(); f.reset(); eq("reset", "intro, -intro, hal USE_ORIGINAL", r.take());
        f.open(); f.back(); eq("back on card", "intro, -intro", r.take());
        r.answers.add(true); r.answers.add(false);
        f.open(); f.start(); eq("refused start", "intro, -intro, hal AE_OFF, hal START, toast NOT_STARTED", r.take());
        r.answers.add(false); r.answers.add(true);
        f.start(); eq("AE answer not judged", "hal AE_OFF, hal START", r.take());
        eq("showing idle", false, f.showing());
        // the feature makes exactly the four stock calls
        eq("calls", "[AE_OFF, START, USE_MATCHED, USE_ORIGINAL]", Arrays.toString(WallColorStep.values()));

        // 3. the pattern
        near("disc 0", 0.1, WallColorPattern.discScale(0));
        near("disc 250", 1.1, WallColorPattern.discScale(250));
        near("disc 500", 2.1, WallColorPattern.discScale(500));
        eq("disc shown 699", true, WallColorPattern.discShown(699));
        eq("disc gone 700", false, WallColorPattern.discShown(700));
        eq("loop at 500", true, WallColorPattern.loopShown(500));
        eq("no loop 499", false, WallColorPattern.loopShown(499));
        near("frame start", 81, WallColorPattern.frame(500));
        near("frame wraps", 81, WallColorPattern.frame(500 + WallColorPattern.LOOP_MS));
        eq("loop ms", 1033, WallColorPattern.LOOP_MS);
        near("corner 81", 1.0, WallColorPattern.cornerAlpha(81));
        near("corner 92", 0.5, WallColorPattern.cornerAlpha(92));
        near("corner 103", 0.0, WallColorPattern.cornerAlpha(103));
        near("corner 113", 0.0, WallColorPattern.cornerAlpha(113));
        near("corner 133", 0.49, WallColorPattern.cornerAlpha(133));
        near("corner 143", 0.98, WallColorPattern.cornerAlpha(143));
        eq("swatches", 6, WallColorPattern.SWATCH.length);
        eq("swatch places", 6, WallColorPattern.SWATCH_AT.length);
        eq("dark disc", Integer.toHexString(0xFF343434), Integer.toHexString(WallColorPattern.disc(3)));
        eq("light disc", Integer.toHexString(0xFFFFFFFF), Integer.toHexString(WallColorPattern.disc(5)));
        // the corner marks frame the swatches, the pill stays below them
        float top = 228 - 42, bottom = 852 + 42;
        for (float[] s : WallColorPattern.SWATCH_AT) {
            eq("swatch inside x " + s[0], true, s[0] - WallColorPattern.SWATCH_W / 2 > 240 - 42 && s[0] + WallColorPattern.SWATCH_W / 2 < 1680 + 42);
            eq("swatch inside y " + s[1], true, s[1] - WallColorPattern.SWATCH_H / 2 > top && s[1] + WallColorPattern.SWATCH_H / 2 < bottom);
        }
        eq("pill below", true, WallColorPattern.PILL_Y - WallColorPattern.PILL_H / 2 > bottom);
        for (float[] c : WallColorPattern.CORNER_AT) eq("mirror " + c[0] + "," + c[1], true,
                (c[0] < 960) == (c[2] > 0) && (c[1] < 540) == (c[3] > 0));

        // 4. the model files
        for (int i = 0; i < a.length; i += 3) {
            WallColorModel m;
            try (FileReader in = new FileReader(a[i])) { m = WallColorModel.read(in); }
            eq(a[i] + " ambient", a[i + 1], m.ambientLight);
            eq(a[i] + " type", a[i + 2], m.type);
        }
        WallColorModel d = WallColorModel.read(new java.io.StringReader("# x\ntype=oops\n"));
        eq("bad type", 3, d.type); eq("default ambient", false, d.ambientLight);

        System.out.println((fails == 0 ? "PASS" : "FAIL") + " WallColorTest " + (checks - fails) + "/" + checks);
        System.exit(fails == 0 ? 0 : 1);
    }
}
