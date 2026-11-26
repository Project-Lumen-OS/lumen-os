// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

/**
 * JackOutput: a 3.5 mm jack the kernel does not sense (Z9X HB: no h2w switch, config_useDevInputEventForAudioJack
 * false), offered as the "Headphones" output like stock. Stock never connects such a jack: the choice is only
 * setForceUse(FOR_MEDIA, HEADPHONES) in its patched audio policy. Lumen's AOSP policy routes only to a connected device,
 * so the app connects the wired headphone output ONCE (AudioManager.setWiredDeviceConnectionState, what
 * WiredAccessoryManager sends for a real plug) and never removes it: a removal broadcasts ACTION_HEADSET_PLUG state 0
 * and AUDIO_BECOMING_NOISY, and players pause (HB rig 2026-11-15: Kodi paused when the user left Headphones, up to
 * beta15). While another output is chosen the jack is a non-default media device (AOSP DEVICE_ROLE_DISABLED, the role
 * SpdifOff uses): AOSP never routes to it on its own, a preferred device (the chosen jack) still wins, and the Auto rule
 * never counts it (AutoOutput jackSensed false). The role is set before the first connection and, on later evaluations,
 * after the preferred device (settle).
 *
 * The Bluetooth stack drops the active A2DP device when a wired output is added (AOSP ActiveDeviceManager, HB rig
 * 2026-11-14 18:42:30), so the A2DP device active before the connection is made active again on the next evaluation
 * within RESTORE_MS. The connection and the role live in AudioService memory: after a reboot the first evaluation sets
 * both again. Caller's thread (the output choice thread). Log tag LumenJack.
 */
public final class JackOutput {
    private static final String TAG = "LumenJack";
    static final long RESTORE_MS = 10_000L;
    private static final int[] TYPES = {AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET};
    private static String sRestoreBt;
    private static long sRestoreUntil;

    private JackOutput() {}

    /**
     * Start of an evaluation: the connection when it is missing, the role set first (out of the default route unless
     * chosen, so AOSP never routes to it on its own). Returns true when it connected the output now ("devices added"
     * evaluates again with it listed).
     */
    public static synchronized boolean keep(AudioManager am, AudioProductStrategy media, boolean chosen, String why) {
        if (am == null || media == null) return false;
        try {
            restoreBt(why);
            if (listed(am)) return false;
            aside(am, media, !chosen, why);
            List<String> active = BtA2dp.active();
            am.setWiredDeviceConnectionState(jack(), 1);
            Log.i(TAG, why + ": 3.5 mm output connected for good" + (chosen ? " (Headphones chosen)" : ", out of the default route"));
            if (!active.isEmpty()) {
                sRestoreBt = active.get(0);
                sRestoreUntil = SystemClock.uptimeMillis() + RESTORE_MS;
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, why + ": " + t);
            return false;
        }
    }

    /**
     * End of an evaluation, after the preferred media device is set: the role for the choice. Leaving Headphones the
     * new device is preferred first, so taking the jack out of the route never leaves a moment without a preferred one.
     */
    public static synchronized void settle(AudioManager am, AudioProductStrategy media, boolean chosen, String why) {
        if (am == null || media == null) return;
        try {
            if (listed(am)) aside(am, media, !chosen, why);
        } catch (Throwable t) {
            Log.w(TAG, why + ": " + t);
        }
    }

    /** A wired headphone / headset output is listed now. */
    public static boolean listed(AudioManager am) {
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            for (int t : TYPES) if (d.getType() == t) return true;
        }
        return false;
    }

    private static void aside(AudioManager am, AudioProductStrategy media, boolean aside, String why) {
        List<AudioDeviceAttributes> cur = am.getNonDefaultDevicesForStrategy(media);
        boolean now = false;
        if (cur != null) for (AudioDeviceAttributes a : cur) if (a.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) now = true;
        if (now == aside) return;
        boolean ok = aside ? am.setDeviceAsNonDefaultForStrategy(media, jack()) : am.removeDeviceAsNonDefaultForStrategy(media, jack());
        Log.i(TAG, why + ": 3.5 mm output " + (aside ? "out of the default media route" : "back in the media route (chosen)")
                + (ok ? "" : " REFUSED"));
    }

    private static void restoreBt(String why) {
        String addr = sRestoreBt;
        if (addr == null) return;
        if (SystemClock.uptimeMillis() > sRestoreUntil) {
            sRestoreBt = null;
            return;
        }
        if (!BtA2dp.connected().contains(addr) || BtA2dp.active().contains(addr)) return;
        sRestoreBt = null;
        BtA2dp.activateIfNeeded(addr, why + " (dropped by the 3.5 mm connection)");
    }

    private static AudioDeviceAttributes jack() {
        return new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "");
    }
}
