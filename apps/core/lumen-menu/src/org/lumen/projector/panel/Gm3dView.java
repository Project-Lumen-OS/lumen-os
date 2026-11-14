package org.lumen.projector.panel;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.TextureView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * The console on the game menu's status card, drawn in real time on the GPU (OpenGL ES 3.0) instead of the frame
 * strip: a TextureView over the card's picture slot (GmStage places it, GmView fades and moves it with the card),
 * its own EGL context on one shared GL thread, a frame per display refresh while the menu is visible, nothing while
 * it is hidden, every GL object and the context released when the menu closes. Any failure is reported to GmView
 * (Listener), which falls back to the frame strip (Gm3dGate).
 */
final class Gm3dView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String TAG = GameMenu.TAG;
    /** setprop debug.lumen.gm3d 0: the frame strip only (side-by-side checks on a projector). */
    static final String PROP = "debug.lumen.gm3d";
    static final String ASSETS = "gm3d/";

    interface Listener {
        /** The first frame of {@code theme} is on screen. Main thread. */
        void onModelReady(String theme);

        /** {@code theme} cannot be drawn ({@code hard}: GL itself failed, not only this model). Main thread. */
        void onModelFailed(String theme, String why, boolean hard);
    }

    /** The real-time model may be used (not switched off by the property). */
    static boolean enabled() {
        try {
            String v = android.os.SystemProperties.get(PROP, "");
            return !"0".equals(v);
        } catch (Throwable t) {
            return true;
        }
    }

    private static Handler sGl;

    /** One GL thread for every menu shown (a menu is a new view each time). */
    private static synchronized Handler gl() {
        if (sGl == null) {
            HandlerThread t = new HandlerThread("gm3d-gl");
            t.start();
            sGl = new Handler(t.getLooper());
        }
        return sGl;
    }

    private final Session s;
    private boolean released, dimmed;

    Gm3dView(Context c, Listener l) {
        super(c);
        setOpaque(false);
        setSurfaceTextureListener(this);
        s = new Session(c.getAssets(), new Handler(Looper.getMainLooper()), l);
    }

    /** Loads {@code theme} (a console); its turn starts with its first frame on screen. Main thread. */
    void show(String theme) {
        if (released) return;
        setVisibility(VISIBLE);
        final long asked = SystemClock.uptimeMillis();
        gl().post(() -> s.load(theme, asked));
    }

    /** No console on the card (or the strip took over): nothing drawn, nothing running. Main thread. */
    void hide() {
        setVisibility(INVISIBLE);
        gl().post(s::unload);
    }

    /** The menu closed: the context and every GL object go. Main thread. */
    void release() {
        if (released) return;
        released = true;
        gl().post(s::destroy);
    }

    /** Darkens the picture like the question's shade over the card (0x66 black: colours times 0.6). */
    void dim(boolean on) {
        if (on == dimmed) return;
        dimmed = on;
        Paint p = null;
        if (on) {
            p = new Paint();
            ColorMatrix m = new ColorMatrix();
            m.setScale(0.6f, 0.6f, 0.6f, 1f);
            p.setColorFilter(new ColorMatrixColorFilter(m));
        }
        setLayerPaint(p);
    }

    /** Render harness: hold the pose at {@code yawDeg} from the hero pose (NaN: move again). */
    void freeze(float yawDeg) { gl().post(() -> s.frozen = yawDeg); }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        gl().post(() -> s.setRunning(visible));
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        gl().post(() -> s.surface(st, w, h));
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
        gl().post(() -> s.surface(st, w, h));
    }

    /** The surface goes when the window does: the GL thread lets go of it first, then releases it. */
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        gl().post(() -> s.surfaceGone(st));
        return false;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }

    // ================================================================== GL thread
    private static final class Session implements Choreographer.FrameCallback {
        private final AssetManager am;
        private final Handler main;
        private final Listener listener;
        private EGLDisplay dpy = EGL14.EGL_NO_DISPLAY;
        private EGLContext ctx = EGL14.EGL_NO_CONTEXT;
        private EGLConfig cfg;
        private EGLSurface surf = EGL14.EGL_NO_SURFACE;
        private SurfaceTexture st;
        private int w, h;
        private Gm3dRenderer r;
        private Gm3dModel.Env env;
        private Gm3dModel model;
        private String theme;
        private long t0, asked;
        private boolean uploaded, shown, running = true, posted, dead;
        private String loadInfo = "";
        float frozen = Float.NaN;
        // frame pacing of the shown theme, logged when it goes (a check on the projector: logcat -s Z9xGameMenu)
        private int drawn, late;
        private long firstNs, lastNs, cpuNs;

        Session(AssetManager am, Handler main, Listener l) {
            this.am = am;
            this.main = main;
            this.listener = l;
        }

        void load(String th, long askedAt) {
            if (dead) return;
            pacing();
            theme = th;
            asked = askedAt;
            uploaded = shown = false;
            model = null;
            try {
                long a = SystemClock.uptimeMillis();
                ByteBuffer b = read(am, ASSETS + th + ".lgm");
                long rd = SystemClock.uptimeMillis();
                model = Gm3dModel.parse(b);
                loadInfo = "read " + (rd - a) + " ms, parse " + (SystemClock.uptimeMillis() - rd) + " ms";
            } catch (Throwable e) {
                fail("model " + th + ": " + e, false);
                return;
            }
            prepare();
        }

        /** Frames drawn since the theme showed: rate, refreshes skipped, CPU time of a frame on the GL thread. */
        private void pacing() {
            if (drawn > 1 && theme != null) {
                double s = (lastNs - firstNs) / 1e9;
                Log.i(TAG, String.format(java.util.Locale.ROOT, "gm3d %s: %d frames in %.1f s (%.1f fps), %d late, %.2f ms per frame on the GL thread",
                        theme, drawn, s, (drawn - 1) / s, late, cpuNs / 1e6 / drawn));
            }
            drawn = late = 0;
            cpuNs = 0;
        }

        void unload() {
            pacing();
            theme = null;
            model = null;
            stop();
        }

        void surface(SurfaceTexture t, int width, int height) {
            if (dead) return;
            st = t;
            w = width;
            h = height;
            prepare();
        }

        void surfaceGone(SurfaceTexture t) {
            stop();
            if (r != null && current()) r.release();
            r = null;
            uploaded = false;
            dropSurface();
            if (st == t) st = null;
            t.release();
        }

        void setRunning(boolean on) {
            running = on;
            if (on) schedule();
            else stop();
        }

        /** Context, surface, program and model as far as they can be made now; then the first frame. */
        private void prepare() {
            if (dead || st == null || model == null) return;
            try {
                if (ctx == EGL14.EGL_NO_CONTEXT) makeContext();
                if (surf == EGL14.EGL_NO_SURFACE) {
                    surf = EGL14.eglCreateWindowSurface(dpy, cfg, st, new int[] {EGL14.EGL_NONE}, 0);
                    if (surf == null || surf == EGL14.EGL_NO_SURFACE) throw new IllegalStateException("eglCreateWindowSurface 0x" + Integer.toHexString(EGL14.eglGetError()));
                }
                if (!current()) throw new IllegalStateException("eglMakeCurrent 0x" + Integer.toHexString(EGL14.eglGetError()));
                long a = SystemClock.uptimeMillis();
                if (r == null) {
                    if (env == null) env = Gm3dModel.parseEnv(read(am, ASSETS + "env.lge"));
                    Gm3dRenderer nr = new Gm3dRenderer();
                    nr.init(text(am, ASSETS + "model.vert"), text(am, ASSETS + "model.frag"), env, model.scene);
                    r = nr;
                }
                r.resize(w, h);
                if (!uploaded) {
                    r.setModel(model);
                    uploaded = true;
                }
                loadInfo += ", gl " + (SystemClock.uptimeMillis() - a) + " ms";
            } catch (Throwable e) {
                fail("gl: " + e, true);
                return;
            }
            schedule();
        }

        private void makeContext() {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] v = new int[2];
            if (dpy == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(dpy, v, 0, v, 1)) throw new IllegalStateException("no EGL display");
            int[] attr = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT_KHR */, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_NONE};
            EGLConfig[] cs = new EGLConfig[1];
            int[] n = new int[1];
            if (!EGL14.eglChooseConfig(dpy, attr, 0, cs, 0, 1, n, 0) || n[0] < 1) throw new IllegalStateException("no ES 3 RGBA8888 config");
            cfg = cs[0];
            ctx = EGL14.eglCreateContext(dpy, cfg, EGL14.EGL_NO_CONTEXT, new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
            if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) throw new IllegalStateException("no ES 3 context 0x" + Integer.toHexString(EGL14.eglGetError()));
        }

        private boolean current() {
            return ctx != EGL14.EGL_NO_CONTEXT && surf != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(dpy, surf, surf, ctx);
        }

        private void schedule() {
            if (posted || dead || !running || r == null || !uploaded || surf == EGL14.EGL_NO_SURFACE) return;
            posted = true;
            Choreographer.getInstance().postFrameCallback(this);
        }

        private void stop() {
            if (posted) Choreographer.getInstance().removeFrameCallback(this);
            posted = false;
        }

        @Override public void doFrame(long frameTimeNanos) {
            posted = false;
            if (dead || !running || r == null || !uploaded || surf == EGL14.EGL_NO_SURFACE) return;
            try {
                if (!current()) throw new IllegalStateException("eglMakeCurrent 0x" + Integer.toHexString(EGL14.eglGetError()));
                if (!shown) t0 = frameTimeNanos / 1_000_000L;   // the turn starts on the first frame on screen
                long c0 = System.nanoTime();
                long t = frameTimeNanos / 1_000_000L - t0;
                float yaw = Float.isNaN(frozen) ? Gm3dMotion.yawAt(t) : frozen;
                float bob = Float.isNaN(frozen) ? Gm3dMotion.bobAt(t) : 0f;
                r.draw(yaw, bob);
                if (!shown) Gm3dRenderer.check("first frame");
                if (!EGL14.eglSwapBuffers(dpy, surf)) {
                    int e = EGL14.eglGetError();
                    if (e == EGL14.EGL_BAD_SURFACE || e == EGL14.EGL_BAD_NATIVE_WINDOW) return;   // the window is going
                    throw new IllegalStateException("eglSwapBuffers 0x" + Integer.toHexString(e));
                }
                cpuNs += System.nanoTime() - c0;
                if (drawn == 0) firstNs = frameTimeNanos;
                else if (frameTimeNanos - lastNs > 25_000_000L) late++;   // a refresh skipped at 60 Hz
                lastNs = frameTimeNanos;
                drawn++;
            } catch (Throwable e) {
                fail("frame: " + e, true);
                return;
            }
            if (!shown) {
                shown = true;
                final String th = theme;
                Log.i(TAG, "gm3d " + th + ": first frame " + (SystemClock.uptimeMillis() - asked) + " ms after the ask ("
                        + loadInfo + "), " + model.triangles() + " triangles, " + r.samples() + "x MSAA at "
                        + r.width() + " x " + r.height() + " for " + w + " x " + h);
                main.post(() -> listener.onModelReady(th));
            }
            schedule();
        }

        private void fail(String why, boolean hard) {
            Log.w(TAG, "gm3d " + theme + " failed, frame strip instead: " + why);
            final String th = theme;
            releaseGl();
            dead = true;
            main.post(() -> listener.onModelFailed(th, why, hard));
        }

        void destroy() {
            pacing();
            releaseGl();
            dead = true;
        }

        private void releaseGl() {
            stop();
            if (r != null && current()) r.release();
            r = null;
            uploaded = false;
            model = null;
            env = null;
            dropSurface();
            if (ctx != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroyContext(dpy, ctx);
                EGL14.eglReleaseThread();
                ctx = EGL14.EGL_NO_CONTEXT;
            }
        }

        private void dropSurface() {
            if (surf != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(dpy, surf);
                surf = EGL14.EGL_NO_SURFACE;
            }
        }
    }

    // ================================================================== assets
    /** A whole asset as a direct little-endian buffer (GL reads it as it is): one read of its exact length. */
    static ByteBuffer read(AssetManager am, String name) throws IOException {
        try (InputStream in = am.open(name, AssetManager.ACCESS_STREAMING)) {
            int n = in.available();                   // an asset stream knows its whole (inflated) length
            if (n <= 0 || n > Gm3dModel.MAX_FILE) throw new IOException(name + ": length " + n);
            byte[] all = new byte[n];
            int off = 0;
            for (int r; off < n && (r = in.read(all, off, n - off)) > 0; ) off += r;
            if (off != n || in.read() != -1) throw new IOException(name + ": read " + off + " of " + n);
            ByteBuffer b = ByteBuffer.allocateDirect(n).order(ByteOrder.LITTLE_ENDIAN);
            b.put(all).flip();
            return b;
        }
    }

    static String text(AssetManager am, String name) throws IOException {
        ByteBuffer b = read(am, name);
        byte[] a = new byte[b.remaining()];
        b.get(a);
        return new String(a, StandardCharsets.UTF_8);
    }
}
