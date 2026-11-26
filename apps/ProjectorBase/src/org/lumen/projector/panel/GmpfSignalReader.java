// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.projector.Hal;
import org.lumen.projector.game.GameProfile;
import org.lumen.projector.hal.Gmpf2Base;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.HdmiSignal;
import org.lumen.projector.hal.OutputTimingInfo;
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
 * The signal panel's reader (apps/core/lumen-signal) over this app's own gmpf clients: Z9X HIDL IGmpf 662 / 620 / 157 /
 * 634 / 661 / 438 / 439 (LED sensor), IGmpf2 310 / 300 / 271 / 279 / 55 / 269, and on the HB the same client methods
 * over its AIDL codes (TV 38 / 78, DM 57, TV 64 / 39 / 131 / 146 / 173 / 163 / 153 / 192 / 175, DM 148, TEMPERATURE 6),
 * and the menu's own picture mode and MEMC reads (Z9X IGmpf 666 / 668, HB TV 34 / 32) named by {@link SignalMenuTokens}.
 * A call a model's code table lacks throws before anything is sent, so its row stays hidden. Never IGmpf 194, 275 or
 * IGmpf2 252. Which of these a model shows is its devices/&lt;c&gt;/signal.tsv.
 */
public final class GmpfSignalReader implements SignalReader {
    private static final String TAG = "LumenSignal";
    private static final Set<SignalItem> ITEMS = Collections.unmodifiableSet(EnumSet.of(SignalItem.HDMI_INFO,
            SignalItem.HDR, SignalItem.CHIP, SignalItem.RANGE, SignalItem.ASPECT, SignalItem.EDID, SignalItem.ALLM, SignalItem.VRR,
            SignalItem.FREESYNC, SignalItem.IMAX, SignalItem.FILMMAKER, SignalItem.PORT_HW, SignalItem.DV_MODE,
            SignalItem.GAME, SignalItem.HGIG, SignalItem.TONE_MAP, SignalItem.TEMP_LIGHT, SignalItem.TEMP_SYSTEM,
            SignalItem.PICTURE_MODE, SignalItem.MEMC));
    private static final int CENTI = 100;
    /** Z9X 439 sensor 5: the LED board (LumenSensors Z9xHalSource folds 4 and 8 into it). */
    private static final int LED_SENSOR = 5;
    /** getHdmiHwConfig fields: hwPortId, cecEnable, arcEnable, physicalAddr. */
    private static final int HW_CEC = 1, HW_ARC = 2, HW_ADDRESS = 3, HW_FIELDS = 4;

    private final SignalMenuTokens tokens;

    /** {@code tokens}: this model's menu tables for the picture mode and motion smoothing rows. */
    public GmpfSignalReader(SignalMenuTokens tokens) { this.tokens = tokens; }

    @Override public Set<SignalItem> items() { return ITEMS; }

    @Override public int hdmiPort() { return Math.max(0, GameProfile.activePort()); }

    @Override public void read(List<SignalItem> items, int port, Consumer<SignalValues> cb) {
        Hal.query((g, g2) -> readOnHal(g, g2, items, port, tokens), cb::accept);
    }

    static SignalValues readOnHal(GmpfClient g, Gmpf2Client g2, List<SignalItem> items, int port, SignalMenuTokens tokens) {
        SignalValues v = new SignalValues();
        for (SignalItem i : items) {
            try {
                switch (i) {
                    case HDMI_INFO: {
                        HdmiSignal s = g.getHdmiInfo();
                        if (s != null && s.plausible()) {
                            v.width = s.width;
                            v.height = s.height;
                            v.rateCenti = s.frameRateCenti;
                            v.interlaced = s.interlace;
                            v.dvi = !s.hdmiMode;
                        }
                        break;
                    }
                    case HDR: v.hdrType = g.getHdrType(); break;
                    case CHIP: {
                        OutputTimingInfo o = g.getOutputTimingInfo();
                        if (o != null && o.vfreq > 0) {
                            v.chipWidth = o.currWidth;
                            v.chipHeight = o.currHeight;
                            v.chipRateCenti = o.vfreq * CENTI;
                        }
                        break;
                    }
                    case RANGE: v.range = SignalFormat.rangeToken(g.getHdmiRange()); break;
                    case EDID: v.edid = SignalFormat.edidToken(g.getEdidVersion()); break;
                    case ASPECT: v.aspectSetting = SignalFormat.aspectSettingToken(g.getAspectRatioRaw()); break;
                    case ALLM: if (hdmi(port)) v.allm = g2.getAllmStatus(port); break;
                    case VRR: if (hdmi(port)) v.vrr = g2.vrrOn(port); break;
                    case FREESYNC: v.freesync = g2.getFreeSync(); break;
                    case IMAX: v.imax = g2.getExtDecInfo(Gmpf2Base.EXT_IMAX) == 1; break;
                    case FILMMAKER: v.filmmaker = g2.getExtDecInfo(Gmpf2Base.EXT_FILMMAKER) == 1; break;
                    case PORT_HW: {
                        int[] f = hdmi(port) ? g.getHdmiHwConfig(port) : null;
                        if (f != null && f.length >= HW_FIELDS) {
                            v.cec = f[HW_CEC] != 0;
                            v.arc = f[HW_ARC] != 0;
                            v.physicalAddress = f[HW_ADDRESS];
                        }
                        break;
                    }
                    case DV_MODE: v.dvMode = g.getDolbyVisionPicMode(); break;
                    case HGIG: v.hgig = g2.getHgig(); break;
                    case TONE_MAP: v.toneMap = g2.getHdrDynamicToneMapping(); break;
                    case TEMP_LIGHT: v.tempLight = SignalFormat.temp(g.getSensorTemperature(LED_SENSOR)); break;
                    case TEMP_SYSTEM: v.tempSystem = SignalFormat.temp(g.getSystemTemperature()); break;
                    // the menu's own reads (Snapshot: 666 picture mode, 668 MEMC) named with the menu's tokens
                    case PICTURE_MODE: v.pictureMode = tokens.pictureMode(g.getPictureModeRaw()); break;
                    case MEMC: v.memc = tokens.memc(g.getMemcLevel()); break;
                    case GAME: {
                        int[] p = g2.getGameModeProp();
                        if (p != null && p.length >= 1) v.game = p[0] == 0;
                        break;
                    }
                    default: break;
                }
            } catch (Throwable t) {
                Log.d(TAG, i + ": " + t);
            }
        }
        return v;
    }

    private static boolean hdmi(int port) { return port == 1 || port == 2; }
}
