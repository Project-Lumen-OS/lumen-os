// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.graphics.Canvas;
import android.view.View;

import org.lumen.projector.ui.Theme;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * The stock picture adjustment strip (AdjustStrip, the REAL view over the host stubs): three rows, the middle one the
 * setting being changed with its slider track or choice arrows, the neighbours muted, closed rows dimmer, the row
 * motion after UP / DOWN. java org.lumen.projector.panel.AdjustStripTest [PNG_DIR] (renders over a bright frame).
 */
public final class AdjustStripTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    static final int W = Math.round(Theme.pxf(null, AdjustStrip.W)), H = Math.round(Theme.pxf(null, AdjustStrip.H));

    static BufferedImage draw(AdjustStrip s) {
        s.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        s.layout(0, 0, W, H);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        s.draw(new Canvas(img));
        return img;
    }

    static float k() { return W / (float) AdjustStrip.W; }

    static int at(BufferedImage img, float dx, float dy) { return img.getRGB(Math.round(dx * k()), Math.round(dy * k())); }

    static boolean near(int a, int b) {
        for (int sh = 0; sh < 32; sh += 8) if (Math.abs((a >>> sh & 0xFF) - (b >>> sh & 0xFF)) > 24) return false;
        return true;
    }

    static AdjustStrip strip(AdjustStrip.Item p, AdjustStrip.Item c, AdjustStrip.Item n) {
        AdjustStrip s = new AdjustStrip(null);
        s.show(p, c, n);
        return s;
    }

    static AdjustStrip.Item slider(String t, int v, boolean on) { return AdjustStrip.Item.slider(t, 0, 100, v, true, Integer.toString(v), on); }

    public static void main(String[] a) throws Exception {
        float l = AdjustStrip.PAD + 28, r = AdjustStrip.W - AdjustStrip.PAD - 28, trackY = AdjustStrip.PAD + AdjustStrip.ROW_H + 84;
        AdjustStrip sl = strip(slider("Contrast", 50, true), slider("Brightness", 25, true), slider("Saturation", 50, true));
        BufferedImage img = draw(sl);
        check(near(QpdStyle.PANEL, at(img, AdjustStrip.W / 2f, 6)), "the strip is the panel colour");
        check(near(QpdStyle.ACCENT, at(img, l + (r - l) * 0.1f, trackY)), "the track is filled up to the value");
        check(!near(QpdStyle.ACCENT, at(img, l + (r - l) * 0.6f, trackY)), "and not past it");
        check(img.getRGB(0, 0) >>> 24 == 0, "rounded corners: the corner is see-through");
        AdjustStrip full = strip(null, slider("Brightness", 100, true), null);
        BufferedImage fi = draw(full);
        check(near(QpdStyle.ACCENT, at(fi, l + (r - l) * 0.9f, trackY)), "a full slider fills the track");
        check(near(QpdStyle.PANEL, at(fi, AdjustStrip.W / 2f, AdjustStrip.PAD + AdjustStrip.ROW_H / 2f)), "no row above the first one");
        AdjustStrip ch = strip(slider("Sharpness", 10, true), AdjustStrip.Item.choice("Colour temperature", "Standard", true), null);
        BufferedImage ci = draw(ch);
        check(near(QpdStyle.ACCENT, at(ci, r - 10, AdjustStrip.PAD + AdjustStrip.ROW_H + AdjustStrip.CUR_H / 2f)), "a choice shows its right arrow");
        check(!near(QpdStyle.ACCENT, at(ci, l + (r - l) * 0.1f, trackY)), "a choice has no track");
        AdjustStrip closed = strip(null, slider("Brightness", 50, false), null);
        check(!near(QpdStyle.ACCENT, at(draw(closed), l + (r - l) * 0.1f, trackY)), "a closed row draws no accent");
        AdjustStrip unknown = strip(null, AdjustStrip.Item.slider("Brightness", 0, 100, 0, false, "0", true), null);
        check(!unknown.current().enabled && unknown.current().value.equals("\u2026"), "a value not read yet: a dots value, closed");
        sl.setShift(1f);
        BufferedImage moved = draw(sl);
        check(!near(QpdStyle.ACCENT, at(moved, l + (r - l) * 0.1f, trackY)), "the rows slide during the motion");
        sl.setShift(0f);
        check(near(QpdStyle.ACCENT, at(draw(sl), l + (r - l) * 0.1f, trackY)), "and settle");
        if (a.length > 0) png(new File(a[0]), new AdjustStrip[]{sl, ch, closed},
                new String[]{"1_brightness_slider", "2_colour_temperature_choice", "3_closed_row"});
        System.out.println("AdjustStripTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    /** Each strip at the bottom centre of a bright 1920 x 1080 frame (the menu hidden, the film at full brightness). */
    static void png(File dir, AdjustStrip[] strips, String[] names) throws Exception {
        dir.mkdirs();
        for (int i = 0; i < strips.length; i++) {
            int fw = Math.round(Theme.pxf(null, 1920)), fh = Math.round(Theme.pxf(null, 1080));
            BufferedImage out = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setPaint(new GradientPaint(0, 0, new Color(0xE8B36A), fw, fh, new Color(0x3F7FB8)));
            g.fillRect(0, 0, fw, fh);
            g.drawImage(draw(strips[i]), (fw - W) / 2, fh - H - Math.round(Theme.pxf(null, AdjustStrip.BOTTOM)), null);
            g.dispose();
            ImageIO.write(out, "png", new File(dir, names[i] + ".png"));
        }
    }
}
