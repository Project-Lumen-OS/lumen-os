package org.lumen.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.Picture;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * The ONE live theme switch of every Lumen screen that follows {@link LumenTheme} (Home, Setup, the quick menu, the
 * projector overlays). It observes the setting ({@link ThemeWatcher}: the choice, the place, the clock and the next
 * Авто flip, no restart needed) and switches with a cross fade: what is on screen is frozen into a picture over the
 * window (the root's ViewOverlay, no view added, focus and scroll stay), the app re-styles its views in place under
 * it, and the picture fades out over {@link Tokens#MS_THEME} on {@link Motion#STANDARD}. Hidden, stopped or with the
 * system animations removed it switches at once. The rules are {@link ThemeFollow}.
 * <p>
 * Activity: {@link #activity} in onCreate (after the views are styled with {@link LumenTheme#palette}), start() in
 * onStart, stop() in onStop. Overlay window: {@link #follow} on its root, it starts and stops with the window. The app
 * that stores a choice itself calls {@link #apply} so its own screen does not wait for the observer.
 */
public final class ThemeFade implements ThemeWatcher.Listener, ThemeFollow.Screen {
    /** Re-styles the screen's views in place in {@code p} (or rebuilds them; focus and scroll are the caller's). */
    public interface Restyle {
        void restyle(Palette p);
    }

    private static final String TAG = "LumenTheme";

    private final View mRoot;
    /** An overlay window (translucent): its own background (the scrim) fades in under the picture, never doubled. */
    private final boolean mTranslucent;
    private final Restyle mRestyle;
    private final ThemeWatcher mWatcher;
    private final ThemeFollow mFollow;
    private BitmapDrawable mFrozen;
    private ValueAnimator mAnim;
    private boolean mStarted;

    private ThemeFade(View root, Restyle r, boolean translucent) {
        mRoot = root;
        mTranslucent = translucent;
        mRestyle = r;
        mWatcher = new ThemeWatcher(root.getContext(), this);
        mFollow = new ThemeFollow(this, mWatcher.current());
    }

    /** An activity's window (the decor view). start() in onStart, stop() in onStop. */
    public static ThemeFade activity(Activity a, Restyle r) {
        return new ThemeFade(a.getWindow().getDecorView(), r, false);
    }

    /** An overlay window's root: follows the theme while the window is attached. */
    public static ThemeFade follow(View root, Restyle r) {
        final ThemeFade f = new ThemeFade(root, r, true);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                f.start();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                f.stop();
            }
        });
        if (root.isAttachedToWindow()) f.start();
        return f;
    }

    /** Starts following; a change made while stopped is taken at once, before the next frame. */
    public void start() {
        if (mStarted) return;
        mStarted = true;
        mWatcher.start();
        mFollow.target(mWatcher.current());
    }

    public void stop() {
        if (!mStarted) return;
        mStarted = false;
        mWatcher.stop();
        mFollow.stop();
    }

    /** The palette the views are drawn in now. */
    public Palette shown() {
        return mFollow.shown();
    }

    /** The app stored a choice itself: the same switch now, the observer's echo then changes nothing. */
    public void apply(Palette p) {
        mFollow.target(p);
    }

    @Override
    public void onTheme(Palette p) {
        mFollow.target(p);
    }

    /**
     * Where the focus is under {@code root} as child indexes (null: none), for a screen that rebuilds its views on a
     * theme change: {@link #refocus} puts the focus back on the view at the same place in the new tree.
     */
    public static int[] focusPath(ViewGroup root) {
        View f = root.findFocus();
        if (f == null || f == root) return null;
        java.util.ArrayList<Integer> up = new java.util.ArrayList<>();
        for (View v = f; v != root; v = (View) v.getParent()) {
            if (!(v.getParent() instanceof ViewGroup)) return null;
            up.add(((ViewGroup) v.getParent()).indexOfChild(v));
        }
        int[] path = new int[up.size()];
        for (int i = 0; i < path.length; i++) path[i] = up.get(path.length - 1 - i);
        return path;
    }

    /** Focuses the view at {@code path} under {@code root}; false when it is gone or takes no focus. */
    public static boolean refocus(ViewGroup root, int[] path) {
        if (path == null) return false;
        View v = root;
        for (int i : path) {
            if (!(v instanceof ViewGroup) || i < 0 || i >= ((ViewGroup) v).getChildCount()) return false;
            v = ((ViewGroup) v).getChildAt(i);
        }
        return v.isShown() && v.isFocusable() && v.requestFocus();
    }

    // ------------------------------------------------------------------ ThemeFollow.Screen

    @Override
    public boolean visible() {
        return mStarted && mRoot.isAttachedToWindow() && mRoot.getWindowVisibility() == View.VISIBLE
                && mRoot.isShown() && mRoot.getWidth() > 0 && mRoot.getHeight() > 0;
    }

    @Override
    public boolean animations() {
        return !Motion.reduced(mRoot.getContext());
    }

    @Override
    public boolean freeze() {
        int w = mRoot.getWidth(), h = mRoot.getHeight();
        Bitmap b = null;
        try {
            b = snapshot(w, h);
        } catch (Throwable t) {
            Log.w(TAG, "theme snapshot: " + t);
        }
        if (b == null) {
            try {
                // fallback: a recorded Picture (takes hardware bitmaps, but no elevation shadows)
                Picture pic = new Picture();
                mRoot.draw(pic.beginRecording(w, h));
                pic.endRecording();
                b = Bitmap.createBitmap(pic, w, h, Bitmap.Config.HARDWARE);
            } catch (Throwable t) {
                Log.w(TAG, "theme snapshot fallback: " + t);
                return false;
            }
        }
        dropFrozen();
        mFrozen = new BitmapDrawable(mRoot.getResources(), b);
        mFrozen.setBounds(0, 0, b.getWidth(), b.getHeight());
        mRoot.getOverlay().add(mFrozen);
        return true;
    }

    /**
     * What is on screen, drawn by the GPU exactly as the window draws it (the views' own display lists: elevation
     * shadows, hardware art, layers, a running fade), into an offscreen buffer; null when the device gave no frame.
     */
    private Bitmap snapshot(int w, int h) {
        RenderNode node = new RenderNode("ThemeFade");
        node.setPosition(0, 0, w, h);
        RecordingCanvas rc = node.beginRecording(w, h);
        try {
            mRoot.draw(rc);
        } finally {
            node.endRecording();
        }
        ImageReader reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
        HardwareRenderer r = new HardwareRenderer();
        try {
            r.setOpaque(false);
            r.setSurface(reader.getSurface());
            r.setContentRoot(node);
            r.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            Image img = reader.acquireNextImage();
            if (img == null) return null;
            try (HardwareBuffer hb = img.getHardwareBuffer()) {
                Bitmap wrapped = Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB));
                // an own copy: the reader's buffer goes back with the reader
                return wrapped == null ? null : wrapped.copy(Bitmap.Config.HARDWARE, false);
            } finally {
                img.close();
            }
        } finally {
            r.destroy();
            reader.close();
            node.discardDisplayList();
        }
    }

    @Override
    public void restyle(Palette p) {
        mRestyle.restyle(p);
    }

    @Override
    public void fade(final int token) {
        if (mAnim != null) mAnim.cancel();
        final BitmapDrawable d = mFrozen;
        final Drawable bg = mTranslucent && mRoot.getBackground() != null ? mRoot.getBackground().mutate() : null;
        if (bg != null) bg.setAlpha(0);
        ValueAnimator a = ValueAnimator.ofInt(255, 0);
        a.setDuration(Tokens.MS_THEME);
        a.setInterpolator(Motion.STANDARD);
        a.addUpdateListener(v -> {
            int al = (Integer) v.getAnimatedValue();
            if (d != null) d.setAlpha(al);
            if (bg != null) bg.setAlpha(255 - al);
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator x) {
                if (mAnim == x) mAnim = null;
                mFollow.faded(token);
            }
        });
        mAnim = a;
        a.start();
    }

    @Override
    public void unfreeze() {
        if (mAnim != null) {
            ValueAnimator a = mAnim;
            mAnim = null;
            a.cancel();
        }
        if (mTranslucent && mRoot.getBackground() != null) mRoot.getBackground().setAlpha(255);
        dropFrozen();
    }

    private void dropFrozen() {
        if (mFrozen == null) return;
        // no recycle(): the frame drawn last may still hold the buffer, the collector frees it
        mRoot.getOverlay().remove(mFrozen);
        mFrozen = null;
    }
}
