// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

/**
 * Which art the Витрина shows for a card (Home A, the art rule the owner approved on 2026-11-18). Pure.
 *
 * <p>Candidates come in the card's own order: the art its app publishes first (Lumen Play: the show's TMDB backdrop),
 * then its other art (the TMDB episode still, else Lumen Play's 640x360 frame grab). Sizes are the art's real pixels.
 * <ol>
 *   <li>FULL: the first landscape candidate at least {@link #MIN_FULL_W} wide fills the whole screen, sharp.</li>
 *   <li>FRAME: else the largest candidate is blurred into the field and shown sharp at or below its own size, never
 *   enlarged, at most the still box (16:9 art fills its width, a poster or a square keeps its height).</li>
 *   <li>NONE: nothing known, a neutral field with the text only.</li>
 * </ol>
 */
public final class ArtRule {
    public static final int NONE = 0, FULL = 1, FRAME = 2;
    /** Art shown over the whole screen is at least this wide (TMDB w1280). */
    public static final int MIN_FULL_W = 1280;
    /** Landscape: at least this wide for its height. */
    static final float LANDSCAPE = 1.25f;

    private ArtRule() {}

    public static final class Pick {
        public int mode = NONE, index = -1;
        /** FULL: the art's size; FRAME: the sharp frame on screen (real px, at most the art's own pixels). */
        public int w, h;

        @Override
        public String toString() {
            return (mode == FULL ? "full" : mode == FRAME ? "frame" : "none") + (mode == NONE ? "" : " #" + index + " " + w + "x" + h);
        }
    }

    /** True for art that may fill the screen. */
    public static boolean full(int w, int h) {
        return w >= MIN_FULL_W && h > 0 && w >= h * LANDSCAPE;
    }

    /**
     * @param sizes {w0, h0, w1, h1, ...} real pixels of each candidate in the card's order (0: unknown or failed)
     * @param boxW  the still box width in real px
     * @param boxH  the still box height in real px
     */
    public static Pick pick(int[] sizes, int boxW, int boxH) {
        Pick p = new Pick();
        if (sizes == null) return p;
        for (int i = 0; i + 1 < sizes.length; i += 2) {
            if (full(sizes[i], sizes[i + 1])) {
                p.mode = FULL;
                p.index = i / 2;
                p.w = sizes[i];
                p.h = sizes[i + 1];
                return p;
            }
        }
        long best = 0;
        for (int i = 0; i + 1 < sizes.length; i += 2) {
            int w = sizes[i], h = sizes[i + 1];
            if (w <= 0 || h <= 0 || (long) w * h <= best) continue;
            best = (long) w * h;
            float s = Math.min(1f, Math.min(boxW / (float) w, boxH / (float) h));
            p.mode = FRAME;
            p.index = i / 2;
            p.w = Math.max(1, (int) Math.floor(w * s + 1e-3));
            p.h = Math.max(1, (int) Math.floor(h * s + 1e-3));
        }
        return p;
    }
}
