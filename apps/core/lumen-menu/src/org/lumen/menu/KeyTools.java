// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * Lumen tools that own the remote while they are on screen (manual focus, the fine corner adjustment, the zoom card),
 * one key rule for every projector app:
 * <ul>
 *   <li>The remote's menu key goes to the tool. The gear pressed short or held (the remote's own gear hold too) reaches
 *       the tool panel of a window host ({@link Host#menuKey}) or the tool screen in front ({@link Tool#onMenuKey}),
 *       never the quick menu, the game menu or Android settings over it ({@link #menuKey}). A menu panel opened over the
 *       tool screen keeps the gear as before (it closes that menu).</li>
 *   <li>A menu panel on screen when a tool screen comes in front steps aside ({@link Host#aside}): it takes no key and
 *       is not seen. Window focus goes to the topmost focusable window, and the overlay host's menu window sits above
 *       every activity, so a tool activity opened under it never had the keys (LEFT moved the menu instead of the
 *       lens). When the last tool screen goes, the menu has the keys again on the same page and row.</li>
 * </ul>
 * A tool screen reports {@link #shown} when it comes in front (onResume, before it has the window focus: a focusable
 * menu above it would keep the focus) and {@link #gone} when it leaves (onPause). Plain Java, main thread only.
 */
public final class KeyTools {
    private KeyTools() {}

    /** A tool screen (an activity) that owns the remote while it is in front. */
    public interface Tool {
        /** The remote's menu key while this tool is in front: the gear pressed short ({@code held} false) or held. */
        void onMenuKey(boolean held);
    }

    /** A window host of panels (the overlay host, the activity host). */
    public interface Host {
        /** {@code on}: a tool screen came in front, the panel on screen takes no key and is hidden; false: it is back. */
        void aside(boolean on);

        /** A panel of this host is on screen and has the keys (not aside). */
        boolean panelOnScreen();

        /** The remote's menu key: true when this host's panel on screen is a tool panel and took it. */
        boolean menuKey(boolean held);
    }

    /** Tool screens in front, the newest last. */
    private static final List<Tool> FRONT = new ArrayList<>();
    private static final List<Host> HOSTS = new ArrayList<>();

    /** A window host joins once (its constructor). */
    public static void addHost(Host h) {
        if (h != null && !HOSTS.contains(h)) HOSTS.add(h);
    }

    /** {@code t} came in front (onResume): it owns the remote; the first tool screen sends the menu panels aside. */
    public static void shown(Tool t) {
        if (t == null) return;
        boolean first = FRONT.isEmpty();
        FRONT.remove(t);
        FRONT.add(t);
        if (first) for (Host h : HOSTS) h.aside(true);
    }

    /** {@code t} left (onPause): when no tool screen is left, the menu panel that stepped aside is back. */
    public static void gone(Tool t) {
        if (!FRONT.remove(t) || !FRONT.isEmpty()) return;
        for (Host h : HOSTS) h.aside(false);
    }

    /** The tool screen in front, null when none. */
    public static Tool front() { return FRONT.isEmpty() ? null : FRONT.get(FRONT.size() - 1); }

    /**
     * The remote's menu key (KeyReceiver: the gear's short press, its long press, the remote's own gear hold): true when
     * a tool took it, then the caller opens nothing; false = no tool has the keys, the gear does what it always did.
     */
    public static boolean menuKey(boolean held) {
        for (Host h : HOSTS) if (h.menuKey(held)) return true;
        for (Host h : HOSTS) if (h.panelOnScreen()) return false;
        Tool t = front();
        if (t == null) return false;
        t.onMenuKey(held);
        return true;
    }
}
