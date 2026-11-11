package org.lumen.projector.panel;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;
import android.view.WindowManager;

import org.lumen.projector.R;
import org.lumen.projector.SafeHandler;
import org.lumen.projector.Ui;
import org.lumen.projector.ui.OverlayHost;

/**
 * Our own layers over the game (no vendor call): the crosshair (10 styles, 5 colours, moved in 6 px steps) and the
 * frame counter (left / centre / right; frames per second of the HDMI signal from 662, read every 2 s). One
 * full-screen window that takes no keys and no touches, shown only while game mode runs on the HDMI input on screen
 * (GameMenu.Watch) and something is switched on. Settings persist (prefs z9x_gamemenu). Main thread only.
 */
final class GmOverlays {
    private static GmOverlays sInstance;
    private static final String K_AIM = "aim_on", K_AIM_STYLE = "aim_style", K_AIM_COL = "aim_col", K_AIM_X = "aim_x",
            K_AIM_Y = "aim_y", K_FPS = "fps_pos";
    private static final long FPS_POLL_MS = 2_000;

    private final Context app;
    private final SharedPreferences prefs;
    private final SafeHandler main = Ui.main();
    private Layer layer;
    private boolean added, game, moving;
    private int fps = -1;
    private final Runnable fpsTick = this::pollFps;

    static synchronized GmOverlays get(Context c) {
        if (sInstance == null) sInstance = new GmOverlays(c.getApplicationContext());
        return sInstance;
    }

    private GmOverlays(Context app) {
        this.app = app;
        prefs = app.getSharedPreferences(GameMenu.PREFS, Context.MODE_PRIVATE);
    }

    boolean aimOn() { return prefs.getBoolean(K_AIM, false); }
    int aimStyle() { return clamp(prefs.getInt(K_AIM_STYLE, 1), 0, GmModel.AIM_STYLES - 1); }
    int aimColour() { return clamp(prefs.getInt(K_AIM_COL, 0), 0, GmModel.AIM_COLOURS.length - 1); }
    int aimX() { return clamp(prefs.getInt(K_AIM_X, 960), GmModel.AIM_MIN_X, GmModel.AIM_MAX_X); }
    int aimY() { return clamp(prefs.getInt(K_AIM_Y, 540), GmModel.AIM_MIN_Y, GmModel.AIM_MAX_Y); }
    int fpsPos() { return clamp(prefs.getInt(K_FPS, 0), 0, 3); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    void setAimOn(boolean on) { prefs.edit().putBoolean(K_AIM, on).apply(); update(); }

    void setAimStyle(int style, int colour) {
        prefs.edit().putInt(K_AIM_STYLE, clamp(style, 0, GmModel.AIM_STYLES - 1))
                .putInt(K_AIM_COL, clamp(colour, 0, GmModel.AIM_COLOURS.length - 1)).apply();
        update();
    }

    void setAimPos(int x, int y) { prefs.edit().putInt(K_AIM_X, x).putInt(K_AIM_Y, y).apply(); update(); }

    void setFps(int pos) {
        prefs.edit().putInt(K_FPS, clamp(pos, 0, 3)).apply();
        fps = -1;
        update();
    }

    void setMoving(boolean on) { moving = on; update(); }

    /** The menu opened or closed (the crosshair preview follows it). */
    void menuChanged() { update(); }

    /** The watcher: game mode runs (or not) on the HDMI input on screen. */
    void setGame(boolean running) {
        if (game == running) return;
        game = running;
        update();
    }

    /** Also shown while the menu is open (the preview of the crosshair), even before the first watcher read. */
    private boolean wanted() {
        boolean menu = GameMenu.isShowing();
        return (game || menu) && (aimOn() || fpsPos() != 0 || moving);
    }

    private void update() {
        try {
            if (wanted()) {
                if (!added) {
                    layer = new Layer(app);
                    WindowManager.LayoutParams lp = OverlayHost.fullscreenParams(false);
                    lp.setTitle("Z9xGameLayer");
                    // a host without static layers (the activity host) answers false: nothing is drawn
                    added = GameMenu.sHost != null && GameMenu.sHost.addStatic(layer, lp);
                }
                if (layer != null) layer.invalidate();
                main.removeCallbacks(fpsTick);
                if (fpsPos() != 0) main.post(fpsTick);
            } else {
                main.removeCallbacks(fpsTick);
                if (added && layer != null && GameMenu.sHost != null) GameMenu.sHost.removeStatic(layer);
                added = false;
                layer = null;
            }
        } catch (Throwable t) {
            android.util.Log.w(GameMenu.TAG, "overlays: " + t);
        }
    }

    private void pollFps() {
        if (!added || fpsPos() == 0) return;
        GameMenu.Io io = GameMenu.sIo;
        if (io == null) return;
        io.fps(v -> {
            fps = v == null ? -1 : v;
            if (layer != null) layer.invalidate();
        });
        main.postDelayed(fpsTick, FPS_POLL_MS);
    }

    // ================================================================== drawing
    /** Crosshair style {@code i} (prototype AIMS, 64 grid) centred on (cx, cy), {@code size} px wide. */
    static void drawAim(Canvas c, int style, int colour, float cx, float cy, float size, Paint p, boolean shadow) {
        float s = size / 64f;
        c.save();
        c.translate(cx - 32 * s, cy - 32 * s);
        c.scale(s, s);
        p.setAntiAlias(true);
        p.setColor(colour);
        p.setStrokeWidth(3.2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        if (shadow) p.setShadowLayer(2f, 0, 0, 0xCC000000); else p.clearShadowLayer();
        switch (style) {
            case 0: lines(c, p, 32, 10, 32, 25, 32, 39, 32, 54, 10, 32, 25, 32, 39, 32, 54, 32); break;
            case 1: lines(c, p, 32, 12, 32, 25, 32, 39, 32, 52, 12, 32, 25, 32, 39, 32, 52, 32); dot(c, p, 2.6f); break;
            case 2: dot(c, p, 4f); break;
            case 3: ring(c, p, 13); break;
            case 4: ring(c, p, 13); dot(c, p, 2.6f); break;
            case 5: lines(c, p, 32, 39, 32, 54, 10, 32, 25, 32, 39, 32, 54, 32); break;
            case 6: lines(c, p, 20, 40, 32, 28, 32, 28, 44, 40); break;
            case 7: ring(c, p, 11); lines(c, p, 32, 13, 32, 21, 32, 43, 32, 51, 13, 32, 21, 32, 43, 32, 51, 32); break;
            case 8: lines(c, p, 18, 24, 18, 18, 18, 18, 24, 18, 40, 18, 46, 18, 46, 18, 46, 24,
                    46, 40, 46, 46, 46, 46, 40, 46, 24, 46, 18, 46, 18, 46, 18, 40); break;
            default: lines(c, p, 32, 14, 32, 50, 14, 32, 50, 32); break;
        }
        c.restore();
        p.clearShadowLayer();
    }

    private static void lines(Canvas c, Paint p, float... xy) {
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i + 3 < xy.length; i += 4) c.drawLine(xy[i], xy[i + 1], xy[i + 2], xy[i + 3], p);
    }

    private static void ring(Canvas c, Paint p, float r) {
        p.setStyle(Paint.Style.STROKE);
        c.drawCircle(32, 32, r, p);
    }

    private static void dot(Canvas c, Paint p, float r) {
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(32, 32, r, p);
    }

    /** The layer window: crosshair and frame counter in 1920 x 1080 design units. */
    private final class Layer extends View {
        private final Paint aim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint big, unit;
        private final RectF r = new RectF();

        Layer(Context c) {
            super(c);
            dash.setStyle(Paint.Style.STROKE);
            dash.setStrokeWidth(2f);
            dash.setColor(0x80FFFFFF);
            dash.setPathEffect(new DashPathEffect(new float[] {7, 6}, 0));
            pill.setColor(0xB80A0A0E);
            big = GmView.paint(c, 30, 650, 0xFFF4F4F6);
            unit = GmView.paint(c, 16, 550, 0x8FF4F4F6);
            big.setFontFeatureSettings("'tnum', 'cv11', 'ss03'");
        }

        @Override protected void onDraw(Canvas c) {
            float k = getWidth() / 1920f;
            if (k <= 0) return;
            c.save();
            c.scale(k, k);
            if (aimOn() || moving) {
                float x = aimX(), y = aimY();
                drawAim(c, aimStyle(), GmModel.AIM_COLOURS[aimColour()], x, y, 64, aim, true);
                if (moving) {
                    r.set(x - 42, y - 42, x + 42, y + 42);
                    c.drawRoundRect(r, 8, 8, dash);
                }
            }
            int pos = fpsPos();
            if (pos != 0) {
                String n = fps > 0 ? Integer.toString(fps) : "--";
                String u = getContext().getString(R.string.lumen_gm_fps_unit);
                float w = 18 + big.measureText(n) + 8 + unit.measureText(u) + 18;
                float x0 = pos == GmModel.FPS_LEFT ? 40 : pos == GmModel.FPS_RIGHT ? 1880 - w : 960 - w / 2;
                r.set(x0, 28, x0 + w, 80);
                c.drawRoundRect(r, 26, 26, pill);
                c.drawText(n, x0 + 18, 65, big);
                c.drawText(u, x0 + 18 + big.measureText(n) + 8, 65, unit);
            }
            c.restore();
        }
    }
}
