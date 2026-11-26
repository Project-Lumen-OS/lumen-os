// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.graphics;

import java.awt.BasicStroke;

/** Host stub of Paint: colour or shader, fill or stroke, width, dash; text size for TextPaint. */
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1, SUBPIXEL_TEXT_FLAG = 128;

    public enum Style { FILL, STROKE, FILL_AND_STROKE }

    private int color = 0xFF000000;
    private Shader shader;
    private float strokeWidth;
    private Style style = Style.FILL;
    private PathEffect effect;
    private float textSize = 12f;

    public Paint() {}

    public Paint(int flags) {}

    public void setColor(int c) { color = c; }

    public int getColor() { return color; }

    public Shader setShader(Shader s) { shader = s; return s; }

    public void setStrokeWidth(float w) { strokeWidth = w; }

    public void setStyle(Style s) { style = s; }

    public Style getStyle() { return style; }

    public PathEffect setPathEffect(PathEffect e) { effect = e; return e; }

    public void setTextSize(float s) { textSize = s; }

    public float getTextSize() { return textSize; }

    java.awt.Paint awt() { return shader != null && shader.awt() != null ? shader.awt() : Shader.color(color); }

    java.awt.Stroke stroke() {
        DashPathEffect d = effect instanceof DashPathEffect ? (DashPathEffect) effect : null;
        return new BasicStroke(Math.max(1f, strokeWidth), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                d == null ? null : d.intervals, d == null ? 0f : d.phase);
    }
}
