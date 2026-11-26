// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Host tests of the Витрина's page motion: when it turns by itself (8 s, paused on the buttons, after keys, without
 * animations, under a screen reader), which pages stay decoded, the one plain cross fade on every model (800 ms,
 * the standard curve, the old picture opaque underneath), and the wiring in the activity, backdrop and loader.
 */
public final class HeroMotionTest {
    static int checks, fails;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static void eq(Object a, Object b, String what) {
        check(a == null ? b == null : a.equals(b), what + ": got [" + a + "] want [" + b + "]");
    }

    /** The motion tokens of lumen-ui Tokens.java (read from the source: Tokens needs the Android theme classes). */
    static final class Tokens {
        static int MS_HERO_PAGE = 8_000, MS_HERO_FADE = 800, MS_HERO_TEXT_OUT = 220, MS_HERO_TEXT_IN = 480;
        static boolean read;

        static void load() throws Exception {
            String dir = System.getProperty("home.src");
            if (dir == null) return;
            String f = dir + "/../../../../../core/lumen-ui/src/org/lumen/ui/Tokens.java";
            if (!Files.exists(Paths.get(f))) return;
            String src = read(f);
            MS_HERO_PAGE = token(src, "MS_HERO_PAGE");
            MS_HERO_FADE = token(src, "MS_HERO_FADE");
            MS_HERO_TEXT_OUT = token(src, "MS_HERO_TEXT_OUT");
            MS_HERO_TEXT_IN = token(src, "MS_HERO_TEXT_IN");
            read = true;
        }

        static int token(String src, String name) {
            Matcher m = Pattern.compile("\\b" + name + " = ([0-9_]+)").matcher(src);
            return m.find() ? Integer.parseInt(m.group(1).replace("_", "")) : -1;
        }
    }

    public static void main(String[] a) throws Exception {
        Tokens.load();
        tokens();
        rotation();
        pauses();
        window();
        curve();
        wiring();
        System.out.println((fails == 0 ? "ok   " : "FAIL ") + "Hero motion: " + checks + " checks, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }

    static void tokens() {
        check(Tokens.read || System.getProperty("home.src") == null, "the motion tokens were read from Tokens.java");
        eq(Tokens.MS_HERO_PAGE, 8_000, "a page stays 8 s");
        check(Tokens.MS_HERO_FADE >= 700 && Tokens.MS_HERO_FADE <= 900, "the cross fade takes about 0.8 s");
        check(Tokens.MS_HERO_TEXT_OUT + Tokens.MS_HERO_TEXT_IN <= Tokens.MS_HERO_FADE,
                "the text swap ends within the picture's fade");
    }

    static HeroRotation.State free(int pages) {
        HeroRotation.State s = new HeroRotation.State();
        s.started = true;
        s.home = true;
        s.pages = pages;
        return s;
    }

    static void rotation() {
        HeroRotation r = new HeroRotation(8000);
        HeroRotation.State s = free(3);
        r.shown(0);
        r.input(0);
        eq(r.delay(0, s), 8000L, "a fresh page waits the whole dwell");
        eq(r.delay(7999, s), 1L, "one ms before");
        eq(r.delay(8000, s), 0L, "due at 8 s");
        check(r.turn(8000, s, true), "turns when due and the art is ready");
        check(!r.turn(8000, s, false), "waits while the next picture is not decoded");
        check(!r.turn(7000, s, true), "never before the dwell");
        r.input(5000);
        eq(r.delay(8000, s), 5000L, "a key at 5 s pushes the turn to 13 s");
        eq(r.delay(13000, s), 0L, "13 s after a key at 5 s");
        r.shown(20000 + Tokens.MS_HERO_FADE);
        eq(r.delay(20000, s), (long) (8000 + Tokens.MS_HERO_FADE), "the dwell counts from the end of the change");
        HeroRotation q = new HeroRotation(Tokens.MS_HERO_PAGE);
        long t = 0;
        int turns = 0;
        q.shown(0);
        for (long now = 0; now <= 60_000; now += 100) {
            if (q.turn(now, s, true)) {
                turns++;
                q.shown(now + Tokens.MS_HERO_FADE);
                t = now;
            }
        }
        eq(turns, 60_000 / (Tokens.MS_HERO_PAGE + Tokens.MS_HERO_FADE), "one minute idle: a page every 8.8 s");
        check(t > 0, "the idle hero did turn");
        // keys every 3 s for a minute: never a turn (the user is busy), then one 8 s after the last key
        HeroRotation k = new HeroRotation(8000);
        k.shown(0);
        int busy = 0;
        for (long now = 0; now <= 60_000; now += 100) {
            if (now % 3000 == 0) k.input(now);
            if (k.turn(now, s, true)) busy++;
        }
        eq(busy, 0, "keys every 3 s: no turn under the user's hands");
        eq(k.delay(60_000, s), 8000L, "after the last key the full dwell");
    }

    static void pauses() {
        HeroRotation r = new HeroRotation(8000);
        r.shown(0);
        HeroRotation.State s = free(3);
        eq(HeroRotation.blocked(s), HeroRotation.NONE, "idle on the dock: turns");
        s.heroFocus = true;
        eq(HeroRotation.blocked(s), HeroRotation.HERO_FOCUS, "focus on the hero buttons: paused");
        eq(r.delay(60_000, s), -1L, "paused on the buttons however long");
        s.heroFocus = false;
        r.input(60_000);
        eq(r.delay(60_000, s), 8000L, "leaving the buttons starts the whole dwell again");
        s = free(3);
        s.animations = false;
        eq(HeroRotation.blocked(s), HeroRotation.NO_MOTION, "Remove animations: never turns");
        s = free(3);
        s.touchExploration = true;
        eq(HeroRotation.blocked(s), HeroRotation.ACCESSIBILITY, "screen reader: never turns");
        s = free(1);
        eq(HeroRotation.blocked(s), HeroRotation.ONE_PAGE, "one page: nothing to turn");
        s = free(3);
        s.started = false;
        eq(HeroRotation.blocked(s), HeroRotation.HIDDEN, "Home hidden (another app, standby)");
        s = free(3);
        s.home = false;
        eq(HeroRotation.blocked(s), HeroRotation.NOT_HOME, "Apps page");
        s = free(3);
        s.panel = true;
        eq(HeroRotation.blocked(s), HeroRotation.PANEL, "a panel is open");
        s = free(3);
        s.followsRow = true;
        eq(HeroRotation.blocked(s), HeroRotation.FOLLOWS_ROW, "the hero follows the Continue row");
        for (int i = 0; i < 9; i++) {
            HeroRotation.State x = free(3);
            if (i == 1) x.started = false;
            if (i == 2) x.pages = 0;
            if (i == 3) x.home = false;
            if (i == 4) x.panel = true;
            if (i == 5) x.followsRow = true;
            if (i == 6) x.heroFocus = true;
            if (i == 7) x.animations = false;
            if (i == 8) x.touchExploration = true;
            eq(new HeroRotation(8000).delay(100_000, x) < 0, i != 0, "only the free state turns, case " + i);
        }
    }

    static void window() {
        eq(Arrays.toString(HeroWindow.decoded(0, 5, 3)), "[0, 1, 4]", "5 pages on 0: shown, next, previous");
        eq(Arrays.toString(HeroWindow.decoded(4, 5, 3)), "[4, 0, 3]", "wraps at the end");
        eq(Arrays.toString(HeroWindow.decoded(1, 3, 3)), "[1, 2, 0]", "3 pages: all decoded, rotation order");
        eq(Arrays.toString(HeroWindow.decoded(1, 2, 3)), "[1, 0]", "2 pages: both");
        eq(Arrays.toString(HeroWindow.decoded(0, 1, 3)), "[0]", "one page");
        eq(Arrays.toString(HeroWindow.decoded(0, 0, 3)), "[]", "no page");
        eq(Arrays.toString(HeroWindow.decoded(7, 5, 3)), "[2, 3, 1]", "an index past the end wraps");
        eq(Arrays.toString(HeroWindow.fetchOrder(1, 5)), "[2, 3, 4, 0, 1]", "fetch: next first, shown last");
        eq(Arrays.toString(HeroWindow.fetchOrder(0, 1)), "[0]", "fetch one page");
        for (int pages = 1; pages <= 7; pages++) {
            for (int cur = 0; cur < pages; cur++) {
                int[] w = HeroWindow.decoded(cur, pages, HeroWindow.SLOTS);
                check(w.length == Math.min(pages, HeroWindow.SLOTS), "window size " + pages + "/" + cur);
                check(w[0] == cur, "the shown page is decoded first " + pages + "/" + cur);
                if (pages > 1) check(HeroWindow.keep((cur + 1) % pages, cur, pages, HeroWindow.SLOTS),
                        "the next page (the rotation's turn) is always decoded " + pages + "/" + cur);
                if (pages > 2) check(HeroWindow.keep((cur + pages - 1) % pages, cur, pages, HeroWindow.SLOTS),
                        "the previous page (LEFT) is decoded " + pages + "/" + cur);
                check(Arrays.stream(w).distinct().count() == w.length, "no page twice " + pages + "/" + cur);
                int[] f = HeroWindow.fetchOrder(cur, pages);
                check(Arrays.stream(f).sorted().distinct().count() == pages, "every page is fetched " + pages + "/" + cur);
            }
        }
    }

    /**
     * The fade curve, cubic-bezier(0.4, 0, 0.2, 1) (lumen-ui Motion.STANDARD, read from the source): a gentle start
     * (no jump in the first frames), never back, a soft landing. The picture is a straight mix of the two pages at
     * this progress, so the mean brightness moves one way from the old page to the new one.
     */
    static void curve() throws Exception {
        float[] p = { 0.4f, 0f, 0.2f, 1f };
        String dir = System.getProperty("home.src");
        if (dir != null) {
            String m = read(dir + "/../../../../../core/lumen-ui/src/org/lumen/ui/Motion.java");
            Matcher x = Pattern.compile("STANDARD = new PathInterpolator\\(([0-9.f, ]+)\\)").matcher(m);
            check(x.find(), "Motion.STANDARD is a cubic bezier");
            if (x.find(0)) {
                String[] v = x.group(1).replace("f", "").split(",");
                for (int i = 0; i < 4; i++) p[i] = Float.parseFloat(v[i].trim());
            }
        }
        eq(Arrays.toString(p), "[0.4, 0.0, 0.2, 1.0]", "the standard curve");
        double prev = -1, first = bezier(p, 1.0 / 48), mid = bezier(p, 0.5);
        boolean mono = true;
        for (int i = 0; i <= 48; i++) {
            double e = bezier(p, i / 48.0);
            mono &= e >= prev - 1e-9;
            prev = e;
        }
        check(mono, "the fade never goes back");
        check(Math.abs(bezier(p, 0)) < 1e-6 && Math.abs(bezier(p, 1) - 1) < 1e-6, "from the old page to the new one");
        check(first < 0.02, "a gentle start: the first of 48 frames moves under 2 % (" + first + ")");
        check(1 - bezier(p, 47.0 / 48) < 0.01, "a soft landing: the last frame moves under 1 %");
        check(mid > 0.5 && mid < 0.85, "half way in time the new page leads (" + mid + ")");
        // mean brightness of the mix (a, b = the two pages' mean levels): linear in the progress, so monotonic
        for (double[] ab : new double[][] { { 40, 200 }, { 200, 40 }, { 120, 121 } }) {
            double last = ab[0];
            boolean one = true;
            for (int i = 1; i <= 48; i++) {
                double e = bezier(p, i / 48.0), lum = ab[0] * (1 - e) + ab[1] * e;
                one &= ab[1] >= ab[0] ? lum >= last - 1e-9 : lum <= last + 1e-9;
                last = lum;
            }
            check(one, "mean brightness goes one way " + ab[0] + " -> " + ab[1]);
        }
    }

    /** y of the cubic bezier (0,0) (p0,p1) (p2,p3) (1,1) at x (bisection, as PathInterpolator does it). */
    static double bezier(float[] p, double x) {
        double lo = 0, hi = 1, t = x;
        for (int i = 0; i < 60; i++) {
            t = (lo + hi) / 2;
            double bx = 3 * (1 - t) * (1 - t) * t * p[0] + 3 * (1 - t) * t * t * p[2] + t * t * t;
            if (bx < x) lo = t;
            else hi = t;
        }
        return 3 * (1 - t) * (1 - t) * t * p[1] + 3 * (1 - t) * t * t * p[3] + t * t * t;
    }

    static String read(String f) throws Exception {
        return new String(Files.readAllBytes(Paths.get(f)), StandardCharsets.UTF_8);
    }

    static String body(String src, String head) {
        int a = src.indexOf(head);
        return a < 0 ? "" : src.substring(a, src.indexOf("\n    }\n", a));
    }

    static void wiring() throws Exception {
        String dir = System.getProperty("home.src");
        if (dir == null) return;
        String act = read(dir + "/HomeActivity.java"), back = read(dir + "/ui/BackdropView.java");
        String img = read(dir + "/img/ImageLoader.java");
        check(!act.contains("postDelayed(mHeroTick, Tokens.MS_HERO_PAGE)"),
                "wiring: no fixed 10 s tick that turns a page right after a key");
        check(body(act, "public boolean dispatchKeyEvent(KeyEvent e)").contains("mRotation.input("),
                "wiring: every key press restarts the dwell");
        check(body(act, "private void heroTick()").contains("preload().ready("), "wiring: the turn waits for the decoded art");
        check(body(act, "private void loadHeroArt()").contains("preload().now(k)"),
                "wiring: a preloaded page shows its art in the same frame");
        check(body(act, "private void pageHero(int delta)").contains("mShelf.swapText("),
                "wiring: the shelf text crossfades on its own");
        check(body(act, "private void pageHero(int delta)").contains("preload().moved("),
                "wiring: the window follows the page");
        check(act.contains("preload().pages(mPages"), "wiring: new pages are preloaded");
        check(!body(act, "private void build()").contains("images()"),
                "wiring: building the views does not open the image cache on the main thread (startup)");
        check(body(act, "private void rotationState()").contains("Theme.animations()")
                && body(act, "private void rotationState()").contains("isTouchExplorationEnabled()"),
                "wiring: Remove animations and the screen reader stop the turning");
        check(body(act, "private void heroContent(boolean animate)").contains("if (!mShelf.swapping()) bindHeroPage();"),
                "wiring: a focus move does not bind the next text mid swap");
        String fade = body(back, "private void crossFade(Face out, Face in)");
        check(fade.contains("in.setAlpha(0f)") && fade.contains("in.animate().alpha(1f).setDuration(Tokens.MS_HERO_FADE)"),
                "wiring: the new page fades in over 800 ms");
        check(fade.contains(".setInterpolator(Motion.STANDARD)"), "wiring: the fade runs on the standard curve");
        check(fade.contains(".withEndAction(() -> out.setAlpha(0f))") && !fade.contains("out.animate()"),
                "wiring: the old page stays opaque under the new one until the end (no dip to the field)");
        check(fade.contains(".withLayer()"), "wiring: the new page fades as one layer (scrims and art together)");
        check(body(back, "public void set(int mode, Bitmap sharp").contains("crossFade(front, back);"),
                "wiring: every page change is the cross fade");
        for (String gone : new String[] { "ui/HeroShaders.java", "model/NoiseField.java", "model/HeroMotion.java" })
            check(!Files.exists(Paths.get(dir + "/" + gone)), "no shader code left: " + gone);
        for (String src : new String[] { act, back })
            check(!src.contains("RenderEffect") && !src.contains("RuntimeShader") && !src.contains("lumen_hero_motion")
                    && !src.contains("HeroMotion"), "no shader, style setting or frame judge in the activity and backdrop");
        check(body(act, "private void pageHero(int delta)").contains("mRotation.shown(SystemClock.uptimeMillis() + Tokens.MS_HERO_FADE)"),
                "wiring: the dwell starts when the fade ends");
        check(img.contains("else if (kind == KIND_HERO) putHero(key, result);"),
                "wiring: hero art goes to its own small LRU, cards keep theirs");
    }
}
