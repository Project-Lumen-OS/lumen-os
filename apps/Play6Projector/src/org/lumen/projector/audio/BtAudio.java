package org.lumen.projector.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import org.lumen.audio.PathSwitch;
import org.lumen.audio.VendorAudio;
import org.lumen.audio.VendorCall;
import org.lumen.audio.VendorOutput;
import org.lumen.projector.Hal;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Values;

import java.lang.reflect.Method;

/**
 * The Play 6 side of the shared vendor audio path switch and volume split (apps/core/lumen-audio PathSwitch,
 * StockVolume; the model data is devices/dragon_healthy/audio/chip_volume.tsv: the Android 11 stock keys, every level
 * again after switch_end because the mt5889 HAL keeps no level per sink). Only how this model reaches its vendor
 * (IGmpf 59 / 60 on p6-hal) and one Play 6 fix:
 *
 * Route (Lumen 1.0.5, tester bug on play6-beta1: Bluetooth headphones connect, their keys work, the sound stays on the
 * speaker). On t10 the media force-use was FORCE_NO_BT_A2DP (10): AudioService set it with setBluetoothA2dpOn(false)
 * when a phantom wired headset (h2w) connected, and Android 14 then keeps media on the speaker even with A2DP up. When
 * an A2DP sink appears and the force-use is still 10, AudioService itself is asked to turn it back
 * (IAudioService.setBluetoothA2dpOn(true), MODIFY_AUDIO_SETTINGS), so its own state stays consistent. Log tag P6BtAudio.
 */
public final class BtAudio implements VendorAudio {
    private static final String TAG = "P6BtAudio";
    private static final int FOR_MEDIA = 1;
    private static final int FORCE_NO_BT_A2DP = 10;
    private static final BtAudio VENDOR = new BtAudio();

    private BtAudio() {}

    /** Play6App: the shared path switch and levels with this vendor, SCREEN_ON checks the path again. No HAL call here. */
    public static void install(Context ctx) {
        PathSwitch.install(ctx, VENDOR);
        try {
            ctx.getApplicationContext().registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    PathSwitch.onScreenOn();
                }
            }, new IntentFilter(Intent.ACTION_SCREEN_ON));
        } catch (Throwable e) {
            Log.w(TAG, "screen-on receiver: " + e);
        }
    }

    @Override public boolean post(String what, VendorCall call, Runnable onSkipped) {
        return Hal.runDelayed((g, g2) -> call.run(new Output(g)), 0, onSkipped);
    }

    @Override public boolean ready() {
        return Hal.isReady();
    }

    @Override public void onConnected(Runnable r) {
        Hal.addConnectedListener(r);
    }

    @Override public void registerDevices(Context app, AudioDeviceCallback cb, Handler h) {
        AudioManager am = app.getSystemService(AudioManager.class);
        if (am == null) return;
        am.registerAudioDeviceCallback(cb, h);
        am.registerAudioDeviceCallback(new AudioDeviceCallback() {
            @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                for (AudioDeviceInfo d : added) {
                    if (d.isSink() && d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                        route();
                        return;
                    }
                }
            }
        }, h);
    }

    /** The Play 6 output choice has no digital format row (digital_output_format is not checked on the mt5889). */
    @Override public void formatAgain(String why) {}

    /** IGmpf 59 / 60 of one p6-hal call. */
    private static final class Output implements VendorOutput {
        private final Play6Gmpf g;

        Output(Play6Gmpf g) { this.g = g; }

        @Override public void select(int path) throws Exception { g.setAudioOutput(Play6Values.audioPath(path)); }

        @Override public int read() throws Exception { return g.getAudioOutputRaw(); }
    }

    /** A2DP sink up but media still forced off Bluetooth: ask AudioService to allow A2DP again. */
    private static void route() {
        try {
            Class<?> as = Class.forName("android.media.AudioSystem");
            int force = (int) as.getMethod("getForceUse", int.class).invoke(null, FOR_MEDIA);
            if (force != FORCE_NO_BT_A2DP) {
                Log.i(TAG, "media force-use " + force + ": A2DP allowed, nothing to do");
                return;
            }
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, Context.AUDIO_SERVICE);
            Class<?> stub = Class.forName("android.media.IAudioService$Stub");
            Object svc = stub.getMethod("asInterface", IBinder.class).invoke(null, b);
            Method m = svc.getClass().getMethod("setBluetoothA2dpOn", boolean.class);
            m.invoke(svc, true);
            int now = (int) as.getMethod("getForceUse", int.class).invoke(null, FOR_MEDIA);
            Log.i(TAG, "media force-use was FORCE_NO_BT_A2DP (a phantom wired headset?): A2DP turned back on, now " + now);
        } catch (Throwable e) {
            Log.w(TAG, "route: " + e);
        }
    }
}
