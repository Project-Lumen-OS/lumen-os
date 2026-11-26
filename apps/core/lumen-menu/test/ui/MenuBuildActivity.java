// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.walk;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.projector.panel.MenuController;
import org.lumen.projector.ui.ActivityPanelHost;
import org.lumen.projector.ui.HeaderRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.PagedPanel;
import org.lumen.projector.ui.Panel;
import org.lumen.projector.ui.TextRow;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Test harness of test/ui/run_build.sh, built only into a test APK by test/ui/build.sh (WALK=MenuBuildActivity) from a
 * model's real assembled app, never into an image. It builds the real MenuController of the model's profile the way
 * the apps do: module rows of every section registered first (Extension, CONTRACT R4: a group title of its own and a
 * row, as the screensaver rows of General), one more module after the build, then the card, every tile's popup
 * (the path of a tile's OK and RIGHT) and every section through the public show. Any exception is a FAIL line;
 * {@code dumpsys activity <pkg>/org.lumen.menu.walk.MenuBuildActivity} prints the result.
 */
public class MenuBuildActivity extends Activity {
    private static final String TAG = "MenuBuild";
    private final List<String> fails = new ArrayList<>();
    private final List<String> done = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private MenuController menu;
    private volatile boolean finished;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        View v = new View(this);
        v.setBackgroundColor(0xFF808080);
        setContentView(v);
        Context app = getApplicationContext();
        MenuSpec spec = MenuSpec.of(MenuController.profile(app));
        for (String sec : spec.sections.keySet()) MenuController.addExtension(sec, new ModuleRows("before " + sec), null);
        try {
            ActivityPanelHost host = ActivityPanelHost.install(app,
                    (c, t) -> android.widget.Toast.makeText(c, t, android.widget.Toast.LENGTH_SHORT).show());
            menu = new MenuController(app, standIn(), null, host);
            done.add("build");
        } catch (Throwable t) {
            fail("build", t);
            finished = true;
            return;
        }
        step("no tile popup of an All settings only section", () -> {
            java.lang.reflect.Field f = MenuController.class.getDeclaredField("popups");
            f.setAccessible(true);
            for (Object key : ((java.util.Map<?, ?>) f.get(menu)).keySet()) {
                if (MenuSpec.allSettingsOnly(String.valueOf(key))) throw new IllegalStateException("a popup of " + key);
            }
        });
        for (String sec : spec.sections.keySet()) {
            step("module added after the build " + sec,
                    () -> MenuController.addExtension(sec, new ModuleRows("after " + sec), menu));
        }
        List<Runnable> steps = new ArrayList<>();
        steps.add(() -> step("card", () -> menu.show(null)));
        for (String id : tileIds(spec)) {
            steps.add(() -> step("tile popup " + id, () -> {
                if (!menu.isShowing()) menu.show(null);
                openPopup(id);
                checkShown(id);
            }));
        }
        for (String sec : spec.sections.keySet()) {
            steps.add(() -> step("show " + sec, () -> {
                menu.show(sec);
                checkShown(sec);
            }));
        }
        // owner option B 2026-11-26: rows other modules add to General (the screensaver of the Z9X and the HB) stand
        // right after Display and menu (before Remote and voice) and above Help, before and after the build
        if (spec.sections.containsKey(MenuSpec.GENERAL)) steps.add(() -> step("general: module rows above Help", () -> {
            menu.show(MenuSpec.GENERAL);
            Page p = ((PagedPanel) menu.host().current()).currentPage();
            String help = getString(org.lumen.projector.R.string.lumen_m_hdr_help);
            String next = getString(org.lumen.projector.R.string.lumen_m_hdr_remote_voice);
            int h = -1, module = -1, remote = -1;
            List<View> rows = p.rows();
            for (int i = 0; i < rows.size(); i++) {
                View r = rows.get(i);
                String t = r instanceof org.lumen.projector.ui.Row ? String.valueOf(((org.lumen.projector.ui.Row) r).getTitle())
                        : r instanceof HeaderRow ? headerText((HeaderRow) r) : "";
                if (r instanceof HeaderRow && help.equals(t)) h = i;
                if (r instanceof HeaderRow && next.equals(t)) remote = i;
                if (t.contains("module before general") || t.contains("module after general")) module = i;
            }
            if (h < 0 || module < 0 || module > h || remote >= 0 && module > remote) {
                throw new IllegalStateException("General: Help at " + h + ", Remote and voice at " + remote + ", a module row at "
                        + module + " of " + rows.size());
            }
        }));
        // the HDMI page (HdmiPage) as the HDMI app's old settings address asks for it: All settings with the page on
        // top, in the menu's own rows (two switches, the power-on choice, the ports header)
        steps.add(() -> step("hdmi page", () -> {
            menu.show(FeatureId.SETTINGS_HDMI.id);
            java.lang.reflect.Field pf = MenuController.class.getDeclaredField("panel");
            pf.setAccessible(true);
            Object panel = pf.get(menu);
            Object page = panel.getClass().getMethod("currentPage").invoke(panel);
            String title = String.valueOf(page.getClass().getMethod("getTitle").invoke(page));
            if (!title.equals(getString(org.lumen.projector.R.string.lumen_m_hdmi_options))) {
                throw new IllegalStateException("HDMI page not shown, on top: " + title);
            }
            List<?> rows = (List<?>) page.getClass().getMethod("rows").invoke(page);
            if (!(rows.get(0) instanceof org.lumen.projector.ui.ToggleRow) || !(rows.get(1) instanceof org.lumen.projector.ui.ToggleRow)
                    || !(rows.get(2) instanceof org.lumen.projector.ui.ChoiceRow)) {
                throw new IllegalStateException("HDMI page rows are not the menu's rows: " + rows);
            }
        }));
        // Back to the XGIMI system as Settings > About asks for it (lumen-about XgimiSystemActivity): All settings with
        // the erase warning on top (Continue, Cancel), Continue then shows the page of the menu with its QR code row
        steps.add(() -> step("xgimi system page", () -> {
            menu.show(org.lumen.menu.MenuRequest.XGIMI_SYSTEM);
            java.lang.reflect.Field pf = MenuController.class.getDeclaredField("panel");
            pf.setAccessible(true);
            Object panel = pf.get(menu);
            Object page = panel.getClass().getMethod("currentPage").invoke(panel);
            String title = String.valueOf(page.getClass().getMethod("getTitle").invoke(page));
            if (!title.equals(getString(org.lumen.projector.R.string.lumen_about_back))) {
                throw new IllegalStateException("XGIMI system warning not shown, on top: " + title);
            }
            java.util.List<?> rows = (java.util.List<?>) page.getClass().getMethod("rows").invoke(page);
            java.lang.reflect.Method act = org.lumen.projector.ui.Row.class.getDeclaredMethod("onActivate");
            act.setAccessible(true);
            act.invoke(rows.get(1));                                      // Continue
            Object shown = panel.getClass().getMethod("currentPage").invoke(panel);
            java.util.List<?> facts = (java.util.List<?>) shown.getClass().getMethod("rows").invoke(shown);
            boolean qr = false;
            for (Object r : facts) qr |= r.getClass().getSimpleName().equals("QrRow");
            if (shown == page || !qr) throw new IllegalStateException("XGIMI system page not shown: " + facts);
        }));

        steps.add(() -> step("close", () -> menu.dismiss()));
        run(steps, 0);
    }

    /** Every tile of the card (round buttons, tile rows, sliders) and every section popup, once each. */
    private static Set<String> tileIds(MenuSpec spec) {
        Set<String> out = new LinkedHashSet<>();
        for (MenuSpec.Tile t : spec.round) out.add(t.id);
        for (List<MenuSpec.Tile> row : spec.rows) {
            for (MenuSpec.Tile t : row) {
                out.add(t.id);
                if (t.orElse != null) out.add(t.orElse);
            }
        }
        for (MenuSpec.Tile t : spec.sliders) out.add(t.id);
        out.addAll(spec.popups.keySet());
        return out;
    }

    private void run(List<Runnable> steps, int i) {
        if (i >= steps.size()) {
            finished = true;
            Log.i(TAG, "finished: " + done.size() + " steps, " + fails.size() + " failed");
            return;
        }
        main.postDelayed(() -> {
            steps.get(i).run();
            run(steps, i + 1);
        }, i == 0 ? 700 : 150);
    }

    /** The private popup path of a tile (MenuController.openPopup: a tile's OK, RIGHT on the last tile of a row). */
    private void openPopup(String id) throws Exception {
        Method m = MenuController.class.getDeclaredMethod("openPopup", String.class);
        m.setAccessible(true);
        try {
            m.invoke(menu, id);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception) throw (Exception) c;
            throw new RuntimeException(c);
        }
    }

    /** A pushed page (a popup over the card) must have rows: an empty popup is a menu that opened nothing. */
    private void checkShown(String what) {
        Panel cur = menu.host().current();
        if (!(cur instanceof PagedPanel) || ((PagedPanel) cur).depth() < 2) return;   // the card itself, or no popup
        Page p = ((PagedPanel) cur).currentPage();
        if (p != null && p.rows().isEmpty()) throw new IllegalStateException(what + ": an empty page is shown");
    }

    private interface Step { void run() throws Exception; }

    private void step(String what, Step s) {
        try {
            s.run();
            done.add(what);
        } catch (Throwable t) {
            fail(what, t);
        }
    }

    private void fail(String what, Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        String[] lines = w.toString().split("\n");
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(4, lines.length); i++) top.append(i == 0 ? "" : " | ").append(lines[i].trim());
        fails.add(what + ": " + top);
        Log.e(TAG, what, t);
    }

    /** A module's rows (Extension): its own group title, one row, as the screensaver rows of General. */
    private static String headerText(HeaderRow h) throws ReflectiveOperationException {
        java.lang.reflect.Field f = HeaderRow.class.getDeclaredField("text");
        f.setAccessible(true);
        return String.valueOf(f.get(h));
    }

    private static final class ModuleRows implements MenuController.Extension {
        private final String name;

        ModuleRows(String name) { this.name = name; }

        @Override public void addRows(Context app, Page page) {
            page.add(new HeaderRow(app, "module " + name));
            page.add(new TextRow(app, "row of module " + name));
        }
    }

    /** The adapter stand-in of the menu walks: every row, nothing answered (no vendor HAL is involved). */
    @SuppressWarnings("unchecked")
    private ProjectorFeatures standIn() {
        return (ProjectorFeatures) Proxy.newProxyInstance(getClassLoader(),
                new Class<?>[]{ProjectorFeatures.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "implemented": return EnumSet.allOf(FeatureId.class);
                        case "ready": case "available": case "setupDone": return true;
                        case "read": ((Consumer<MenuState>) a[0]).accept(MenuState.builder().build()); return null;
                        case "set": case "setConfirmed":
                            ((Consumer<ProjectorFeatures.Result>) a[2]).accept(ProjectorFeatures.Result.failed(null)); return null;
                        case "run": case "runConfirmed":
                            ((Consumer<ProjectorFeatures.Result>) a[1]).accept(ProjectorFeatures.Result.failed(null)); return null;
                        case "readFormat": ((Consumer<Object>) a[0]).accept(null); return null;
                        case "open": return false;
                        case "hashCode": return 1;
                        case "equals": return p == a[0];
                        case "toString": return "build adapter";
                        default: return null;
                    }
                });
    }

    @Override public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args) {
        w.println("build.finished " + finished);
        for (String d : new ArrayList<>(done)) w.println("build.ok " + d);
        for (String f : new ArrayList<>(fails)) w.println("build.FAIL " + f);
        w.println("build.result " + (!finished ? "RUNNING" : fails.isEmpty() ? "OK" : "FAIL") + " steps=" + done.size()
                + " failed=" + fails.size());
        w.flush();
    }
}
