// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import org.lumen.ui.Palette;

/**
 * Colours of the panel from the shared Lumen 2.0 palette (lumen-ui Palette, picked by the one theme switch
 * LumenTheme: Светлая, Тёмная, Авто): the panel colours, its texts and lines, the accent, the ok and the warning
 * colour. Pure.
 */
public final class SignalLook {
    /** The card over video stays a little see-through: this alpha over the palette's panel colour. */
    static final int CARD_ALPHA = 0xF2;

    public final boolean dark;
    public final int card, ink, mute, line, chip, chipInk, accent, onAccent, ok, warn, okBg, warnBg, key;

    private SignalLook(Palette p) {
        dark = p.dark;
        card = (CARD_ALPHA << 24) | (p.panel & 0xFFFFFF);
        ink = p.ptext1;
        mute = p.ptext3;
        line = p.panelLine;
        chip = p.dark ? 0x1AFFFFFF : p.pill;
        chipInk = p.ptext1;
        accent = p.accent;
        onAccent = p.onPfocus;
        ok = p.okPanel;
        warn = p.warn;
        okBg = (0x1F << 24) | (ok & 0xFFFFFF);
        warnBg = (0x24 << 24) | (warn & 0xFFFFFF);
        key = chip;
    }

    public static SignalLook of(Palette p) { return new SignalLook(p); }

    public int tone(RowTone t) { return t == RowTone.OK ? ok : t == RowTone.WARN ? warn : ink; }
}
