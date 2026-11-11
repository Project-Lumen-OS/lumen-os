/*
 * HbHeadphoneVolume: the audio HAL's 3.5 mm level on the HB (2026-11-10, with audio/HbWiredJack). HB-only file, no
 * gmpf HAL access (audio HAL parameters only); decisions in HbAudioRules (headphoneConfirmed, headphoneVolume,
 * storeHeadphoneBeforeEnd), host tested in test/audio.
 *
 * Stock: the patched AudioService.setDspVolumesToHal sends "headphone_volume=" + index for DEVICE_OUT_WIRED_HEADSET /
 * WIRED_HEADPHONE (4 / 8) on every music volume change, as it sends spk_volume for the speaker; libxgimiaudiopolicy
 * threadInit sends "xgimi_audiopath_switch_end=4" after 59(4), and audio.primary.mt5873 restores the stored level of
 * the new sink on switch_end ("reset sink device 0x%x volume to %d"). The HAL's levels start at 0 from adev_open (the
 * speaker one is why HdmiVolume stores spk_volume before switch_end=0), so a switch_end=4 without a stored
 * headphone_volume would leave the jack silent.
 *
 * Here, the HDMI volume of the jack the way HdmiVolume does it for the speaker:
 *  - selectSink (AudioPathReporter.halSink after 59(4)): headphone_volume = the music level (0..100, 0 while muted),
 *    then switch_end=4;
 *  - follows STREAM_MUSIC (volume and mute broadcasts) while path 4 is both routed and confirmed by the vendor;
 *  - nothing on the other paths (the speaker is HdmiVolume's, Bluetooth BtDspVolume's).
 * Main thread. Log tag Z9xHpVol.
 */
package org.lumen.projector.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;

import org.lumen.projector.Ui;

public final class HbHeadphoneVolume {
    private static final String TAG = "Z9xHpVol";
    static final String ACTION_VOLUME = "android.media.VOLUME_CHANGED_ACTION";
    static final String ACTION_MUTE = "android.media.STREAM_MUTE_CHANGED_ACTION";
    static final String EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE";

    private static Context sApp;
    private static boolean sFollowing;
    private static volatile int sSent = -1;

    private HbHeadphoneVolume() {}

    /** Once per process (AudioPathReporter.install posts it). Main thread. */
    public static void install(Context ctx) {
        if (sApp != null) return;
        final Context app = ctx.getApplicationContext();
        sApp = app;
        try {
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    try {
                        String a = i == null ? null : i.getAction();
                        if (ACTION_VOLUME.equals(a) && i.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return;
                        follow("volume", false);
                    } catch (Throwable t) {
                        Log.w(TAG, "volume: " + t);
                    }
                }
            };
            IntentFilter f = new IntentFilter(ACTION_VOLUME);
            f.addAction(ACTION_MUTE);
            app.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            Log.w(TAG, "receiver: " + t);
        }
        AudioPathReporter.addStateListener((target, confirmed) -> Ui.main().post(() -> {
            try {
                follow("output path " + target + ", confirmed " + confirmed, false);
            } catch (Throwable t) {
                Log.w(TAG, "path " + target + "/" + confirmed + ": " + t);
            }
        }));
    }

    /** headphone_volume follows the music level while path 4 is routed and confirmed. */
    private static void follow(String why, boolean force) {
        Context app = sApp;
        if (app == null) return;
        AudioManager am = app.getSystemService(AudioManager.class);
        if (am == null) return;
        boolean wired = HbAudioRules.headphoneConfirmed(AudioPathReporter.targetPath(), AudioPathReporter.confirmedPath());
        if (!wired) {
            if (sFollowing) Log.i(TAG, "headphone_volume not followed (" + why + "), stays " + sSent);
            sFollowing = false;
            return;
        }
        boolean entering = !sFollowing;
        sFollowing = true;
        int v = HbAudioRules.headphoneVolume(true, musicLevel(am), sSent, force || entering);
        if (v < 0) return;
        send(am, v);
        Log.i(TAG, "headphone_volume=" + v + " (" + why + ")");
    }

    /**
     * After 59(4) (AudioPathReporter.halSink, posted to the main thread): the music level stored as headphone_volume,
     * then switch_end=4, so the HAL restores a real level on the jack.
     */
    static void selectSink(Context ctx, String why) {
        AudioManager am = ctx.getApplicationContext().getSystemService(AudioManager.class);
        if (am == null) return;
        int v = musicLevel(am);
        if (!send(am, v)) {
            Log.w(TAG, "3.5 mm sink not selected (" + why + "): headphone_volume not stored");
            return;
        }
        try {
            am.setParameters("xgimi_audiopath_switch_end=" + HbAudioRules.PATH_WIRED);
            Log.i(TAG, "3.5 mm sink selected, headphone_volume=" + v + " (" + why + ")");
        } catch (Throwable t) {
            Log.w(TAG, "3.5 mm sink: " + t);
        }
    }

    /** STREAM_MUSIC (of the routed device) scaled to 0..100, 0 while muted (the HdmiVolume scale). */
    private static int musicLevel(AudioManager am) {
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int v = am.isStreamMute(AudioManager.STREAM_MUSIC) ? 0
                : Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
        return Math.max(0, Math.min(100, v));
    }

    private static boolean send(AudioManager am, int v) {
        try {
            am.setParameters("headphone_volume=" + v);
            sSent = v;
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "headphone_volume=" + v + ": " + t);
            return false;
        }
    }
}
