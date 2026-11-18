package org.lumen.setup;

/**
 * The step counter "Шаг N из M" and its segments, plain Java for host tests. A step this unit or
 * build does not have (Step.available() false) is not counted. A step that skipped itself (autoSkip, the remote was
 * already connected) is counted and, being before the current one, shows as passed.
 */
public final class Progress {
    private final int mCount, mCurrent;

    public Progress(int count, int current) {
        mCount = Math.max(1, count);
        mCurrent = Math.max(0, Math.min(current, mCount - 1));
    }

    /** From the availability of every step in wizard order and the index of the step on screen. */
    public static Progress of(boolean[] available, int screen) {
        int count = 0, pos = 0;
        for (int i = 0; i < available.length; i++) {
            if (!available[i]) continue;
            if (i == screen) pos = count;
            count++;
        }
        return new Progress(count, pos);
    }

    public int count() { return mCount; }

    public int current() { return mCurrent; }

    /** 1-based number of the step on screen. */
    public int number() { return mCurrent + 1; }

    /** Opacity of segment i (text1 colour): passed 0.75, current 1, coming 0.22. */
    public float opacity(int i) {
        return i < mCurrent ? 0.75f : i == mCurrent ? 1f : 0.22f;
    }

    /** Width of the segment row in design px. */
    public float width(float seg, float cur, float gap) {
        return (mCount - 1) * (seg + gap) + cur;
    }
}
