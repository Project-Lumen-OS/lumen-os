// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoOutput: the device the output choice "Auto" plays on, the stock rule of AutoPick over the outputs AudioManager
 * lists. Each projector's output choice reports device changes and manual choices here and makes the returned device
 * the preferred media device in Auto, as for a chosen output; the per-model part is only whether the 3.5 mm jack is
 * sensed (the HB kernel has no jack detection: its wired output is connected by the app for good and is never an Auto
 * candidate, as on stock, where it never connects).
 *
 * One state per process (the projector app is persistent): a restart forgets lastAccessDevice and Auto takes the
 * fallback order, as the stock audio server after its own restart. Any thread. Log tag LumenAutoOut.
 */
public final class AutoOutput {
    private static final String TAG = "LumenAutoOut";
    private static final AutoPick PICK = new AutoPick();

    private AutoOutput() {}

    /** AudioDeviceCallback.onAudioDevicesAdded. auto: the stored choice is Auto. jackSensed: false on the HB. */
    public static void added(AudioDeviceInfo[] devices, boolean auto, boolean jackSensed) {
        if (devices == null) return;
        for (AudioDeviceInfo d : devices) {
            int p = path(d.getType(), jackSensed);
            if (p == MediaPath.NONE) continue;
            PICK.added(p, auto);
            if (auto) Log.i(TAG, "connected in Auto: path " + p + " (type " + d.getType() + ")");
        }
    }

    /** AudioDeviceCallback.onAudioDevicesRemoved. */
    public static void removed(AudioDeviceInfo[] devices) {
        if (devices == null) return;
        for (AudioDeviceInfo d : devices) PICK.removed(pathOf(d.getType()));
    }

    /** The user chose an output: an AudioOutputKind name (SoundbarMode names), Auto ignored. */
    public static void chose(String kind) {
        PICK.chose(pathOfKind(kind));
    }

    /** The output Auto plays on now, null when AudioManager lists none of the candidates. */
    public static AudioDeviceInfo device(AudioManager am, boolean jackSensed) {
        if (am == null) return null;
        AudioDeviceInfo[] all = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        List<Integer> paths = new ArrayList<>();
        for (AudioDeviceInfo d : all) paths.add(path(d.getType(), jackSensed));
        int p = PICK.pick(paths);
        if (p == MediaPath.NONE) return null;
        AudioDeviceInfo first = null;
        for (AudioDeviceInfo d : all) {
            if (pathOf(d.getType()) != p) continue;
            if (d.getType() == AudioDeviceInfo.TYPE_HDMI_EARC) return d;             // eARC before ARC
            if (first == null) first = d;
        }
        return first;
    }

    /** The path of a type as an Auto candidate: the wired output only where the jack is sensed. */
    static int path(int type, boolean jackSensed) {
        int p = pathOf(type);
        return p == MediaPath.WIRED && !jackSensed ? MediaPath.NONE : p;
    }

    /** AudioDeviceInfo type to MediaPath (the stock threadInit table), NONE for a type that is no media path. */
    public static int pathOf(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return MediaPath.BT;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return MediaPath.WIRED;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET: return MediaPath.USB;
            case AudioDeviceInfo.TYPE_HDMI_ARC:
            case AudioDeviceInfo.TYPE_HDMI_EARC: return MediaPath.ARC;
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return MediaPath.SPEAKER;
            default: return MediaPath.NONE;
        }
    }

    /** An output choice name (SoundbarMode SPEAKER, ARC, BT, WIRED, USB) to its path, NONE for Auto or unknown. */
    public static int pathOfKind(String kind) {
        if (SoundbarMode.SPEAKER.equals(kind)) return MediaPath.SPEAKER;
        if (SoundbarMode.SOUNDBAR.equals(kind)) return MediaPath.ARC;
        if (SoundbarMode.BT.equals(kind)) return MediaPath.BT;
        if (SoundbarMode.WIRED.equals(kind)) return MediaPath.WIRED;
        if (SoundbarMode.USB.equals(kind)) return MediaPath.USB;
        return MediaPath.NONE;
    }
}
