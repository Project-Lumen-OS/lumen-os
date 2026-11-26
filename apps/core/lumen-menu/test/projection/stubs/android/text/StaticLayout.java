// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.text;

import android.graphics.Canvas;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

/** Host stub of StaticLayout: greedy line breaks at BreakIterator opportunities with Java2D font metrics. */
public class StaticLayout extends Layout {
    private final List<String> lines;
    private final TextPaint paint;
    private final float lineH;
    private final FontMetrics fm;

    private StaticLayout(Builder b) {
        paint = b.paint;
        Font f = paint.awtFont();
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        fm = g.getFontMetrics(f);
        lineH = (fm.getAscent() + fm.getDescent()) * b.mult + b.add;
        lines = wrap(b.text.toString(), b.width);
    }

    private List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        BreakIterator it = BreakIterator.getLineInstance();
        it.setText(s);
        int start = 0, last = 0;
        for (int end = it.next(); end != BreakIterator.DONE; end = it.next()) {
            if (fm.stringWidth(s.substring(start, end).trim()) > width && last > start) {
                out.add(s.substring(start, last).trim());
                start = last;
            }
            last = end;
        }
        if (start < s.length()) out.add(s.substring(start).trim());
        return out;
    }

    public int getLineCount() { return lines.size(); }

    @Override public int getHeight() { return Math.round(lines.size() * lineH); }

    @Override public void draw(Canvas c) {
        Graphics2D g = c.graphics();
        g.setFont(paint.awtFont());
        g.setColor(new java.awt.Color(paint.getColor(), true));
        for (int i = 0; i < lines.size(); i++) g.drawString(lines.get(i), 0f, i * lineH + fm.getAscent());
    }

    public static final class Builder {
        private final CharSequence text;
        private final TextPaint paint;
        private final int width;
        private float add, mult = 1f;

        private Builder(CharSequence text, TextPaint paint, int width) {
            this.text = text;
            this.paint = paint;
            this.width = width;
        }

        public static Builder obtain(CharSequence source, int start, int end, TextPaint paint, int width) {
            return new Builder(source.subSequence(start, end), paint, width);
        }

        public Builder setAlignment(Alignment a) { return this; }

        public Builder setLineSpacing(float spacingAdd, float spacingMult) { add = spacingAdd; mult = spacingMult; return this; }

        public Builder setIncludePad(boolean pad) { return this; }

        public StaticLayout build() { return new StaticLayout(this); }
    }
}
