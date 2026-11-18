package org.lumen.home.data;

import org.lumen.home.img.HeroArt;
import org.lumen.home.ui.Theme;
import org.lumen.home.ui.UiScale;

/**
 * The three UI modes of 1.0.1 (1080p @ 320, 2K @ 427 = the default, 4K @ 640; the same 960x540 dp):
 * Home's design px land on the same places in real px (with each mode's rounding), the TV safe area
 * holds, art is decoded for real pixels, and the memory budgets stay bounded. Theme's constants are
 * compile-time ints (read without loading Theme, whose interpolators need Android).
 */
public final class ResolutionTest {
    private static final long MB = 1024L * 1024;

    public static void run() {
        int[][] modes = UiScale.MODES;
        T.eq(modes.length, 3, "three UI modes");
        T.eq(modes[0][0] + "x" + modes[0][1], "1920x1080", "1080p");
        T.eq(modes[1][0] + "x" + modes[1][1], "2560x1440", "2K");
        T.eq(modes[2][0] + "x" + modes[2][1], "3840x2160", "4K");
        String[] safe = {"54", "72", "108"};
        String[] rowY = {"720", "960", "1440"};
        String[] card = {"392x220", "523x293", "784x440"};
        String[] heroCard = {"640x360", "853x480", "1280x720"};
        for (int i = 0; i < modes.length; i++) {
            int w = modes[i][0], h = modes[i][1], dpi = modes[i][2];
            float s = UiScale.scale(w, h);
            float density = dpi / 160f;
            String m = w + "x" + h + " ";
            // the same layout in dp: a design px is half a dp in every mode (427 dpi: 0.1 % off)
            T.ok(Math.abs(s * 2f / density - 1f) < 0.002f, m + "design px = half a dp (scale " + s + ", density " + density + ")");
            // 960x540 dp (427 dpi: 959.3 x 539.6, the nearest whole dpi to 426.67)
            float dpW = UiScale.DESIGN_W * s / density, dpH = UiScale.DESIGN_H * s / density;
            T.ok(Math.abs(dpW - 960f) < 1f && Math.abs(dpH - 540f) < 1f, m + "960x540 dp (got " + dpW + "x" + dpH + ")");
            // TV safe area: 5 % of the height
            int sa = UiScale.px(Theme.SAFE, s);
            T.eq(String.valueOf(sa), safe[i], m + "safe area px");
            T.ok(sa >= h * 0.05f - 0.5f, m + "safe area >= 5 % of the height");
            // the Витрина: the bottom line (chips, round buttons) grown to 1.06 and the dock's focused tile with its
            // name end inside the safe area; the dock starts at y 720 (960, 1440)
            int y = UiScale.px(org.lumen.ui.Grid.DOCK_Y, s);
            T.eq(String.valueOf(y), rowY[i], m + "dock y");
            int ch = UiScale.px(org.lumen.ui.Grid.CHIP_H, s);
            int bottom = UiScale.px(org.lumen.ui.Grid.CHIPS_Y, s) + ch + Math.round(ch * 0.06f / 2f);
            T.ok(bottom <= h - sa, m + "focused chip ends at y " + bottom + " <= " + (h - sa));
            int label = y + UiScale.px(org.lumen.ui.Grid.DOCK_H + org.lumen.ui.Grid.DOCK_LABEL_GAP + 32, s);
            T.ok(label <= UiScale.px(org.lumen.ui.Grid.CHIPS_Y, s), m + "dock name above the chips");
            // the weather and clock corner inside the safe area
            T.ok(UiScale.px(org.lumen.ui.Grid.STATUS_Y, s) >= sa, m + "status corner in the safe area");
            // art at real pixels
            T.eq(UiScale.px(org.lumen.ui.Tokens.CARD_W, s) + "x" + UiScale.px(org.lumen.ui.Tokens.CARD_H, s), card[i],
                    m + "16:9 row card art px (Theme.artW/artH)");
            T.eq(UiScale.px(640, s) + "x" + UiScale.px(360, s), heroCard[i], m + "hero 16:9 card box px");
            int mw = UiScale.masterW(w, h), mh = UiScale.masterH(w, h);
            T.ok(mw <= UiScale.ART_MAX_W && mh <= UiScale.ART_MAX_H && mw <= w && mh <= h, m + "masters within the screen and 2560x1440");
            // soft layers stay at 1080p's pixels, crisp ones follow the screen
            T.ok(UiScale.soft(s) == 1f && UiScale.crisp(s) == s, m + "soft layers at 1080p scale, crisp at " + s);
            // memory: the decoded-art budget holds the hero plus 16 posters and 4 banners
            long budget = UiScale.imageMemBytes(w, h);
            // the biggest hero bitmap: full-bleed from a screen-covering master, else (4K) the 16:9 card
            boolean fills = w <= mw * HeroArt.MAX_UPSCALE;
            long hero = fills ? (long) mw * mh * 4 : UiScale.px(640, s) * (long) UiScale.px(360, s) * 4;
            long need = hero + 16L * UiScale.px(208, s) * UiScale.px(312, s) * 4 + 4L * UiScale.px(272, s) * UiScale.px(153, s) * 4;
            T.ok(need <= budget, m + "image budget " + mb(budget) + " MiB holds hero + 16 posters + 4 banners (" + mb(need) + " MiB)");
            T.ok(budget <= 64 * MB, m + "image budget at most 64 MB");
            long disk = UiScale.diskBytes(w, h);
            T.ok(disk >= 64 * MB && disk <= 128 * MB, m + "disk cache " + mb(disk) + " MiB");
            System.out.println("mode " + m + "scale " + s + ": safe " + sa + " px, row card " + card[i] + ", hero card "
                    + heroCard[i] + ", masters " + mw + "x" + mh + ", hero bitmap <= " + mb(hero) + " MiB, image budget "
                    + mb(budget) + " MiB, disk " + mb(disk) + " MiB");
        }
        // 1080p keeps 1.0.1's numbers
        T.eq(UiScale.imageMemBytes(1920, 1080), 30 * MB, "1080p image budget 30 MB (1.0.1)");
        T.eq(UiScale.diskBytes(1920, 1080), 64 * MB, "1080p disk cache 64 MB (1.0.1)");
        T.eq(UiScale.imageMemBytes(3840, 2160), 64 * MB, "4K image budget capped at 64 MB");
    }

    private static String mb(long b) {
        return String.format(java.util.Locale.ROOT, "%.1f", b / (double) MB);
    }
}
