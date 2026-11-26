// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.text;

import android.graphics.Paint;

import java.awt.Font;

public class TextPaint extends Paint {
    private Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    public TextPaint() {}

    public TextPaint(int flags) {}

    /** Not Android: the Java2D font of this paint (the host QpdStyle sets Inter). */
    public void setAwtFont(Font f) { font = f; }

    public Font awtFont() { return font.deriveFont(getTextSize()); }

    private java.awt.FontMetrics metrics() {
        java.awt.Graphics2D g = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g.getFontMetrics(awtFont());
    }

    public float measureText(String s) {
        return (float) awtFont().getStringBounds(s, metrics().getFontRenderContext()).getWidth();
    }

    public float ascent() { return -metrics().getAscent(); }

    public float descent() { return metrics().getDescent(); }
}
