package android.graphics;

import java.awt.GradientPaint;

public class LinearGradient extends Shader {
    private final GradientPaint paint;

    public LinearGradient(float x0, float y0, float x1, float y1, int c0, int c1, TileMode mode) {
        if (x0 == x1 && y0 == y1) x1 += 0.01f;
        paint = new GradientPaint(x0, y0, color(c0), x1, y1, color(c1));
    }

    @Override java.awt.Paint awt() { return paint; }
}
