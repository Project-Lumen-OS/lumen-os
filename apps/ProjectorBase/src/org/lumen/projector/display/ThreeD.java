// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.display;

import android.util.Log;

import org.lumen.menu.ThreeDMode;
import org.lumen.projector.Hal;
import org.lumen.projector.R;
import org.lumen.projector.hal.GameModeOption;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.HdmiSignal;
import org.lumen.projector.hal.Mode3D;
import org.lumen.projector.hal.OutputTimingInfo;
import org.lumen.projector.panel.PanelRules;
import org.lumen.projector.panel.QuickPanel;
import org.lumen.projector.panel.RefusalReason;
import org.lumen.projector.panel.RuleNotes;

/**
 * Z9X 3D, the stock "3D设置" of XGIMI's settings (com.android.newsettings, Picture page, image_3d_info_state: Off, Side
 * by side, Top and bottom, Side by side as 2D, Top and bottom as 2D). The shared menu shows it (feature picture.3d:
 * the 3D tile, its popup and the last Picture row); the Z9X adapter sends it through here on the HAL thread.
 * Exactly the stock calls (V6.15.58 dump: PictureRepository.setImage3D): Off = IGmpf 190 enable3D(1), side by side =
 * 190(2), top and bottom = 190(3), "as 2D" = 191 enable3DTo2D(2 | 3) (one eye's picture, for watching a 3D file
 * without glasses). Read-back: 192 getCurrent3DFormat, 193 is3DTo2DEnabled ({@link ThreeDMode#read}). The vendor
 * (gmpf_main Msrv_Display_Control_V4::Enable3D) does the rest: MediaTek 3D panel profile, the DLP controller's 3D mode
 * (DLP-Link glasses sync through the light), pixel shift off, a short black frame.
 *
 * As stock, before a 3D mode: a video must be playing (180 isVideoPlaying: the hardware video path), no HDR video
 * (620 getHdrType == SDR; stock's 194 isPlayingHDRVideo is not used here), picture mode not 4. The vendor checks again
 * (game mode, > 65 fps, non-default output timing) and refuses; the read-back decides what the menu shows. The keystone
 * stays (stock resets it first when support_Trapezoid_3D is 0, DisplayMutexManager.threeDimension). The vendor's own
 * "close 3D when the video ends" is off on this model (isAutoClose3DEnable 0), so while 3D is on a watch on the HAL
 * thread turns it off (190(1)) once no video plays any more: otherwise menus and the home screen would stay split.
 */
public final class ThreeD {
    static final String TAG = "Z9x3D";

    /** Picture mode stock refuses 3D in (newsettings: "picture mode 4"). */
    private static final int PICTURE_MODE_NO_3D = 4;
    /** The vendor needs the black frame of a switch before the read-back is meaningful. */
    public static final long READBACK_DELAY_MS = 1_500;

    /**
     * 2026-11-04 (Z9X sweep): the full stock rule list for turning 3D on (panel.PanelRules.threeDOn). Set by the Z9X
     * App only ({@link #useStockRules}); without it only the 1.0.8 checks run.
     */
    private static volatile boolean sStockRules;
    private static boolean sWatching;   // HAL thread

    private ThreeD() {}

    /** Z9X App: turning 3D on follows the stock Z9X rule order (PanelRules.threeDOn). */
    public static void useStockRules() { sStockRules = true; }

    /**
     * HAL thread: sends {@code want}. 0 = sent; else the string of the reason it was not (nothing sent then, or the
     * vendor said no). Lumen 1.0.8 (parity picture item 28): refused while "Original frame rate" (IGmpf2 283) is on,
     * as stock SettingMutexManager.java:228-233 (native_display_not_support_3d).
     */
    public static int apply(GmpfClient g, Gmpf2Client g2, ThreeDMode want) throws Exception {
        if (want == ThreeDMode.OFF) {
            boolean ok = g.set3DMode(Mode3D.OFF);
            Log.i(TAG, "190 enable3D(1) -> " + ok);
            return ok ? 0 : R.string.panel_3d_not_now;
        }
        if (sStockRules) {
            int r = stockRefusal(g, g2);
            if (r != 0) return r;
        }
        if (!g.isVideoPlaying()) return R.string.panel_3d_need_video;
        if (g.getHdrType() != GmpfClient.HDR_SDR) return R.string.panel_3d_not_now;
        if (g.getPictureModeRaw() == PICTURE_MODE_NO_3D) return R.string.panel_3d_not_now;
        boolean nativeFps = false;
        try { nativeFps = g2.getNativeFrameRate(); } catch (Throwable t) { Log.w(TAG, "IGmpf2 283: " + t); }
        if (nativeFps) return R.string.three_d_native_fps;
        Mode3D m = ThreeDCall.enable3D(want);
        Boolean tb = ThreeDCall.to2dTopBottom(want);
        if (m == null && tb == null) return R.string.panel_3d_not_now;     // not a Z9X stock mode: nothing sent
        boolean ok = m != null ? g.set3DMode(m) : g.set3DTo2D(tb);
        Log.i(TAG, (want.to2d ? "191 enable3DTo2D(" : "190 enable3D(") + want.format + ") -> " + ok);
        return ok ? 0 : R.string.panel_3d_not_now;
    }

    /**
     * Z9X: stock image_3d_info then the common.json list (S 0x0684-0x06c3, CJ:196): Ultra 120 (157), original frame
     * rate (IGmpf2 283), game (55 / 666 == 4), top speed, Performance (175 == 15), no video (180), HDR (620), HDMI
     * 120 Hz (662). 0 = allowed (the 1.0.8 checks still run). Each read that fails is skipped.
     */
    private static int stockRefusal(GmpfClient g, Gmpf2Client g2) {
        PanelRules.In in = new PanelRules.In();
        try {
            OutputTimingInfo t = g.getOutputTimingInfo();
            in.ultra120 = t == null ? null : t.isUltra120();
        } catch (Throwable t) { Log.d(TAG, "157: " + t); }
        try { in.nativeFps283 = g2.getNativeFrameRate(); } catch (Throwable t) { Log.d(TAG, "IGmpf2 283: " + t); }
        try {
            GameModeOption o = g.getGameModeOption();
            in.opt647 = o == null ? null : o.wire;
        } catch (Throwable t) { Log.d(TAG, "647: " + t); }
        int src = -1;
        try { src = g.getCurrentInputSource(); } catch (Throwable t) { Log.d(TAG, "696: " + t); }
        in.hdmi = src == GmpfClient.SOURCE_HDMI1 || src == GmpfClient.SOURCE_HDMI2;
        if (in.opt647 != null) {
            try {
                int[] p = g2.getGameModeProp();
                if (Gmpf2Client.gameModeOf(p, in.opt647) >= 0) in.game55 = p[0] == 0;
            } catch (Throwable t) { Log.d(TAG, "IGmpf2 55: " + t); }
        }
        try { in.pic666 = g.getPictureModeRaw(); } catch (Throwable t) { Log.d(TAG, "666: " + t); }
        try { in.lumens175 = g.getLumensModeRaw(); } catch (Throwable t) { Log.d(TAG, "175: " + t); }
        try { in.videoPlaying = g.isVideoPlaying(); } catch (Throwable t) { Log.d(TAG, "180: " + t); }
        try { in.hdr620 = g.getHdrType(); } catch (Throwable t) { Log.d(TAG, "620: " + t); }
        if (in.hdmi) {
            try {
                HdmiSignal sig = g.getHdmiInfo();
                if (sig != null && sig.plausible()) in.hdmiFpsCenti = sig.frameRateCenti;
            } catch (Throwable t) { Log.d(TAG, "662: " + t); }
        }
        RefusalReason w = PanelRules.threeDOn(in);
        if (w == RefusalReason.NONE) return 0;
        Log.i(TAG, "3D refused (stock rule " + w + "): 157=" + in.ultra120 + " 283=" + in.nativeFps283 + " 647="
                + in.opt647 + " 55=" + in.game55 + " 666=" + in.pic666 + " 175=" + in.lumens175 + " 180="
                + in.videoPlaying + " 620=" + in.hdr620 + " 662=" + in.hdmiFpsCenti);
        // original frame rate keeps its 1.0.8 note; the others their own
        if (w == RefusalReason.NATIVE_FPS) return R.string.three_d_native_fps;
        return RuleNotes.note(w, true);
    }

    /** HAL thread, after a read-back: while 3D is on, turn it off once no video plays any more (the vendor does not). */
    public static void watchIfOn(ThreeDMode now) {
        if (now == null || !now.on() || sWatching) return;
        sWatching = true;
        Hal.runDelayed(ThreeD::watch, ThreeDMode.WATCH_MS, () -> sWatching = false);
    }

    private static void watch(GmpfClient g, Gmpf2Client g2) throws Exception {
        boolean again = false;
        try {
            ThreeDMode now = ThreeDMode.read(g.getCurrent3DFormat(), g.is3DTo2DEnabled());
            if (now == null || !now.on()) return;
            if (g.isVideoPlaying()) {
                again = true;
                return;
            }
            boolean ok = g.set3DMode(Mode3D.OFF);
            Log.i(TAG, "video ended: 190 enable3D(1) -> " + ok);
            if (!ok) again = true;
            ui(QuickPanel::refreshIfShowing);
        } finally {
            sWatching = again && Hal.runDelayed(ThreeD::watch, ThreeDMode.WATCH_MS, () -> sWatching = false);
        }
    }

    private static void ui(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
}
