// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.view;

import android.content.Context;
import android.os.Looper;

/** Host stub (test/keytools): visibility, layout params, parent chain, window focus and posted work. */
public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8, LAYOUT_DIRECTION_LTR = 0;
    public static final Object ALPHA = "alpha";
    private final Context ctx;
    private int visibility = VISIBLE;
    private ViewGroup.LayoutParams params;
    ViewParent parent;
    /** Set by the fake window manager on a window's root view. */
    public boolean windowFocus;
    public boolean attached;

    public View(Context c) { ctx = c; }

    public Context getContext() { return ctx; }

    public void setVisibility(int v) { visibility = v; }

    public int getVisibility() { return visibility; }

    private float alpha = 1f;

    public void setAlpha(float a) { alpha = a; }

    public float getAlpha() { return alpha; }

    public ViewGroup.LayoutParams getLayoutParams() { return params; }

    public void setLayoutParams(ViewGroup.LayoutParams p) { params = p; }

    public ViewParent getParent() { return parent; }

    public void setParentForTest(ViewParent p) { parent = p; }

    public boolean isAttachedToWindow() {
        View v = this;
        while (v.parent instanceof View) v = (View) v.parent;
        return v.attached;
    }

    public boolean hasWindowFocus() {
        View v = this;
        while (v.parent instanceof View) v = (View) v.parent;
        return v.attached && v.windowFocus;
    }

    public int getWidth() { return 0; }

    public boolean post(Runnable r) { Looper.post(r, 0); return true; }

    public boolean postDelayed(Runnable r, long ms) { Looper.post(r, ms); return true; }

    public boolean removeCallbacks(Runnable r) { Looper.remove(r); return true; }

    public boolean dispatchKeyEvent(KeyEvent e) { return false; }

    public void onWindowFocusChanged(boolean has) { }

    /** Window focus reaches every view of the window (the framework's dispatchWindowFocusChanged). */
    public void dispatchWindowFocusChanged(boolean has) { onWindowFocusChanged(has); }

    protected void onAttachedToWindow() { }

    protected void onDetachedFromWindow() { }

    public void dispatchAttached(boolean on) { if (on) onAttachedToWindow(); else onDetachedFromWindow(); }

    public ViewPropertyAnimator animate() { return new ViewPropertyAnimator(); }

    public void setTranslationX(float v) { }


    public void invalidate() { }
}
