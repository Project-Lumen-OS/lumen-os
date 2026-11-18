package android.graphics;

import java.awt.RadialGradientPaint;

public class RadialGradient extends Shader {
    private final RadialGradientPaint paint;

    public RadialGradient(float cx, float cy, float r, int c0, int c1, TileMode mode) {
        paint = new RadialGradientPaint(cx, cy, Math.max(0.01f, r), new float[]{0f, 1f},
                new java.awt.Color[]{color(c0), color(c1)});
    }

    @Override java.awt.Paint awt() { return paint; }
}
