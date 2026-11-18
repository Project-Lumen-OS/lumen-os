package android.view;

/** Host stub: animations end at once. */
public class ViewPropertyAnimator {
    private Runnable end;

    public ViewPropertyAnimator translationX(float v) { return this; }

    public ViewPropertyAnimator alpha(float v) { return this; }

    public ViewPropertyAnimator setDuration(long ms) { return this; }

    public ViewPropertyAnimator setInterpolator(Object i) { return this; }

    public ViewPropertyAnimator withLayer() { return this; }

    public ViewPropertyAnimator withEndAction(Runnable r) { end = r; return this; }

    public void start() { if (end != null) end.run(); }

    public void cancel() { }
}
