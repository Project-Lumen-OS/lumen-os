package android.graphics;

import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Host stub of Path over Java2D (Android angles: 0 = 3 o'clock, positive = clockwise on screen). */
public class Path {
    public enum Direction { CW, CCW }

    private Path2D.Float p = new Path2D.Float();

    public void reset() { p = new Path2D.Float(); }

    public void moveTo(float x, float y) { p.moveTo(x, y); }

    public void lineTo(float x, float y) { p.lineTo(x, y); }

    public void quadTo(float x1, float y1, float x2, float y2) { p.quadTo(x1, y1, x2, y2); }

    public void close() { p.closePath(); }

    public void addRoundRect(RectF r, float rx, float ry, Direction d) {
        p.append(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), 2 * rx, 2 * ry), false);
    }

    public void arcTo(RectF oval, float startAngle, float sweepAngle) {
        p.append(new Arc2D.Float(oval.left, oval.top, oval.width(), oval.height(), -startAngle, -sweepAngle, Arc2D.OPEN), true);
    }

    Shape shape() { return p; }
}
