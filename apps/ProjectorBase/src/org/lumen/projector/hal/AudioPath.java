// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/**
 * Output path for 59 setAudioOutput (EN_XGIMI_AUDIO_PATH, stock libxgimiaudiopolicy
 * threadInit / setXgimiPathForDeviceDelay, stock analysis): 0 built-in speaker,
 * 2 HDMI ARC, 3 BT A2DP, 4 wired headset/headphones, 6 USB, 100 "switching" (Msrv mutes every
 * TV path and keeps the current path). Never 1 (SPDIF, no jack on Z9X) and never the wireless
 * speaker paths 7..13.
 *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum AudioPath {
    SPEAKER(0), ARC(2), BT_A2DP(3), WIRED(4), USB(6), SWITCHING(100);
    public final int wire;
    AudioPath(int v) { wire = v; }
}
