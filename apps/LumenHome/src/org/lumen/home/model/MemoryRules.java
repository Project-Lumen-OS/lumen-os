// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Free up memory (4PDA 2026-11-22, "Memory Cleaner is #1"), the honest way: which apps Home may close. Pure. Only
 * apps the user installed (never a system app, so never a Lumen app, the TV input, the casting receivers or a vendor
 * service), only processes Android itself counts as background (importance service or cached: a player with its
 * foreground service, a visible app, the HDMI screen and anything perceptible stay), never the keyboard, the
 * screensaver, an accessibility service or Home. Android then stops those processes the way it does when it needs
 * memory (ActivityManager.killBackgroundProcesses), nothing is force stopped.
 */
public final class MemoryRules {
    /** ActivityManager.RunningAppProcessInfo importance: background service and below may be closed. */
    public static final int IMPORTANCE_SERVICE = 300;

    private MemoryRules() {}

    /** One running process: its packages, its importance, whether its app is a system app, its memory in KB. */
    public static final class Proc {
        public final List<String> pkgs;
        public final int importance;
        public final boolean system;
        public final long kb;

        public Proc(List<String> pkgs, int importance, boolean system, long kb) {
            this.pkgs = pkgs;
            this.importance = importance;
            this.system = system;
            this.kb = kb;
        }
    }

    /**
     * The packages to close, sorted: every process of a package must be a background one of a user app, and no
     * package of it may be protected ({@code keep}: Home, the keyboard, the screensaver, accessibility services).
     */
    public static List<String> victims(Collection<Proc> running, Set<String> keep) {
        TreeSet<String> out = new TreeSet<>(), blocked = new TreeSet<>(keep);
        for (Proc p : running) {
            boolean ok = !p.system && p.importance >= IMPORTANCE_SERVICE;
            for (String pkg : p.pkgs) {
                if (!ok) blocked.add(pkg);
                else out.add(pkg);
            }
        }
        out.removeAll(blocked);
        return new ArrayList<>(out);
    }

    /** Memory freed in MB from the available memory before and after (never below 0, rounded down). */
    public static long freedMb(long availBefore, long availAfter) {
        return Math.max(0, availAfter - availBefore) / (1024 * 1024);
    }
}
