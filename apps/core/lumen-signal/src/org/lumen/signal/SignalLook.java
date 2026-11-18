// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

import org.lumen.ui.Palette;

/**
 * Colours of the panel from the shared Lumen 2.0 palette (lumen-ui Palette, picked by the one theme switch
 * LumenTheme: Светлая, Тёмная, Авто): the panel colours, its texts and lines, the accent and the ok colour. Warn has
 * no palette colour yet, so it is the amber of the design for each theme. Pure.
 */
public final class SignalLook {
    /** The card over video stays a little see-through: this alpha over the palette's panel colour. */
    static final int CARD_ALPHA = 0xF2;
    private static final int WARN_LIGHT = 0xFF9A5B00, WARN_DARK = 0xFFF2B544;

    public final boolean dark;
    public final int card, ink, mute, line, chip, chipInk, accent, onAccent, ok, warn, okBg, warnBg, key;

    private SignalLook(Palette p) {
        dark = p.dark;
        card = (CARD_ALPHA << 24) | (p.panel & 0xFFFFFF);
        ink = p.ptext1;
        mute = p.ptext3;
        line = p.panelLine;
        chip = p.dark ? 0x1AFFFFFF : 0x0F12151B;
        chipInk = p.ptext1;
        accent = p.accent;
        onAccent = p.dark ? p.onPfocus : 0xFFFFFFFF;
        ok = p.okPanel;
        warn = p.dark ? WARN_DARK : WARN_LIGHT;
        okBg = (0x1F << 24) | (ok & 0xFFFFFF);
        warnBg = (0x24 << 24) | (warn & 0xFFFFFF);
        key = chip;
    }

    public static SignalLook of(Palette p) { return new SignalLook(p); }

    public int tone(RowTone t) { return t == RowTone.OK ? ok : t == RowTone.WARN ? warn : ink; }
}
