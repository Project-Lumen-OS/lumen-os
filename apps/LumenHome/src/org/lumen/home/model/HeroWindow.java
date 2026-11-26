// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

/**
 * Which hero pages keep their art decoded at screen size in memory (pure, host tested). Every page's art is fetched
 * to the disk cache ahead in rotation order; only a small window is decoded: the page on screen, the next ones the
 * rotation and RIGHT go to, then the previous one LEFT goes to. At 1080p three full screen pictures are 25 MB.
 */
public final class HeroWindow {
    /** Pages decoded at once: on screen, next, previous. */
    public static final int SLOTS = 3;

    private HeroWindow() {
    }

    /** Page indexes to keep decoded, most wanted first: current, current + 1, ..., then current - 1. */
    public static int[] decoded(int current, int pages, int slots) {
        if (pages <= 0 || slots <= 0) return new int[0];
        int n = Math.min(pages, slots);
        int[] out = new int[n];
        int c = Math.floorMod(current, pages);
        out[0] = c;
        if (n == 1) return out;
        int ahead = pages <= slots ? n - 1 : n - 2;
        for (int i = 1; i <= ahead; i++) out[i] = (c + i) % pages;
        if (ahead < n - 1) out[n - 1] = Math.floorMod(c - 1, pages);
        return out;
    }

    /** Every page in the order its art is fetched to disk: the next one first, the one on screen last. */
    public static int[] fetchOrder(int current, int pages) {
        if (pages <= 0) return new int[0];
        int[] out = new int[pages];
        int c = Math.floorMod(current, pages);
        for (int i = 0; i < pages; i++) out[i] = (c + 1 + i) % pages;
        return out;
    }

    /** True when {@code page} is in the decoded window around {@code current}. */
    public static boolean keep(int page, int current, int pages, int slots) {
        for (int p : decoded(current, pages, slots)) if (p == page) return true;
        return false;
    }
}
