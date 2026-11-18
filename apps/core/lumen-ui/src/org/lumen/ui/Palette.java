package org.lumen.ui;

/**
 * One colour set of the Lumen 2.0 look (Home, Setup, menus): the design notes, the Светлая (light, the default)
 * and Тёмная (dark) columns. ARGB ints, plain Java, so code without resources and host tests use the same values.
 * apps/common/tokens.xml carries the same numbers as lumen_light_* and lumen_dark_* colours (test/ThemeTest checks
 * both stay equal). Pick the set with {@link LumenTheme#palette}.
 */
public final class Palette {
    public final boolean dark;
    /** The field: base tone, the lit top right corner, the bottom left edge. */
    public final int bg, bgGlow, bgLow;
    /** Tiles on the field (inputs, setup tiles) and a raised tile. */
    public final int surface, surface2;
    /** Menus, dialogs, the source list, and their line and outline. */
    public final int panel, panelLine;
    /** Text on the field and on tiles: titles, descriptions, meta. */
    public final int text1, text2, text3;
    /** Text on a panel. */
    public final int ptext1, ptext2, ptext3;
    /** Focused pill or list row fill and its text; a focused row inside a panel and its text. */
    public final int focus, onFocus, pfocus, onPfocus;
    /** The focus ring of a card and the gap between card and ring. */
    public final int ring, ringGap;
    /** Accent on a panel (check mark, link) and on the field. */
    public final int accent, accentOnBg;
    /** Progress bar and its track. */
    public final int progress, track;
    /** Error and success on the field and on a panel. */
    public final int error, errorPanel, ok, okPanel;
    /** Focus shadow colour, offset and blur (design px). */
    public final int shadow, shadowY, shadowBlur;
    /** Scrim over artwork (opaque rgb, apply with an alpha), the brand mark facets and core, the word mark. */
    public final int scrim, markA, markB, markCore, word;
    /** Hairline around app banners, the header pill, the active nav item and the nav texts. */
    public final int bannerLine, pill, navActive, navText, navActiveText;
    /** True: the hero is a rounded card on the field (light). False: full bleed, melting into the field (dark). */
    public final boolean heroCard;

    private Palette(boolean dark, int[] v) {
        this.dark = dark;
        int i = 0;
        bg = v[i++]; bgGlow = v[i++]; bgLow = v[i++]; surface = v[i++]; surface2 = v[i++]; panel = v[i++];
        panelLine = v[i++]; text1 = v[i++]; text2 = v[i++]; text3 = v[i++]; ptext1 = v[i++]; ptext2 = v[i++];
        ptext3 = v[i++]; focus = v[i++]; onFocus = v[i++]; pfocus = v[i++]; onPfocus = v[i++]; ring = v[i++];
        ringGap = v[i++]; accent = v[i++]; accentOnBg = v[i++]; progress = v[i++]; track = v[i++]; error = v[i++];
        errorPanel = v[i++]; ok = v[i++]; okPanel = v[i++]; shadow = v[i++]; shadowY = v[i++]; shadowBlur = v[i++];
        scrim = v[i++]; markA = v[i++]; markB = v[i++]; markCore = v[i++]; word = v[i++]; bannerLine = v[i++];
        pill = v[i++]; navActive = v[i++]; navText = v[i++]; navActiveText = v[i++];
        heroCard = !dark;
        if (i != v.length) throw new IllegalStateException("palette size " + v.length);
    }

    /** The light or the dark set, for code that knows the theme already (host tests, a module's own look). */
    public static Palette of(boolean dark) { return dark ? dark() : light(); }

    static Palette light() {
        return new Palette(false, new int[] {
                0xFFF1F2F4, 0xFFFFFFFF, 0xFFE6E8EC,             // bg, bgGlow, bgLow
                0xFFFFFFFF, 0xFFF6F7F9,                         // surface, surface2
                0xFFFFFFFF, 0xFFE3E6EC,                         // panel, panelLine
                0xFF12151B, 0xFF424955, 0xFF5B6270,             // text1..3
                0xFF12151B, 0xFF424955, 0xFF5B6270,             // ptext1..3
                0xFF12151B, 0xFFFFFFFF, 0xFF2F62D0, 0xFFFFFFFF, // focus, onFocus, pfocus, onPfocus
                0xFF12151B, 0xFFF1F2F4,                         // ring, ringGap
                0xFF2A5CC4, 0xFF2A5CC4,                         // accent, accentOnBg
                0xFF3B78E7, 0x66FFFFFF,                         // progress, track
                0xFFB3261E, 0xFFB3261E, 0xFF1E7A3C, 0xFF1E7A3C, // error, errorPanel, ok, okPanel
                0x3D101828, 20, 44,                             // shadow rgba(16,24,40,.24), y, blur
                0xFF090C14, 0xFF12151B, 0xFF4A505C, 0xFFF2B26B, 0xFF12151B, // scrim, mark A, B, core, word
                0x1412151B, 0x0F12151B, 0x1412151B, 0xFF424955, 0xFF12151B, // bannerLine, pill, navActive, navText(s)
        });
    }

    static Palette dark() {
        return new Palette(true, new int[] {
                0xFF0E1014, 0xFF161A21, 0xFF0B0C10,
                0xFF1B1E25, 0xFF242830,
                0xFF1E2128, 0xFF2E333C,
                0xFFF2F4F7, 0xFFB9BFC9, 0xFF8E95A1,
                0xFFF2F4F7, 0xFFB9BFC9, 0xFF8E95A1,
                0xFFF2F4F7, 0xFF0E1014, 0xFFF2F4F7, 0xFF0E1014,
                0xFFFFFFFF, 0xFF0E1014,
                0xFF6E9BF2, 0xFF6E9BF2,
                0xFFFFFFFF, 0x4CFFFFFF,
                0xFFFFB4AB, 0xFFFFB4AB, 0xFF9BE3B0, 0xFF9BE3B0,
                0x8C000000, 26, 56,                             // rgba(0,0,0,.55)
                0xFF07080A, 0xFFF4EFE6, 0xFFCFC7BA, 0xFFF2B26B, 0xFFF4EFE6,
                0x1AFFFFFF, 0x21FFFFFF, 0x2EFFFFFF, 0xC7FFFFFF, 0xFFFFFFFF,
        });
    }

    /** {@code color} with its alpha replaced by {@code a} (0..1). */
    public static int alpha(int color, float a) {
        int al = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (al << 24) | (color & 0x00FFFFFF);
    }

    /** Linear mix of two ARGB colours, {@code t} 0 = a, 1 = b (the 400 ms theme cross fade). */
    public static int mix(int a, int b, float t) {
        float u = Math.max(0f, Math.min(1f, t));
        int r = 0;
        for (int s = 0; s < 32; s += 8) {
            int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
            r |= (Math.round(ca + (cb - ca) * u) & 0xFF) << s;
        }
        return r;
    }
}
