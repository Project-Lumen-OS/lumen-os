// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.lumen.menu.ShiftRange;
import org.lumen.projector.R;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * x50-feedback1 screens drawn by the REAL module views on host stubs (no device): the focus page's remote picture per
 * remote kind (QpdTip FOCUS_FRONT / FOCUS_SIDE), the four-corner keystone page (CornerView, stock KeyStoneWind look) and
 * the lens shift range picture (ShiftRangeView over ShiftRange, stock DisplacementCell). Pixel checks on each, the
 * stock position rule of ShiftRange, and with a PNG_DIR the review renders in English and Russian.
 *   java org.lumen.projector.panel.ScreensTest REPO_ROOT [PNG_DIR]
 */
public final class ScreensTest {
    static int fails, checks;
    static File root, out;
    static final Context CTX = new Context();
    static final int ACCENT = 0xFF3B78E7;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { fails++; System.out.println("FAIL screens: " + what); }
    }

    public static void main(String[] a) throws Exception {
        root = new File(a[0]);
        out = a.length > 1 ? new File(a[1]) : null;
        if (out != null) out.mkdirs();
        File res = new File(root, "apps/core/lumen-menu/res");
        CTX.DRAWABLES.put(R.drawable.lumen_focus_guide_remote_front, new VectorXml(new File(res, "drawable/lumen_focus_guide_remote_front.xml")));
        CTX.DRAWABLES.put(R.drawable.lumen_focus_guide_remote_side, new VectorXml(new File(res, "drawable/lumen_focus_guide_remote_side.xml")));
        shiftRule();
        for (String loc : new String[]{"values", "values-ru"}) {
            strings(new File(res, loc));
            String tag = loc.equals("values") ? "en" : "ru";
            focus(tag);
            corner(tag);
            shift(tag);
        }
        everyLocale(res);
        System.out.println(fails == 0 ? "ok   screens: " + checks + " checks" : "screens: " + fails + " of " + checks + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ strings of one language into the host Context
    static final Pattern STR = Pattern.compile("<string name=\"(\\w+)\">(.*?)</string>", Pattern.DOTALL);

    static void strings(File dir) throws Exception {
        Map<String, String> s = new HashMap<>();
        for (File f : new File(dir.getParentFile(), "values").listFiles()) {
            Matcher m = STR.matcher(org.lumen.testkit.Check.strings(new File(dir, f.getName())));
            while (m.find()) s.put(m.group(1), m.group(2).replace("\\'", "'").replace("\\\"", "\""));
        }
        CTX.STRINGS.clear();
        for (Field f : R.string.class.getFields()) {
            String v = s.get(f.getName());
            check(v != null, dir.getName() + ": " + f.getName());
            if (v != null) CTX.STRINGS.put(f.getInt(null), v);
        }
    }

    /** Both new texts in every language of the module, the front text never says "side". */
    static void everyLocale(File res) throws Exception {
        int n = 0;
        for (File d : res.listFiles()) {
            if (!d.getName().startsWith("values")) continue;
            n++;
            strings(d);
        }
        check(n == 18, "18 value folders, found " + n);
    }

    // ------------------------------------------------------------------ the focus page's remote picture
    static final int TIP_W = 660, TIP_IH = Math.round(TIP_W * 9f / 16f);

    static BufferedImage tip(HintPicture kind, String text) {
        QpdTip t = new QpdTip(CTX, kind, text);
        t.measure(View.MeasureSpec.makeMeasureSpec(TIP_W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        t.layout(0, 0, t.getMeasuredWidth(), t.getMeasuredHeight());
        BufferedImage img = new BufferedImage(TIP_W, Math.max(1, t.getMeasuredHeight()), BufferedImage.TYPE_INT_ARGB);
        fill(img, QpdStyle.PANEL);
        t.draw(new Canvas(img));
        return img;
    }

    /** The picture's pixel at a point of the 300 x 720 drawable viewport (QpdTip centres it in the 16:9 area). */
    static int remoteAt(BufferedImage img, float vx, float vy) {
        float s = Math.min(TIP_W / 300f, TIP_IH * 0.92f / 720f);
        int dw = Math.round(300 * s), dh = Math.round(720 * s), x = (TIP_W - dw) / 2, y = (TIP_IH - dh) / 2;
        return img.getRGB(Math.round(x + vx * s), Math.round(y + vy * s));
    }

    static void focus(String tag) throws Exception {
        check(HintPicture.of("focus_guide_front") == HintPicture.FOCUS_FRONT && HintPicture.of("focus_guide_side") == HintPicture.FOCUS_SIDE
                && HintPicture.of("focus_guide") == null, "focus hint tokens");
        String front = CTX.getString(R.string.lumen_qpd_tip_focus_guide_front), side = CTX.getString(R.string.lumen_qpd_tip_focus_guide);
        check(!front.equals(side), tag + ": the front text differs from the side one");
        if (tag.equals("en")) check(!front.contains("side") && side.contains("side"), "en: only the side text names the side");
        if (tag.equals("ru")) check(!front.contains("сбоку") && side.contains("сбоку"), "ru: only the side text names the side");
        BufferedImage f = tip(HintPicture.FOCUS_FRONT, front), s = tip(HintPicture.FOCUS_SIDE, side);
        // front key (150, 149) of the front picture; side key (223, 126) of the side picture (group translate -220 -52)
        check(isAccent(remoteAt(f, 150, 149)), tag + ": the front picture marks the key on the front, " + hex(remoteAt(f, 150, 149)));
        check(!isAccent(remoteAt(f, 252, 126)), tag + ": the front picture has nothing on the side");
        check(isAccent(remoteAt(s, 222, 126)), tag + ": the side picture marks the key on the side, " + hex(remoteAt(s, 222, 126)));
        check(!isAccent(remoteAt(s, 150, 149)), tag + ": the side picture has no front key");
        check(f.getHeight() > TIP_IH + 30, tag + ": the text under the picture");
        png(f, "focus_front_" + tag);
        if (tag.equals("en")) png(s, "focus_side_" + tag);
    }

    // ------------------------------------------------------------------ the four-corner keystone page
    static BufferedImage corner(CornerView v) {
        v.layout(0, 0, 1920, 1080);
        BufferedImage img = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        v.draw(new Canvas(img));
        return img;
    }

    static void corner(String tag) throws Exception {
        CornerView v = new CornerView(CTX);
        v.show(CornerView.TL, false, true, false, true);
        BufferedImage tl = corner(v);
        check(tl.getRGB(400, 700) == 0xFF4A4A4A, tag + ": the stock grey page, opaque, " + hex(tl.getRGB(400, 700)));
        check(isAccent(tl.getRGB(114, 106)), tag + ": the chosen corner TL in the accent");
        check(!isAccent(tl.getRGB(1806, 106)) && bright(tl.getRGB(1806, 106)), tag + ": the other corners white");
        check(isAccent(tl.getRGB(114, 152)) && isAccent(tl.getRGB(160, 106)), tag + ": TL arrows down and right shown");
        check(!isAccent(tl.getRGB(114, 60)) && !isAccent(tl.getRGB(68, 106)), tag + ": TL arrows up and left hidden (cannot move)");
        check(bright(tl.getRGB(960 + 160, 540)) || bright(tl.getRGB(960 + 159, 540)) || bright(tl.getRGB(960 + 161, 540)), tag + ": the centre circle");
        check(bright(tl.getRGB(960, 540)), tag + ": the centre cross");
        check(inkIn(tl, 400, 120, 1520, 260), tag + ": the title and the circle hint on top");
        check(inkIn(tl, 400, 862, 1520, 896), tag + ": the move keys line at the bottom");
        check(inkIn(tl, 400, 906, 1520, 940), tag + ": the reset and back line under it");
        check(v.cornerName().equals(CTX.getString(R.string.lumen_corner_tl)), tag + ": corner name TL");
        png(tl, "corner_tl_" + tag);
        v.show(CornerView.BR, true, true, true, true);
        BufferedImage br = corner(v);
        check(isAccent(br.getRGB(1806, 974)) && !isAccent(br.getRGB(114, 106)), tag + ": BR chosen, TL back to white");
        check(isAccent(br.getRGB(1806, 974 - 46)) && isAccent(br.getRGB(1806, 974 + 46)) && isAccent(br.getRGB(1806 - 46, 974))
                && isAccent(br.getRGB(1806 + 46, 974)), tag + ": all four BR arrows");
        check(v.cornerName().equals(CTX.getString(R.string.lumen_corner_br)), tag + ": corner name BR");
        v.show(5, false, false, false, false);
        check(v.cornerName().equals(CTX.getString(R.string.lumen_corner_tr)), "corner 5 wraps to TR");
        v.show(-1, false, false, false, false);
        check(v.cornerName().equals(CTX.getString(R.string.lumen_corner_bl)), "corner -1 wraps to BL");
        png(br, "corner_br_" + tag);
    }

    // ------------------------------------------------------------------ the lens shift range
    static void shiftRule() {
        ShiftRange c = ShiftRange.of("0,0", "0,0", 0);
        check(c.known() && c.x == 0 && c.y == 0 && !c.atLeft() && !c.atRight() && !c.atTop() && !c.atBottom(), "centre");
        check(ShiftRange.of("0.5,0", "0,0", 0).x == -0.5f, "desk: the first horizontal value is the left side");
        check(ShiftRange.of("0.5,0", "0,0", 1).x == 0.5f, "ceiling: the first horizontal value is the right side");
        check(ShiftRange.of("0,0.25", "0,0", 2).x == 0.25f, "rear desk = desk");
        check(ShiftRange.of("0,0", "1,0", 0).atTop() && ShiftRange.of("0,0", "0,1", 0).atBottom(), "desk: vertical first = up");
        check(ShiftRange.of("0,0", "1,0", 3).atBottom() && ShiftRange.of("0,0", "0,1", 3).atTop(), "rear ceiling flips vertical");
        check(ShiftRange.of("0,1", "0,0", 0).atRight() && ShiftRange.of("1,0", "0,0", 0).atLeft(), "desk ends");
        check(ShiftRange.of("0,0.98", "0,0", 0).atRight() == false, "0.98 is not the end yet");
        check(ShiftRange.of("-0.3,0", "0,0", 0).x == -0.3f, "stock abs of a signed value");
        check(ShiftRange.of("0,1.6", "0,0", 0).x == 1f, "held to the end");
        for (String bad : new String[]{null, "", "1", "a,b", "1,2,3", "NaN,0", "Infinity,0", " , "}) {
            check(!ShiftRange.of(bad, "0,0", 0).known() && !ShiftRange.of("0,0", bad, 0).known(), "unknown on '" + bad + "'");
        }
        check(!ShiftRange.UNKNOWN.atLeft() && !ShiftRange.UNKNOWN.atRight() && !ShiftRange.UNKNOWN.atTop()
                && !ShiftRange.UNKNOWN.atBottom(), "unknown: no spent arrow");
    }

    static BufferedImage range(ShiftRange at) {
        ShiftRangeView v = new ShiftRangeView(CTX);
        v.show(at);
        v.layout(0, 0, 520, 520);
        BufferedImage img = new BufferedImage(520, 520, BufferedImage.TYPE_INT_ARGB);
        fill(img, QpdStyle.PANEL);
        v.draw(new Canvas(img));
        return img;
    }

    static void shift(String tag) throws Exception {
        BufferedImage mid = range(ShiftRange.of("0,0", "0,0", 0)), right = range(ShiftRange.of("0,1", "0,0", 0)),
                corner = range(ShiftRange.of("1,0", "1,0", 0)), none = range(ShiftRange.UNKNOWN);
        int m = 260;
        check(isAccent(mid.getRGB(m - 145, m)) && isAccent(mid.getRGB(m + 145, m)), "centre: the picture frame around the middle");
        check(isAccent(right.getRGB(m + 72 + 145, m)) && !isAccent(right.getRGB(m - 145, m)), "right end: the frame moved 72");
        check(alpha(right, m + 240, m) < alpha(right, m - 240, m), "right end: the right arrow spent, the left one not");
        check(isAccent(corner.getRGB(m - 72 - 145, m - 96)) && isAccent(corner.getRGB(m - 72, m - 96 - 83)), "top left end: frame moved");
        check(alpha(corner, m, m - 240) < alpha(corner, m, m + 240) && alpha(corner, m - 240, m) < alpha(corner, m + 240, m),
                "top left end: up and left spent");
        check(alpha(none, m + 240, m) == alpha(mid, m + 240, m), "unknown: drawn as the centre, nothing spent");
        check(bright(mid.getRGB(m + 72, m - 5)) && !bright(mid.getRGB(m + 90, m - 5)) && mid.getRGB(m + 90, m - 5) != mid.getRGB(m + 81, m - 5),
                "the reachable ticks bright, the ones beyond dim");
        if (out != null) {
            png(page(tag, mid, "mid"), "shift_mid_" + tag);
            png(page(tag, right, "right"), "shift_right_end_" + tag);
            png(page(tag, corner, "corner"), "shift_top_left_end_" + tag);
        }
    }

    /** Review only: the range picture in the LensActivity card (title, tabs, picture, hint) with the X50 app texts. */
    static BufferedImage page(String tag, BufferedImage pic, String what) throws Exception {
        File d = new File(root, "apps/LumenProjector/host/activity/res/" + (tag.equals("en") ? "values" : "values-ru") + "/strings_zoom.xml");
        Map<String, String> s = new HashMap<>();
        Matcher mm = STR.matcher(new String(Files.readAllBytes(d.toPath()), StandardCharsets.UTF_8));
        while (mm.find()) s.put(mm.group(1), mm.group(2));
        BufferedImage img = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x2B3A55), 1920, 1080, new Color(0x1A1A22)));
        g.fillRect(0, 0, 1920, 1080);
        int cw = 760, ch = 640, cx = (1920 - cw) / 2, cy = 1080 - 48 - ch;
        g.setColor(new Color(0xF016171A, true));
        g.fillRoundRect(cx, cy, cw, ch, 56, 56);
        Font base = QpdStyle.font();
        g.setColor(Color.WHITE);
        centre(g, base.deriveFont(Font.BOLD, 30f), s.get("zs_title"), cy + 54);
        g.setFont(base.deriveFont(Font.BOLD, 26f));
        String a = s.get("zs_mode_size"), b = s.get("zs_mode_shift");
        int wa = g.getFontMetrics().stringWidth(a), wb = g.getFontMetrics().stringWidth(b), gap = 40;
        int x0 = (1920 - wa - wb - gap) / 2;
        g.setColor(new Color(255, 255, 255, 150));
        g.drawString(a, x0, cy + 104);
        g.setColor(new Color(ACCENT));
        g.drawString(b, x0 + wa + gap, cy + 104);
        g.drawImage(pic.getSubimage(0, 0, 520, 520), (1920 - 400) / 2, cy + 124, 400, 400, null);
        g.setColor(new Color(255, 255, 255, 190));
        centre(g, base.deriveFont(21f), s.get("zs_hint_shift").replace("&#183;", "·"), cy + 560);
        g.dispose();
        return img;
    }

    static void centre(Graphics2D g, Font f, String s, int y) {
        g.setFont(f);
        g.drawString(s, (1920 - g.getFontMetrics().stringWidth(s)) / 2, y);
    }

    // ------------------------------------------------------------------ pixels
    static void fill(BufferedImage img, int argb) {
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(argb, true));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
    }

    static int rgb(int c) { return c & 0xFFFFFF; }

    static String hex(int c) { return String.format("#%08X", c); }

    static boolean isAccent(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return b > 150 && b > r + 60 && b > g + 30;
    }

    static boolean bright(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return r > 150 && g > 150 && b > 150;
    }

    static int alpha(BufferedImage img, int x, int y) {
        int c = img.getRGB(x, y);
        return ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
    }

    static boolean inkIn(BufferedImage img, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y += 2) for (int x = x0; x < x1; x += 2) if (bright(img.getRGB(x, y))) return true;
        return false;
    }

    static void png(BufferedImage img, String name) throws Exception {
        if (out != null) ImageIO.write(img, "png", new File(out, name + ".png"));
    }
}
