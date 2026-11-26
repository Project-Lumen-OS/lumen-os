// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

/**
 * Widths of the place cards: a card is as wide as its longest line needs (a drive's "52.9 GB free of 56.6 GB" in
 * any language and number format), never narrower than its design width and never wider than the cap.
 */
public final class Fit {
    private Fit() {}

    /** min and max: the design width and the cap, text: the widest line in px, pad: the card's side paddings. */
    public static int width(int min, float text, int pad, int max) {
        int need = pad + (int) Math.ceil(text);
        return Math.max(min, Math.min(max, need));
    }
}
