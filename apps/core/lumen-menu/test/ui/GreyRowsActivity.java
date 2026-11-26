// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.walk;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.menu.Signal;
import org.lumen.menu.SignalRule;
import org.lumen.projector.panel.MenuController;
import org.lumen.projector.ui.ActivityPanelHost;
import org.lumen.projector.ui.Row;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Render harness of the disabled row look (test/ui/grey_render.py), built only into a test APK by test/ui/build.sh
 * (WALK=GreyRowsActivity) from a model's real assembled app, never into the image. The real MenuController opens the
 * picture mode list ({@code --es view list}, every mode) or the picture popup ({@code popup}) while the stand-in adapter
 * reads a content kind ({@code --es signal sdr|dolby|imax|hdr10}) and the first mode it offers, so the modes this
 * content does not allow are greyed by SignalRule. Every row on screen is logged as
 * {@code GreyRows <run> row <left> <top> <right> <bottom> <enabled 0|1> <focused 0|1> <title>} and the count SignalRule
 * greys as {@code GreyRows <run> expect <greyed> <listed>}.
 */
public class GreyRowsActivity extends Activity {
    private static final String TAG = "GreyRows";
    private MenuController menu;
    private String run;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        View bg = new View(this);
        bg.setBackgroundColor(0xFF404040);
        setContentView(bg);
        run = extra("run", "0");
        MenuProfile p = MenuController.profile(this);
        Signal sig = Signal.of(extra("signal", "sdr"));
        FeatureId f = FeatureId.PICTURE_MODE;
        List<String> opts = p.options(f);
        Set<String> closed = SignalRule.greyed(p, f, MenuState.builder().signal(sig).build());   // no current mode
        String cur = opts.get(0);
        for (String o : opts) if (!closed.contains(o)) { cur = o; break; }
        MenuState st = MenuState.builder().put(f, cur).signal(sig).put(FeatureId.INPUT, "hdmi1").build();
        Log.i(TAG, run + " expect " + SignalRule.greyed(p, f, st).size() + " " + opts.size());
        ProjectorFeatures dev = (ProjectorFeatures) Proxy.newProxyInstance(getClassLoader(),
                new Class<?>[]{ProjectorFeatures.class}, (px, m, a) -> {
                    switch (m.getName()) {
                        case "implemented": return p.features();
                        case "ready": case "available": case "setupDone": return true;
                        case "read": ((Consumer<MenuState>) a[0]).accept(st); return null;
                        case "set": case "setConfirmed":
                            ((Consumer<ProjectorFeatures.Result>) a[2]).accept(ProjectorFeatures.Result.failed(null)); return null;
                        case "run": case "runConfirmed":
                            ((Consumer<ProjectorFeatures.Result>) a[1]).accept(ProjectorFeatures.Result.failed(null)); return null;
                        case "readFormat": ((Consumer<Object>) a[0]).accept(null); return null;
                        case "open": return false;
                        case "hashCode": return 1;
                        case "equals": return px == a[0];
                        case "toString": return "grey rows adapter";
                        default: return null;
                    }
                });
        ActivityPanelHost host = ActivityPanelHost.install(getApplicationContext(),
                (c, t) -> android.widget.Toast.makeText(c, t, android.widget.Toast.LENGTH_SHORT).show());
        menu = new MenuController(getApplicationContext(), dev, null, host);
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(() -> menu.show("picture"), 700);
        if ("list".equals(extra("view", "list"))) h.postDelayed(() -> call("openModeList"), 2200);
        h.postDelayed(this::dump, 4200);
    }

    private String extra(String k, String d) {
        String v = getIntent().getStringExtra(k);
        return v != null ? v : d;
    }

    private void call(String name) {
        try {
            Method m = MenuController.class.getDeclaredMethod(name);
            m.setAccessible(true);
            m.invoke(menu);
        } catch (Exception e) {
            Log.e(TAG, run + " fail " + name + " " + e);
        }
    }

    /** Every row frame of the menu on screen with its place, state and title. */
    private void dump() {
        try {
            Field pf = MenuController.class.getDeclaredField("panel");
            pf.setAccessible(true);
            Object panel = pf.get(menu);
            Field rf = panel.getClass().getDeclaredField("root");
            rf.setAccessible(true);
            walk((View) rf.get(panel));
            Log.i(TAG, run + " done");
        } catch (Exception e) {
            Log.e(TAG, run + " fail dump " + e);
        }
    }

    private void walk(View v) throws Exception {
        if (v == null || !v.isShown()) return;
        if (v.getClass().getSimpleName().equals("QpdRowFrame") && v.getHeight() > 0) {
            Field rf = v.getClass().getDeclaredField("row");
            rf.setAccessible(true);
            View r = (View) rf.get(v);
            if (r instanceof Row && r.getClass().getSimpleName().equals("CheckRow")) {
                int[] at = new int[2];
                v.getLocationOnScreen(at);
                Row row = (Row) r;
                Log.i(TAG, run + " row " + at[0] + " " + at[1] + " " + (at[0] + v.getWidth()) + " " + (at[1] + v.getHeight())
                        + " " + (row.isRowEnabled() ? 1 : 0) + " " + (row.isFocused() ? 1 : 0) + " " + row.getTitle());
            }
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i));
        }
    }
}
