package org.lumen.menu;

/**
 * Where the lens shift stands and which way it can still go, from the vendor's two position reads (ProjectorFocusManager
 * 98 getTiltShiftMovePositionPercentage, orientation 0 horizontal and 1 vertical): stock newsettings
 * DisplacementUtils, the model the stock shift page draws (DisplacementCell). Each read is "a,b", the two side fractions
 * 0..1 (absolute values); which one is the right / up side depends on how the projector is put (desk = stock
 * isPositiveDirection, put mode 0 or 2). Pure Java: any device adapter reads, the shared view draws.
 */
public final class ShiftRange {
    /** A side at or beyond this fraction is the end of the travel: its arrow is shown spent (stock arrow tint off). */
    static final float END = 0.99f;

    /** -1 (left end) .. 1 (right end), -1 (bottom) .. 1 (top); NaN when the read failed. */
    public final float x, y;

    private ShiftRange(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /** No position known (no read yet, or the vendor answered nothing usable): the view draws the centre, no end. */
    public static final ShiftRange UNKNOWN = new ShiftRange(Float.NaN, Float.NaN);

    /**
     * Stock DisplacementUtils: getXPlusOffset = h[desk ? 1 : 0], getXMinusOffset = h[desk ? 0 : 1], getYPlusOffset =
     * v[desk ? 0 : 1], getYMinusOffset = v[desk ? 1 : 0]; DisplacementCell.initRectangle: plus when it is above 0, else
     * minus the other side.
     */
    public static ShiftRange of(String horizontal, String vertical, int putMode) {
        float[] h = pair(horizontal), v = pair(vertical);
        if (h == null || v == null) return UNKNOWN;
        boolean desk = putMode == 0 || putMode == 2;
        float xPlus = h[desk ? 1 : 0], xMinus = h[desk ? 0 : 1];
        float yPlus = v[desk ? 0 : 1], yMinus = v[desk ? 1 : 0];
        return new ShiftRange(xPlus > 0 ? xPlus : -xMinus, yPlus > 0 ? yPlus : -yMinus);
    }

    public boolean known() { return !Float.isNaN(x) && !Float.isNaN(y); }

    public boolean atRight() { return known() && x >= END; }

    public boolean atLeft() { return known() && x <= -END; }

    public boolean atTop() { return known() && y >= END; }

    public boolean atBottom() { return known() && y <= -END; }

    /** Stock split(",") + abs(parseFloat) of two values, each held to 0..1; null when it is not two numbers. */
    static float[] pair(String s) {
        if (s == null) return null;
        String[] p = s.trim().split(",");
        if (p.length != 2) return null;
        float[] f = new float[2];
        for (int i = 0; i < 2; i++) {
            try {
                f[i] = Math.abs(Float.parseFloat(p[i].trim()));
            } catch (NumberFormatException e) {
                return null;
            }
            if (Float.isNaN(f[i]) || Float.isInfinite(f[i])) return null;
            f[i] = Math.min(1f, f[i]);
        }
        return f;
    }
}
