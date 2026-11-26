// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * Sends the HDMI audio keys of {@link HdmiAudioKeys} to the audio HAL the moment the vendor reports the signal, as stock
 * XRMService does inside its GmTvManager listener on every family (AudioSystem.setParameters right in onTvEvent, the
 * delayed key 500 ms later). The keys have their own thread "lumen-hdmi-audio": one event after the other in the
 * vendor's order, never behind the model's HAL thread. A signal key needs no HAL call, a cable out reads
 * getCurrentInputSource there with the model's reader that works off its HAL thread. Report LR-5WQ7M9 (Z9X, PS5): the
 * mute of a lost signal waited behind 0.3 to 0.6 s HAL reads. A projector app installs it and hands it every
 * GmTvManager event. Without an install the events are dropped.
 */
public final class HdmiAudio {
    private static final String TAG = "LumenHdmiAudio";

    private static final HdmiAudioKeys.Out OUT = new HdmiAudioKeys.Out() {
        @Override public void now(String keyValue) { send(keyValue); }

        @Override public void later(String keyValue, long delayMs) { sLane.postDelayed(() -> send(keyValue), delayMs); }
    };
    private static volatile AudioManager sAudio;
    private static volatile HdmiAudioKeys.Source sSource;
    private static volatile Handler sLane;

    private HdmiAudio() {}

    /**
     * App start, once. {@code source} reads the vendor's current input source on the calling thread (the keys' own
     * thread, never the main one), not through the model's HAL queue.
     */
    public static synchronized void install(Context ctx, HdmiAudioKeys.Source source) {
        sAudio = ctx.getApplicationContext().getSystemService(AudioManager.class);
        sSource = source;
        if (sLane == null) {
            HandlerThread t = new HandlerThread("lumen-hdmi-audio");
            t.start();
            sLane = new Handler(t.getLooper());
        }
    }

    /** A GmTvManager event on the vendor's binder thread: only posts to the keys' own thread. */
    public static void onTvEvent(int type, String msg) {
        Handler lane = sLane;
        if (lane == null || !HdmiAudioKeys.handles(type)) return;
        lane.post(() -> HdmiAudioKeys.onTvEvent(type, msg, sSource, OUT));
    }

    private static void send(String keyValue) {
        AudioManager am = sAudio;
        if (am == null) return;
        try {
            am.setParameters(keyValue);
            Log.i(TAG, keyValue);
        } catch (Throwable t) {
            Log.w(TAG, keyValue + ": " + t);
        }
    }
}
