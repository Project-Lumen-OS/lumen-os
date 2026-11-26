// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui;

/**
 * One colour set of the Lumen 2.0 look (Home, Setup, menus): the Светлая (light, the default) set is the owner's pick
 * Песок (Sand, 2026-11-19, ~/lumen-build-tmp/light-palette: a warm field at L* 80, never white, because a projector
 * throws a white field at full power), Тёмная (dark) is the design notes ARGB ints, plain Java, so code without resources and host tests use the same values.
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
    /**
     * The focus ring every focusable of Home and the quick menu wears (Lumen blue: #3B78E7 on the dark field, the deeper
     * #2F62D0 on the light one, where the brighter blue gives only 2.4:1), the warning amber of overlays, the resting
     * elevation shadow of light tiles (the dark theme draws a hairline instead, 0).
     */
    public final int focusRing, warn, elevation;
    /**
     * The filled focus of the quick menu (a focused tile, popup row, rail item, the editor's chips) under white text:
     * the Lumen blue #3B78E7 on the dark menu, the deeper #2F62D0 on the light one, where white on the brighter blue
     * gives only 4.18:1 (2026-11-19: AA there, the dark menu unchanged).
     */
    public final int focusFill;
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
        pill = v[i++]; navActive = v[i++]; navText = v[i++]; navActiveText = v[i++]; focusRing = v[i++]; warn = v[i++];
        elevation = v[i++]; focusFill = v[i++];
        heroCard = !dark;
        if (i != v.length) throw new IllegalStateException("palette size " + v.length);
    }

    /** The light or the dark set, for code that knows the theme already (host tests, a module's own look). */
    public static Palette of(boolean dark) { return dark ? dark() : light(); }

    static Palette light() {
        return new Palette(false, new int[] {
                0xFFCFC4B0, 0xFFD8CEBB, 0xFFC5BAA6,             // bg, bgGlow, bgLow
                0xFFDBD2C2, 0xFFE4DDD0,                         // surface, surface2
                0xFFD6CDBD, 0xFFB8AD99,                         // panel, panelLine
                0xFF211C13, 0xFF463F33, 0xFF544C3F,             // text1..3
                0xFF211C13, 0xFF463F33, 0xFF544C3F,             // ptext1..3
                0xFF211C13, 0xFFE4DDD0, 0xFF2F62D0, 0xFFFFFFFF, // focus, onFocus, pfocus, onPfocus
                0xFF2F62D0, 0xFFCFC4B0,                         // ring, ringGap
                0xFF1D4AA3, 0xFF1D4AA3,                         // accent, accentOnBg (AA on the field and panel)
                0xFF3B78E7, 0x66FFFFFF,                         // progress, track
                0xFF962019, 0xFF962019, 0xFF155A2C, 0xFF155A2C, // error, errorPanel, ok, okPanel
                0x421E1810, 18, 40,                             // shadow rgba(30,24,16,.26), y, blur
                0xFF0E0B07, 0xFF211C13, 0xFF5E5545, 0xFFF2B26B, 0xFF211C13, // scrim, mark A, B, core, word
                0x14000000, 0x0F211C13, 0x14211C13, 0xFF463F33, 0xFF211C13, // bannerLine, pill, navActive, navText(s)
                0xFF2F62D0, 0xFF754400, 0x1C1E1810,             // focusRing, warn, elevation rgba(30,24,16,.11)
                0xFF2F62D0,                                     // focusFill (white on it 5.6:1)
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
                0xFF3B78E7, 0xFFF2B544, 0x00000000,
                0xFF3B78E7,
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
