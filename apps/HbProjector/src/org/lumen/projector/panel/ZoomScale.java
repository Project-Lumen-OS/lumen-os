package org.lumen.projector.panel;

/**
 * Z9X HB optical zoom positions, the stock dial math of newsettings ZoomStateView (release_838) in one plain Java
 * class for the host test (apps/HbProjector/test/beta6). OpticalZoomPanel keeps the HAL calls; this class only maps.
 *
 * Stock (ZoomStateView.initZoom$1.invokeSuspend, setZoom$lambda$2 / $4; KeyStoneManager.getOpticalZoomStep /
 * getCurOpticalCurZoomStep / zoomControl):
 *   limitStep = zoomControl(12, 10) (our 63(12,10)), maxOpticalStep = 97 getZoomStepRange().zoomOutOpticalMaxNum;
 *   dial progress 0..limitStep + maxOpticalStep, 0 = the biggest picture;
 *   progress = 98 + limitStep when 98 != 0 (or the limit is 0), else limitStep - |190|;
 *   smaller (progress + 1): progress < limitStep -> 189 setOpticalZoomLimitStep, else 95(98 + 1);
 *   bigger (progress - 1): progress <= limitStep -> 189, else 95(98 - 1).
 * The stock HB dial draws no numbers (CompositeDialView text alpha 0 unless isGmLens); Lumen shows "Size N of M" with
 * N = M - progress (1 = the smallest picture, M = the biggest) and M = limitStep + maxOpticalStep + 1.
 * G0130 table (device logs device tests 2.8, hb_session/zoombug 2026-10-31): 97 optical 0..12, 63(12,10) = 1,
 * so 14 positions: Size 14 = 190 -1, Size 13 = 98 0, ..., Size 1 = 98 12.
 *
 * HB beta6 (tester on the installed beta5: "max 15, min 2, 1 cannot be set"): the vendor reported one position more
 * than it lets the lens reach. An end the vendor refuses (the move answered non-zero AND the fresh read-back did not
 * move) is no position: {@link #refusedAtEnd} drops it for the session, so the counter always runs 1..M over the
 * positions the lens really reaches. Stock moves its dial even when the vendor refuses (setProgress before the result),
 * so its dial overstates the same way; Lumen shows the vendor's read-back instead.
 */
public final class ZoomScale {
    /** Sanity bounds as OpticalZoomPanel (97 optical maximum, 63(12,10) limit positions). */
    static final int MAX_POSITIONS = 64;
    static final int MAX_LIMIT = 4;

    private int limit;
    private int max;

    /** @param max97 97 optical maximum (minimum 0), @param limit63 63(12,10) answer (out of 0..4 = 0). */
    public ZoomScale(int max97, int limit63) {
        if (max97 < 1 || max97 > MAX_POSITIONS) throw new IllegalArgumentException("97 optical max " + max97);
        this.max = max97;
        this.limit = limit63 >= 0 && limit63 <= MAX_LIMIT ? limit63 : 0;
    }

    public int limit() { return limit; }
    public int max() { return max; }
    /** Last dial progress (the smallest picture). */
    public int last() { return limit + max; }
    /** Number of positions shown as "of M". */
    public int positions() { return last() + 1; }

    /** Dial progress from the fresh 98 / 190 reads; -1 when they do not fit this scale. */
    public int progress(int cur98, int lim190) {
        if (cur98 < 0 || cur98 > max || lim190 > 0 || lim190 < -limit) return -1;
        if (cur98 != 0 && lim190 != 0) return -1;
        return cur98 == 0 ? limit + lim190 : cur98 + limit;
    }

    /** The "Size N" number of a progress: 1 = the smallest picture, positions() = the biggest. */
    public int sizeNumber(int progress) { return last() - progress + 1; }

    /** One press: kind 189 / 95 and its argument, or null at an end (dir +1 = smaller, -1 = bigger). */
    public int[] move(int progress, int dir) {
        if (dir != 1 && dir != -1) throw new IllegalArgumentException("dir " + dir);
        if (progress < 0 || progress > last()) return null;
        if ((dir < 0 && progress <= 0) || (dir > 0 && progress >= last())) return null;
        boolean limitZone = dir > 0 ? progress < limit : progress <= limit;
        if (limitZone) return new int[] {189, progress - limit + dir};
        return new int[] {95, progress - limit + dir};
    }

    /**
     * A move the vendor refused (non-zero answer, read-back unchanged). When it was the move onto an end position,
     * that end does not exist on this unit: the scale drops it. True when the scale changed.
     */
    public boolean refusedAtEnd(int progress, int dir) {
        if (dir > 0 && progress == last() - 1 && max > 1) { max--; return true; }
        if (dir < 0 && progress == 1 && limit > 0) { limit--; return true; }
        return false;
    }
}
