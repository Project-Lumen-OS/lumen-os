// SPDX-License-Identifier: Apache-2.0
package org.lumen.hdmi;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Sends the HDMI audio keys of {@link HdmiAudioKeys} to the audio HAL (stock: AudioSystem.setParameters in
 * XRMService). A projector app installs it with its HAL thread and hands it every GmTvManager event. Without an
 * install the events are dropped, so a model gets the keys only when its app asks for them.
 */
public final class HdmiAudio {
    private static final String TAG = "LumenHdmiAudio";

    /** The model's HAL thread: runs the job there with a reader of getCurrentInputSource. */
    public interface Host {
        void run(Job j);
    }

    public interface Job {
        void run(HdmiAudioKeys.Source source);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final HdmiAudioKeys.Out OUT = new HdmiAudioKeys.Out() {
        @Override public void now(String keyValue) { send(keyValue); }

        @Override public void later(String keyValue, long delayMs) { MAIN.postDelayed(() -> send(keyValue), delayMs); }
    };
    private static volatile AudioManager sAudio;
    private static volatile Host sHost;

    private HdmiAudio() {}

    /** App start, once. */
    public static void install(Context ctx, Host host) {
        sAudio = ctx.getApplicationContext().getSystemService(AudioManager.class);
        sHost = host;
    }

    /** A GmTvManager event on the vendor's binder thread: only posts to the HAL thread. */
    public static void onTvEvent(int type, String msg) {
        Host h = sHost;
        if (h == null || !HdmiAudioKeys.handles(type)) return;
        h.run(source -> HdmiAudioKeys.onTvEvent(type, msg, source, OUT));
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
