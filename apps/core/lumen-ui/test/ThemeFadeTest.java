// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui;

import org.lumen.testkit.Check;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Host checks of the live theme switch: the cross fade rules of {@link ThemeFollow} driven by a recording screen (the
 * observer's reports, the app's own apply, a hidden screen, removed animations, a change during a fade, a stop, a
 * failed snapshot), and the wiring: every Lumen screen that follows the theme does it through lumen-ui ThemeFade, the
 * observer watches the setting, no app keeps its own snapshot copy.
 */
public final class ThemeFadeTest {
    private static final Palette L = Palette.light(), D = Palette.dark();
    private static Check c;

    /** A window that records what it was asked to do. */
    static final class Rec implements ThemeFollow.Screen {
        boolean visible = true, animations = true, freezeOk = true, frozen;
        int lastToken, freezes;
        Palette drawn;
        final List<String> log = new ArrayList<>();

        Rec(Palette p) { drawn = p; }

        @Override public boolean visible() { return visible; }
        @Override public boolean animations() { return animations; }
        @Override public boolean freeze() {
            if (!freezeOk) { log.add("freeze-failed"); return false; }
            freezes++;
            frozen = true;
            log.add("freeze:" + name(drawn));
            return true;
        }
        @Override public void restyle(Palette p) { drawn = p; log.add("restyle:" + name(p) + (frozen ? ":under" : ":bare")); }
        @Override public void fade(int token) { lastToken = token; log.add("fade"); }
        @Override public void unfreeze() { frozen = false; log.add("unfreeze"); }
    }

    static String name(Palette p) { return p == D ? "dark" : p == L ? "light" : "?"; }

    public static void main(String[] a) throws Exception {
        c = new Check("lumen-ui ThemeFadeTest");
        visibleFades();
        echoIsNothing();
        hiddenIsInstant();
        reducedIsInstant();
        changeDuringFade();
        staleEndIgnored();
        stopEndsFade();
        snapshotFails();
        backAndForth();
        wiring(new File(a[0]));
        c.done();
    }

    static void visibleFades() {
        Rec r = new Rec(L);
        ThemeFollow f = new ThemeFollow(r, L);
        f.target(D);
        c.eq("visible: freeze first, then the new look under it, then the fade",
                "[freeze:light, restyle:dark:under, fade]", r.log.toString());
        c.ok("visible: fading", f.state() == ThemeFollow.State.FADING);
        c.ok("visible: the views are dark at once (under the picture)", r.drawn == D && f.shown() == D);
        f.faded(r.lastToken);
        c.ok("visible: fade end drops the picture", !r.frozen && f.state() == ThemeFollow.State.IDLE);
    }

    static void echoIsNothing() {
        Rec r = new Rec(L);
        ThemeFollow f = new ThemeFollow(r, L);
        f.target(D);            // the app that stored the choice
        f.target(D);            // the observer's echo of the same write
        f.target(null);
        c.eq("echo: one switch for the app's apply plus the observer", 1, r.freezes);
        c.eq("echo: one restyle", 1, count(r.log, "restyle"));
        Rec r2 = new Rec(L);
        new ThemeFollow(r2, L).target(L);
        c.ok("same palette: nothing at all", r2.log.isEmpty());
    }

    static void hiddenIsInstant() {
        Rec r = new Rec(L);
        r.visible = false;
        ThemeFollow f = new ThemeFollow(r, L);
        f.target(D);
        c.eq("hidden (stopped, not attached): restyle at once, no picture", "[restyle:dark:bare]", r.log.toString());
        c.ok("hidden: idle", f.state() == ThemeFollow.State.IDLE);
    }

    static void reducedIsInstant() {
        Rec r = new Rec(D);
        r.animations = false;
        ThemeFollow f = new ThemeFollow(r, D);
        f.target(L);
        c.eq("animations removed: instant", "[restyle:light:bare]", r.log.toString());
    }

    static void changeDuringFade() {
        Rec r = new Rec(L);
        ThemeFollow f = new ThemeFollow(r, L);
        f.target(D);
        int first = r.lastToken;
        f.target(L);
        c.eq("mid fade: the mixed picture on screen is frozen again, no unfreeze jump",
                "[freeze:light, restyle:dark:under, fade, freeze:dark, restyle:light:under, fade]", r.log.toString());
        c.ok("mid fade: a new token", r.lastToken != first);
        f.faded(first);
        c.ok("mid fade: the replaced fade's end changes nothing", r.frozen && f.state() == ThemeFollow.State.FADING);
        f.faded(r.lastToken);
        c.ok("mid fade: the last fade ends it", !r.frozen && f.shown() == L);
    }

    static void staleEndIgnored() {
        Rec r = new Rec(L);
        ThemeFollow f = new ThemeFollow(r, L);
        f.faded(0);
        f.faded(7);
        c.ok("no fade: an end report does nothing", r.log.isEmpty());
    }

    static void stopEndsFade() {
        Rec r = new Rec(L);
        ThemeFollow f = new ThemeFollow(r, L);
        f.target(D);
        int t = r.lastToken;
        f.stop();
        c.ok("stop: picture dropped at once", !r.frozen && f.state() == ThemeFollow.State.IDLE);
        int n = r.log.size();
        f.faded(t);
        f.stop();
        c.eq("stop: the cancelled animator's end report and a second stop do nothing", n, r.log.size());
        r.visible = false;
        f.target(L);
        c.ok("stopped then changed: instant", r.log.get(r.log.size() - 1).equals("restyle:light:bare"));
    }

    static void snapshotFails() {
        Rec r = new Rec(L);
        r.freezeOk = false;
        ThemeFollow f = new ThemeFollow(r, L);
        f.target(D);
        c.eq("snapshot failed: instant switch, never a half state", "[freeze-failed, restyle:dark:bare]", r.log.toString());
        Rec r2 = new Rec(L);
        ThemeFollow f2 = new ThemeFollow(r2, L);
        f2.target(D);
        r2.freezeOk = false;
        f2.target(L);
        c.ok("snapshot failed mid fade: the old picture goes, the new look is bare",
                !r2.frozen && f2.state() == ThemeFollow.State.IDLE && r2.drawn == L);
    }

    static void backAndForth() {
        Rec r = new Rec(L);
        ThemeFollow f = new ThemeFollow(r, L);
        for (int i = 0; i < 6; i++) f.target(i % 2 == 0 ? D : L);
        c.ok("six presses in a row: one picture at a time", r.frozen && r.freezes == 6 && count(r.log, "unfreeze") == 0);
        f.faded(r.lastToken);
        c.ok("six presses: ends light and idle", f.shown() == L && !r.frozen);
    }

    static int count(List<String> log, String prefix) {
        int n = 0;
        for (String s : log) if (s.startsWith(prefix)) n++;
        return n;
    }

    // ------------------------------------------------------------------ wiring (static, the Android side)

    static void wiring(File gsi) throws Exception {
        String ui = "apps/core/lumen-ui/src/org/lumen/ui/";
        String fade = Check.read(new File(gsi, ui + "ThemeFade.java"));
        String watch = Check.read(new File(gsi, ui + "ThemeWatcher.java"));
        c.ok("observer: Settings.Secure lumen_ui_theme through a ContentObserver",
                watch.contains("registerContentObserver(Settings.Secure.getUriFor(LumenTheme.KEY_MODE)"));
        c.ok("observer: the place too (Авто)", watch.contains("getUriFor(LumenTheme.KEY_GEO)"));
        c.ok("fade: ThemeFade feeds ThemeFollow from the watcher", fade.contains("new ThemeWatcher(")
                && fade.contains("new ThemeFollow(") && Check.countCode(fade, "mFollow.target(") >= 3);
        c.ok("fade: the snapshot takes hardware bitmaps (Picture, HARDWARE), never a software Canvas",
                fade.contains("new Picture()") && fade.contains("Bitmap.Config.HARDWARE")
                        && !fade.contains("new Canvas("));
        c.ok("fade: drawn in the root's overlay (no view added, focus stays)", fade.contains("getOverlay().add("));
        c.ok("fade: the snapshot is the GPU drawing of the views' own display lists (shadows, art, layers)",
                fade.contains("new HardwareRenderer()") && fade.contains("mRoot.draw(rc)")
                        && fade.indexOf("snapshot(w, h)") < fade.indexOf("new Picture()"));
        c.ok("fade: an overlay window's own background fades in under the picture, never drawn twice",
                fade.contains("bg.setAlpha(255 - al)") && fade.contains("getBackground().setAlpha(255)"));
        c.ok("fade: theme time and the standard curve", fade.contains("Tokens.MS_THEME")
                && fade.contains("Motion.STANDARD"));
        c.ok("fade: removed animations make it instant", fade.contains("Motion.reduced("));
        String tokens = Check.read(new File(gsi, ui + "Tokens.java"));
        c.ok("tokens: MS_THEME is 350 ms", tokens.contains("MS_THEME = 350"));

        String[][] users = {
                {"apps/LumenHome/src/org/lumen/home/HomeActivity.java", "ThemeFade.activity(this, this::restyle)"},
                {"apps/LumenSetup/src/org/lumen/setup/SetupActivity.java", "ThemeFade.activity(this, this::restyle)"},
                {"apps/core/lumen-menu/src/org/lumen/projector/panel/QpdPanel.java", "ThemeFade.follow(root, host::onTheme)"},
                {"apps/core/lumen-signal/src/org/lumen/signal/SignalWindow.java", "ThemeFade.follow(r,"},
                {"apps/core/lumen-source/src/org/lumen/source/SourceOsd.java", "ThemeFade.follow(root,"},
        };
        for (String[] u : users) {
            String src = Check.read(new File(gsi, u[0]));
            c.ok("follows live through ThemeFade: " + u[0], src.contains(u[1]));
            c.ok("no own snapshot or watcher copy: " + u[0], !src.contains("new ThemeWatcher(")
                    && !src.contains(".draw(new Canvas(") && !src.contains("Bitmap.createBitmap("));
        }
        String home = Check.read(new File(gsi, users[0][0]));
        c.ok("Home: start/stop with the activity", home.contains("mTheme.start();") && home.contains("mTheme.stop();"));
        c.ok("Home: its own theme menu switches through the same fade", home.contains("mTheme.apply(LumenTheme.palette(this))"));
        String setup = Check.read(new File(gsi, users[1][0]));
        c.ok("Setup: start/stop with the activity", setup.contains("mTheme.start();") && setup.contains("mTheme.stop();"));
        c.ok("Setup: rebuild keeps the focus place", setup.contains("ThemeFade.focusPath(mRoot)")
                && setup.contains("ThemeFade.refocus(mRoot, focus)"));
        String menu = Check.read(new File(gsi, "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java"));
        c.ok("quick menu: the theme button switches through the window's fade",
                menu.contains("panel.fade().apply(LumenTheme.palette(app))"));
        c.ok("quick menu: live changes re-style card and popup", menu.contains("public void onTheme(Palette p)"));
    }
}
