package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.AiScene;
import org.lumen.projector.hal.ColorTemp;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.MemcLevel;
import org.lumen.projector.hal.PictureMode;
import org.lumen.projector.hal.PictureReset;
import org.lumen.projector.hal.PqItem;
import org.lumen.projector.ui.Notify;

import java.util.function.Consumer;

import static org.lumen.projector.panel.HbMenuFeatures.EXPLAINED;
import static org.lumen.projector.utils.HbHalReads.dynamicBlack;
import static org.lumen.projector.utils.HbHalReads.safeBoost;

/** Light level and Boost, picture mode and sliders, colour temperature, MEMC, picture resets. */
final class HbPictureRows extends HbRows {
    /** IGmpf 666 / TV 34 value of the vendor's own game picture mode (stock DisplayHelper.isGameModeBasic). */
    private static final int PICTURE_MODE_GAME = 4;
    /** Picture slider range (stock 0..100). */
    private static final int PQ_MIN = 0, PQ_MAX = 100;

    HbPictureRows(HbMenuFeatures menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, GmpfClient g, Gmpf2Client g2) throws Exception {
        switch (feature) {
            case LAMP: {
                int v = Integer.parseInt(value.trim());
                if (!HbMenuGates.lampValue(v)) throw new IllegalArgumentException("lamp " + v);
                if (Boolean.TRUE.equals(safeBoost(g2))) {
                    // a light step in Boost leaves Boost first (BoostRules: never against a picture mode that holds it)
                    String r = setBoost(false, g, g2);
                    if (r != null) return r;
                }
                Boolean db = dynamicBlack(g2);
                g.setLampLevel(v);                                              // DISPLAY 6
                dynamicBlackOff(g2, db, R.string.panel_lamp);
                return null;
            }
            case BOOST:
                return setBoost(on, g, g2);
            case PICTURE_MODE:
                return setPictureMode(value, g, g2);
            case PICTURE_COLOR_TEMP: {
                Integer w = HbMenuGates.COLOR_TEMP.wire(value);
                if (w == null) throw new IllegalArgumentException("colour temperature " + value);
                Boolean db = dynamicBlack(g2);
                g.setColorTemp(w == 0 ? ColorTemp.STANDARD : ColorTemp.MOVIE);   // DISPLAY 4
                dynamicBlackOff(g2, db, R.string.panel_color_temp);
                return null;
            }
            case PICTURE_MEMC: {
                Integer w = HbMenuGates.MEMC.wire(value);
                if (w == null) throw new IllegalArgumentException("memc " + value);
                if (!g.setMemcLevel(MemcLevel.values()[w])) throw new IllegalStateException("TV 31 false");
                return null;
            }
            default:
                return OTHER;
        }
    }

    @Override
    boolean run(FeatureId action, Consumer<Result> cb) {
        switch (action) {
            case PICTURE_RESET:
                write("TV 164 reset basic + professional", cb, (g, g2) -> {
                    if (!g2.resetPicture(PictureReset.BASIC)) throw new IllegalStateException("164 basic false");
                    if (!g2.resetPicture(PictureReset.PROFESSIONAL)) throw new IllegalStateException("164 professional false");
                    return null;
                });
                return true;
            case PICTURE_RESET_ALL:
                write("TV 164 reset all picture modes", cb, (g, g2) -> {
                    if (!g2.resetPicture(PictureReset.ALL)) throw new IllegalStateException("164 all false");
                    return null;
                });
                return true;
            default:
                return false;
        }
    }

    /** HAL thread. Boost by the 152 read-back (BoostRules); refused without a call where the picture mode holds it. */
    private String setBoost(boolean on, GmpfClient g, Gmpf2Client g2) throws Exception {
        Integer pm;
        try { pm = g.getPictureModeRaw(); } catch (Throwable t) { pm = null; }
        Boolean cur = safeBoost(g2);
        if (!on && BoostRules.onLeavePress(cur, pm) == BoostLeave.EXPLAIN_PICTURE_MODE) return boostLocked(pm);
        Boolean db = dynamicBlack(g2);
        boolean ret = g2.setBoost(on);                                          // DISPLAY 151
        int lensAfter;
        try { lensAfter = g2.isBoost() ? 1 : 0; } catch (Throwable t) { lensAfter = -1; }
        Integer pmAfter;
        try { pmAfter = g.getPictureModeRaw(); } catch (Throwable t) { pmAfter = null; }
        BoostOutcome o = BoostRules.judge(on, ret, lensAfter, pmAfter);
        Log.i(TAG, "151(" + (on ? 1 : 0) + ") -> " + ret + ", 152 -> " + lensAfter + ", picture mode " + pmAfter + ": " + o);
        if (o == BoostOutcome.LOCKED_BY_PICTURE_MODE) return boostLocked(pmAfter);
        if (o == BoostOutcome.FAILED) throw new IllegalStateException("151 returned " + ret + ", 152 reads " + lensAfter);
        dynamicBlackOff(g2, db, R.string.panel_boost);
        return null;
    }

    /** "Boost belongs to <picture mode>: choose another picture mode" (Performance / Office), stock HB text. */
    private String boostLocked(Integer pictureModeWire) {
        String tok = pictureModeWire == null ? null : HbMenuGates.PICTURE_MODE.token(pictureModeWire);
        int label = tok == null ? 0 : MenuLabels.option(FeatureId.PICTURE_MODE, tok);
        String mode = app.getString(label != 0 ? label : R.string.panel_picture_mode);
        String text = app.getString(R.string.hb_boost_locked, mode);
        Ui.main().post(() -> Notify.show(app, app.getString(R.string.panel_boost), text));
        return EXPLAINED;
    }

    /** HAL thread: picture mode with the stock refusals (game mode, Colour accuracy in the user's Boost). */
    private String setPictureMode(String value, GmpfClient g, Gmpf2Client g2) throws Exception {
        Integer w = HbMenuGates.PICTURE_MODE.wire(value);
        if (w == null) throw new IllegalArgumentException("picture mode " + value);
        PictureMode m = PictureMode.fromWire(w);
        Integer raw = g.getPictureModeRaw();
        if (raw != null && PictureMode.fromWire(raw) == m) return null;
        Integer hdr;
        try { hdr = g.getHdrType(); } catch (Throwable t) { hdr = null; }
        if (!offered(w, hdr)) return EXPLAINED;                         // stock hides it for this content
        if (gameModeOn(g, raw)) return "blocked_game";
        if (m == PictureMode.COLOR_ACCURACY && BoostRules.blocksColorAccuracy(safeBoost(g2), raw)) {
            return "boost_blocks_change";
        }
        if (!g.setPictureMode(m)) throw new IllegalStateException("TV 33 false");
        if (m == PictureMode.AI) g.setAiScene(AiScene.FILM);
        return null;
    }

    /**
     * Stock visibility of a picture mode for the content on screen (dapengse support lists): HDR Vivid only with HDR
     * Vivid content, colour accuracy for SDR, HDR10, HLG and HDR10+; every other mode always. Unknown content = SDR.
     */
    static boolean offered(int pictureModeWire, Integer hdrType) {
        int hdr = hdrType == null ? GmpfClient.HDR_SDR : hdrType;
        if (pictureModeWire == PictureMode.HDR_VIVID.wire) return hdr == GmpfClient.HDR_VIVID;
        if (pictureModeWire == PictureMode.COLOR_ACCURACY.wire) {
            return hdr == GmpfClient.HDR_SDR || hdr == GmpfClient.HDR_HDR10 || hdr == GmpfClient.HDR_HLG
                    || hdr == GmpfClient.HDR_HDR10_PLUS;
        }
        return true;
    }

    /** Picture sliders: through the HB slide coalescer (newest value, one write in flight), as the HB panel did. */
    void setPq(String item, String value, Consumer<Result> cb) {
        PqItem it;
        switch (item) {
            case "brightness": it = PqItem.BRIGHTNESS; break;
            case "contrast": it = PqItem.CONTRAST; break;
            case "saturation": it = PqItem.SATURATION; break;
            case "sharpness": it = PqItem.SHARPNESS; break;
            default: refuse(cb, null); return;
        }
        final int v;
        try { v = Integer.parseInt(value.trim()); } catch (NumberFormatException e) { refuse(cb, null); return; }
        if (v < PQ_MIN || v > PQ_MAX) { refuse(cb, null); return; }
        write("pq " + it + " " + v, cb, (g, g2) -> {
            if (!g.setPq(it, v)) throw new IllegalStateException("pq " + it + " false");
            return null;
        });
    }

    /** HAL thread: game mode on for the picture rules (stock isGameModeBasic): picture mode 4, or 148 state on HDMI. */
    private boolean gameModeOn(GmpfClient g, Integer pictureMode) {
        if (pictureMode != null && pictureMode == PICTURE_MODE_GAME) return true;
        Snapshot sn = last();
        if (sn != null && sn.gameActive55 != null) return sn.gameActive55 && hdmiNow(g);
        return prefs.getInt(QuickPanel.KEY_GAME_MODE, -1) == 1 && hdmiNow(g);
    }

    /** HAL thread: the stock notice when the vendor turned dynamic black off after our setter (event 19 DB_OFF). */
    private void dynamicBlackOff(Gmpf2Client g2, Boolean before, int causeRes) {
        if (!Boolean.TRUE.equals(before)) return;
        if (!Boolean.FALSE.equals(dynamicBlack(g2))) return;
        Log.i(TAG, "dynamic black turned off by the vendor after " + app.getString(causeRes));
        Ui.main().post(() -> Notify.show(app, app.getString(R.string.pro_dynamic_black_off_hb, app.getString(causeRes))));
    }
}
