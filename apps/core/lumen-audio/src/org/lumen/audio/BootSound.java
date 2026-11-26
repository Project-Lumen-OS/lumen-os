// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Lumen's power-on sound on every projector, one implementation (decisions: {@link BootSoundRules}). The sound is
 * assets/lumen_audio/poweron.wav, own work made by tools/brand/boot_sound (owner pick B, -18 LUFS, peak -6.9 dBTP).
 *  - Cold boot: the AOSP boot animation plays the same bytes as part0/audio.wav of the shared bootanimation.zip
 *    while persist.sys.bootanim.play_sound is not 0. That is a system_prop only system uid apps may set (the Z9X and
 *    Play 6 apps are platform_app), so every app writes {@link #PROP_GATE} and the image's lumen_boot_sound.rc copies
 *    it there. The gate is 0 while the music is muted or at 0, written at process start, on a switch change and at
 *    every screen off, so the next power on follows the volume the projector was left at.
 *  - Wake from standby: stock plays its music on every wake too (XgimiBootMusic on the STR path). Here the
 *    persistent projector process plays the bytes on the music stream once the vendor path check after SCREEN_ON is
 *    done (PathSwitch, 3 s), so it sounds at the user's music level on the current output (StockVolume puts that level
 *    on the chip), never while muted or at 0 and never over an app that already plays.
 *  - The switch {@link #PROP_SWITCH} is the menu row power.on_music (lumen-menu ModuleScreens), unset = on.
 * XGIMI's own music lives in its /system (libbootanimation XgimiBootMusic, bootpoweron.wav), which Lumen replaces; the
 * adapters with the vendor switch (Z9X and Play 6 IGmpf 419, HB SystemManager 5) still turn it off through
 * {@link #adopt}, so a stock system on the other half never plays it next to ours either.
 * Needs nothing beyond the projector apps' permissions. Log tag LumenBootSound.
 */
public final class BootSound {
    private static final String TAG = "LumenBootSound";
    static final String PROP_SWITCH = "persist.lumen.boot_sound";
    static final String PROP_GATE = "persist.lumen.boot_sound.play";
    static final String ASSET = "lumen_audio/poweron.wav";
    private static final long WAKE_DELAY_MS = 3_500L, RELEASE_MARGIN_MS = 1_000L;
    private static Context sApp;
    private static Handler sH;
    private static final Runnable WAKE = BootSound::wake;
    private static long sLast = Long.MIN_VALUE / 2;

    private BootSound() {}

    /** The switch. Any thread. */
    public static boolean on() {
        return BootSoundRules.on(SystemProperties.get(PROP_SWITCH, ""));
    }

    /** Writes the switch and the boot animation gate. Any thread. */
    public static void set(Context c, boolean on) {
        SystemProperties.set(PROP_SWITCH, on ? BootSoundRules.ON : BootSoundRules.OFF);
        sync(c);
    }

    /**
     * The unit's stock switch ({@code vendor}, null = no answer) becomes the first value while Lumen stored none.
     * True when the vendor music is on and the caller turns it off.
     */
    public static boolean adopt(Context c, Boolean vendor) {
        String first = BootSoundRules.adopt(SystemProperties.get(PROP_SWITCH, ""), vendor);
        if (first != null) {
            Log.i(TAG, "first value from the stock switch: " + first);
            SystemProperties.set(PROP_SWITCH, first);
            sync(c);
        }
        return Boolean.TRUE.equals(vendor);
    }

    /** The boot animation gate from the switch and the music level now. Any thread, never throws. */
    public static void sync(Context c) {
        try {
            AudioManager am = c.getSystemService(AudioManager.class);
            String want = BootSoundRules.gate(on(), am.isStreamMute(AudioManager.STREAM_MUSIC),
                    am.getStreamVolume(AudioManager.STREAM_MUSIC));
            if (!want.equals(SystemProperties.get(PROP_GATE, ""))) {
                SystemProperties.set(PROP_GATE, want);
                Log.i(TAG, "boot animation sound " + want);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "gate: " + e);
        }
    }

    /** Application.onCreate (through PathSwitch.install): screen on and off, the gate now. Idempotent, never throws. */
    public static synchronized void install(Context ctx) {
        if (sApp != null) return;
        try {
            sApp = ctx.getApplicationContext();
            HandlerThread t = new HandlerThread("lumen-bootsound");
            t.start();
            sH = new Handler(t.getLooper());
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            sApp.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    sH.removeCallbacks(WAKE);
                    if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) sH.postDelayed(WAKE, WAKE_DELAY_MS);
                    else sync(sApp);
                }
            }, f, null, sH, Context.RECEIVER_NOT_EXPORTED);
            sH.post(() -> sync(sApp));
        } catch (Throwable e) {
            Log.e(TAG, "install", e);
        }
    }

    private static void wake() {
        try {
            AudioManager am = sApp.getSystemService(AudioManager.class);
            long now = SystemClock.elapsedRealtime();
            boolean go = BootSoundRules.playOnWake(on(), "1".equals(SystemProperties.get("sys.boot_completed")),
                    now, now - sLast, am.isStreamMute(AudioManager.STREAM_MUSIC),
                    am.getStreamVolume(AudioManager.STREAM_MUSIC), am.isMusicActive());
            if (!go) return;
            sLast = now;
            play(sApp);
        } catch (Throwable e) {
            Log.w(TAG, "wake: " + e);
        }
    }

    private static void play(Context c) throws Exception {
        byte[] w;
        try (InputStream in = c.getAssets().open(ASSET); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[64 * 1024];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            w = out.toByteArray();
        }
        int[] p = BootSoundRules.pcm(w);
        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(p[2]).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(p[1]).build();
        t.write(w, p[0], p[1]);
        t.play();
        Log.i(TAG, "wake: played " + p[1] + " bytes");
        sH.postDelayed(t::release, p[1] * 1000L / (p[2] * p[3] * 2) + RELEASE_MARGIN_MS);
    }
}
