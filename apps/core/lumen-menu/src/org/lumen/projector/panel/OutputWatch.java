// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.sound.OutputRefresh;
import org.lumen.projector.Ui;

import java.util.function.BooleanSupplier;

/**
 * While the menu is open, every audio device change reads the sound rows again (OutputRefresh), from AudioService's
 * and the Bluetooth stack's own broadcasts: the HDMI audio plug (sent for HDMI, ARC and eARC alike), the headset plug,
 * the devices of a stream changing (the media route: ARC joins every route, USB, the jack), Bluetooth A2DP connection
 * and active device. No AudioDeviceCallback here: device callbacks go through the app's AudioPorts only (Z9X rule, no
 * port list loop while the audio policy is down). A change of eARC or of the output reads the rows again over the time
 * the link needs. Stops by itself once the menu is closed. Main thread.
 */
final class OutputWatch {
    /** AudioService's broadcast when the devices of a stream change (AudioManager.STREAM_DEVICES_CHANGED_ACTION, hidden). */
    static final String STREAM_DEVICES_CHANGED = "android.media.STREAM_DEVICES_CHANGED_ACTION";
    static final String A2DP_CONNECTION = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED";
    static final String A2DP_ACTIVE = "android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED";

    private final Context app;
    private final BooleanSupplier open;
    private final Runnable reread;
    private final Runnable fire = this::fire;
    private BroadcastReceiver plugs;

    OutputWatch(Context app, BooleanSupplier open, Runnable reread) {
        this.app = app;
        this.open = open;
        this.reread = reread;
    }

    /** Starts the watch (no-op while it runs). */
    void start() {
        if (plugs != null) return;
        plugs = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (isInitialStickyBroadcast()) return;           // the last plug state, not a change
                changed(i.getAction());
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(AudioManager.ACTION_HDMI_AUDIO_PLUG);
        f.addAction(AudioManager.ACTION_HEADSET_PLUG);
        f.addAction(STREAM_DEVICES_CHANGED);
        f.addAction(A2DP_CONNECTION);
        f.addAction(A2DP_ACTIVE);
        try {
            // the senders are system_server (AudioService) and the Bluetooth stack: protected broadcasts nobody else
            // can send, received from another uid only by an exported receiver
            app.registerReceiver(plugs, f, Context.RECEIVER_EXPORTED);
        } catch (RuntimeException e) {
            Log.w(MenuController.TAG, "output watch: " + e);
            stop();
        }
    }

    /** Ends the watch (the menu closed). */
    void stop() {
        Ui.main().removeCallbacks(fire);
        try {
            if (plugs != null) app.unregisterReceiver(plugs);
        } catch (RuntimeException e) {
            Log.w(MenuController.TAG, "output watch stop: " + e);
        }
        plugs = null;
    }

    /** A change of {@code f} by the user: the rows read again over the time its link needs (none for other rows). */
    void after(FeatureId f) {
        for (long ms : OutputRefresh.after(f)) Ui.main().postDelayed(() -> { if (open.getAsBoolean()) reread.run(); }, ms);
    }

    private void changed(String why) {
        if (!open.getAsBoolean()) { stop(); return; }
        Log.i(MenuController.TAG, "output watch: " + why);
        Ui.main().removeCallbacks(fire);
        Ui.main().postDelayed(fire, OutputRefresh.CALM_MS);
    }

    private void fire() {
        if (open.getAsBoolean()) reread.run();
        else stop();
    }
}
