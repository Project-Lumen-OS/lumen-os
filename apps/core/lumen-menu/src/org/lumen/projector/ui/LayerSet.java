// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The layers over the game and the cover rule, plain Java (CoverLayers is the Android shell): every layer the host was
 * given is on the screen exactly while the device's cover is known to be down; while the cover is up, or its state is
 * unknown (fail closed), every layer is off the screen and comes back when the cover goes. {@link #sync} runs on every
 * front task change and before nothing else. Main thread.
 *
 * @param <V> the window content (android.view.View)
 * @param <P> its window parameters (WindowManager.LayoutParams)
 */
final class LayerSet<V, P> {
    /** Where layers go (the window manager). */
    interface Windows<V, P> {
        void add(V v, P lp) throws Exception;

        void remove(V v) throws Exception;
    }

    /** The device's cover: true = up (the front task covers the picture), false = down, null = unknown. */
    interface Cover {
        Boolean up();
    }

    private final Windows<V, P> windows;
    private final Cover cover;
    private final Map<V, P> layers = new LinkedHashMap<>();
    private final Set<V> shown = new HashSet<>();

    LayerSet(Windows<V, P> windows, Cover cover) {
        this.windows = windows;
        this.cover = cover;
    }

    /** A layer on screen only with the cover known to be down. */
    static boolean onScreen(Boolean coverUp) {
        return Boolean.FALSE.equals(coverUp);
    }

    /** A new layer: kept, and on the screen now unless the cover is up. */
    boolean add(V v, P lp) {
        layers.put(v, lp);
        sync();
        return true;
    }

    /** A layer the host no longer wants: off the screen, never put back. */
    void remove(V v) {
        layers.remove(v);
        if (shown.remove(v)) drop(v);
    }

    /** The cover may have changed (a front task change): every layer on or off the screen by the cover rule. */
    void sync() {
        Boolean up;
        try {
            up = cover.up();
        } catch (Throwable t) {
            up = null;
        }
        boolean on = onScreen(up);
        for (Map.Entry<V, P> e : layers.entrySet()) {
            V v = e.getKey();
            if (on && !shown.contains(v)) {
                try {
                    windows.add(v, e.getValue());
                    shown.add(v);
                } catch (Throwable t) {
                    // not shown: the next sync tries again
                }
            } else if (!on && shown.remove(v)) {
                drop(v);
            }
        }
    }

    boolean isShown(V v) { return shown.contains(v); }

    int count() { return layers.size(); }

    private void drop(V v) {
        try {
            windows.remove(v);
        } catch (Throwable ignored) {
            // already gone
        }
    }
}
