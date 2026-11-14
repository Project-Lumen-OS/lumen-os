// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Note;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.hal.AiScene;
import org.lumen.projector.hal.ColorTemp;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.HdmiSignal;
import org.lumen.projector.hal.MemcLevel;
import org.lumen.projector.hal.OutputTiming;
import org.lumen.projector.hal.OutputTimingInfo;
import org.lumen.projector.hal.PictureMode;
import org.lumen.projector.hal.PictureReset;
import org.lumen.projector.hal.PqItem;
import org.lumen.projector.source.TifHdmiState;

import java.util.function.Consumer;

import static org.lumen.menu.utils.MenuValues.ofToken;
import static org.lumen.projector.utils.Z9xHalReads.dolbyVision;
import static org.lumen.projector.utils.Z9xHalReads.threeD;

/** Light level, Boost, picture mode and sliders, colour temperature, MEMC, AI contrast, super resolution, 120 Hz, resets. */
final class Z9xPictureRows extends Z9xRows {
    /** 662 frame rate (centi-Hz) from which the 120 Hz output is refused on HDMI. */
    private static final int ULTRA120_MAX_FPS_CENTI = 6200;

    Z9xPictureRows(Z9xFeatures menu) { super(menu); }

    @Override boolean set(FeatureId f, String v, boolean confirmed, Consumer<Result> cb) {
        final boolean on = "on".equals(v);
        if (f.parent == FeatureId.PICTURE_PQ) {
            PqItem item = ofToken(PqItem.values(), f.item);
            final int n = Integer.parseInt(v);
            if (item == null) { cb.accept(Result.failed(null)); return true; }
            write("pq " + item + " " + n, (g, g2) -> { g.setPq(item, n); return null; }, cb);
            return true;
        }
        switch (f) {
            case LAMP: {
                final int n = Integer.parseInt(v);
                write("176 lamp " + n, (g, g2) -> { g.setLampLevel(n); return null; }, cb);
                return true;
            }
            case BOOST:                                                   // QPC:1708
                write("IGmpf2 52 boost " + on, (g, g2) -> {
                    if (on && threeD(g)) return Result.refusedWith(Note.of("panel_boost_3d"), null);   // stock: Boost blocked in 3D
                    if (!g2.setBoost(on)) throw new IllegalStateException("52 returned false");
                    return null;
                }, cb);
                return true;
            case PICTURE_MODE: {                                          // QPC:1682
                int w = Z9xMenuRules.pmWire(v);
                PictureMode m = w < 0 ? null : PictureMode.fromWire(w);
                if (m == null) return false;
                write("667 picture mode " + m, (g, g2) -> {
                    if (!g.setPictureMode(m)) throw new IllegalStateException("667 returned false");
                    if (m == PictureMode.AI && !dolbyVision(g)) g.setAiScene(AiScene.FILM);
                    return null;
                }, cb);
                return true;
            }
            case PICTURE_COLOR_TEMP: {                                    // QPC:1058
                int i = Z9xMenuRules.indexOf(Z9xMenuRules.CT, v);
                if (i < 0) return false;
                final ColorTemp c = ColorTemp.values()[i];
                write("174 color temp " + c, (g, g2) -> { g.setColorTemp(c); return null; }, cb);
                return true;
            }
            case PICTURE_MEMC: {
                int i = Z9xMenuRules.indexOf(Z9xMenuRules.MEMC, v);
                if (i < 0) return false;
                final MemcLevel l = MemcLevel.values()[i];
                write("669 memc " + l, (g, g2) -> { g.setMemcLevel(l); return null; }, cb);
                return true;
            }
            case PICTURE_AI_CONTRAST:
                write("IGmpf2 288 ai contrast " + on, (g, g2) -> { g2.setAiContrast(on); return null; }, cb);
                return true;
            case PICTURE_SUPER_RES:
                write("IGmpf2 322 super resolution " + on, (g, g2) -> { g2.setSuperResolution(on); return null; }, cb);
                return true;
            case PICTURE_ULTRA120:
                setUltra120(on, cb);
                return true;
            default:
                return false;
        }
    }

    @Override boolean run(FeatureId a, Consumer<Result> cb) {
        switch (a) {
            case PICTURE_RESET:                                           // QPC:1107
                write("IGmpf2 278 reset basic", (g, g2) -> { g2.resetPicture(PictureReset.BASIC); return null; }, cb);
                return true;
            case PICTURE_RESET_ALL:                                       // QPC:1115
                write("IGmpf2 278 reset all", (g, g2) -> {
                    if (!g2.resetPicture(PictureReset.ALL)) throw new IllegalStateException("278 returned false");
                    return null;
                }, cb);
                return true;
            default:
                return false;
        }
    }

    /** Ultra 120 Hz, IGmpf 161 setOutputTiming(4 on / 6 off) with the stock checks on the HAL thread (QPC:1747). */
    private void setUltra120(boolean on, Consumer<Result> cb) {
        write("161 output timing " + (on ? 4 : 6), (g, g2) -> {
            if (on) {
                if (threeD(g)) return Result.refusedWith(Note.of("panel_ultra120_3d"), null);
                int src = g.getCurrentInputSource();
                Boolean tif = TifHdmiState.read(app);
                if (src != GmpfClient.SOURCE_MEDIA || !Boolean.FALSE.equals(tif)) {
                    HdmiSignal sig = null;
                    try { sig = g.getHdmiInfo(); } catch (Throwable t) { Log.d(TAG, "662: " + t); }
                    if (sig == null || !sig.plausible() || sig.frameRateCenti >= ULTRA120_MAX_FPS_CENTI) {
                        Log.i(TAG, "161(4) refused: 696=" + src + " tvinput session=" + tif + " 662=" + sig);
                        return Result.refusedWith(Note.of(sig != null && sig.plausible() && sig.frameRateCenti >= ULTRA120_MAX_FPS_CENTI
                                ? "panel_ultra120_refused" : "panel_ultra120_hdmi"), null);
                    }
                }
            }
            boolean ok = g.setOutputTiming(on ? OutputTiming.ULTRA_120_ON : OutputTiming.ULTRA_120_OFF);
            if (!ok) return Result.refusedWith(Note.of("panel_ultra120_refused"), null);
            boolean now = on;
            try {
                OutputTimingInfo t = g.getOutputTimingInfo();
                if (t != null) now = t.isUltra120();
            } catch (Throwable t) {
                Log.d(TAG, "157: " + t);
            }
            prefs.edit().putBoolean(Z9xFeatures.KEY_ULTRA_120, now).apply();
            return null;
        }, cb);
    }
}
