// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.walk;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;

import org.lumen.menu.FeatureId;
import org.lumen.menu.FeatureKind;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.projector.panel.MenuController;
import org.lumen.projector.ui.ActivityPanelHost;
import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.PageAccess;
import org.lumen.projector.ui.Panel;
import org.lumen.projector.ui.Row;
import org.lumen.projector.ui.SliderRow;
import org.lumen.projector.ui.ToggleRow;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Test harness of the quick menu key walk (test/ui/quick_walk.py), built only into a test APK by test/ui/build.sh
 * (WALK=QuickWalkActivity) from a model's real assembled app, never into the image. The menu is the real
 * MenuController over a stand-in adapter that keeps the values set (as a projector would) and starts from the first
 * option of every choice, switches off, sliders in the middle; Settings.Global lumen_walk_hide lists features the
 * adapter hides now (a signal rule), lumen_walk_watch the features whose values and rows the dump prints.
 * {@code dumpsys activity <pkg>/org.lumen.menu.walk.QuickWalkActivity} prints the panel, the focus, the card, the
 * editor's list, the strip and the watched values.
 */
public class QuickWalkActivity extends Activity {
    private static final Map<String, String> VALUES = new TreeMap<>();
    private static final List<String> SETS = new ArrayList<>();
    private MenuController menu;
    private MenuProfile profile;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        View v = new View(this);
        v.setBackgroundColor(0xFF505860);
        setContentView(v);
        profile = MenuProfile.parse(asset(MenuController.PROFILE_ASSET));
        ProjectorFeatures dev = (ProjectorFeatures) Proxy.newProxyInstance(getClassLoader(),
                new Class<?>[]{ProjectorFeatures.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "implemented": return EnumSet.allOf(FeatureId.class);
                        case "ready": case "available": case "setupDone": return true;
                        case "read": ((Consumer<MenuState>) a[0]).accept(state()); return null;
                        case "set": case "setConfirmed":
                            VALUES.put(((FeatureId) a[0]).id, (String) a[1]);
                            SETS.add(((FeatureId) a[0]).id + "=" + a[1]);
                            ((Consumer<ProjectorFeatures.Result>) a[2]).accept(ProjectorFeatures.Result.done(state())); return null;
                        case "run": case "runConfirmed":
                            ((Consumer<ProjectorFeatures.Result>) a[1]).accept(ProjectorFeatures.Result.done(state())); return null;
                        case "readFormat": ((Consumer<Object>) a[0]).accept(null); return null;
                        case "open": return false;
                        case "hashCode": return 1;
                        case "equals": return p == a[0];
                        case "toString": return "quick walk adapter";
                        default: return null;
                    }
                });
        ActivityPanelHost host = ActivityPanelHost.install(getApplicationContext(),
                (c, t) -> android.widget.Toast.makeText(c, t, android.widget.Toast.LENGTH_SHORT).show());
        menu = new MenuController(getApplicationContext(), dev, null, host);
        new Handler(Looper.getMainLooper()).postDelayed(() -> menu.show(null), 700);
    }

    @Override protected void onNewIntent(android.content.Intent i) {
        super.onNewIntent(i);
        if (menu != null && !menu.isShowing()) menu.show(null);
    }

    private String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Set<String> global(String key) {
        String v = Settings.Global.getString(getContentResolver(), key);
        return v == null || v.trim().isEmpty() ? new HashSet<>() : new HashSet<>(Arrays.asList(v.trim().split(",")));
    }

    /** What the stand-in projector reads now: the values set so far, the rest at its start value. */
    private MenuState state() {
        MenuState.Builder s = MenuState.builder();
        Set<String> hide = global("lumen_walk_hide");
        for (FeatureId f : FeatureId.values()) {
            if (f.parent != null || !profile.has(f)) continue;
            if (f == FeatureId.PICTURE_PQ) {
                for (String item : profile.items(f)) number(s, FeatureId.pq(item), 50);
                continue;
            }
            if (f.kind == FeatureKind.SLIDER) {
                number(s, f, (profile.number(f, "min", 0) + profile.number(f, "max", 100)) / 2);
            } else if (f.kind == FeatureKind.CHOICE) {
                List<String> o = profile.options(f);
                String v = VALUES.get(f.id);
                s.put(f, v != null ? v : o.isEmpty() ? null : o.get(0));
            } else if (f.kind == FeatureKind.TOGGLE) {
                String v = VALUES.get(f.id);
                s.put(f, v != null ? v : "off");
            }
            if (hide.contains(f.id)) s.hide(f);
        }
        for (FeatureId f : FeatureId.values()) {             // the rows of the pages (white balance, dynamic black page)
            if (f.parent != null || profile.has(f) || f.kind != FeatureKind.TOGGLE && f.kind != FeatureKind.SLIDER) continue;
            if (f.kind == FeatureKind.TOGGLE) s.put(f, VALUES.getOrDefault(f.id, "off"));
            else number(s, f, 0);
        }
        return s.build();
    }

    private static void number(MenuState.Builder s, FeatureId f, int start) {
        String v = VALUES.get(f.id);
        s.number(f, v != null ? Integer.valueOf(v) : start);
    }

    @Override public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args) {
        Runnable r = () -> {
            try {
                dumpNow(w);
            } catch (Throwable t) {
                w.println("walk.error " + t);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else {
            java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
            new Handler(Looper.getMainLooper()).post(() -> { try { r.run(); } finally { l.countDown(); } });
            try { l.await(3, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        }
        w.flush();
    }

    private void dumpNow(PrintWriter w) throws Exception {
        Panel cur = menu == null ? null : menu.host().current();
        Object pages = cur == null ? null : call(cur, "depth");
        w.println("walk.menu showing=" + (menu != null && menu.isShowing()) + " panel="
                + (cur == null ? null : cur.getClass().getSimpleName()) + " depth=" + pages
                + " all=" + (cur == null ? null : call(cur, "isAllMode")) + " strip=" + (field(cur, "strip") != null));
        View root = (View) field(cur, "root");
        View focus = root == null ? null : root.findFocus();
        w.println("walk.focus " + describe(focus) + (focus instanceof Row ? " " + rowText(focus) : ""));
        Object page = cur == null ? null : call(cur, "currentPage");
        if (page != null && Boolean.TRUE.equals(call(cur, "isAllMode"))) {
            for (Object row : (List<?>) call(page, "rows")) if (row instanceof Row) w.println("walk.row " + rowText(row));
        }
        Object card = field(menu, "card");
        if (card != null) {
            w.println("walk.cardrows " + call(card, "rows"));
            int[] at = (int[]) call(card, "focusedCell");
            w.println("walk.cell " + (at == null ? "none" : at[0] + "," + at[1]));
            for (Object t : (Iterable<?>) call(card, "tiles")) {
                View tv = (View) t;
                w.println("walk.tile key=" + field(t, "key") + " value=" + field(t, "value") + " active=" + field(t, "active")
                        + " alpha=" + tv.getAlpha() + " focused=" + tv.isFocused());
            }
        }
        Object edit = field(menu, "edit");
        w.println("walk.edit " + (edit == null ? "none" : "mode=" + field(edit, "mode") + " list=" + field(edit, "list")
                + " query=" + field(edit, "query")));
        if (edit != null) {
            List<String> picks = new ArrayList<>();
            picks((View) field(edit, "sheet"), picks);
            w.println("walk.picks " + String.join(" | ", picks));
        }
        Object adjust = field(menu, "adjust");
        Object srow = adjust == null ? null : field(adjust, "row");
        w.println("walk.adjust active=" + (adjust != null && (Boolean) call(adjust, "active")) + " row="
                + (srow instanceof Row ? ((Row) srow).getTitle() : null) + " walk=" + (adjust != null && field(adjust, "walk") != null));
        Object cat = field(menu, "quickCatalog");
        Map<?, ?> rows = (Map<?, ?>) field(menu, "rows");
        for (String id : global("lumen_walk_watch")) {
            FeatureId f = FeatureId.of(id);
            Object row = f == null || rows == null ? null : rows.get(f);
            w.println("walk.watch " + id + " value=" + VALUES.get(id) + " catalogue=" + (cat != null && (Boolean) call(cat, "has", id))
                    + " row=" + rowText(row));
        }
        w.println("walk.sets " + SETS);
    }

    private static String rowText(Object row) {
        if (row == null) return "none";
        String t = row.getClass().getSimpleName() + " title=" + ((Row) row).getTitle() + " enabled=" + ((Row) row).isRowEnabled()
                + " shown=" + (((View) row).getVisibility() == View.VISIBLE);
        if (row instanceof ToggleRow) return t + " checked=" + ((ToggleRow) row).isChecked();
        if (row instanceof ChoiceRow) return t + " text=" + PageAccess.choiceLabel((ChoiceRow) row);
        if (row instanceof SliderRow) return t + " number=" + (((SliderRow) row).isKnown() ? ((SliderRow) row).getValue() : null);
        return t;
    }

    private static void picks(View v, List<String> out) throws Exception {
        if (v == null) return;
        if (v.getClass().getSimpleName().equals("Pick")) {
            out.add(field(v, "key") + "~" + field(v, "title") + "~" + field(v, "badgeText") + (v.isFocused() ? "~FOCUS" : ""));
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) picks(((ViewGroup) v).getChildAt(i), out);
    }

    private static String describe(View v) {
        if (v == null) return "none";
        StringBuilder s = new StringBuilder("class=" + v.getClass().getSimpleName() + " focused=" + v.isFocused());
        for (String n : new String[]{"key", "title", "value", "text"}) {
            Object o = field(v, n);
            if (o != null) s.append(' ').append(n).append('=').append(String.valueOf(o).replace('\n', ' '));
        }
        return s.toString();
    }

    private static Object call(Object o, String name, Object... args) throws Exception {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    m.setAccessible(true);
                    return m.invoke(o, args);
                }
            }
        }
        return null;
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
