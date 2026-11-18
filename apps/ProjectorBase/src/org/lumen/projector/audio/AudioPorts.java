package org.lumen.projector.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;

import org.lumen.projector.R;
import org.lumen.projector.ui.Notify;

import java.util.ArrayList;
import java.util.List;

/**
 * Audio device callbacks only while the audio policy answers (Z9X trial on stock, 2026-11-11).
 *
 * Seen live: in a trial on a stock unit there is no z9x_blobs partition (sys.z9x.blobs=missing; the user's own
 * audio files are copied only by the real install), so the vendor audio policy file keeps XGIMI's enums, audioserver
 * runs without an AudioPolicyManager ("AudioPolicyManager: 0x0" in dumpsys media.audio_policy, AudioService
 * "initStreamVolume failed with 1 will retry") and every listAudioPorts fails with -19. A process that has an
 * AudioDeviceCallback registered then repeats the call every 100 ms for good: the framework's AudioPortEventHandler
 * reschedules each port event after a failed updateAudioPortCache (RESCHEDULE_MESSAGE_DELAY_MS). Our three callbacks
 * (AudioPathReporter, AudioOutputChoice, cec/EarcLink) kept org.lumen.projector at 10 failed binder calls a second.
 *
 * So a callback (or a module that registers one) waits here until the output list is not empty (every unit lists
 * its speaker once the policy works): probed now, then after 2, 4, 8 ... s, at most every 5 min. On an installed
 * system the first probe answers and everything registers at once, as before.
 */
public final class AudioPorts {
    static final String TAG = "Z9xAudioPorts";
    static final String PREFS = "z9x_audio_ports";

    private static final Object LOCK = new Object();
    private static final List<Runnable> WAITING = new ArrayList<>();
    private static Handler sProbe;
    private static Context sApp;
    private static boolean sUp, sProbing;
    private static int sFailures;

    private AudioPorts() {}

    // ================================================================== registration
    /**
     * {@code am.registerAudioDeviceCallback(cb, h)} now when the audio policy answers, else as soon as a probe sees it
     * answer. Never throws.
     */
    public static void registerDeviceCallback(Context ctx, AudioDeviceCallback cb, Handler h, String who) {
        whenUp(ctx, h, who, () -> {
            AudioManager am = ctx.getApplicationContext().getSystemService(AudioManager.class);
            if (am != null) am.registerAudioDeviceCallback(cb, h);
        });
    }

    /** Runs {@code action} on {@code h} once the audio policy answers (at once when it does now). Never throws. */
    public static void whenUp(Context ctx, Handler h, String who, Runnable action) {
        final Runnable run = () -> {
            try {
                action.run();
                Log.i(TAG, who + ": registered");
            } catch (Throwable t) {
                Log.w(TAG, who + ": " + t);
            }
        };
        final Runnable onH = () -> { if (h != null) h.post(run); else run.run(); };
        synchronized (LOCK) {
            if (sApp == null) sApp = ctx.getApplicationContext();
            if (sUp) { onH.run(); return; }
            WAITING.add(onH);
            if (sProbing) return;
            sProbing = true;
            probeHandler().post(AudioPorts::probe);
        }
    }

    private static Handler probeHandler() {
        if (sProbe == null) {
            HandlerThread t = new HandlerThread("z9x-audioports");
            t.start();
            sProbe = new Handler(t.getLooper());
        }
        return sProbe;
    }

    /** "z9x-audioports": one getDevices (one listAudioPorts); runs the waiting actions or probes again later. */
    private static void probe() {
        int outputs = 0;
        try {
            AudioManager am = sApp.getSystemService(AudioManager.class);
            if (am != null) outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).length;
        } catch (Throwable t) {
            Log.w(TAG, "getDevices: " + t);
        }
        boolean up = AudioPortRules.policyUp(outputs);
        List<Runnable> run = null;
        long next = 0;
        synchronized (LOCK) {
            if (up) {
                if (sFailures > 0) Log.i(TAG, "audio policy answers after " + sFailures + " probes");
                sUp = true;
                sProbing = false;
                run = new ArrayList<>(WAITING);
                WAITING.clear();
            } else {
                next = AudioPortRules.backoffMs(sFailures);
                sFailures++;
                if (sFailures == 1 || sFailures % 10 == 0) {
                    Log.w(TAG, "no audio outputs (audio policy not answering, sys.z9x.blobs=" + prop("sys.z9x.blobs")
                            + "): " + WAITING.size() + " device callbacks wait, next probe in " + next + " ms");
                }
            }
        }
        if (run != null) {
            for (Runnable r : run) r.run();
            return;
        }
        sProbe.postDelayed(AudioPorts::probe, next);
    }

    private static String prop(String k) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class).invoke(null, k);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
