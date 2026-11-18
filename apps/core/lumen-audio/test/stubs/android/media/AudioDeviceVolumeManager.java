package android.media;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Host stub: records every absolute volume registration in order. */
public class AudioDeviceVolumeManager {
    public static final List<String> CALLS = new ArrayList<>();

    public interface OnAudioDeviceVolumeChangedListener {
        void onAudioDeviceVolumeChanged(AudioDeviceAttributes d, VolumeInfo v);
        void onAudioDeviceVolumeAdjusted(AudioDeviceAttributes d, VolumeInfo v, int dir, int mode);
    }

    public AudioDeviceVolumeManager(Context c) {}

    public void setDeviceAbsoluteVolumeBehavior(AudioDeviceAttributes d, VolumeInfo v, Executor e,
            OnAudioDeviceVolumeChangedListener l, boolean handlesVolumeAdjustment) {
        CALLS.add("role " + d.role + " type " + d.type + " stream " + v.stream + " index " + v.indexSet
                + " adjust " + handlesVolumeAdjustment + " listener " + (l != null) + " executor " + (e != null));
    }
}
