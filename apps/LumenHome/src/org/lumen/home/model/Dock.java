// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The top level of the dock and of the Apps page: apps and folders in the user's order. Pure. A folder shows only
 * while one of its apps is installed and not hidden, and stands where its token stands in the order; the apps inside
 * a folder are not on the top level.
 */
public final class Dock {
    private Dock() {}

    /**
     * The top level for {@code visible}, the visible apps in their order (AppOrder: the user's order first, then
     * the rest): packages and {@code folder:<id>} tokens.
     */
    public static List<String> tokens(List<String> visible, HomeLayout l) {
        Set<String> vis = new HashSet<>(visible);
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < l.order.size(); i++) rank.put(l.order.get(i), i);
        ArrayList<String> top = new ArrayList<>();
        ArrayList<Integer> ranks = new ArrayList<>();
        int next = l.order.size();
        for (String p : visible) {
            if (l.folderOf(p) != null) continue;
            top.add(p);
            Integer r = rank.get(p);
            ranks.add(r != null ? r : next++);
        }
        for (HomeLayout.Folder f : l.folders) {
            if (members(f, vis).isEmpty()) continue;
            Integer r = rank.get(f.token());
            top.add(f.token());
            ranks.add(r != null ? r : next++);
        }
        Integer[] idx = new Integer[top.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> ranks.get(a).equals(ranks.get(b)) ? Integer.compare(a, b) : Integer.compare(ranks.get(a), ranks.get(b)));
        ArrayList<String> out = new ArrayList<>();
        for (Integer i : idx) out.add(top.get(i));
        return out;
    }

    /** The folder's apps that are visible, in the folder's order. */
    public static List<String> members(HomeLayout.Folder f, Set<String> visible) {
        ArrayList<String> out = new ArrayList<>();
        for (String p : f.apps) if (visible.contains(p)) out.add(p);
        return out;
    }
}
