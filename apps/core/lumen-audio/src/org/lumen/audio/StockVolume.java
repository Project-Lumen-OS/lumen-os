// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioDeviceVolumeManager;
import android.media.AudioManager;
import android.media.VolumeInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The stock volume split on every Lumen projector, one implementation for every model (data: the model's ChipTable).
 *
 * Stock XGIMI AudioService (Z9X V6.15.62 VolumeStreamState.setDspVolumes / setDspVolumesToHal, the same code in the HB
 * G0130, X50 G0111, RS30 G0155 and Titan H0062 services.jar, the Android 11 form on the Play 6) pins Android's index of
 * every stream on every device to the maximum, so the mixer and the offload stream gain stay at 0 dB, and sends the
 * music index of each device to the vendor DSP with that device's key (spk_volume, headphone_volume,
 * bluetooth_volume, usb_volume, xgimi_pcm_volume), on every index or mute change, at boot and after an audio server
 * restart. The user's volume is applied once, after the Dolby chain, for every source alike (Android PCM, compressed
 * offload, the HDMI input). Here:
 *  - every output with a key gets AudioService's absolute volume behaviour (DspOwned): Android at 0 dB on it for every
 *    stream, the UI clicks included, the index still moving with the keys;
 *  - each output's OWN music index (AudioDeviceVolumeManager.getDeviceVolume) goes to its key: at process start, at
 *    boot, after an audio server restart, on every music volume or mute change (unchanged slots skipped);
 *  - {@link #store} sends the levels of a path again right before its switch_end (PathSwitch: the HAL restores the
 *    stored level of the new sink, it starts at 0 after adev_open);
 *  - the fixed volume outputs of the table get AudioService's fixed behaviour (stock device list).
 * Needs MODIFY_AUDIO_ROUTING and MODIFY_AUDIO_SETTINGS (every projector app has both). Log tag LumenChipVol.
 */
public final class StockVolume {
    private static final String TAG = "LumenChipVol";
    static final String ASSET = "lumen_audio/chip_volume.tsv";
    static final String PROP_NO_ABSVOL = "persist.bluetooth.disableabsvol";
    static final String ACTION_VOLUME = "android.media.VOLUME_CHANGED_ACTION";
    static final String ACTION_MUTE = "android.media.STREAM_MUTE_CHANGED_ACTION";
    static final String EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    private static final long LOG_GAP_MS = 500;

    private static final ChipLevels LEVELS = new ChipLevels();
    private static final CopyOnWriteArrayList<Runnable> SERVER_UP = new CopyOnWriteArrayList<>();
    private static volatile ChipTable sTable;
    private static Context sApp;
    private static Handler sH;
    private static long sLastLogAt;
    private static int sUnlogged;
    private static final Runnable TRAILING = StockVolume::logTrailing;

    private StockVolume() {}

    /** The model's table (the app asset), read once; an empty table when the app has none (nothing owned). */
    public static ChipTable table(Context ctx) {
        ChipTable t = sTable;
        if (t != null) return t;
        synchronized (StockVolume.class) {
            if (sTable != null) return sTable;
            try (InputStream in = ctx.getApplicationContext().getAssets().open(ASSET)) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
                sTable = ChipTable.parse(new String(b.toByteArray(), StandardCharsets.UTF_8));
            } catch (Throwable e) {
                Log.e(TAG, "no chip volume table (" + ASSET + "): Android keeps every level: " + e);
                sTable = ChipTable.empty();
            }
            return sTable;
        }
    }

    /** Once per process (any thread): the split on, the levels sent, the volume and audio server listeners. */
    public static synchronized void install(Context ctx) {
        if (sApp != null) return;
        final Context app = ctx.getApplicationContext();
        sApp = app;
        ChipTable t = table(app);
        HandlerThread th = new HandlerThread("lumen-chipvol");
        th.start();
        sH = new Handler(th.getLooper());
        DspOwned.apply(app, t.owned(avrcpAbsolute()), "process start");
        fixedVolume(app, t);
        try {
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    String a = i == null ? null : i.getAction();
                    if (ACTION_VOLUME.equals(a) && i.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return;
                    sync("volume", false);
                }
            };
            IntentFilter f = new IntentFilter(ACTION_VOLUME);
            f.addAction(ACTION_MUTE);
            app.registerReceiver(r, f, null, sH, Context.RECEIVER_EXPORTED);
        } catch (Throwable e) {
            Log.w(TAG, "volume receiver: " + e);
        }
        try {
            AudioManager am = app.getSystemService(AudioManager.class);
            am.setAudioServerStateCallback(sH::post, new AudioManager.AudioServerStateCallback() {
                @Override public void onAudioServerDown() {
                    LEVELS.forget();
                }

                @Override public void onAudioServerUp() {
                    sync("audio server up", true);
                    for (Runnable l : SERVER_UP) {
                        try { l.run(); } catch (Throwable e) { Log.w(TAG, "audio server listener: " + e); }
                    }
                }
            });
        } catch (Throwable e) {
            Log.w(TAG, "audio server callback: " + e);
        }
        sH.post(() -> sync("process start", true));
    }

    /** BOOT_COMPLETED: every level again (the HAL may have been reopened since the process started). */
    public static void onBootCompleted(Context ctx) {
        install(ctx);
        sH.post(() -> sync("boot completed", true));
    }

    /** {@code r} after the audio server came back, once the levels are in the HAL again (PathSwitch: the sink again). */
    public static void onAudioServerUp(Runnable r) {
        if (r != null) SERVER_UP.addIfAbsent(r);
    }

    /** AVRCP absolute volume allowed (stock: no, every vendor build.prop and the Lumen images set the property true). */
    public static boolean avrcpAbsolute() {
        try {
            return !"true".equals(SystemProperties.get(PROP_NO_ABSVOL, "false"));
        } catch (Throwable t) {
            return true;
        }
    }

    /** Every slot of the table gets its output's level; an unchanged slot only with force. Any thread. */
    public static void sync(String why, boolean force) {
        ChipTable t = sTable;
        Context app = sApp;
        if (t == null || app == null) return;
        send(app, t.slots(), why, force);
    }

    /**
     * The levels of the outputs of {@code path} again (before switch_end=path: the HAL restores the stored level of its
     * new sink). Any thread. Returns the parameters sent, for the caller's log line.
     */
    public static String store(int path, String why) {
        ChipTable t = sTable;
        Context app = sApp;
        if (t == null || app == null) return "";
        List<List<ChipKey>> slots = new ArrayList<>();
        for (List<ChipKey> slot : t.slots()) {
            for (ChipKey r : slot) {
                if (r.path == path) {
                    slots.add(slot);
                    break;
                }
            }
        }
        return send(app, slots, why, true);
    }

    /** One line for diagnostics: what each slot got last. */
    public static String describe() {
        ChipTable t = sTable;
        if (t == null) return "chip volume not installed";
        StringBuilder s = new StringBuilder("chip volume");
        for (List<ChipKey> slot : t.slots()) {
            ChipKey r = slot.get(0);
            s.append(' ').append(r.slot()).append('=').append(LEVELS.last(r));
        }
        return s.toString();
    }

    private static synchronized String send(Context app, List<List<ChipKey>> slots, String why, boolean force) {
        AudioManager am = app.getSystemService(AudioManager.class);
        if (am == null) return "";
        AudioDeviceVolumeManager dvm;
        try {
            dvm = new AudioDeviceVolumeManager(app);
        } catch (Throwable e) {
            dvm = null;
        }
        List<Integer> connected = connectedTypes(am);
        boolean abs = avrcpAbsolute();
        StringBuilder sent = new StringBuilder();
        for (List<ChipKey> slot : slots) {
            ChipKey r = ChipLevels.pick(slot, connected);
            int level = level(am, dvm, r, abs);
            String kv = LEVELS.due(r, level, force);
            if (kv == null) continue;
            try {
                am.setParameters(kv);
                LEVELS.sent(r, level);
                sent.append(' ').append(kv);
            } catch (Throwable e) {
                Log.w(TAG, kv + ": " + e);
            }
        }
        if (sent.length() > 0) log(sent + " (" + why + ")", force);
        return sent.toString();
    }

    /** The output's own music level (its device index, the stream mute), -1 when unreadable. */
    private static int level(AudioManager am, AudioDeviceVolumeManager dvm, ChipKey r, boolean abs) {
        try {
            if (dvm != null) {
                VolumeInfo vi = dvm.getDeviceVolume(new VolumeInfo.Builder(AudioManager.STREAM_MUSIC).build(),
                        new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_OUTPUT, r.type, ""));
                return ChipLevels.levelOf(r, vi.getVolumeIndex(), vi.getMaxVolumeIndex(), vi.isMuted(), abs);
            }
        } catch (Throwable e) {
            Log.w(TAG, "device volume of type " + r.type + ": " + e);
        }
        try {
            // fallback: the routed device's index (the volume the keys move)
            return ChipLevels.levelOf(r, am.getStreamVolume(AudioManager.STREAM_MUSIC),
                    am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), am.isStreamMute(AudioManager.STREAM_MUSIC), abs);
        } catch (Throwable e) {
            return -1;
        }
    }

    private static List<Integer> connectedTypes(AudioManager am) {
        List<Integer> out = new ArrayList<>();
        try {
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) out.add(d.getType());
        } catch (Throwable e) {
            Log.w(TAG, "outputs: " + e);
        }
        return out;
    }

    /** The table's fixed volume outputs (stock mFixedVolumeDevices) get AudioService's fixed behaviour. */
    private static void fixedVolume(Context app, ChipTable t) {
        AudioManager am = app.getSystemService(AudioManager.class);
        if (am == null) return;
        for (int type : t.fixed()) {
            try {
                AudioDeviceAttributes d = new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_OUTPUT, type, "");
                if (am.getDeviceVolumeBehavior(d) == AudioManager.DEVICE_VOLUME_BEHAVIOR_FIXED) continue;
                am.setDeviceVolumeBehavior(d, AudioManager.DEVICE_VOLUME_BEHAVIOR_FIXED);
                Log.i(TAG, "device type " + type + ": fixed volume (stock device list)");
            } catch (Throwable e) {
                Log.w(TAG, "fixed volume type " + type + ": " + e);
            }
        }
    }

    /** One line per change, at most one every LOG_GAP_MS while a volume key repeats (the last one when it settles). */
    private static void log(String line, boolean always) {
        long now = SystemClock.uptimeMillis();
        Handler h = sH;
        if (always || now - sLastLogAt >= LOG_GAP_MS || h == null) {
            sLastLogAt = now;
            sUnlogged = 0;
            Log.i(TAG, line.trim());
            return;
        }
        sUnlogged++;
        h.removeCallbacks(TRAILING);
        h.postDelayed(TRAILING, LOG_GAP_MS);
    }

    private static synchronized void logTrailing() {
        if (sUnlogged == 0) return;
        sLastLogAt = SystemClock.uptimeMillis();
        Log.i(TAG, describe() + " (volume, " + sUnlogged + " more step" + (sUnlogged == 1 ? "" : "s") + ")");
        sUnlogged = 0;
    }
}
