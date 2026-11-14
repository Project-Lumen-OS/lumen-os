package org.lumen.projector.panel;

/**
 * Which picture the game menu card shows for a console (plain Java, main thread): the real-time model (Gm3dView) or
 * the frame strip (GmView.Frames). The model is tried first (the strip's hero frame holds the card while it loads);
 * the strip takes over when the model is switched off
 * (debug.lumen.gm3d=0), the window has no GPU drawing, the model does not come up in READY_MS, or GL fails. A GL
 * failure keeps this menu on the strip; a hard one (EGL, shader, upload) keeps the whole process on it.
 */
final class Gm3dGate {
    static final int OFF = 0, PENDING = 1, LIVE = 2, FRAMES = 3;
    /** The model must draw its first frame within this time after the menu asked for it. */
    static final long READY_MS = 2500;

    /** A hard GL failure happened in this process: no more tries until it restarts. */
    static volatile boolean sBroken;

    private int state = OFF;
    private String theme;
    private long since;
    private boolean failedHere;

    int state() { return state; }

    /**
     * The card now shows {@code theme} (a console). True: start the model and draw nothing until it is ready; false:
     * start the frame strip now.
     */
    boolean begin(String theme, boolean enabled, boolean gpu, long now) {
        this.theme = theme;
        since = now;
        if (!enabled || !gpu || failedHere || sBroken) {
            state = FRAMES;
            return false;
        }
        state = PENDING;
        return true;
    }

    /** The card shows no console (a computer icon). */
    void off() {
        state = OFF;
        theme = null;
    }

    /** The model drew its first frame of {@code t}. */
    void ready(String t) {
        if (state == PENDING && t != null && t.equals(theme)) state = LIVE;
    }

    /**
     * The model failed for {@code t}. True when the card must start the strip now (it shows {@code t} and was not on
     * the strip yet).
     */
    boolean failed(String t, boolean hard) {
        failedHere = true;
        if (hard) sBroken = true;
        if (t == null || !t.equals(theme) || state == FRAMES) return false;   // off() forgets the theme
        state = FRAMES;
        return true;
    }

    /** Checked on every draw: true once when the model missed READY_MS (the card must start the strip now). */
    boolean timedOut(long now) {
        if (state != PENDING || now - since < READY_MS) return false;
        failedHere = true;
        state = FRAMES;
        return true;
    }

    /** The card draws the strip's bitmap. */
    boolean frames() { return state == FRAMES; }

    /** The model is on screen. */
    boolean live() { return state == LIVE; }

    /** Waiting for the model's first frame (the card keeps checking the time). */
    boolean pending() { return state == PENDING; }
}
