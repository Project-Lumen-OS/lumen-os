// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

/**
 * The colour of a device's glyph on an Inputs card. A CEC device's name, brand and mark come from the HDMI app
 * (org.lumen.tvinput Brands, through its port states: lumen-hdmiports HdmiPort), never from a second list here.
 */
public final class CecNames {
    private CecNames() {}

    /** A brand accent softened towards the paper colour, readable as a glyph on the dark card (>= 4:1). */
    public static int softAccent(int accent) {
        return mix(accent, 0xFFF4EFE6, 0.35f);
    }

    /** Linear mix of two ARGB colours (alpha opaque), t in 0..1. */
    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
