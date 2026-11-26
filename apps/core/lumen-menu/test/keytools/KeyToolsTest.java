// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.lumen.menu.KeyTools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared tool key rule (org.lumen.menu.KeyTools) on the REAL window hosts (OverlayHost and Panel of
 * apps/common/projector, ActivityPanelHost, PanelActivity and ToolActivity of lumen-menu) over a fake screen that gives
 * the key focus as WindowManager does: to the topmost window that is drawn and focusable, overlay windows above every
 * activity window. Owner bugs of 2026-11-13: (1) quick menu open, focus key held (manual focus), LEFT: the menu moved
 * and the lens did not; (2) the gear on the fine corner adjustment opened the quick menu instead of the view switch.
 */
public final class KeyToolsTest {
    private static int fails, checks;

    private static void ok(String what, boolean cond) {
        checks++;
        if (cond) System.out.println("ok   " + what);
        else { fails++; System.out.println("FAIL " + what); }
    }

    // ------------------------------------------------------------------ the fake screen
    /** Windows bottom to top; activity windows go under every overlay window. */
    static final class Screen implements WindowManager {
        final List<View> wins = new ArrayList<>();
        /** The window flags as the window manager last got them (addView, updateViewLayout), never the live params. */
        final Map<View, Integer> flags = new HashMap<>();
        View focus;

        @Override public void addView(View v, ViewGroup.LayoutParams p) {
            v.setLayoutParams(p);
            flags.put(v, wmFlags(p));
            wins.add(v);
            v.attached = true;
            v.dispatchAttached(true);
            refocus();
        }

        @Override public void updateViewLayout(View v, ViewGroup.LayoutParams p) {
            v.setLayoutParams(p);
            flags.put(v, wmFlags(p));
            refocus();
        }

        @Override public void removeViewImmediate(View v) {
            if (!wins.remove(v)) throw new IllegalArgumentException("not added");
            v.dispatchAttached(false);
            v.attached = false;
            flags.remove(v);
            if (focus == v) { focus = null; v.windowFocus = false; v.dispatchWindowFocusChanged(false); }
            refocus();
        }

        /** An activity's window (its content view), above the other activity windows, below every overlay. */
        void addActivity(View root) {
            int i = 0;
            while (i < wins.size() && !overlay(wins.get(i))) i++;
            root.setLayoutParams(new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION, 0, 0));
            flags.put(root, 0);
            wins.add(i, root);
            root.attached = true;
            root.dispatchAttached(true);
            refocus();
        }

        void remove(View root) { removeViewImmediate(root); }

        static boolean overlay(View v) {
            return ((WindowManager.LayoutParams) v.getLayoutParams()).type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }

        static int wmFlags(ViewGroup.LayoutParams p) {
            return p instanceof WindowManager.LayoutParams ? ((WindowManager.LayoutParams) p).flags : 0;
        }

        boolean focusable(View v) {
            return v.getVisibility() == View.VISIBLE
                    && (flags.getOrDefault(v, 0) & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0;
        }

        void refocus() {
            View top = null;
            for (int i = wins.size() - 1; i >= 0; i--) if (focusable(wins.get(i))) { top = wins.get(i); break; }
            if (top == focus) return;
            View old = focus;
            focus = top;
            if (old != null) { old.windowFocus = false; old.dispatchWindowFocusChanged(false); }
            if (top != null) { top.windowFocus = true; top.dispatchWindowFocusChanged(true); }
        }

        /** A remote key: DOWN and UP to the window that has the focus now. */
        void press(int code) {
            long t = Looper.now;
            if (focus != null) focus.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
            if (focus != null) focus.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
        }
    }

    /** A content view that records the keys its window gets. */
    static final class Keys extends FrameLayout {
        final List<String> got = new ArrayList<>();

        Keys(Context c) { super(c); }

        @Override public boolean dispatchKeyEvent(KeyEvent e) { got.add(e.toString()); return true; }
    }

    /** A menu panel (the quick menu): its page state lives in the object, so "the same place" is the same object. */
    static class TestPanel extends Panel {
        final List<String> keys = new ArrayList<>();
        int created, dismissed;
        String page = "keystone";

        @Override protected View onCreateView(Context c) { created++; return new FrameLayout(c); }

        @Override protected WindowManager.LayoutParams onCreateLayoutParams(Context c) {
            return OverlayHost.params(true, 600, ViewGroup.LayoutParams.MATCH_PARENT, android.view.Gravity.END);
        }

        @Override protected boolean onKeyEvent(KeyEvent e) { keys.add(e.toString()); return true; }

        @Override protected void onShown(View root) { }

        @Override protected void animateOut(View root, Runnable end) { end.run(); }

        @Override protected void onDismissed() { dismissed++; }
    }

    /** The fine corner adjustment: a tool panel whose MENU switches the view. */
    static final class CornerPanel extends TestPanel {
        int view;

        @Override protected boolean takesMenuKey() { return true; }

        @Override protected boolean onKeyEvent(KeyEvent e) {
            keys.add(e.toString());
            if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) view = (view + 1) % 3;
            return true;
        }
    }

    /** Manual focus: a tool screen (the real ToolActivity base) whose window records its keys. */
    static final class FocusTool extends ToolActivity {
        final Keys window;
        final List<Boolean> menuKeys = new ArrayList<>();

        FocusTool(Context c) { window = new Keys(c); setContentView(window); }

        @Override public void onMenuKey(boolean held) { menuKeys.add(held); finish(); }
    }

    /** The X50 subtitle mask editor: a tool screen stock shows without its menu (KeyTools.Tool.hidesMenu). */
    static final class MaskTool extends ToolActivity {
        final Keys window;

        MaskTool(Context c) { window = new Keys(c); setContentView(window); }

        @Override public void onMenuKey(boolean held) { finish(); }

        @Override public boolean hidesMenu() { return true; }
    }

    /** The gear's short press in KeyReceiver: the tool rule first, else the quick menu toggles. */
    static int toggles;

    static void gearShort() { if (!KeyTools.menuKey(false)) toggles++; }

    static void gearLong() { KeyTools.menuKey(true); }

    // ------------------------------------------------------------------ the rule alone
    static final class RecHost implements KeyTools.Host {
        boolean on, tool, panel;
        final List<Boolean> asides = new ArrayList<>();
        final List<Boolean> menu = new ArrayList<>();

        @Override public void aside(boolean a) { if (on) asides.add(a); }

        @Override public boolean panelOnScreen() { return on && panel; }

        @Override public boolean menuKey(boolean held) { if (on && tool) { menu.add(held); return true; } return false; }
    }

    static final class Rec implements KeyTools.Tool {
        final List<Boolean> got = new ArrayList<>();

        @Override public void onMenuKey(boolean held) { got.add(held); }
    }

    private static void rule() {
        ok("rule: no tool, the gear does what it always did", !KeyTools.menuKey(false) && !KeyTools.menuKey(true) && KeyTools.front() == null);
        RecHost h = new RecHost();
        h.on = true;
        KeyTools.addHost(h);
        Rec a = new Rec(), b = new Rec();
        KeyTools.shown(a);
        ok("rule: the first tool screen sends the panels aside once", h.asides.equals(List.of(true)));
        KeyTools.shown(a);
        ok("rule: a second onResume of the same tool sends nothing again", h.asides.equals(List.of(true)));
        ok("rule: the gear short and held go to the tool in front", KeyTools.menuKey(false) && KeyTools.menuKey(true)
                && a.got.equals(List.of(false, true)));
        KeyTools.shown(b);
        ok("rule: a tool over a tool takes the key, no second aside", KeyTools.menuKey(false) && b.got.equals(List.of(false))
                && a.got.size() == 2 && h.asides.equals(List.of(true)));
        KeyTools.gone(a);
        ok("rule: the lower tool leaving keeps the panels aside", KeyTools.front() == b && h.asides.equals(List.of(true)));
        KeyTools.gone(new Rec());
        ok("rule: a tool that never came changes nothing", KeyTools.front() == b && h.asides.equals(List.of(true)));
        h.panel = true;
        ok("rule: a menu opened over the tool screen keeps the gear (it closes that menu)", !KeyTools.menuKey(false) && b.got.size() == 1);
        h.panel = false;
        h.tool = true;
        ok("rule: a tool panel on screen takes the gear before the tool screen", KeyTools.menuKey(true) && h.menu.equals(List.of(true))
                && b.got.size() == 1);
        h.tool = false;
        KeyTools.gone(b);
        ok("rule: the last tool leaving brings the panels back once", KeyTools.front() == null && h.asides.equals(List.of(true, false)));
        ok("rule: no tool left, the gear opens the menu again", !KeyTools.menuKey(false));
        h.on = false;
    }

    // ------------------------------------------------------------------ overlay host (Z9X, HB, Play 6)
    private static void overlay(Context app, Screen scr) {
        OverlayHost host = OverlayHost.get(app);
        TestPanel menu = new TestPanel();
        host.show(menu);
        Looper.advance(10);
        View menuWin = menu.rootView();
        ok("overlay: the quick menu has the keys", scr.focus == menuWin);
        scr.press(KeyEvent.KEYCODE_DPAD_DOWN);
        ok("overlay: DOWN moves the menu", menu.keys.size() == 2);
        menu.page = "focus";                                       // the user went to the focus page
        menu.keys.clear();

        // the focus key held: KeyReceiver opens manual focus, its onResume runs before its window is on screen
        FocusTool tool = new FocusTool(app);
        tool.performResume();
        scr.addActivity(tool.window);
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) menuWin.getLayoutParams();
        ok("overlay: manual focus in front takes the key focus", scr.focus == tool.window);
        ok("overlay: the menu window is not focusable while it is in front (the window manager got the flag)",
                (lp.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0
                        && (scr.flags.get(menuWin) & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
        ok("overlay: the menu is not seen while manual focus is in front", menuWin.getVisibility() == View.INVISIBLE);
        scr.press(KeyEvent.KEYCODE_DPAD_LEFT);
        scr.press(KeyEvent.KEYCODE_DPAD_CENTER);
        scr.press(KeyEvent.KEYCODE_BACK);
        ok("overlay: LEFT, OK and BACK reach manual focus only", tool.window.got.size() == 6 && menu.keys.isEmpty());
        Looper.advance(120_000);
        ok("overlay: neither the focus loss nor the auto-hide closes the menu under manual focus", menu.isShowing()
                && host.current() == menu && menu.dismissed == 0);
        ok("overlay: the menu under a tool counts as no panel on screen", !host.panelOnScreen() && !host.menuKey(false));

        // the gear while manual focus is in front: its menu key (closes it), never the quick menu
        toggles = 0;
        gearShort();
        ok("overlay: the gear goes to manual focus, the quick menu does not toggle", toggles == 0
                && tool.menuKeys.equals(List.of(false)) && menu.isShowing());
        gearLong();
        ok("overlay: the held gear goes to manual focus too", tool.menuKeys.equals(List.of(false, true)));

        // manual focus ends (its onPause, then the window goes)
        tool.performPause();
        scr.remove(tool.window);
        ok("overlay: the menu is back with the keys", scr.focus == menuWin && menuWin.getVisibility() == View.VISIBLE
                && (lp.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0);
        scr.press(KeyEvent.KEYCODE_DPAD_LEFT);
        Looper.advance(OverlayHost.BACK_FOCUS_MS + 100);
        ok("overlay: in the same place: the same panel and page, never rebuilt, LEFT moves it", menu.isShowing()
                && menu.created == 1 && menu.dismissed == 0 && "focus".equals(menu.page) && menu.keys.size() == 2);
        host.dismissAll(false);

        // no key after manual focus: the menu's own timer runs again from the moment it is back
        TestPanel idle = new TestPanel();
        host.show(idle);
        FocusTool t1 = new FocusTool(app);
        t1.performResume();
        scr.addActivity(t1.window);
        Looper.advance(5_000);
        t1.performPause();
        scr.remove(t1.window);
        Looper.advance(Panel.DEFAULT_AUTO_HIDE_MS - 1_000);
        ok("overlay: back from aside it waits its whole timer", idle.isShowing());
        ok("overlay: then its auto-hide closes it as before", advanceCloses(idle, 1_000));

        // back from aside while another window keeps the focus: it closes as on a focus loss
        TestPanel kept = new TestPanel();
        host.show(kept);
        FocusTool t3 = new FocusTool(app);
        t3.performResume();
        scr.addActivity(t3.window);
        View card = new FrameLayout(app);
        host.addStatic(card, OverlayHost.fullscreenParams(true));
        Looper.advance(1_000);                                     // manual focus stays in front a while
        ok("overlay: a menu aside stays open while the tool is in front", kept.isShowing());
        t3.performPause();
        scr.remove(t3.window);
        Looper.advance(OverlayHost.BACK_FOCUS_MS + 100);
        ok("overlay: back without the focus (a card on top keeps it), the menu closes", !kept.isShowing() && scr.focus == card);
        host.removeStatic(card);

        // a menu opened over manual focus keeps the gear (it toggles that menu)
        FocusTool t2 = new FocusTool(app);
        t2.performResume();
        scr.addActivity(t2.window);
        TestPanel over = new TestPanel();
        host.show(over);
        toggles = 0;
        gearShort();
        ok("overlay: a menu opened over manual focus has the keys and the gear toggles it", scr.focus == over.rootView()
                && toggles == 1 && t2.menuKeys.isEmpty());
        host.dismissAll(false);
        t2.performPause();
        scr.remove(t2.window);

        // the fine corner adjustment (a tool panel): the gear is its MENU
        CornerPanel corner = new CornerPanel();
        host.show(corner);
        toggles = 0;
        gearShort();
        ok("corner: a short gear is one MENU press of the panel, the quick menu does not open", toggles == 0
                && corner.keys.equals(List.of("DOWN 82", "UP 82")) && corner.view == 1 && host.current() == corner);
        gearShort();
        gearShort();
        ok("corner: grid, over the picture, test quad, grid again", corner.view == 0);
        corner.keys.clear();
        gearLong();
        ok("corner: a held gear does nothing there (no game menu, no settings, no reset)", corner.keys.isEmpty()
                && host.current() == corner);
        host.dismissAll(false);
        toggles = 0;
        gearShort();
        ok("corner closed: the gear opens the quick menu again", toggles == 1);
        TestPanel plain = new TestPanel();
        host.show(plain);
        toggles = 0;
        gearShort();
        ok("a menu panel is no tool panel: the gear toggles it", toggles == 1 && plain.keys.isEmpty());
        host.dismissAll(false);
    }

    private static boolean advanceCloses(Panel p, long ms) {
        Looper.advance(ms + 10);
        return !p.isShowing();
    }

    // ------------------------------------------------------------------ activity host (X50)
    private static void activity(Context app, Screen scr) {
        ActivityPanelHost host = ActivityPanelHost.install(app, (c, t) -> { });
        TestPanel menu = new TestPanel();
        host.show(menu);
        PanelActivity pa = new PanelActivity();
        pa.performCreate();
        pa.performStart();
        pa.performResume();
        scr.addActivity(pa.contentView());
        Looper.advance(10);
        ok("activity host: the quick menu has the keys", scr.focus == pa.contentView() && menu.isShowing());
        menu.page = "focus";
        FocusTool tool = new FocusTool(app);
        pa.performPause();
        tool.performResume();
        scr.addActivity(tool.window);
        ok("activity host: manual focus in front takes the key focus", scr.focus == tool.window);
        scr.press(KeyEvent.KEYCODE_DPAD_LEFT);
        Looper.advance(120_000);
        ok("activity host: LEFT reaches manual focus only, the menu waits under it", tool.window.got.size() == 2
                && menu.keys.isEmpty() && menu.isShowing() && menu.dismissed == 0 && !pa.isFinishing());
        ok("activity host: the menu stays seen under manual focus (the stock X50 menu under its focus bar)",
                menu.root.getAlpha() == 1f && !KeyTools.frontHidesMenu());
        toggles = 0;
        gearShort();
        ok("activity host: the gear goes to manual focus", toggles == 0 && tool.menuKeys.equals(List.of(false)));
        tool.performPause();
        scr.remove(tool.window);
        pa.performResume();
        scr.press(KeyEvent.KEYCODE_DPAD_LEFT);
        Looper.advance(ActivityPanelHost.BACK_FOCUS_MS + 100);
        ok("activity host: the menu is back in the same place with the keys", scr.focus == pa.contentView()
                && menu.isShowing() && menu.created == 1 && "focus".equals(menu.page) && menu.keys.size() == 2);
        menu.page = "dynamic black";
        MaskTool mask = new MaskTool(app);
        pa.performPause();
        mask.performResume();
        scr.addActivity(mask.window);
        scr.press(KeyEvent.KEYCODE_DPAD_UP);
        Looper.advance(120_000);
        ok("activity host: the mask editor has the keys, the menu waits unseen under it (stock hides its menu), still VISIBLE so its row keeps the focus",
                scr.focus == mask.window && mask.window.got.size() == 2 && menu.keys.size() == 2 && menu.isShowing()
                        && menu.dismissed == 0 && menu.root.getAlpha() == 0f && menu.root.getVisibility() == View.VISIBLE
                        && KeyTools.frontHidesMenu());
        toggles = 0;
        gearShort();
        ok("activity host: the gear goes to the mask editor, never the quick menu", toggles == 0 && mask.isFinishing());
        mask.performPause();
        scr.remove(mask.window);
        pa.performResume();
        scr.press(KeyEvent.KEYCODE_BACK);
        Looper.advance(ActivityPanelHost.BACK_FOCUS_MS + 100);
        ok("activity host: after the mask editor the menu is seen again on the same page with the keys",
                menu.root.getAlpha() == 1f && scr.focus == pa.contentView() && menu.isShowing()
                        && menu.created == 1 && "dynamic black".equals(menu.page) && menu.keys.size() == 4 && !KeyTools.frontHidesMenu());
        host.dismissAll();
        Looper.advance(10);
        CornerPanel corner = new CornerPanel();
        host.show(corner);
        PanelActivity pb = new PanelActivity();
        pb.performCreate();
        pb.performStart();
        toggles = 0;
        gearShort();
        ok("activity host: a tool panel gets the gear as its MENU", toggles == 0 && corner.view == 1);
        host.dismissAll();
    }

    public static void main(String[] a) {
        Context app = new Context();
        Screen scr = new Screen();
        Context.SERVICES.put(WindowManager.class, scr);
        rule();
        overlay(app, scr);
        activity(app, scr);
        System.out.println(fails == 0 ? "keytools: " + checks + " checks, all green" : "keytools: " + fails + " of " + checks + " FAILED");
        if (fails != 0) System.exit(1);
    }
}
