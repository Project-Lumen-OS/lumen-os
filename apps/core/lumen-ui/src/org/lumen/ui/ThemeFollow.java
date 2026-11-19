package org.lumen.ui;

/**
 * How a screen follows the theme, plain Java (host test: test/ThemeFadeTest). {@link ThemeFade} feeds it every palette
 * the observer, the clock or the app itself reports and does what it asks on the real window:
 * <ul>
 *   <li>a screen that is not on screen, or with the system animations removed, takes the new colours at once (nothing
 *       old is ever drawn again, so no wrong theme flashes in);</li>
 *   <li>a visible screen first freezes what is on screen now over the window, re-styles its views in place under it
 *       and then fades the frozen picture out ({@link Tokens#MS_THEME}, {@link Motion#STANDARD});</li>
 *   <li>a change during a fade freezes the mixed picture on screen and fades again from there, never jumping;</li>
 *   <li>a stop (onStop, window gone) ends a fade at once.</li>
 * </ul>
 * The same palette twice (the app that stored the choice, then the observer) changes nothing.
 */
public final class ThemeFollow {
    /** The window side, done by ThemeFade on the main thread. */
    public interface Screen {
        /** On screen now and laid out. */
        boolean visible();

        /** False when the user removed animations: every switch is instant. */
        boolean animations();

        /** Freezes what is on screen now (a running fade included) over the window; false when it could not. */
        boolean freeze();

        /** Re-styles the views in place in {@code p}, under the frozen picture when there is one. */
        void restyle(Palette p);

        /** Fades the frozen picture out, then calls {@link ThemeFollow#faded} with {@code token}. */
        void fade(int token);

        /** Drops the frozen picture at once. */
        void unfreeze();
    }

    public enum State { IDLE, FADING }

    private final Screen mScreen;
    private Palette mShown;
    private State mState = State.IDLE;
    private int mToken;

    /** {@code shown}: the palette the views are drawn in now. */
    public ThemeFollow(Screen s, Palette shown) {
        mScreen = s;
        mShown = shown;
    }

    public Palette shown() {
        return mShown;
    }

    public State state() {
        return mState;
    }

    /** The palette that belongs on screen now. */
    public void target(Palette p) {
        if (p == null || p == mShown) return;
        mShown = p;
        if (mScreen.visible() && mScreen.animations() && mScreen.freeze()) {
            mScreen.restyle(p);
            mState = State.FADING;
            mScreen.fade(++mToken);
            return;
        }
        end();
        mScreen.restyle(p);
    }

    /** The fade with {@code token} is over; an older fade that was replaced reports nothing. */
    public void faded(int token) {
        if (token == mToken && mState == State.FADING) end();
    }

    /** The screen left: a running fade ends now. */
    public void stop() {
        end();
    }

    private void end() {
        if (mState != State.FADING) return;
        mToken++;
        mState = State.IDLE;
        mScreen.unfreeze();
    }
}
