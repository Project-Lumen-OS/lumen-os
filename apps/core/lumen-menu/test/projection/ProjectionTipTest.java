// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.graphics.Canvas;
import android.view.View;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectionSetup;
import org.lumen.menu.utils.Texts;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * The Projection hint (tester photo 2026-11-13: always the rear picture and text, whatever was chosen): the REAL QpdTip
 * drawn offscreen on host stubs of android.graphics over Java2D. Every setup draws its own picture and caption, a new
 * read-back redraws it, Auto shows no orientation, an unknown setup shows nothing; the All settings copy follows.
 * java ProjectionTipTest GSI_ROOT [PNG_DIR]
 */
public final class ProjectionTipTest {
    static final int BODY = 0xFFB9C2D0, LENS = 0xFF3B78E7, STAND = 0xFF5A6476, SCREEN = 0xFFEFF3FA, PERSON = 0xFFF4F4F6;
    static int fails, checks;
    static File root;
    static final android.content.Context CTX = new android.content.Context();

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { fails++; System.out.println("FAIL projection tip: " + what); }
    }

    public static void main(String[] a) throws Exception {
        root = new File(a[0]);
        setups();
        profiles();
        Map<String, Map<String, String>> res = strings();
        captions(res);
        Map<ProjectionSetup, String> en = new HashMap<>();
        for (ProjectionSetup s : ProjectionSetup.values()) en.put(s, caption(res.get("values"), s));
        for (ProjectionSetup s : ProjectionSetup.values()) picture(s, en.get(s));
        redraw(en);
        if (a.length > 1) png(new File(a[1]), en);
        System.out.println(fails == 0 ? "ok   projection tip: " + checks + " checks" : "projection tip: " + fails + " of " + checks + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ the setup of a read-back
    static MenuState st(String mode, Boolean rear) {
        MenuState.Builder b = MenuState.builder();
        if (mode != null) b.put(FeatureId.PROJECTION_MODE, mode);
        if (rear != null) b.on(FeatureId.PROJECTION_REAR, rear);
        return b.build();
    }

    static void setups() {
        String[][] want = {{"table", "false", "FRONT_TABLE"}, {"ceiling", "false", "FRONT_CEILING"},
                {"table", "true", "REAR_TABLE"}, {"ceiling", "true", "REAR_CEILING"},
                {"auto", "false", "AUTO_FRONT"}, {"auto", "true", "AUTO_REAR"}};
        for (String[] w : want) {
            ProjectionSetup s = ProjectionSetup.of(st(w[0], Boolean.valueOf(w[1])));
            check(s != null && s.name().equals(w[2]), "mode " + w[0] + " rear " + w[1] + " -> " + s + " (want " + w[2] + ")");
            check(s != null && s.rear == Boolean.parseBoolean(w[1]) && s.mount.equals(w[0])
                    && s.ceiling() == "ceiling".equals(w[0]) && s.auto() == "auto".equals(w[0])
                    && s.side().equals(s.rear ? "rear" : "front"), "fields of " + w[2]);
        }
        check(ProjectionSetup.of((MenuState) null) == null, "no read-back: no setup");
        check(ProjectionSetup.of(st("table", null)) == null, "rear unknown: no setup");
        check(ProjectionSetup.of(st(null, true)) == null, "mode unknown: no setup");
        check(ProjectionSetup.of(st("desk", false)) == null, "unknown mode token: no setup");
        check(HintPicture.of(ProjectionSetup.HINT) == HintPicture.PROJECTION && HintPicture.of("picture_mode") == HintPicture.PICTURE_MODE
                && HintPicture.of("curtain_fit") == null && HintPicture.of(null) == null, "hint tokens to pictures");
        check(MenuProfile.HINT_PICTURES.contains(ProjectionSetup.HINT), "the projection hint is a profile hint picture");
    }

    /** Every model with the rear switch names the projection picture there (stock tipsImg of rearProject on all five). */
    static void profiles() throws Exception {
        File[] devs = new File(root, "devices").listFiles();
        int n = 0;
        for (File d : devs == null ? new File[0] : devs) {
            File j = new File(d, "menu.json");
            if (!j.isFile()) continue;
            MenuProfile p = MenuProfile.parse(new String(Files.readAllBytes(j.toPath()), StandardCharsets.UTF_8));
            if (!p.has(FeatureId.PROJECTION_REAR)) continue;
            n++;
            check(ProjectionSetup.HINT.equals(p.hint(FeatureId.PROJECTION_REAR)), d.getName() + ": rear row without the projection picture");
            check(p.options(FeatureId.PROJECTION_MODE).equals(java.util.Arrays.asList("auto", "table", "ceiling")),
                    d.getName() + ": projection options are not the stock auto, table, ceiling");
        }
        check(n >= 6, "profiles with the rear switch: " + n);
    }

    // ------------------------------------------------------------------ captions in every language
    static final Pattern STR = Pattern.compile("<string name=\"(\\w+)\">(.*?)</string>");

    static Map<String, Map<String, String>> strings() throws Exception {
        Map<String, Map<String, String>> out = new HashMap<>();
        File[] dirs = new File(root, "apps/core/lumen-menu/res").listFiles((x, n) -> n.startsWith("values"));
        for (File d : dirs == null ? new File[0] : dirs) {
            File f = new File(d, "lumen_strings_qpd.xml");
            Map<String, String> m = new HashMap<>();
            if (f.isFile()) {
                Matcher x = STR.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                while (x.find()) m.put(x.group(1), x.group(2).replace("\\'", "'").replace("&amp;", "&"));
            }
            out.put(d.getName(), m);
        }
        return out;
    }

    /** MenuLabels.tipMount / tipSide as the source maps them: token -> string name. */
    static Map<String, String> labels(String fn) throws Exception {
        String src = new String(Files.readAllBytes(new File(root,
                "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuLabels.java").toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("static int " + fn + "\\(String[^)]*\\) \\{(.*?)\\n    \\}", Pattern.DOTALL).matcher(src);
        Map<String, String> out = new HashMap<>();
        if (!m.find()) return out;
        Matcher c = Pattern.compile("case \"([^\"]+)\": return R\\.string\\.(\\w+);").matcher(m.group(1));
        while (c.find()) out.put(c.group(1), c.group(2));
        return out;
    }

    static Map<String, String> mount, side;

    static String caption(Map<String, String> r, ProjectionSetup s) {
        return Texts.sentences(r.get(mount.get(s.mount)), r.get(side.get(s.side())));
    }

    static void captions(Map<String, Map<String, String>> res) throws Exception {
        mount = labels("tipMount");
        side = labels("tipSide");
        check(mount.size() == 3 && "lumen_qpd_tip_table".equals(mount.get("table")) && "lumen_qpd_tip_ceiling".equals(mount.get("ceiling"))
                && "lumen_qpd_tip_auto".equals(mount.get("auto")), "MenuLabels.tipMount " + mount);
        check(side.size() == 2 && "lumen_qpd_tip_front".equals(side.get("front")) && "lumen_qpd_tip_rear".equals(side.get("rear")),
                "MenuLabels.tipSide " + side);
        check(res.size() == 18, res.size() + " values dirs, want 18 (17 languages, pt and pt-rBR)");
        for (Map.Entry<String, Map<String, String>> e : res.entrySet()) {
            for (String n : new String[]{"lumen_qpd_tip_table", "lumen_qpd_tip_ceiling", "lumen_qpd_tip_auto", "lumen_qpd_tip_front", "lumen_qpd_tip_rear"}) {
                String t = e.getValue().get(n);
                check(t != null && !t.trim().isEmpty(), e.getKey() + ": no " + n);
                if (t == null) continue;
                check(!t.matches(".*[\u2014\u2013;\uff1b].*"), e.getKey() + ": " + n + " has a dash or a semicolon");
            }
            List<String> seen = new ArrayList<>();
            for (ProjectionSetup s : ProjectionSetup.values()) {
                String c = caption(e.getValue(), s);
                check(c != null && !seen.contains(c), e.getKey() + ": caption of " + s + " missing or equal to another one");
                seen.add(c);
            }
        }
        Map<String, String> en = res.get("values");
        String[][] want = {
                {"FRONT_TABLE", "The projector stands on a table. You and the projector are on the same side of the screen."},
                {"FRONT_CEILING", "The projector hangs upside down from the ceiling. You and the projector are on the same side of the screen."},
                {"REAR_TABLE", "The projector stands on a table. You and the projector are on opposite sides of the screen."},
                {"REAR_CEILING", "The projector hangs upside down from the ceiling. You and the projector are on opposite sides of the screen."},
                {"AUTO_FRONT", "The projector sees whether it stands or hangs and turns the picture by itself. You and the projector are on the same side of the screen."},
                {"AUTO_REAR", "The projector sees whether it stands or hangs and turns the picture by itself. You and the projector are on opposite sides of the screen."}};
        for (String[] w : want) check(w[1].equals(caption(en, ProjectionSetup.valueOf(w[0]))), "English caption of " + w[0] + ": " + caption(en, ProjectionSetup.valueOf(w[0])));
        String zh = caption(res.get("values-zh-rCN"), ProjectionSetup.REAR_CEILING);
        check(zh != null && !zh.contains(" "), "Chinese sentences joined without a space: " + zh);
        check("A. B.".equals(Texts.sentences("A.", "B.")) && "\u4e00\u3002\u4e8c\u3002".equals(Texts.sentences("\u4e00\u3002", "\u4e8c\u3002"))
                && "B.".equals(Texts.sentences(null, "B.")) && "A.".equals(Texts.sentences("A.", "")), "Texts.sentences");
    }

    // ------------------------------------------------------------------ the picture
    static final int W = 660;                    // the 440 px popup at 1.5 (Theme.SCALE in run.sh)

    static QpdTip tip(ProjectionSetup s, String caption) {
        QpdTip t = new QpdTip(CTX, HintPicture.PROJECTION, null);
        t.show(s, caption);
        return t;
    }

    static BufferedImage draw(QpdTip t) {
        t.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        t.layout(0, 0, t.getMeasuredWidth(), t.getMeasuredHeight());
        BufferedImage img = new BufferedImage(W, Math.max(1, t.getMeasuredHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(QpdStyle.PANEL, true));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
        t.draw(new Canvas(img));
        return img;
    }

    static final int IH = Math.round(W * 9f / 16f);

    static int at(BufferedImage img, double fx, double fy) { return img.getRGB((int) Math.round(fx * W), (int) Math.round(fy * IH)); }

    static int blue(int c) { return c & 0xFF; }

    static int red(int c) { return (c >> 16) & 0xFF; }

    /** Body centre x of the projector: left of the screen (front) or behind it (rear). */
    static double bodyX(boolean rear) { return rear ? 0.82 : 0.28; }

    static void picture(ProjectionSetup s, String caption) {
        QpdTip t = tip(s, caption);
        check(t.getVisibility() == View.VISIBLE && t.setup() == s && caption.contentEquals(t.text()), s + ": shown with its caption");
        BufferedImage img = draw(t);
        check(img.getHeight() > IH + 20, s + ": caption below the picture");
        double bx = bodyX(s.rear), ox = bodyX(!s.rear);
        check(at(img, 0.55, 0.47) == SCREEN, s + ": screen in the middle");
        check(at(img, 0.08, 0.42) == PERSON && at(img, 0.08, 0.7) == PERSON, s + ": viewer on the left");
        check(at(img, ox, 0.26) != BODY && at(img, ox, 0.6) != BODY, s + ": a projector on the wrong side of the screen");
        if (s.auto()) {
            for (double y : new double[]{0.26, 0.6}) {
                int c = at(img, bx, y);
                check(c != BODY && red(c) > 0x40 && red(c) < 0xA0, s + ": faint projector at " + y + " (" + Integer.toHexString(c) + ")");
            }
            double arc = (s.rear ? 0.74 - 0.04 : 0.36 + 0.04) + (s.rear ? -1 : 1) * (0.17 * IH) / W;
            check(blue(at(img, arc, 0.43)) > 0xB0, s + ": no turn arrow");
            check(blue(at(img, beamX(s.rear), 0.5)) < 0x80, s + ": a beam under Auto");
        } else {
            double y = s.ceiling() ? 0.26 : 0.6, other = s.ceiling() ? 0.6 : 0.26;
            check(at(img, bx, y) == BODY, s + ": projector body at " + y);
            check(at(img, bx, other) != BODY, s + ": projector also at " + other);
            check(at(img, s.ceiling() ? bx : bx, s.ceiling() ? 0.16 : 0.672) == STAND, s + ": " + (s.ceiling() ? "no ceiling mount" : "no table"));
            int lens = at(img, (s.rear ? 0.74 : 0.36) + (s.rear ? 12.0 * 1.5 : -12.0 * 1.5) / W, y);
            check(lens == LENS, s + ": lens not on the screen side (" + Integer.toHexString(lens) + ")");
            int beam = at(img, beamX(s.rear), 0.5);
            check(blue(beam) > 0x90 && blue(beam) > red(beam) + 0x30, s + ": no beam on the screen (" + Integer.toHexString(beam) + ")");
        }
    }

    static double beamX(boolean rear) { double near = rear ? 0.74 : 0.36; return near + (0.55 - near) * 0.8; }

    /** A changed read-back redraws the same view (the bug: it never did); the same one does nothing; the copy follows. */
    static void redraw(Map<ProjectionSetup, String> en) {
        QpdTip t = new QpdTip(CTX, HintPicture.PROJECTION, null);
        t.show(ProjectionSetup.of((MenuState) null), null);
        check(t.getVisibility() == View.GONE, "unknown setup: hint hidden");
        // the tester's sequence: Auto with rear off, then Table, the rear switch, Ceiling, back to Auto
        Object[][] steps = {{"auto", false}, {"table", false}, {"table", true}, {"ceiling", true}, {"ceiling", false}, {"auto", true}};
        BufferedImage prev = null;
        for (Object[] step : steps) {
            ProjectionSetup s = ProjectionSetup.of(st((String) step[0], (Boolean) step[1]));
            int inv = t.invalidates, lay = t.layoutRequests;
            t.show(s, en.get(s));
            check(t.invalidates > inv && t.layoutRequests > lay, "read-back " + step[0] + "/" + step[1] + ": no redraw");
            check(t.getVisibility() == View.VISIBLE && t.setup() == s && en.get(s).contentEquals(t.text()), "read-back " + s + ": not shown");
            BufferedImage img = draw(t);
            if (prev != null) check(differs(prev, img), "read-back " + s + ": the same picture as before");
            prev = img;
            inv = t.invalidates;
            t.show(s, en.get(s));
            check(t.invalidates == inv, s + " again: a needless redraw");
        }
        QpdTip copy = t.copyFor("Rear projection");
        check(copy.setup() == t.setup() && copy.text().toString().equals(t.text().toString()) && copy.getVisibility() == View.VISIBLE,
                "All settings copy starts as its hint");
        int inv = copy.invalidates;
        t.show(ProjectionSetup.FRONT_CEILING, en.get(ProjectionSetup.FRONT_CEILING));
        check(copy.setup() == ProjectionSetup.FRONT_CEILING && copy.invalidates > inv
                && en.get(ProjectionSetup.FRONT_CEILING).equals(copy.text().toString()), "All settings copy does not follow a change");
        t.show(null, null);
        check(t.getVisibility() == View.GONE && copy.getVisibility() == View.GONE, "unknown setup: copy not hidden");
        t.show(ProjectionSetup.REAR_TABLE, en.get(ProjectionSetup.REAR_TABLE));
        check(t.getVisibility() == View.VISIBLE && copy.getVisibility() == View.VISIBLE, "known again: not shown again");
        inv = t.invalidates;
        t.show(ProjectionSetup.FRONT_TABLE, en.get(ProjectionSetup.REAR_TABLE));
        check(t.setup() == ProjectionSetup.FRONT_TABLE && t.invalidates > inv, "another setup under the same caption: not redrawn");
        QpdTip pm = new QpdTip(CTX, HintPicture.PICTURE_MODE, "Choose a mode for what you watch.");
        check(pm.getVisibility() == View.VISIBLE && pm.setup() == null && draw(pm).getHeight() > IH, "picture mode hint still drawn");
    }

    static boolean differs(BufferedImage a, BufferedImage b) {
        int n = 0;
        for (int y = 0; y < Math.min(IH, Math.min(a.getHeight(), b.getHeight())); y += 3)
            for (int x = 0; x < W; x += 3) if (a.getRGB(x, y) != b.getRGB(x, y)) n++;
        return n > 200;
    }

    // ------------------------------------------------------------------ review PNGs
    static void png(File dir, Map<ProjectionSetup, String> en) throws Exception {
        dir.mkdirs();
        String[][] names = {{"FRONT_TABLE", "1_front_table", "Projection mode: Table, Rear projection: off"},
                {"FRONT_CEILING", "2_front_ceiling", "Projection mode: Ceiling, Rear projection: off"},
                {"REAR_TABLE", "3_rear_table", "Projection mode: Table, Rear projection: on"},
                {"REAR_CEILING", "4_rear_ceiling", "Projection mode: Ceiling, Rear projection: on"},
                {"AUTO_FRONT", "5_auto_front", "Projection mode: Auto, Rear projection: off"},
                {"AUTO_REAR", "6_auto_rear", "Projection mode: Auto, Rear projection: on"}};
        List<BufferedImage> all = new ArrayList<>();
        for (String[] n : names) {
            BufferedImage tipImg = draw(tip(ProjectionSetup.valueOf(n[0]), en.get(ProjectionSetup.valueOf(n[0]))));
            int head = 48, pad = 24;
            BufferedImage out = new BufferedImage(W + 2 * pad, tipImg.getHeight() + head + pad, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0xFF16171A));
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.setColor(new Color(0xFFB0B4BC));
            g.setFont(QpdStyle.font().deriveFont(Font.BOLD, 20f));
            g.drawString(n[2], pad, 32);
            g.drawImage(tipImg, pad, head, null);
            g.dispose();
            ImageIO.write(out, "png", new File(dir, n[1] + ".png"));
            all.add(out);
        }
        int cw = all.get(0).getWidth(), ch = 0;
        for (BufferedImage b : all) ch = Math.max(ch, b.getHeight());
        BufferedImage sheet = new BufferedImage(cw * 2, ch * 3, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0xFF16171A));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < all.size(); i++) g.drawImage(all.get(i), (i % 2) * cw, (i / 2) * ch, null);
        g.dispose();
        ImageIO.write(sheet, "png", new File(dir, "0_all_states.png"));
        System.out.println("png: " + (all.size() + 1) + " files in " + dir);
    }
}
