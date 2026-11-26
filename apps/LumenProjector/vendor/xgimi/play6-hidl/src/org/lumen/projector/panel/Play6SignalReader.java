// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.projector.Hal;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Gmpf2;
import org.lumen.projector.source.HdmiSource;
import org.lumen.signal.SignalFormat;
import org.lumen.signal.SignalItem;
import org.lumen.signal.SignalReader;
import org.lumen.signal.SignalValues;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The signal panel's reader (apps/core/lumen-signal) for the Play 6 over its own gmpf clients, the getters its menu
 * already calls: IGmpf 662 getHdmiInfo, 620 getHdrType, 634 getHdmiRange, 661 getEdidVersion, 438 temperature,
 * IGmpf2 212 ALLM, 666 picture mode, 668 MEMC. The
 * Play 6 has one HDMI input. Its picture on the chip (output timing) has no checked getter, so no judder line here.
 */
public final class Play6SignalReader implements SignalReader {
    private static final String TAG = "LumenSignal";
    private static final Set<SignalItem> ITEMS = Collections.unmodifiableSet(EnumSet.of(SignalItem.HDMI_INFO,
            SignalItem.HDR, SignalItem.RANGE, SignalItem.ASPECT, SignalItem.EDID, SignalItem.ALLM, SignalItem.TEMP_SYSTEM,
            SignalItem.PICTURE_MODE, SignalItem.MEMC));
    /** getHdmiInfo: {width, height, interlace 0/1, HDMI mode 0/1, frame rate 1/100 Hz} */
    private static final int WIDTH = 0, HEIGHT = 1, INTERLACE = 2, HDMI = 3, RATE = 4, FIELDS = 5;
    private static final int ONLY_PORT = 1;

    /** the last picture mode read (666): MEMC is shown only in the modes the menu shows it in, no extra call */
    private static volatile Integer sPictureMode;

    @Override public Set<SignalItem> items() { return ITEMS; }

    @Override public int hdmiPort() { return HdmiSource.shown() ? ONLY_PORT : 0; }

    @Override public void read(List<SignalItem> items, int port, Consumer<SignalValues> cb) {
        Hal.query((g, g2) -> readOnHal(g, g2, items), cb::accept);
    }

    static SignalValues readOnHal(Play6Gmpf g, Play6Gmpf2 g2, List<SignalItem> items) {
        SignalValues v = new SignalValues();
        for (SignalItem i : items) {
            try {
                switch (i) {
                    case HDMI_INFO: {
                        int[] s = g.getHdmiInfo();
                        if (s != null && s.length >= FIELDS && SignalFormat.plausible(s[WIDTH], s[HEIGHT], s[RATE])) {
                            v.width = s[WIDTH];
                            v.height = s[HEIGHT];
                            v.rateCenti = s[RATE];
                            v.interlaced = s[INTERLACE] == 1;
                            v.dvi = s[HDMI] == 0;
                        }
                        break;
                    }
                    case HDR: v.hdrType = g.getHdrType(); break;
                    case RANGE: v.range = SignalFormat.rangeToken(g.getHdmiRange()); break;
                    case EDID: v.edid = SignalFormat.edidToken(g.getEdidVersion()); break;
                    case ASPECT: v.aspectSetting = SignalFormat.aspectSettingToken(g.getAspectRatioRaw()); break;
                    case ALLM: v.allm = g2.getAllmStatus(); break;
                    case TEMP_SYSTEM: v.tempSystem = SignalFormat.temp(g.getSystemTemperature()); break;
                    // the menu's own reads and rules (Play6MenuGates.state): Game shows no mode, AI scenes as AI,
                    // MEMC only in Movie, TV and Custom
                    case PICTURE_MODE: {
                        int raw = g.getPictureModeRaw();
                        sPictureMode = raw;
                        v.pictureMode = Play6MenuGates.gamePicture(raw) ? null
                                : Play6MenuGates.PICTURE_MODE.token(Play6MenuGates.shownPictureMode(raw));
                        break;
                    }
                    case MEMC: if (Play6MenuGates.memcMode(sPictureMode)) v.memc = Play6MenuGates.MEMC.token(g.getMemcLevel()); break;
                    default: break;
                }
            } catch (Throwable t) {
                Log.d(TAG, i + ": " + t);
            }
        }
        return v;
    }
}
