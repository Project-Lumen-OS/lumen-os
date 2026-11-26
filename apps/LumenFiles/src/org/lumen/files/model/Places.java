// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The strip's order: the projector's storage first, then the drives by name (USB before SD on a tie). */
public final class Places {
    private Places() {}

    public static List<Place> order(List<Place> in, Comparator<String> names) {
        List<Place> out = new ArrayList<>(in);
        out.sort((a, b) -> {
            if (a.kind == PlaceKind.INTERNAL || b.kind == PlaceKind.INTERNAL) {
                return a.kind == b.kind ? 0 : a.kind == PlaceKind.INTERNAL ? -1 : 1;
            }
            int c = names.compare(a.label, b.label);
            return c != 0 ? c : Integer.compare(a.kind.ordinal(), b.kind.ordinal());
        });
        return out;
    }

    /** The place whose root holds {@code path}, null when none does. */
    public static Place of(List<Place> all, String path) {
        Place best = null;
        for (Place p : all) {
            boolean in = path.equals(p.root) || path.startsWith(p.root.endsWith("/") ? p.root : p.root + "/");
            if (in && (best == null || p.root.length() > best.root.length())) best = p;
        }
        return best;
    }
}
