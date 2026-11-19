package org.lumen.ui;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Host checks of the shared theme: the choice, the Авто rule, tokens.xml equal to Palette, WCAG AA of every text pair,
 * 3:1 for the focus ring, the Sand light set (2026-11-19) and no light colour copies outside lumen-ui.
 */
public final class ThemeTest {
    private static int fails, checks;
    private static final TimeZone MSK = TimeZone.getTimeZone("Europe/Moscow");

    public static void main(String[] a) throws Exception {
        choice();
        autoWithoutPlace();
        autoWithPlace();
        geo();
        xmlEqualsPalette(a[0]);
        contrast("light", Palette.light());
        contrast("dark", Palette.dark());
        ring("light", Palette.light());
        ring("dark", Palette.dark());
        sand(Palette.light());
        fill(Palette.light(), Palette.dark());
        String gsi = Paths.get(a[0]).toAbsolutePath().getParent().getParent().getParent().toString();
        overlay(gsi + "/apps/LumenPackageInstallerOverlay/res/values/lumen_pkg_colors.xml", Palette.light());
        overlay(gsi + "/apps/LumenPackageInstallerOverlay/res/values-night/lumen_pkg_colors.xml", Palette.dark());
        noLightCopies(gsi);
        mix();
        System.out.println((fails == 0 ? "ok   " : "FAIL ") + "lumen-ui ThemeTest: " + checks + " checks, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static long msk(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(MSK);
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    static void choice() {
        check(ThemeMode.parse(null) == ThemeMode.LIGHT, "missing choice is light");
        check(ThemeMode.parse("junk") == ThemeMode.LIGHT, "unknown choice is light");
        check(ThemeMode.parse("dark") == ThemeMode.DARK && ThemeMode.parse(" auto ") == ThemeMode.AUTO, "parse");
        check(ThemeMode.LIGHT.next() == ThemeMode.DARK && ThemeMode.DARK.next() == ThemeMode.AUTO
                && ThemeMode.AUTO.next() == ThemeMode.LIGHT, "gear menu theme button cycles light, dark, auto");
        for (ThemeMode m : ThemeMode.values()) check(ThemeMode.parse(m.id) == m, "round trip " + m);
        long noon = msk(2026, 11, 18, 12, 0), night = msk(2026, 11, 18, 23, 0);
        check(!ThemeRule.dark(ThemeMode.LIGHT, night, MSK, null), "light stays light at night");
        check(ThemeRule.dark(ThemeMode.DARK, noon, MSK, null), "dark stays dark at noon");
        check(ThemeRule.nextChange(ThemeMode.LIGHT, noon, MSK, null) == Long.MAX_VALUE, "fixed choice never flips");
    }

    static void autoWithoutPlace() {
        check(ThemeRule.dark(ThemeMode.AUTO, msk(2026, 11, 18, 6, 59), MSK, null), "06:59 dark");
        check(!ThemeRule.dark(ThemeMode.AUTO, msk(2026, 11, 18, 7, 0), MSK, null), "07:00 light");
        check(!ThemeRule.dark(ThemeMode.AUTO, msk(2026, 11, 18, 18, 59), MSK, null), "18:59 light");
        check(ThemeRule.dark(ThemeMode.AUTO, msk(2026, 11, 18, 19, 0), MSK, null), "19:00 dark");
        check(ThemeRule.nextChange(ThemeMode.AUTO, msk(2026, 11, 18, 12, 0) + 1234, MSK, null)
                == msk(2026, 11, 18, 19, 0), "next flip at 19:00");
    }

    static void autoWithPlace() {
        double[] moscow = { 55.75, 37.62 };
        check(!ThemeRule.dark(ThemeMode.AUTO, msk(2026, 6, 21, 12, 0), MSK, moscow), "Moscow June noon light");
        check(!ThemeRule.dark(ThemeMode.AUTO, msk(2026, 6, 21, 20, 30), MSK, moscow), "Moscow June 20:30 still light");
        check(ThemeRule.dark(ThemeMode.AUTO, msk(2026, 6, 21, 23, 30), MSK, moscow), "Moscow June 23:30 dark");
        check(ThemeRule.dark(ThemeMode.AUTO, msk(2026, 12, 21, 17, 0), MSK, moscow), "Moscow December 17:00 dark");
        long next = ThemeRule.nextChange(ThemeMode.AUTO, msk(2026, 6, 21, 12, 0), MSK, moscow);
        check(next > msk(2026, 6, 21, 21, 0) && next < msk(2026, 6, 21, 21, 30), "Moscow June sunset 21:00..21:30");
        check(ThemeRule.dark(ThemeMode.AUTO, next, MSK, moscow) && !ThemeRule.dark(ThemeMode.AUTO, next - 60_000, MSK, moscow),
                "flip lands on the first dark minute");
        double[] polar = { 69.0, 33.1 };
        long dec = msk(2026, 12, 21, 12, 0);
        check(ThemeRule.dark(ThemeMode.AUTO, dec, MSK, polar), "polar night noon dark");
        long pn = ThemeRule.nextChange(ThemeMode.AUTO, dec, MSK, polar);
        check(pn > dec && pn - dec <= 27L * 3_600_000L, "polar night: check again within a day");
    }

    static void geo() {
        check(ThemeRule.parseGeo(null) == null && ThemeRule.parseGeo("") == null && ThemeRule.parseGeo("x,y") == null, "bad geo");
        check(ThemeRule.parseGeo("91,0") == null && ThemeRule.parseGeo("0,181") == null, "geo range");
        double[] g = ThemeRule.parseGeo(ThemeRule.geo(55.7512, 37.6184));
        check(g != null && g[0] == 55.75 && g[1] == 37.62, "geo round trip two decimals");
    }

    static void xmlEqualsPalette(String xmlPath) throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get(xmlPath)), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<(color|integer) name=\"lumen_(light|dark)_([a-z0-9_]+)\">#?([0-9A-Fa-f]+)</").matcher(xml);
        int n = 0;
        int fields = 0;
        for (Field f : Palette.class.getFields()) if (f.getType() == int.class) fields++;
        while (m.find()) {
            n++;
            Palette p = m.group(2).equals("light") ? Palette.light() : Palette.dark();
            String camel = camel(m.group(3));
            int want = m.group(1).equals("color") ? (int) Long.parseLong(m.group(4), 16) : Integer.parseInt(m.group(4));
            try {
                Field f = Palette.class.getField(camel);
                check(f.getInt(p) == want, "tokens.xml lumen_" + m.group(2) + "_" + m.group(3) + " equals Palette." + camel);
            } catch (NoSuchFieldException e) {
                check(false, "tokens.xml lumen_" + m.group(2) + "_" + m.group(3) + " has no Palette field");
            }
        }
        check(n == 2 * fields, "tokens.xml carries every Palette value of both themes (" + n + " of " + 2 * fields + ")");
    }

    static String camel(String s) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (char c : s.toCharArray()) {
            if (c == '_') up = true;
            else {
                b.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return b.toString();
    }

    static void contrast(String t, Palette p) {
        int[][] pairs = {
                { p.text1, p.bg }, { p.text2, p.bg }, { p.text3, p.bg }, { p.text1, p.surface }, { p.text2, p.surface },
                { p.text3, p.surface }, { p.onFocus, p.focus }, { p.ptext1, p.panel }, { p.ptext2, p.panel },
                { p.ptext3, p.panel }, { p.onPfocus, p.pfocus }, { p.accent, p.panel }, { p.accentOnBg, p.bg },
                { p.error, p.bg }, { p.errorPanel, p.panel }, { p.ok, p.bg }, { p.text1, p.bgGlow }, { p.navText, p.bg },
                { p.text3, p.bgGlow }, { p.text1, p.bgLow }, { p.text2, p.bgLow }, { p.ptext3, p.surface2 },
                { p.accent, p.surface }, { p.okPanel, p.panel }, { p.warn, p.panel }, { p.warn, over(p.panel, 0xF2, 0xFF000000) },
                { p.text1, p.surface2 }, { p.onFocus, p.accent },
                // the quick menu's quiet text (QpdStyle.light: panel ink at .68) on its card, the panel at .97 over black video
                { over(p.ptext1, p.dark ? 0x8F : 0xAD, over(p.panel, 0xF7, 0xFF000000)), over(p.panel, 0xF7, 0xFF000000) },
        };
        for (int i = 0; i < pairs.length; i++) {
            double r = ratio(pairs[i][0], pairs[i][1]);
            check(r >= 4.5, t + " pair " + i + " contrast " + String.format("%.2f", r) + " >= 4.5");
        }
    }

    static double lum(int c) {
        double[] ch = { (c >> 16) & 255, (c >> 8) & 255, c & 255 };
        double l = 0, w[] = { 0.2126, 0.7152, 0.0722 };
        for (int i = 0; i < 3; i++) {
            double s = ch[i] / 255.0;
            l += w[i] * (s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4));
        }
        return l;
    }

    static double ratio(int a, int b) {
        double x = lum(a), y = lum(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    /** {@code fg} with alpha {@code a} (0..255) painted over the opaque {@code bg}. */
    static int over(int fg, int a, int bg) {
        int r = 0xFF000000;
        for (int s = 0; s < 24; s += 8) {
            int f = (fg >>> s) & 0xFF, b = (bg >>> s) & 0xFF;
            r |= (Math.round((f * a + b * (255 - a)) / 255f) & 0xFF) << s;
        }
        return r;
    }

    /** The focus ring (non-text, WCAG 1.4.11): at least 3:1 on the field, its lit corner, tiles and panels. */
    static void ring(String t, Palette p) {
        int[] grounds = { p.bg, p.bgGlow, p.surface, p.surface2, p.panel };
        for (int i = 0; i < grounds.length; i++) {
            double r = ratio(p.focusRing, grounds[i]);
            check(r >= 3.0, t + " focus ring on ground " + i + " " + String.format("%.2f", r) + " >= 3");
        }
        check(ratio(p.ring, p.bg) >= 3.0, t + " Setup ring on the field >= 3");
    }

    /** The owner's pick Песок (light-palette/palettes.json sand) and the deeper ring blue on it, the dark ring kept. */
    static void sand(Palette p) {
        int[] want = { 0xFFCFC4B0, 0xFFD8CEBB, 0xFFC5BAA6, 0xFFDBD2C2, 0xFFE4DDD0, 0xFFD6CDBD, 0xFFB8AD99, 0xFF211C13,
                0xFF463F33, 0xFF544C3F, 0xFF2F62D0, 0xFF5E5545 };
        int[] got = { p.bg, p.bgGlow, p.bgLow, p.surface, p.surface2, p.panel, p.panelLine, p.text1, p.text2, p.text3,
                p.focusRing, p.markB };
        for (int i = 0; i < want.length; i++)
            check(got[i] == want[i], "Sand value " + i + " " + Integer.toHexString(got[i]) + " = " + Integer.toHexString(want[i]));
        check(Palette.dark().focusRing == Tokens_ACCENT, "dark focus ring keeps #3B78E7");
        // never white: the light field and tiles stay below L* 86 (a projector throws a white field at full power)
        check(lum(p.bg) < 0.62 && lum(p.surface) < 0.68 && lum(p.surface2) < 0.75, "light field and tiles are not white");
    }

    static final int Tokens_ACCENT = 0xFF3B78E7;

    /**
     * The quick menu's filled focus (2026-11-19): on the light menu white text and the value lines (.88 white,
     * lumen-menu QpdStyle.LIGHT_VALUE_FOCUSED) on the deeper Lumen blue reach AA; the dark menu keeps #3B78E7.
     */
    static void fill(Palette light, Palette dark) {
        check(light.focusFill == 0xFF2F62D0, "light focus fill is the deeper Lumen blue #2F62D0");
        check(dark.focusFill == Tokens_ACCENT, "dark focus fill keeps #3B78E7");
        double white = ratio(0xFFFFFFFF, light.focusFill);
        check(white >= 4.5, "light white on the focus fill " + String.format("%.2f", white) + " >= 4.5");
        double value = ratio(over(0xFFFFFFFF, 0xE0, light.focusFill), light.focusFill);
        check(value >= 4.5, "light focused value line on the focus fill " + String.format("%.2f", value) + " >= 4.5");
    }

    /** The package installer overlay (a static RRO, no code) carries the palette's numbers: checked here. */
    static void overlay(String path, Palette p) throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        String[] names = { "scrim", "card", "button", "button_focus", "text1", "text2", "ring", "accent" };
        int[] want = { Palette.alpha(p.bg, 0xF5 / 255f), p.surface, p.dark ? p.surface2 : p.pill,
                p.dark ? p.panelLine : p.surface2, p.text1, p.text2, p.focusRing, p.accent };
        for (int i = 0; i < names.length; i++) {
            Matcher m = Pattern.compile("name=\"lumen_pkg_" + names[i] + "\">#([0-9A-Fa-f]{8})<").matcher(xml);
            check(m.find() && (int) Long.parseLong(m.group(1), 16) == want[i],
                    (p.dark ? "dark" : "light") + " package installer lumen_pkg_" + names[i] + " equals the palette");
        }
    }

    /** The old light colours copied into apps (the white menu card, the grey signal chip) must not come back. */
    static void noLightCopies(String gsi) throws Exception {
        String[][] files = {
                { "apps/core/lumen-menu/src/org/lumen/projector/panel/QpdStyle.java", "0xF7ECEEF2", "0x8F17181B" },
                { "apps/core/lumen-signal/src/org/lumen/signal/SignalLook.java", "0x0F12151B", "0xFF9A5B00" },
                { "apps/core/lumen-source/src/org/lumen/source/SourceOsd.java", "p.dark ? p.onPfocus : 0xFFFFFFFF" },
                { "apps/LumenHome/src/org/lumen/home/ui/TileView.java", "Tokens.E1_COLOR", "0xFF0E1116, f);" },
                { "apps/LumenHome/src/org/lumen/home/ui/ListPanel.java", "0xFFDADCE0" },
                { "apps/LumenHome/res/drawable/bg_start.xml", "#00F4F5F8" },
                { "apps/LumenSetup/src/org/lumen/setup/steps/AliceStep.java", "roundRect(0xFFFFFFFF" },
                { "apps/LumenSetup/src/org/lumen/setup/steps/GoogleStep.java", "roundRect(0xFFFFFFFF" },
        };
        for (String[] f : files) {
            String src = new String(Files.readAllBytes(Paths.get(gsi, f[0])), StandardCharsets.UTF_8);
            for (int i = 1; i < f.length; i++) check(!src.contains(f[i]), f[0] + " has no light copy " + f[i]);
        }
        String qpd = new String(Files.readAllBytes(Paths.get(gsi, files[0][0])), StandardCharsets.UTF_8);
        check(qpd.contains("LIGHT = light(Palette.of(false))"), "the light quick menu is made from the lumen-ui palette");
        check(!qpd.contains("ACCENT = Theme.ACCENT") && qpd.contains("p.focusFill, LIGHT_VALUE_FOCUSED")
                && qpd.contains("LIGHT_VALUE_FOCUSED = 0xE0FFFFFF"), "the quick menu's filled focus follows the theme (Palette.focusFill)");
    }

    static void mix() {
        check(Palette.mix(0xFF000000, 0xFFFFFFFF, 0f) == 0xFF000000, "mix 0");
        check(Palette.mix(0xFF000000, 0xFFFFFFFF, 1f) == 0xFFFFFFFF, "mix 1");
        check(Palette.mix(0xFF000000, 0xFFFFFFFF, 0.5f) == 0xFF808080, "mix half");
        check(Palette.alpha(0xFF123456, 0.5f) == 0x80123456, "alpha");
        check(Palette.light().heroCard && !Palette.dark().heroCard, "hero card only in light");
    }
}
