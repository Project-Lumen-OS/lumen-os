package android.graphics;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

/** Host stub of Canvas drawing into a BufferedImage with Java2D (offscreen, no device). */
public class Canvas {
    private final Graphics2D g;
    private final Deque<Object[]> saved = new ArrayDeque<>();

    public Canvas(BufferedImage img) {
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /** Not Android: the Java2D target, for the host text layout. */
    public Graphics2D graphics() { return g; }

    public int save() {
        int n = saved.size();
        saved.push(new Object[]{g.getTransform(), g.getClip()});
        return n;
    }

    public void restore() {
        Object[] s = saved.pop();
        g.setTransform((AffineTransform) s[0]);
        g.setClip((Shape) s[1]);
    }

    public void restoreToCount(int n) { while (saved.size() > n) restore(); }

    public boolean clipPath(Path path) { g.clip(path.shape()); return true; }

    public void translate(float dx, float dy) { g.translate(dx, dy); }

    public void drawRect(float l, float t, float r, float b, Paint p) { paint(new Rectangle2D.Float(l, t, r - l, b - t), p); }

    public void drawRect(RectF r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }

    public void drawRoundRect(RectF r, float rx, float ry, Paint p) {
        paint(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), 2 * rx, 2 * ry), p);
    }

    public void drawCircle(float cx, float cy, float rad, Paint p) {
        paint(new Ellipse2D.Float(cx - rad, cy - rad, 2 * rad, 2 * rad), p);
    }

    public void drawPath(Path path, Paint p) { paint(path.shape(), p); }

    public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        g.setPaint(p.awt());
        g.setStroke(p.stroke());
        g.draw(new Line2D.Float(x0, y0, x1, y1));
    }

    public void drawText(String text, float x, float y, Paint p) {
        g.setPaint(p.awt());
        if (p instanceof android.text.TextPaint) g.setFont(((android.text.TextPaint) p).awtFont());
        g.drawString(text, x, y);
    }

    private void paint(Shape s, Paint p) {
        g.setPaint(p.awt());
        if (p.getStyle() == Paint.Style.STROKE) {
            g.setStroke(p.stroke());
            g.draw(s);
        } else {
            g.fill(s);
        }
    }
}
