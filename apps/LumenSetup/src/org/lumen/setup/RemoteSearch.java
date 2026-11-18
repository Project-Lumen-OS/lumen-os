package org.lumen.setup;

/**
 * What the remote step shows, plain Java for host tests. The bridge reports searching, a remote
 * found while pairing, a paired remote that sleeps, or connected; after {@link #TIMEOUT_MS} of searching without
 * anything found the step says "Пульт не найден" with "Искать снова" (focused) and "Пропустить". "Искать снова" starts
 * a new 60 s search. Anything found or paired leaves the not-found state at once, connected always wins and stays.
 */
public final class RemoteSearch {
    public static final long TIMEOUT_MS = 60_000;

    public enum State { SEARCHING, FOUND, WAKE, CONNECTED, NOT_FOUND }

    private long mStart;
    private State mState = State.SEARCHING;
    private String mName = "";

    public RemoteSearch(long now) {
        mStart = now;
    }

    public State state() { return mState; }

    /** The remote's name while FOUND (empty otherwise). */
    public String name() { return mName; }

    /** "Искать снова": a new search of 60 s. */
    public void retry(long now) {
        if (mState == State.CONNECTED) return;
        mStart = now;
        mState = State.SEARCHING;
        mName = "";
    }

    /** A key of the XGIMI remote or the bridge's connected flag. */
    public void connected() {
        mState = State.CONNECTED;
    }

    /**
     * One bridge report (or a timer tick with no news: pairing false, bonded 0, no name). Returns true when the state
     * on screen changed.
     */
    public boolean report(long now, boolean connected, boolean pairing, String name, int bonded) {
        State before = mState;
        String nameBefore = mName;
        if (mState == State.CONNECTED) return false;
        if (connected) {
            mState = State.CONNECTED;
        } else if (pairing && name != null && !name.isEmpty()) {
            mState = State.FOUND;
            mName = name;
        } else if (bonded > 0) {
            mState = State.WAKE;
        } else if (mState == State.NOT_FOUND || now - mStart >= TIMEOUT_MS) {
            mState = State.NOT_FOUND;
        } else {
            mState = State.SEARCHING;
        }
        if (mState != State.FOUND) mName = "";
        return mState != before || !mName.equals(nameBefore);
    }
}
