// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/**
 * Layers over the game for a host whose panels are activities (ActivityPanelHost, the X50 family): the game menu's
 * crosshair and frame counter (GmOverlays). Stock xhplayer draws them inside its HDMI activity (InputActivity GameView),
 * so whatever covers the HDMI picture covers them too. Here they are windows of the projector app
 * (OverlayHost.fullscreenParams(false): TYPE_APPLICATION_OVERLAY, translucent, no keys, no touches); an overlay always
 * draws above every activity, so while the device's cover is the front task (X50: X50EyeGuard's black CoverActivity),
 * or the front task is unknown, every layer is taken off the screen, and put back when the cover is gone (LayerSet).
 * The cover is checked on every front task change (ITaskStackListener; the app is android.uid.system), no polling.
 * Main thread.
 */
public final class CoverLayers implements ActivityPanelHost.Layers {
    private static final String TAG = "LumenMenuHost";

    /** The device's cover: true = it is the front task, false = not, null = unknown (layers stay off). */
    public interface Cover {
        Boolean up();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final LayerSet<View, WindowManager.LayoutParams> set;

    /** {@code cover}: true = the device's cover is the front task, false = not, null = unknown. */
    public CoverLayers(Context app, Cover cover) {
        WindowManager wm = app.getSystemService(WindowManager.class);
        set = new LayerSet<>(new LayerSet.Windows<View, WindowManager.LayoutParams>() {
            @Override public void add(View v, WindowManager.LayoutParams lp) { wm.addView(v, lp); }

            @Override public void remove(View v) { wm.removeViewImmediate(v); }
        }, cover::up);
        try {
            ActivityTaskManager.getService().registerTaskStackListener(new TaskStackListener() {
                @Override public void onTaskStackChanged() { main.post(set::sync); }
            });
        } catch (Throwable t) {
            Log.w(TAG, "no front task events: layers follow the cover only when added (" + t + ")");
        }
    }

    @Override public boolean add(View v, WindowManager.LayoutParams lp) {
        boolean ok = set.add(v, lp);
        Log.i(TAG, "layer " + lp.getTitle() + (set.isShown(v) ? " on screen" : " held under the cover"));
        return ok;
    }

    @Override public void remove(View v) { set.remove(v); }
}
