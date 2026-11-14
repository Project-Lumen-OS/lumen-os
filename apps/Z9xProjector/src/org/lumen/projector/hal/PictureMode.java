// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/**
 * Picture modes of the Z9X (QS decisions "Picture mode"; picture.json 'other' values VERIFIED).
 * AI = "AI picture" (mode 10, FS 0.2); HDR_VIVID only while getHdrType()==6; COLOR_ACCURACY only
 * for SDR/HDR10 content; PERFORMANCE needs the stock heat warning first. Never 19/20 (Youku).
 *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum PictureMode {
    AI(10), STANDARD(1), SPORT(9), HDR_VIVID(26), COLOR_ACCURACY(29), OFFICE(25), PERFORMANCE(5);
    public final int wire;
    PictureMode(int v) { wire = v; }
    /** Maps a read-back value; 10..17 all show as AI (FS 3.2). null = unknown/other. */
    public static PictureMode fromWire(int v) {
        if (v >= 10 && v <= 17) return AI;
        for (PictureMode m : values()) if (m.wire == v) return m;
        return null;
    }
}
