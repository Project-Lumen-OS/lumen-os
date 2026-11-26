// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OutputScan: the sound outputs of the shared output menu, read from the framework only (no vendor HAL), the same on
 * every Lumen projector. Connected outputs with their real names: the CEC OSD name of the audio system on HDMI ARC
 * (HdmiControlManager.getConnectedDevices, as the HDMI-CEC page reads it), the Bluetooth alias (BtA2dp.name) and the USB
 * product name; Bluetooth A2DP devices the stack has connected but not made active are listed too (BtA2dp.connected, as
 * the output choices of every model list them). Paired Bluetooth audio devices that are not connected are the offline
 * rows (a normal A2DP connect through {@link BtA2dp#connect}). Where media plays now: the media route
 * (getDevicesForAttributes) by what the listener hears (MediaPath.heard), never by list order. Any thread; never throws.
 */
public final class OutputScan {
    private static final String TAG = "LumenOutScan";
    private static final AudioAttributes MEDIA = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();
    /** Bluetooth A2DP sink (Assigned Numbers, Service Class 0x110B): the device plays audio the projector sends. */
    private static final ParcelUuid A2DP_SINK = ParcelUuid.fromString("0000110B-0000-1000-8000-00805F9B34FB");

    private OutputScan() {}

    public static OutputDevice.Scan read(Context ctx) {
        Context app = ctx.getApplicationContext();
        List<OutputDevice> connected = new ArrayList<>();
        Set<String> btSeen = new HashSet<>();
        String playKind = null, playAddr = null;
        AudioManager am = app.getSystemService(AudioManager.class);
        try {
            if (am != null) {
                for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    String k = kindOf(d.getType());
                    if (k == null || OutputDevice.SPEAKER.equals(k)) continue;
                    String addr = d.getAddress();
                    if (OutputDevice.BT.equals(k)) {
                        if (!btSeen.add(lower(addr))) continue;
                        String n = BtA2dp.name(addr);
                        connected.add(new OutputDevice(k, addr, n != null ? n : str(d.getProductName()), false, true));
                    } else {
                        String n = OutputDevice.USB.equals(k) ? str(d.getProductName()) : null;
                        connected.add(new OutputDevice(k, addr, n, d.getType() == AudioDeviceInfo.TYPE_HDMI_EARC, true));
                    }
                }
                List<Integer> paths = new ArrayList<>();
                List<AudioDeviceAttributes> route = am.getDevicesForAttributes(MEDIA);
                for (AudioDeviceAttributes a : route) paths.add(AutoOutput.pathOf(a.getType()));
                int heard = MediaPath.heard(paths);
                playKind = kindOfPath(heard);
                for (AudioDeviceAttributes a : route) {
                    if (AutoOutput.pathOf(a.getType()) == heard) { playAddr = a.getAddress(); break; }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "audio outputs: " + t);
        }
        for (String addr : BtA2dp.connected()) {               // connected, not active: AudioManager does not list it
            if (btSeen.add(lower(addr))) connected.add(new OutputDevice(OutputDevice.BT, addr, BtA2dp.name(addr), false, true));
        }
        return new OutputDevice.Scan(connected, offline(btSeen), playKind, playAddr, soundbarName(app));
    }

    /** Paired Bluetooth audio sinks that are not connected (Bluetooth on only: off, nothing can connect). */
    private static List<OutputDevice> offline(Set<String> connected) {
        List<OutputDevice> out = new ArrayList<>();
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || !a.isEnabled()) return out;
            for (BluetoothDevice d : a.getBondedDevices()) {
                if (d == null || connected.contains(lower(d.getAddress())) || !audioSink(d)) continue;
                String n = d.getAlias();
                out.add(new OutputDevice(OutputDevice.BT, d.getAddress(), n != null && !n.isEmpty() ? n : d.getName(), false, false));
            }
        } catch (Throwable t) {
            Log.w(TAG, "paired devices: " + t);
        }
        return out;
    }

    /** An A2DP sink by its service list; without one, by its class (audio / video, never a remote or keyboard). */
    private static boolean audioSink(BluetoothDevice d) {
        ParcelUuid[] u = d.getUuids();
        if (u != null && u.length > 0) {
            for (ParcelUuid x : u) if (A2DP_SINK.equals(x)) return true;
            return false;
        }
        BluetoothClass c = d.getBluetoothClass();
        return c != null && c.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
    }

    /** The OSD name of the CEC audio system (the soundbar), null when none is listed. */
    private static String soundbarName(Context app) {
        try {
            HdmiControlManager m = app.getSystemService(HdmiControlManager.class);
            List<HdmiDeviceInfo> list = m == null ? null : m.getConnectedDevices();
            if (list == null) return null;
            for (HdmiDeviceInfo d : list) {
                if (d != null && d.isCecDevice() && d.getDeviceType() == HdmiDeviceInfo.DEVICE_AUDIO_SYSTEM) return str(d.getDisplayName());
            }
        } catch (Throwable t) {
            Log.w(TAG, "CEC devices: " + t);
        }
        return null;
    }

    static String kindOf(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return OutputDevice.SPEAKER;
            case AudioDeviceInfo.TYPE_HDMI_ARC:
            case AudioDeviceInfo.TYPE_HDMI_EARC: return OutputDevice.ARC;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return OutputDevice.BT;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return OutputDevice.WIRED;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET: return OutputDevice.USB;
            default: return null;
        }
    }

    private static String kindOfPath(int path) {
        switch (path) {
            case MediaPath.SPEAKER: return OutputDevice.SPEAKER;
            case MediaPath.ARC: return OutputDevice.ARC;
            case MediaPath.BT: return OutputDevice.BT;
            case MediaPath.WIRED: return OutputDevice.WIRED;
            case MediaPath.USB: return OutputDevice.USB;
            default: return null;
        }
    }

    private static String str(CharSequence s) { return s == null ? null : s.toString(); }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT); }
}
