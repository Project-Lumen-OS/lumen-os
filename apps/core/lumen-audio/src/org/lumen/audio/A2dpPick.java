// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

import java.util.List;

/**
 * Which Bluetooth device a "Bluetooth" output choice plays on, and whether the Bluetooth stack must make it active first.
 *
 * Why (HB rig 2026-11-14, Slava): the choice stored the address of a soundbar that was unpaired later while another A2DP
 * device was connected, and AOSP lists an A2DP output in AudioManager only while the stack has it as the ACTIVE device.
 * A wired output (the HB 3.5 mm row connects one) makes the stack drop the active A2DP device (ActiveDeviceManager,
 * 18:42:30 "setActiveDevice: device=null") while the device stays connected, so "Bluetooth" was refused ("not offered
 * now") or fell back to Auto ("BT not connected") and the sound stayed on ARC.
 *
 * Rule: the stored address while that device is connected, else the first connected one; a connected device that is not
 * active is made active before the route is set. Plain Java, host tested (test/run.sh). Used by every output choice
 * (Z9X / HB / Play 6 AudioOutputChoice, X50 AudioOutput) through BtA2dp.
 */
public final class A2dpPick {
    private A2dpPick() {}

    /** The device of a Bluetooth choice, null when no A2DP device is connected. */
    public static String device(String stored, List<String> connected) {
        if (connected == null || connected.isEmpty()) return null;
        if (stored != null && !stored.isEmpty()) {
            for (String a : connected) if (stored.equalsIgnoreCase(a)) return a;
        }
        return connected.get(0);
    }

    /** The picked device is connected but not among the active ones: make it active first. */
    public static boolean activate(String pick, List<String> active) {
        if (pick == null || pick.isEmpty()) return false;
        if (active != null) {
            for (String a : active) if (pick.equalsIgnoreCase(a)) return false;
        }
        return true;
    }
}
