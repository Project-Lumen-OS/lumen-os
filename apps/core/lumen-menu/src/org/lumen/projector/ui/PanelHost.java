// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.ui;

import android.view.View;
import android.view.WindowManager;

/**
 * Where the shared menu's windows live (apps/core/lumen-menu the design notes). The menu (MenuController,
 * GameMenu, GmOverlays) shows, closes and finds its {@link Panel}s only through this interface, so the same views run
 * in either host:
 * <ul>
 *   <li>{@link OverlayPanelHost}: the projector's overlay windows ({@link OverlayHost}, TYPE_APPLICATION_OVERLAY, the
 *       app underneath keeps running). The default; the Z9X and the HB use it.</li>
 *   <li>{@link ActivityPanelHost}: the same Panel views inside one translucent activity window of the app
 *       ({@link PanelActivity}), for a projector app that adds no overlay windows (the X50).</li>
 * </ul>
 * Main thread only.
 */
public interface PanelHost {
    /** Shows {@code p} in place of the panel shown now (one at a time). False when it cannot be shown. */
    boolean show(Panel p);

    /** Closes {@code p} with its exit animation; nothing when it is not shown. */
    void dismiss(Panel p);

    /** Closes the panel shown now, if any. */
    void dismissAll();

    /** The panel shown now (or being opened), null when none. */
    Panel current();

    /**
     * A layer over everything that takes no keys and no touches (the game crosshair and frame counter). False when
     * this host has no such layer: the caller then draws nothing.
     */
    boolean addStatic(View v, WindowManager.LayoutParams lp);

    void removeStatic(View v);

    /**
     * This host has layers over everything ({@link #addStatic} can succeed). The game menu offers its crosshair and
     * frame counter tiles only then; the activity host answers false.
     */
    default boolean hasStaticLayers() { return true; }

    /** A short note on screen (the menu's refusal reasons, e.g. "HDMI only"). */
    void note(CharSequence text);

    /** A short note with a title (the Boost notes, stock notes titled with the pressed row). */
    void note(CharSequence title, CharSequence text);

    /** A short toast of one of the app's strings ("Not ready yet", "Could not do that"). */
    void toast(int res);
}
