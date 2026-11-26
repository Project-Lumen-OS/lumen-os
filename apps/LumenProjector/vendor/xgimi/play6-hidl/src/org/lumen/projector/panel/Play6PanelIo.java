// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;

import org.lumen.projector.hal.AbsentException;
import org.lumen.projector.hal.ColorTemp;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Gmpf2;
import org.lumen.projector.hal.Play6KstPoint;
import org.lumen.projector.hal.ReplyException;
import org.lumen.projector.hal.ShiftDir;
import org.lumen.projector.hal.WbItem;
import org.lumen.projector.hal.Play6Values;

/**
 * The Play 6 HAL steps of the core cards and pages of its menu (RotateIo, ZoomIo, KeystoneIo, Play6MenuPages), over the IGmpf / IGmpf2 clients one Hal.run hands
 * out, with the adjustment bracket of Play6AdjustSession. HAL thread only, like the clients.
 */
public final class Play6PanelIo {
    /** Picture styles in the row order of the professional page (174 values, stock color_style_*). */
    static final int[] STYLE_ORDER = {ColorTemp.STANDARD.wire, ColorTemp.MOVIE_D65.wire, ColorTemp.OFFICE.wire,
            ColorTemp.CUSTOM.wire};
    static final int STYLE_CUSTOM = ColorTemp.CUSTOM.wire;

    private Play6PanelIo() {}

    /** 671: the noise reduction row index (0 off .. 4 auto) as the stock value. */
    static void noiseReduction(Play6Gmpf g, int i) throws Exception {
        g.setNoiseReduction(Play6Values.noiseReduction(i));
    }

    /** 174: a picture style of STYLE_ORDER. */
    static void pictureStyle(Play6Gmpf g, int style) throws Exception {
        g.setColorTemp(Play6Values.pictureStyle(style));
    }

    /** Stock PictureViewModel: the HDMI input in HDMI 1.4 mode (EDID 1) takes no HDR change (the input, then its EDID). */
    static boolean hdmi14(Play6Gmpf g) throws Exception {
        return g.getCurrentInputSource() == Play6Gmpf.SOURCE_HDMI1 && g.getEdidVersion() == Play6Gmpf.EDID_V14;
    }

    /** RGB gain / offset by row (0 red gain 683, 1 green 681, 2 blue 679, 3 red offset 677, 4 green 675, 5 blue 673). */
    static boolean setWb(Play6Gmpf g, int item, int value) throws Exception {
        switch (item) {
            case 0: return g.setWb(WbItem.RED_GAIN, value);
            case 1: return g.setWb(WbItem.GREEN_GAIN, value);
            case 2: return g.setWb(WbItem.BLUE_GAIN, value);
            case 3: return g.setWb(WbItem.RED_OFFSET, value);
            case 4: return g.setWb(WbItem.GREEN_OFFSET, value);
            default: return g.setWb(WbItem.BLUE_OFFSET, value);
        }
    }

    public static RotateIo rotate(Context app, Play6Gmpf g, Play6Gmpf2 g2) {
        return new RotateIo() {
            @Override public boolean open(String who) {
                if (!Play6AdjustSession.pushPullOn(app, g2, who)) return false;
                Play6AdjustSession.pauseRealtime(app, g, who);
                return true;
            }

            @Override public boolean close(String who, boolean moved) { return Play6AdjustSession.close(app, g, g2, who); }

            @Override public float[] rotation() throws Exception { return g2.getScreenRotation(); }

            @Override public boolean rotate(boolean cw, float deg) throws Exception { return g2.rotateScreen(cw, deg); }

            /** A 130 parsed on this projector (any process), as before the fold. */
            @Override public void supported() {
                try {
                    app.getSharedPreferences("p6rot", Context.MODE_PRIVATE).edit().putBoolean("ok", true).apply();
                } catch (Throwable ignored) { }
            }

            @Override public boolean refused(Throwable t) { return t instanceof ReplyException || t instanceof AbsentException; }
        };
    }

    static ZoomIo zoom(Context app, Play6Gmpf g, Play6Gmpf2 g2) {
        return new ZoomIo() {
            @Override public boolean pushPullOn(String who) { return Play6AdjustSession.pushPullOn(app, g2, who); }

            @Override public boolean pauseRealtime(String who) { return Play6AdjustSession.pauseRealtime(app, g, who); }

            @Override public void zoomStart(String who) throws Exception { Play6AdjustSession.zoomStart(app, g, who); }

            @Override public void zoomRestart(String who) throws Exception { Play6AdjustSession.zoomRestart(app, g, who); }

            @Override public boolean close(String who) { return Play6AdjustSession.close(app, g, g2, who); }

            @Override public int[] zoomStepRange() throws Exception { return g.getZoomStepRange(); }

            @Override public int[] zoomStep() throws Exception { return g.getZoomStep(); }

            @Override public int setDigitalZoomStep(int step) throws Exception { return g.setDigitalZoomStep(step); }

            @Override public int shift(ShiftMove dir, boolean check) throws Exception { return g.shiftPicture(dir(dir), check); }

            @Override public int resetKeystone() throws Exception { return g.resetKeystone(); }

            @Override public boolean replyRefused(Throwable t) { return t instanceof ReplyException; }
        };
    }

    static KeystoneIo keystone(Play6Gmpf g, Play6Gmpf2 g2) {
        return new KeystoneIo() {
            @Override public boolean getMoveAk() throws Exception { return g.getMoveAk(); }

            @Override public boolean setRealtimeAkTemporarily(boolean on) throws Exception { return g.setRealtimeAkTemporarily(on); }

            @Override public boolean setAdjustScreenOpen(boolean open) throws Exception { return g2.setAdjustScreenOpen(open); }

            @Override public int getPictureModeRaw() throws Exception { return g.getPictureModeRaw(); }

            @Override public int[] getGameModeProp() throws Exception { return g2.getGameModeProp(); }

            @Override public int getGameModeOptionRaw() throws Exception { return g.getGameModeOptionRaw(); }

            @Override public int getKeystoneMode() throws Exception { return g.getKeystoneMode(); }

            @Override public Corners getKeystonePoints() throws Exception { return g.getKeystonePoints(); }

            @Override public boolean checkKeystonePoints(Corners p) throws Exception { return g.checkKeystonePoints((Play6KstPoint) p); }

            @Override public int applyKeystonePoints(Corners p) throws Exception { return g.applyKeystonePoints((Play6KstPoint) p); }

            @Override public Corners newCorners() { return new Play6KstPoint(); }

            @Override public boolean refused(Throwable t) { return t instanceof ReplyException; }
        };
    }

    private static ShiftDir dir(ShiftMove m) {
        switch (m) {
            case UP: return ShiftDir.UP;
            case DOWN: return ShiftDir.DOWN;
            case LEFT: return ShiftDir.LEFT;
            default: return ShiftDir.RIGHT;
        }
    }
}
