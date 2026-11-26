// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.walk;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.projector.panel.MenuController;
import org.lumen.projector.ui.ActivityPanelHost;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.PagedPanel;
import org.lumen.projector.ui.Panel;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * Test harness of the shared menu key walks (test/ui/general_walk.py), built only into a test APK by test/ui/build.sh
 * from a model's real assembled app (its merged profile, strings, modules), never into the image. The menu is the real
 * MenuController; the device adapter is a stand-in that has every row and answers nothing (the walk checks rows the
 * menu owns itself, module rows, so no vendor HAL is involved). {@code dumpsys activity <pkg>/org.lumen.menu.walk.MenuWalkActivity}
 * prints the panel, its page, the focused view and the page's rows.
 */
public class MenuWalkActivity extends Activity {
    private MenuController menu;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        View v = new View(this);
        v.setBackgroundColor(0xFF808080);
        setContentView(v);
        ProjectorFeatures dev = (ProjectorFeatures) Proxy.newProxyInstance(getClassLoader(),
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
                        case "toString": return "walk adapter";
                        default: return null;
                    }
                });
        ActivityPanelHost host = ActivityPanelHost.install(getApplicationContext(),
                (c, t) -> android.widget.Toast.makeText(c, t, android.widget.Toast.LENGTH_SHORT).show());
        menu = new MenuController(getApplicationContext(), dev,
                getIntent().getBooleanExtra("real_pages", false) ? realPages(dev) : null, host);
        String section = getIntent().getStringExtra("section");    // "general": All settings
        live = this;
        new Handler(Looper.getMainLooper()).postDelayed(() -> menu.show(section), 700);
    }

    /** The walk activity up now: the menu requests of {@link Request} go to its menu. */
    static MenuWalkActivity live;

    /**
     * The projector app's menu request (SHOW_PANEL with a section) as the real app's receiver answers it: an old
     * screen's address that only redirects (lumen-about XgimiSystemActivity) is walked through it.
     */
    public static final class Request extends android.content.BroadcastReceiver {
        @Override public void onReceive(android.content.Context c, android.content.Intent i) {
            String section = i.getStringExtra("section");
            MenuWalkActivity a = live;
            if (a != null) new Handler(Looper.getMainLooper()).post(() -> a.menu.show(section));
        }
    }

    @Override public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args) {
        Runnable r = () -> {
            Panel cur = menu == null ? null : menu.host().current();
            int depth = cur instanceof PagedPanel ? ((PagedPanel) cur).depth() : 0;
            Page page = cur instanceof PagedPanel ? ((PagedPanel) cur).currentPage() : null;
            w.println("walk.menu showing=" + (menu != null && menu.isShowing()) + " panel="
                    + (cur == null ? null : cur.getClass().getSimpleName()) + " depth=" + depth
                    + " page=" + (page == null ? null : field(page, "title")));
            View root = (View) field(cur, "root");
            View focus = root == null ? null : root.findFocus();
            w.println("walk.focus " + describe(focus));
            if (page != null) {
                List<View> rows = page.rows();
                for (int i = 0; i < rows.size(); i++) w.println("walk.row i=" + i + " " + describe(rows.get(i)));
            }
        };
        source(w);
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else {
            java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
            new Handler(Looper.getMainLooper()).post(() -> { try { r.run(); } finally { l.countDown(); } });
            try { l.await(3, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        }
        w.flush();
    }

    /**
     * The shared HDMI-CEC page (apps/core/lumen-cec CecPage) with a stand-in CEC driver that shows every row, in an app
     * that carries the module; null in one without it. Its HDMI page row opens the menu's HDMI page as on a unit.
     */
    private MenuController.Pages realPages(ProjectorFeatures dev) {
        try {
            Class<?> pageK = Class.forName("org.lumen.projector.cec.CecPage");
            Class<?> hostK = Class.forName("org.lumen.projector.cec.CecPage$Host");
            Class<?> driverK = null, rowK = null;
            for (java.lang.reflect.Method m : pageK.getDeclaredMethods()) {
                if (m.getName().equals("build")) driverK = m.getParameterTypes()[2];
            }
            rowK = Class.forName("org.lumen.cec.CecRow");
            final Object all = EnumSet.allOf((Class) rowK);
            Object driver = Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{driverK}, (p, m, a) -> {
                if (m.getName().equals("rows")) return all;
                Class<?> r = m.getReturnType();
                if (r == boolean.class) return false;
                if (r == int.class) return 0;
                if (r == long.class) return 0L;
                return null;
            });
            Object host = Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{hostK}, (p, m, a) -> {
                switch (m.getName()) {
                    case "openHdmiOptions": menu.showHdmiPage(); return null;
                    case "setupDone": return true;
                    case "closeThen": ((Runnable) a[0]).run(); return null;
                    case "refreshPage":
                        java.lang.reflect.Method rp = MenuController.class.getDeclaredMethod("refreshPage", Page.class);
                        rp.setAccessible(true);
                        rp.invoke(menu, a[0]);
                        return null;
                    default: return null;
                }
            });
            java.lang.reflect.Method build = pageK.getMethod("build", android.content.Context.class, hostK, driverK);
            Page[] cec = new Page[1];
            return (id, m) -> {
                if (id != FeatureId.SETTINGS_CEC) return null;
                try {
                    if (cec[0] == null) cec[0] = (Page) build.invoke(null, getApplicationContext(), host, driver);
                } catch (Throwable t) {
                    android.util.Log.w("MenuWalk", "cec page: " + t);
                }
                return cec[0];
            };
        } catch (Throwable t) {
            android.util.Log.w("MenuWalk", "no cec page: " + t);
            return null;
        }
    }

    /** What the Source key would step through (lumen-source SourceInputs: connected ports, CEC names), worker read. */
    private void source(PrintWriter w) {
        try {
            Class<?> k = Class.forName("org.lumen.source.SourceInputs");
            java.lang.reflect.Method read = k.getDeclaredMethod("read", android.content.Context.class);
            read.setAccessible(true);
            Object in = read.invoke(null, getApplicationContext());
            StringBuilder names = new StringBuilder();
            java.lang.reflect.Method dev = k.getDeclaredMethod("device", int.class);
            dev.setAccessible(true);
            for (int p = 1; p <= 4; p++) {
                Object n = in == null ? null : dev.invoke(in, p);
                if (n != null) names.append(p).append(':').append(n).append(';');
            }
            w.println("walk.source connected=" + new java.util.TreeSet<>((java.util.Set<?>) field(in, "connected"))
                    + " devices=" + names);
        } catch (Throwable t) {
            w.println("walk.source none " + t);
        }
    }

    private static String describe(View v) {
        if (v == null) return "none";
        StringBuilder s = new StringBuilder("class=" + v.getClass().getSimpleName() + " focused=" + v.isFocused());
        for (String n : new String[]{"key", "title", "value", "text", "checked", "index"}) {
            Object o = field(v, n);
            if (o != null) s.append(' ').append(n).append('=').append(String.valueOf(o).replace('\n', ' '));
        }
        CharSequence cd = v.getContentDescription();
        if (cd != null) s.append(" desc=").append(String.valueOf(cd).replace('\n', ' '));
        return s.toString();
    }

    private static Object field(Object o, String name) {
        if (o == null) return null;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
                // the next class up
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
