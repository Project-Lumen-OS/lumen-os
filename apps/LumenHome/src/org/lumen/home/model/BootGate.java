// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.ArrayList;

/**
 * Home before and after the user is unlocked (direct boot). HomeActivity is direct boot aware, so Android starts it
 * right after the boot animation in place of the system's FallbackHome, while only the device protected storage can
 * be read: Home's prefs, caches, TMDB files and the providers of other apps come with the unlock. Until then Home
 * shows the saved last screen (device protected) and nothing else; every step that needs the credential protected
 * storage waits here and runs once, in the order asked, when the user is unlocked. One per process (App); pure Java
 * (host test test/home2 BootGateTest).
 */
public final class BootGate {
    /** HomeActivity's start: everything at once / the saved screen until the unlock / a plain field until the unlock. */
    public static final int FULL = 0, SAVED = 1, STAND_IN = 2;

    /** A step that failed: logged, the next steps still run (a missing file must not keep Home empty). */
    public interface Errors {
        void failed(String step, Throwable t);
    }

    private final Errors mErrors;
    private boolean mUnlocked;
    private final ArrayList<Wait> mWaiting = new ArrayList<>();

    private static final class Wait {
        final String name;
        final Runnable body;
        boolean cancelled;

        Wait(String name, Runnable body) {
            this.name = name;
            this.body = body;
        }
    }

    /** A step waiting for the unlock; {@link #cancel} drops it (an activity destroyed before the unlock). */
    public final class Ticket {
        private final Wait mWait;

        Ticket(Wait w) {
            mWait = w;
        }

        public void cancel() {
            synchronized (BootGate.this) {
                if (mWait != null) mWait.cancelled = true;
            }
        }
    }

    public BootGate(boolean unlocked, Errors errors) {
        mUnlocked = unlocked;
        mErrors = errors;
    }

    /** True once the credential protected storage can be used (prefs, caches, files, other apps' providers). */
    public synchronized boolean unlocked() {
        return mUnlocked;
    }

    /** Runs {@code body} now when the user is unlocked, else once at the unlock, after the steps asked before it. */
    public Ticket whenUnlocked(String name, Runnable body) {
        Wait w = new Wait(name, body);
        synchronized (this) {
            if (!mUnlocked) {
                mWaiting.add(w);
                return new Ticket(w);
            }
        }
        run(w);
        return new Ticket(null);
    }

    /** The user was unlocked: the waiting steps run here in order; a second call does nothing. */
    public void unlock() {
        ArrayList<Wait> now;
        synchronized (this) {
            if (mUnlocked) return;
            mUnlocked = true;
            now = new ArrayList<>(mWaiting);
            mWaiting.clear();
        }
        for (Wait w : now) {
            synchronized (this) {
                if (w.cancelled) continue;
            }
            run(w);
        }
    }

    private void run(Wait w) {
        try {
            w.body.run();
        } catch (Throwable t) {
            if (mErrors != null) mErrors.failed(w.name, t);
        }
    }

    /**
     * How HomeActivity starts. Before the unlock it shows the saved screen only when it is the HOME the user gets after
     * the unlock; otherwise (Lumen Setup during the wizard, a launcher the user picked, the chooser) it stands in like
     * FallbackHome: a plain field, handed over at the unlock.
     */
    public static int mode(boolean unlocked, boolean homeAfterUnlockIsUs) {
        if (unlocked) return FULL;
        return homeAfterUnlockIsUs ? SAVED : STAND_IN;
    }

    /** At the unlock: true when HOME now resolves to another activity, which then takes over (Home finishes). */
    public static boolean handOver(int mode, boolean resolvedHomeIsUs) {
        return mode == STAND_IN || (mode == SAVED && !resolvedHomeIsUs);
    }

    /** What Home reads before its first frame. */
    public interface Source<T> {
        T read() throws Exception;
    }

    /** The start reads of Home: the saved screen always (device protected), the weather only after the unlock. */
    public static final class Start<D, W> {
        public D saved;
        public W weather;
    }

    /**
     * Reads the start of Home on the data lane: the saved screen from the device protected storage, the stored weather
     * (credential protected prefs) only when the user is unlocked. A read that fails (a missing or broken file) leaves
     * its part empty, never the other one.
     */
    public static <D, W> Start<D, W> start(boolean unlocked, Source<D> saved, Source<W> weather, Errors errors) {
        Start<D, W> s = new Start<>();
        try {
            s.saved = saved.read();
        } catch (Throwable t) {
            if (errors != null) errors.failed("saved screen", t);
        }
        if (!unlocked) return s;
        try {
            s.weather = weather.read();
        } catch (Throwable t) {
            if (errors != null) errors.failed("stored weather", t);
        }
        return s;
    }
}
