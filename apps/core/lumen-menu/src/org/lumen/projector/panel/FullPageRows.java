// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.view.View;

import org.lumen.menu.FeatureId;
import org.lumen.menu.quick.FullLayout;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.Row;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rows of one section page of All settings in the stock order, recorded while the page is built, and the page laid
 * out again for the user's {@link FullLayout}. The row views stay the same objects (their state, their twins and the
 * tile popups built from them), only their place on the page changes and a hidden row leaves the page.
 */
final class FullPageRows {
    /** A group of the page: its header (null: none) and the views of each row in the stock order. */
    static final class Block {
        final View header;
        /** Rows other modules added go right after this group (General's Display and menu: the screensaver). */
        final boolean anchor;
        final List<FeatureId> ids = new ArrayList<>();
        final Map<FeatureId, List<View>> views = new HashMap<>();

        Block(View header, boolean anchor) {
            this.header = header;
            this.anchor = anchor;
        }
    }

    private final List<View> head = new ArrayList<>();
    private final List<Block> blocks = new ArrayList<>();
    private final Set<View> own = new HashSet<>();

    /** A view above the first group (the section's note line). */
    void head(View v) {
        head.add(v);
        own.add(v);
    }

    Block block(View header, boolean anchor) {
        Block b = new Block(header, anchor);
        blocks.add(b);
        if (header != null) own.add(header);
        return b;
    }

    /**
     * The views row {@code f} added to the page. A row without a title of its own (Boost's hint under the light slider)
     * rides with the row before it, so it can never be parted from it.
     */
    void rows(Block b, FeatureId f, List<View> views) {
        if (views.isEmpty()) return;
        own.addAll(views);
        if (title(views) == null && !b.ids.isEmpty()) {
            b.views.get(b.ids.get(b.ids.size() - 1)).addAll(views);
            return;
        }
        b.ids.add(f);
        b.views.put(f, new ArrayList<>(views));
    }

    /** The rows of each group in the stock order (groups without rows left out): what the editor offers. */
    List<List<FeatureId>> groups() {
        List<List<FeatureId>> out = new ArrayList<>();
        for (Block b : blocks) if (!b.ids.isEmpty()) out.add(new ArrayList<>(b.ids));
        return out;
    }

    /** The title the page shows for {@code f}, or null. */
    CharSequence title(FeatureId f) {
        for (Block b : blocks) if (b.views.containsKey(f)) return title(b.views.get(f));
        return null;
    }

    private static CharSequence title(List<View> views) {
        for (View v : views) {
            if (!(v instanceof Row)) continue;
            CharSequence t = ((Row) v).getTitle();
            if (t != null && t.length() > 0) return t;
        }
        return null;
    }

    /**
     * Lays {@code p} out for {@code l}: the note line, each group in the user's order without its hidden rows (a group
     * whose rows are all hidden loses its header too), the rest as it stands (rows other modules added) right after the
     * anchor group (General's Display and menu, so they stay above Help), else at the end, {@code last} at the very end (the editor's own row, never hidden).
     */
    void apply(Page p, String section, FullLayout l, View last) {
        List<View> rest = new ArrayList<>();
        for (View v : p.rows()) if (!own.contains(v) && v != last) rest.add(v);
        p.clear();
        for (View v : head) p.add(v);
        for (Block b : blocks) {
            List<FeatureId> shown = l.arrange(section, b.ids);
            if (shown.isEmpty()) continue;
            if (b.header != null) p.add(b.header);
            for (FeatureId f : shown) for (View v : b.views.get(f)) p.add(v);
            if (b.anchor) rest = addAll(p, rest);
        }
        addAll(p, rest);
        if (last != null) p.add(last);
    }

    private static List<View> addAll(Page p, List<View> views) {
        for (View v : views) p.add(v);
        return new ArrayList<>();
    }
}
