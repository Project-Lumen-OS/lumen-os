package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.projector.Ui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * While the menu is open, reads the kind of content alone (ProjectorFeatures.readSignal, one getter) and asks for a
 * full re-read when it differs from the shown state, so options with a stock support list grey and ungrey live
 * (SignalRule). Stops by itself once the menu is closed.
 */
final class SignalWatch implements Runnable {
    static final long POLL_MS = 1_500;

    private final ProjectorFeatures dev;
    private final BooleanSupplier open;
    private final Supplier<MenuState> shown;
    private final Runnable reread;

    SignalWatch(ProjectorFeatures dev, BooleanSupplier open, Supplier<MenuState> shown, Runnable reread) {
        this.dev = dev;
        this.open = open;
        this.shown = shown;
        this.reread = reread;
    }

    /** (Re)starts the watch; a no-op chain when the menu is closed. */
    void start() {
        Ui.main().removeCallbacks(this);
        run();
    }

    @Override
    public void run() {
        if (!open.getAsBoolean()) return;
        try {
            dev.readSignal(now -> {
                MenuState st = shown.get();
                if (now != null && open.getAsBoolean() && st != null && now != st.signal()) reread.run();
            });
        } catch (Throwable t) {
            Log.w(MenuController.TAG, "signal: " + t);
        }
        Ui.main().postDelayed(this, POLL_MS);
    }
}
