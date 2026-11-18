package org.lumen.audio;

import android.content.Context;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceVolumeManager;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.VolumeInfo;
import android.util.Log;

/**
 * Tells AudioService which outputs the vendor HAL attenuates (ChipTable.owned): each listed AudioDeviceInfo type gets the
 * absolute volume behaviour, so AudioService keeps the mixer at full scale on it (VolumeStreamState
 * applyDeviceVolume_syncVSS: index max for an absolute volume device) while the index keeps moving with the keys. That
 * is the stock split: Android at 0 dB, the HAL follows the index (the model's followers send the keys, from the volume
 * broadcasts, StockVolume). AudioService applies that only at the next volume change of the device, so the policy index
 * of every stream on the device is set to the maximum here at once, as AudioService then would (otherwise the first
 * playback after boot stays attenuated twice until a volume key). The registration lives in system_server until
 * reboot; a restarted process registers again (AudioService keeps the first one, same behaviour). No VolumeInfo
 * index: the stored indexes stay as they are. Needs MODIFY_AUDIO_ROUTING and MODIFY_AUDIO_SETTINGS (the projector apps
 * run as the system uid). Log tag LumenDspVol.
 */
public final class DspOwned {
    private static final String TAG = "LumenDspVol";
    /**
     * The streams whose policy index goes to the maximum at once: every stream type 0..10 (voice call, system, ring,
     * music, alarm, notification, Bluetooth SCO, system enforced, DTMF, TTS, accessibility), as stock setDspVolumes does
     * for each stream on each device, so the UI clicks too play at 0 dB and only the chip's level attenuates them.
     */
    static final int[] STREAMS = {AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING, AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM, AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_BLUETOOTH_SCO, AudioManager.STREAM_SYSTEM_ENFORCED, AudioManager.STREAM_DTMF,
            AudioManager.STREAM_TTS, AudioManager.STREAM_ACCESSIBILITY};
    private static final AudioDeviceVolumeManager.OnAudioDeviceVolumeChangedListener NONE =
            new AudioDeviceVolumeManager.OnAudioDeviceVolumeChangedListener() {
                @Override public void onAudioDeviceVolumeChanged(AudioDeviceAttributes d, VolumeInfo v) {}
                @Override public void onAudioDeviceVolumeAdjusted(AudioDeviceAttributes d, VolumeInfo v, int dir, int mode) {}
            };
    private static boolean sDone;

    private DspOwned() {}

    /** Once per process. types: the model's chip-owned device types (ChipTable.owned). Any thread. */
    public static synchronized void apply(Context ctx, int[] types, String why) {
        if (sDone || types == null) return;
        sDone = true;
        Context app = ctx.getApplicationContext();
        AudioDeviceVolumeManager m;
        AudioManager am;
        try {
            m = new AudioDeviceVolumeManager(app);
            am = app.getSystemService(AudioManager.class);
        } catch (Throwable t) {
            Log.w(TAG, "volume manager: " + t);
            return;
        }
        VolumeInfo music = new VolumeInfo.Builder(AudioManager.STREAM_MUSIC).build();
        for (int type : types) {
            AudioDeviceAttributes dev = new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_OUTPUT, type, "");
            try {
                m.setDeviceAbsoluteVolumeBehavior(dev, music, Runnable::run, NONE, false);
            } catch (Throwable t) {
                Log.w(TAG, "device type " + type + ": " + t);
                continue;
            }
            StringBuilder s = new StringBuilder();
            for (int stream : STREAMS) {
                try {
                    if (am.isStreamMute(stream)) continue;          // a muted stream stays as AudioService left it
                    int max = am.getStreamMaxVolume(stream);
                    AudioSystem.setStreamVolumeIndexAS(stream, max, dev.getInternalType());
                    s.append(' ').append(stream).append('=').append(max);
                } catch (Throwable t) {
                    Log.w(TAG, "device type " + type + " stream " + stream + ": " + t);
                }
            }
            Log.i(TAG, "device type " + type + ": Android at full scale, the HAL applies the volume (" + why
                    + ", policy index" + s + ")");
        }
    }
}
