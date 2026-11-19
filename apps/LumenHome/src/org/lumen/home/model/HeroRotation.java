package org.lumen.home.model;

/**
 * When the Витрина turns to its next page by itself (pure, host tested).
 * <ul>
 *   <li>A page stays {@code dwell} ms after it appeared AND after the last key press, so a page never changes right
 *   after the user did something (W3C WAI carousels: do not move while the user acts).</li>
 *   <li>No turning while the hero buttons have the focus (W3C WAI and androidx.tv Carousel: pause on focus): leaving
 *   them counts as a key press, the full dwell starts again.</li>
 *   <li>No turning at all when the system's "Remove animations" is on or a screen reader explores by touch
 *   (androidx.tv Carousel turns off auto scroll under accessibility, WCAG 2.2.2), on the Apps page, under a panel,
 *   when the hero follows the Continue row or the calm screen, with fewer than two pages or while Home is hidden.</li>
 *   <li>The next page turns only when its art is ready in memory, so the change never shows half a picture.</li>
 * </ul>
 */
public final class HeroRotation {
    /** Why the hero does not turn: {@link #NONE} means it may. */
    public static final int NONE = 0, HIDDEN = 1, ONE_PAGE = 2, NOT_HOME = 3, PANEL = 4, FOLLOWS_ROW = 5,
            HERO_FOCUS = 6, NO_MOTION = 7, ACCESSIBILITY = 8;

    /** What the activity knows right now. */
    public static final class State {
        public boolean started, home, panel, followsRow, heroFocus, animations = true, touchExploration;
        public int pages;
    }

    private final long mDwell;
    private long mShownAt, mInputAt;

    public HeroRotation(long dwellMs) {
        mDwell = dwellMs;
    }

    /** The first reason that stops the turning, or {@link #NONE}. */
    public static int blocked(State s) {
        if (!s.started) return HIDDEN;
        if (s.pages < 2) return ONE_PAGE;
        if (!s.home) return NOT_HOME;
        if (s.panel) return PANEL;
        if (s.followsRow) return FOLLOWS_ROW;
        if (!s.animations) return NO_MOTION;
        if (s.touchExploration) return ACCESSIBILITY;
        if (s.heroFocus) return HERO_FOCUS;
        return NONE;
    }

    /** A page appeared (the change ended, or the page was bound at once). */
    public void shown(long now) {
        mShownAt = now;
    }

    /** Any key press, Home coming back, the focus leaving the hero buttons. */
    public void input(long now) {
        mInputAt = now;
    }

    /** Milliseconds until the next page is due (0: now), or -1 while something stops the turning. */
    public long delay(long now, State s) {
        if (blocked(s) != NONE) return -1;
        return Math.max(0, Math.max(mShownAt, mInputAt) + mDwell - now);
    }

    /** True when the next page may come now: due, and its art is ready. */
    public boolean turn(long now, State s, boolean nextReady) {
        return delay(now, s) == 0 && nextReady;
    }
}
