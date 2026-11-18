package org.lumen.projector;

import android.content.Context;
import android.util.Log;

import org.lumen.projector.hal.GmpfBase;
import org.lumen.aipq.AiPqDriver;
import org.lumen.aipq.AiPqVendor;
import org.lumen.hal.gmpf.TvEventSink;

/**
 * The stock AI picture quality of XRMService (org.lumen.aipq.AiPq) on the gmpf projectors (Z9X, HB, Play 6): the
 * menu writes picture mode AI (10) only, the vendor answers every picture mode change with GmTvManager event 5
 * "picturemode:N" (IGmTvCallback, registered after every HAL connection: Z9X / Play 6 IGmpf 702 setGmTvListener, HB
 * GmTvManager 1 setGmTvCallback) and AiPq turns AI 10 into the default scene (ro.xgimi.default.aipq film = 16, Play 6
 * persist.xgimi.default.aipq 16), also when the vendor itself reloads AI (source switch, HDR change, boot). Start at
 * the first HAL connection, reset at every screen on, the video types of com.xgimi.ai.NOTIFICATION. Lumen OS 1.0.8
 * only wrote 16 from the menu, at connect and at screen on: every vendor reload of AI stayed at scene 10 ("tv"), the
 * pale picture of the reports. One file for the three apps: Hal is each app's own HAL thread, its clients extend the
 * shared GmpfBase.
 */
public final class AiScenePolicy {
    private static final String TAG = "LumenAiPq";
    /** Every current model file: ro.xgimi.default.aipq=film, support_multipq_type 2 (Play 6: no ro, persist 16). */
    private static final String MODEL_SCENE = "film";
    private static final int MODEL_MULTIPQ = 2;
    private static AiPqDriver sDriver;
    /** More receivers of the same GmTvManager events: the vendor keeps one callback per client. */
    private static final java.util.concurrent.CopyOnWriteArrayList<TvEventSink> EXTRA =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private AiScenePolicy() {}

    /** App start: sink gets every GmTvManager event too, on the vendor's binder thread (it must only post). */
    public static void addTvEventSink(TvEventSink sink) { EXTRA.addIfAbsent(sink); }

    /** App start, main thread, once, before the HAL gate opens. */
    public static synchronized void install(Context ctx) {
        if (sDriver != null) return;
        AiPqDriver d = new AiPqDriver(TAG, task -> Hal.run((g, g2) -> task.run(new Vendor(g))), MODEL_SCENE,
                MODEL_MULTIPQ);
        sDriver = d;
        d.install(ctx);
        Hal.addConnectedListener(() -> {
            Hal.run((g, g2) -> {
                if (!g.hasTvListener()) return;
                try {
                    g.setTvListener((type, msg) -> {
                        d.onTvEvent(type, msg);
                        for (TvEventSink s : EXTRA) s.onTvEvent(type, msg);
                    });
                    Log.i(TAG, "GmTvManager event callback registered");
                } catch (Throwable t) {
                    Log.w(TAG, "GmTvManager event callback: " + t);
                }
            });
            d.connected();
        });
    }

    /** Screen on (XRMService.onScreenOn): an AI scene goes back to the default scene. Any thread. */
    public static void reassert(String why) {
        AiPqDriver d = sDriver;
        if (d != null) d.screenOn();
    }

    /** HAL thread, right after this app wrote picture mode mode: the vendor event of that write, handled now. */
    public static void afterOwnWrite(GmpfBase<?> g, int mode) {
        AiPqDriver d = sDriver;
        if (d != null) d.afterOwnWrite(new Vendor(g), mode);
    }

    /**
     * The HAL calls of AiPq over the shared gmpf client, on the HAL thread. HDR Vivid and IMAX: no call on these
     * clients, read as not playing (stock reads them on Chinese firmware content; the vendors of these models report
     * neither for the HDMI and player content Lumen shows). isVideoPlaying where the device table has it (Z9X IGmpf 180, HB DisplayManager 10, Play 6 IGmpf 180).
     */
    private static final class Vendor implements AiPqVendor {
        private final GmpfBase<?> g;

        Vendor(GmpfBase<?> g) { this.g = g; }

        @Override public int pictureMode() throws Exception { return g.getPictureModeRaw(); }
        @Override public void setPictureMode(int mode) throws Exception { g.setAiScene(mode); }
        @Override public int hdrType() throws Exception { return g.getHdrType(); }
        @Override public boolean hdrVividAuto(int pictureMode) { return false; }
        @Override public boolean hdrVividPlaying() { return false; }
        @Override public boolean imaxPlaying() { return false; }
        @Override public int current3DFormat() throws Exception { return g.getCurrent3DFormat(); }
        @Override public int lumensMode() throws Exception { return g.getLumensModeRaw(); }
        @Override public boolean videoPlaying() throws Exception { return g.isVideoPlayingOrFalse(); }
    }
}
