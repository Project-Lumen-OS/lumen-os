// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

/**
 * One output of a model's chip volume table (ChipTable): the AudioDeviceInfo type, the key the vendor audio HAL takes
 * that output's level with (stock XGIMI AudioService VolumeStreamState.setDspVolumesToHal), the HAL device id a key
 * names (A2DP on the Android 14 stocks), and the vendor path (MediaPath) the output plays on. Plain Java, host tested.
 */
public final class ChipKey {
    /** No HAL device id in the key. */
    public static final int NO_DEVICE = -1;

    public final int type;
    /** The HAL key ("spk_volume", ...); null for an output the chip keeps no level of (HDMI ARC / eARC). */
    public final String key;
    public final int device;
    /** MediaPath value, MediaPath.NONE for an output that is never a path (SPDIF). */
    public final int path;

    ChipKey(int type, String key, int device, int path) {
        this.type = type;
        this.key = key;
        this.device = device;
        this.path = path;
    }

    /** The audio HAL parameter of a level ("bluetooth_volume=15;device=128"). */
    public String param(int level) {
        return device == NO_DEVICE ? key + "=" + level : key + "=" + level + ";device=" + device;
    }

    /** The HAL level slot this row writes: the key and its device. Rows of one slot share one level. */
    public String slot() {
        return device == NO_DEVICE ? key : key + ";device=" + device;
    }

    public boolean hasKey() {
        return key != null;
    }
}
