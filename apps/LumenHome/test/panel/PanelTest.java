// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Host tests of Customize Home's side panel (owner report 2026-11-23: a new look closed the panel, it opened again by
 * itself with the check mark on the old look, the switches did not move): whose key press it is (KeyPress), where the
 * focus goes when a page is filled again (PanelFocus), and the wiring greps that keep the fix in place. The key walk on
 * an emulator is test/ui.
 */
public final class PanelTest {
    static int checks, fails;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static String read(String rel) throws Exception {
        return new String(Files.readAllBytes(Paths.get(System.getProperty("home.dir"), rel)), "UTF-8");
    }

    /** The text of method {@code name} (from its signature to the next method at the same indent). */
    static String method(String src, String signature) {
        int a = src.indexOf(signature);
        if (a < 0) return "";
        int b = src.indexOf("\n    }\n", a);
        return b < 0 ? src.substring(a) : src.substring(a, b);
    }

    public static void main(String[] a) throws Exception {
        keyPress();
        panelFocus();
        wiring();
        System.out.println((fails == 0 ? "OK" : "FAILED") + " panel: " + checks + " checks, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }

    static void keyPress() {
        final int ok = 23, back = 4;
        KeyPress p = new KeyPress();
        p.down(ok, 100, KeyPress.PANEL);                     // a confirm row (Restore, Reset) takes OK and closes the panel
        check(!p.upActs(ok, 100, KeyPress.HOME), "a press the panel took and closed it on: its release is no one's");
        p.down(ok, 200, KeyPress.HOME);
        check(p.upActs(ok, 200, KeyPress.HOME), "Home's own press: Home acts on the release");
        check(!p.upActs(ok, 200, KeyPress.HOME), "the press is over after its release");
        check(!p.upActs(ok, 300, KeyPress.HOME), "a release without its press (another window, a Home built anew) is no one's");
        check(!p.upActs(ok, 300, KeyPress.PANEL), "a release without its press does not reach an open panel either");
        p.down(ok, 400, KeyPress.SOURCE);
        check(!p.upActs(ok, 401, KeyPress.SOURCE), "the release of another press (other down time) is no one's");
        p.down(ok, 500, KeyPress.HOME);                      // a held OK on a tile opens its menu over Home
        check(!p.upActs(ok, 500, KeyPress.PANEL), "Home's press that opened the panel: the panel takes nothing from its release");
        p.down(ok, 600, KeyPress.SOURCE);
        check(p.upActs(ok, 600, KeyPress.SOURCE), "the source list's own press: it picks on the release");
        p.down(ok, 700, KeyPress.HOME);
        check(p.held(ok, 700) && !p.held(ok, 650) && !p.held(back, 700), "repeats count only for the press this window saw");
        p.down(back, 800, KeyPress.HOME);
        check(p.upActs(ok, 700, KeyPress.HOME) && p.upActs(back, 800, KeyPress.HOME), "two keys at once keep their own presses");
        KeyPress fresh = new KeyPress();                     // Home built anew while OK was down
        check(!fresh.held(ok, 900) && !fresh.upActs(ok, 900, KeyPress.HOME), "a new Home owns no press of the old one");
    }

    static void panelFocus() {
        List<Object> before = Arrays.asList(null, "look:classic", "look:compact", "clock", "cont");
        List<Object> custom = Arrays.asList(null, "look:classic", "look:compact", null, "clock", "cont");
        check(PanelFocus.keep(before, custom, 3) == 4, "a row inserted above (Custom look): the focus stays on Big clock");
        check(PanelFocus.keep(custom, before, 4) == 3, "the row above goes away: the focus stays on Big clock");
        check(PanelFocus.keep(before, before, 2) == 2, "same page: same row");
        check(PanelFocus.keep(before, Arrays.asList(null, "a"), 4) == 1, "the tag is gone: the nearest place");
        check(PanelFocus.keep(before, custom, -1) == -1, "the text field keeps the focus");
        check(PanelFocus.sameRows(before, Arrays.asList(5, 2, 2, 1, 1), before, Arrays.asList(5, 2, 2, 1, 1)), "same rows");
        check(!PanelFocus.sameRows(before, Arrays.asList(5, 2, 2, 1, 1), custom, Arrays.asList(5, 2, 2, 3, 1, 1)), "a row more");
    }

    static void wiring() throws Exception {
        String act = read("src/org/lumen/home/HomeActivity.java");
        String list = read("src/org/lumen/home/ui/ListPanel.java");
        String dispatch = method(act, "public boolean dispatchKeyEvent(KeyEvent e)");
        check(dispatch.contains("mPress.down(code, e.getDownTime(), layer())")
                && dispatch.contains("else if (down && !mPress.held(code, e.getDownTime())) return true;")
                && dispatch.contains("!mPress.upActs(code, e.getDownTime(), layer())")
                && dispatch.indexOf("mPress.down(") < dispatch.indexOf("if (mPanel.isOpen())"),
                "a release acts only on the layer that saw its press (before the panel, the card or the list take keys)");
        String rel = method(act, "private static boolean actsOnRelease(int k)");
        check(rel.contains("isOk(k)") && rel.contains("KEYCODE_BACK") && rel.contains("KEYCODE_MENU") && rel.contains("KEYCODE_TV_INPUT"),
                "OK, BACK, MENU and the input key need their own press");
        String layer = method(act, "private int layer()");
        check(layer.indexOf("mPanel.isOpen()") < layer.indexOf("mDetails.isOpen()") && layer.indexOf("mDetails.isOpen()") < layer.indexOf("mSource.isOpen()"),
                "the layers in the order keys reach them");
        check(method(act, "protected void onStop()").contains("endOkPress();"), "a stopped Home keeps no held OK (no tile menu later)");
        String items = method(act, "private ArrayList<ListPanel.Item> customizeItems()");
        check(!items.isEmpty() && !items.contains("mPanel.close()"), "no row of Customize Home closes the panel");
        check(items.contains("change(l -> l.apply(k), true)") && items.contains("change(HomeLayout::cycleRows, true)"),
                "a look and the rows go through change()");
        check(items.contains("refreshCustomize();"), "the theme rows show the stored theme at once");
        String change = method(act, "private void change(Change c, boolean layout)");
        check(change.contains("mApp.layout().put(l)") && change.contains("refreshCustomize();"),
                "a change is stored at once and the panel shows it");
        check(!act.contains("x.on = !x.on") && !list.contains("it.on = !it.on"), "a switch shows the stored value, nobody flips it");
        check(act.contains("mPanel.update(customizeItems())"), "the panel is filled again from what is stored");
        check(list.contains("PanelFocus.keep("), "the focus keeps to its row by tag");
        String obs = method(act, "private void observeLayout()");
        check(obs.contains("if (mApp.layout().current() == mLayoutShown) return;"), "Home's own writes do not build the screen again");
        check(!act.contains("fadeLayout(") && act.contains("if (mRelayout) {"), "one fade and one build per new layout");
        check(method(act, "private void store(HomeLayout l)").indexOf("bind(") < 0, "a stored dock builds the screen once, from fresh data");
        check(method(act, "private void restyle(Palette p)").contains("mPanel.restyle();"), "the panel takes a new theme");
        check(!method(act, "private void freeMemory()").contains("mPanel.close()"), "Free up memory keeps the panel");
        check(!method(act, "private void saveBackup(BackupPlaces.Place pl)").contains("mPanel.close()")
                && method(act, "private void saveBackup(BackupPlaces.Place pl)").contains("backupPage(true)"),
                "saving keeps the backup page and offers the new file");
        check(act.contains("public void dump(String prefix"), "dumpsys shows Home and the panel (test/ui reads it)");
    }
}
