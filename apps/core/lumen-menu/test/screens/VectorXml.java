package org.lumen.projector.panel;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A module vector drawable drawn on the host: viewport, px size, one group translation, each path's fill and stroke
 * with their alphas, SVG path data M L H V C Q A Z (absolute and relative). Enough for the remote pictures.
 */
final class VectorXml extends Drawable {
    private static final Pattern NUM = Pattern.compile("[MmLlHhVvCcQqAaZz]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");
    private final float vw, vh, w, h, tx, ty;
    private final List<Path2D.Float> paths = new ArrayList<>();
    private final List<long[]> colors = new ArrayList<>();    // fill argb, stroke argb (-1 = none)
    private final List<float[]> styles = new ArrayList<>();   // fill alpha, stroke width, stroke alpha
    private int l, t, r, b;

    VectorXml(File xml) throws Exception {
        String s = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8).replaceAll("<!--.*?-->", "");
        vw = attr(s, "viewportWidth", 24);
        vh = attr(s, "viewportHeight", 24);
        w = attr(s, "android:width", vw);
        h = attr(s, "android:height", vh);
        tx = attr(s, "translateX", 0);
        ty = attr(s, "translateY", 0);
        Matcher m = Pattern.compile("<path\\b(.*?)/>", Pattern.DOTALL).matcher(s);
        while (m.find()) {
            String p = m.group(1);
            paths.add(parse(str(p, "pathData")));
            colors.add(new long[]{color(str(p, "fillColor")), color(str(p, "strokeColor"))});
            styles.add(new float[]{attr(p, "fillAlpha", 1), attr(p, "strokeWidth", 0), attr(p, "strokeAlpha", 1)});
        }
    }

    private static String str(String s, String name) {
        Matcher m = Pattern.compile("android:" + name + "=\"([^\"]*)\"").matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static float attr(String s, String name, float def) {
        Matcher m = Pattern.compile((name.contains(":") ? name : "android:" + name) + "=\"([-0-9.]+)(px|dp)?\"").matcher(s);
        return m.find() ? Float.parseFloat(m.group(1)) : def;
    }

    private static long color(String c) {
        if (c == null) return -1;
        long v = Long.parseLong(c.substring(1), 16);
        if (c.length() == 7) v |= 0xFF000000L;
        return v;
    }

    static Path2D.Float parse(String d) {
        Path2D.Float p = new Path2D.Float();
        Matcher m = NUM.matcher(d);
        List<String> tok = new ArrayList<>();
        while (m.find()) tok.add(m.group());
        int i = 0;
        char cmd = 'M';
        float x = 0, y = 0, sx = 0, sy = 0;
        while (i < tok.size()) {
            String k = tok.get(i);
            if (Character.isLetter(k.charAt(0))) { cmd = k.charAt(0); i++; if (cmd == 'Z' || cmd == 'z') { p.closePath(); x = sx; y = sy; continue; } }
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? x : 0, oy = rel ? y : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M': x = ox + f(tok, i++); y = oy + f(tok, i++); p.moveTo(x, y); sx = x; sy = y; cmd = rel ? 'l' : 'L'; break;
                case 'L': x = ox + f(tok, i++); y = oy + f(tok, i++); p.lineTo(x, y); break;
                case 'H': x = ox + f(tok, i++); p.lineTo(x, y); break;
                case 'V': y = oy + f(tok, i++); p.lineTo(x, y); break;
                case 'C': {
                    float x1 = ox + f(tok, i++), y1 = oy + f(tok, i++), x2 = ox + f(tok, i++), y2 = oy + f(tok, i++);
                    x = ox + f(tok, i++); y = oy + f(tok, i++); p.curveTo(x1, y1, x2, y2, x, y); break;
                }
                case 'Q': {
                    float x1 = ox + f(tok, i++), y1 = oy + f(tok, i++);
                    x = ox + f(tok, i++); y = oy + f(tok, i++); p.quadTo(x1, y1, x, y); break;
                }
                case 'A': {
                    float rx = f(tok, i++), ry = f(tok, i++), rot = f(tok, i++);
                    boolean large = f(tok, i++) != 0, sweep = f(tok, i++) != 0;
                    float x2 = ox + f(tok, i++), y2 = oy + f(tok, i++);
                    arc(p, x, y, rx, ry, rot, large, sweep, x2, y2);
                    x = x2; y = y2; break;
                }
                default: throw new IllegalArgumentException("path command " + cmd);
            }
        }
        return p;
    }

    private static float f(List<String> t, int i) { return Float.parseFloat(t.get(i)); }

    /** SVG endpoint arc to Java2D (W3C SVG implementation notes F.6.5). */
    private static void arc(Path2D.Float p, float x1, float y1, float rx, float ry, float rotDeg, boolean large,
                            boolean sweep, float x2, float y2) {
        if (rx == 0 || ry == 0) { p.lineTo(x2, y2); return; }
        double phi = Math.toRadians(rotDeg), cos = Math.cos(phi), sin = Math.sin(phi);
        double dx = (x1 - x2) / 2.0, dy = (y1 - y2) / 2.0;
        double x1p = cos * dx + sin * dy, y1p = -sin * dx + cos * dy;
        double arx = Math.abs(rx), ary = Math.abs(ry);
        double lam = (x1p * x1p) / (arx * arx) + (y1p * y1p) / (ary * ary);
        if (lam > 1) { arx *= Math.sqrt(lam); ary *= Math.sqrt(lam); }
        double num = arx * arx * ary * ary - arx * arx * y1p * y1p - ary * ary * x1p * x1p;
        double den = arx * arx * y1p * y1p + ary * ary * x1p * x1p;
        double co = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
        double cxp = co * arx * y1p / ary, cyp = -co * ary * x1p / arx;
        double cx = cos * cxp - sin * cyp + (x1 + x2) / 2.0, cy = sin * cxp + cos * cyp + (y1 + y2) / 2.0;
        double a1 = Math.atan2((y1p - cyp) / ary, (x1p - cxp) / arx);
        double a2 = Math.atan2((-y1p - cyp) / ary, (-x1p - cxp) / arx);
        double da = a2 - a1;
        if (sweep && da < 0) da += 2 * Math.PI;
        if (!sweep && da > 0) da -= 2 * Math.PI;
        Arc2D.Double arc = new Arc2D.Double(-arx, -ary, 2 * arx, 2 * ary, -Math.toDegrees(a1), -Math.toDegrees(da), Arc2D.OPEN);
        AffineTransform at = new AffineTransform();
        at.translate(cx, cy);
        at.rotate(phi);
        p.append(at.createTransformedShape(arc).getPathIterator(null), true);
    }

    @Override public int getIntrinsicWidth() { return Math.round(w); }

    @Override public int getIntrinsicHeight() { return Math.round(h); }

    @Override public void setBounds(int left, int top, int right, int bottom) { l = left; t = top; r = right; b = bottom; }

    @Override public void draw(Canvas c) {
        Graphics2D g = (Graphics2D) c.graphics().create();
        g.translate(l, t);
        g.scale((r - l) / vw, (b - t) / vh);
        g.translate(tx, ty);
        for (int i = 0; i < paths.size(); i++) {
            float[] st = styles.get(i);
            long[] co = colors.get(i);
            if (co[0] >= 0 && (co[0] >>> 24) != 0) {
                g.setColor(argb((int) co[0], st[0]));
                g.fill(paths.get(i));
            }
            if (co[1] >= 0 && st[1] > 0) {
                g.setColor(argb((int) co[1], st[2]));
                g.setStroke(new BasicStroke(st[1], BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.draw(paths.get(i));
            }
        }
        g.dispose();
    }

    private static Color argb(int c, float alpha) {
        int a = Math.round(((c >>> 24) & 0xFF) * alpha);
        return new Color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, a);
    }
}
