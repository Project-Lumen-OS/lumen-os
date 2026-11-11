package org.lumen.projector.panel;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import org.lumen.projector.R;
import org.lumen.projector.SafeHandler;
import org.lumen.projector.Ui;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.TextRow;
import org.lumen.projector.ui.ToggleRow;

/**
 * The screen mirroring tile's popup of layout D (2026-11-11: the tile takes the USB tile's place; the receiver
 * is in the image, its default name is the device name and it asks before every connection): on / off, ask before
 * connecting, the name phones and computers list, the status line, and the receiver's full settings screen.
 *
 * The receiver (org.lumen.miracast, apps/LumenMiracast) owns every value. This only reads and changes them through
 * its StateProvider (call "state" / "set_enabled" / "set_ask", signature permission
 * org.lumen.miracast.permission.CONTROL), on its own worker thread, never on the main thread, and a switch shows
 * only what the receiver read back (QpdLayout.settled). Renaming needs a text field, so the Name row opens the
 * receiver's settings screen straight on its name field (extra "edit" = "name"), which closes again after it.
 */
final class QpdMirror {
    private static final String TAG = "Z9xPanel";
    static final String PKG = "org.lumen.miracast";
    private static final String AUTHORITY = "org.lumen.miracast";
    private static final Uri URI = Uri.parse("content://" + AUTHORITY);
    private static final String ACTION_SETTINGS = "org.lumen.miracast.SETTINGS";
    private static final String STATE = "state", SET_ENABLED = "set_enabled", SET_ASK = "set_ask";
    /** After switching on, the receiver needs a moment to become visible: the status is read again then. */
    private static final long[] REREAD_MS = {1_500, 4_000};

    interface Host {
        /** Closes the quick panel, then runs {@code r} (an activity takes the screen). */
        void closeThen(Runnable r);
        /** False (with the setup note) while the first-run setup is not done. */
        boolean setupDone(String what);
    }

    /** What the receiver reported; null = it did not answer. */
    static final class State {
        Boolean enabled, ask;
        String name, status;
    }

    final Page page;
    private final Context app;
    private final Host host;
    private final ToggleRow on, ask;
    private final NavRow name;
    private final TextRow status;
    private boolean shown;
    private int gen;
    private static SafeHandler worker;

    QpdMirror(Context app, CharSequence title, Host host) {
        this.app = app;
        this.host = host;
        page = new Page(title);
        on = page.add(new ToggleRow(app, app.getString(R.string.lumen_qpd_mirror_on), (r, want) -> set(r, SET_ENABLED, want)));
        ask = page.add(new ToggleRow(app, app.getString(R.string.lumen_qpd_mirror_ask), (r, want) -> set(r, SET_ASK, want)));
        name = page.add(new NavRow(app, app.getString(R.string.lumen_qpd_mirror_name), () -> open(true)));
        status = page.add(new TextRow(app, ""));
        status.setVisibility(View.GONE);
        page.add(new NavRow(app, app.getString(R.string.lumen_qpd_mirror_more), () -> open(false)));
        page.setOnShown(this::onShown);
        page.setOnHidden(() -> { shown = false; gen++; });
    }

    /** The receiver with its control provider is in this system (the tile is built only then). */
    static boolean installed(Context c) {
        try {
            return c.getPackageManager().resolveContentProvider(AUTHORITY, 0) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ reads and changes
    private void onShown() {
        shown = true;
        read(++gen);
    }

    private void read(int g) {
        post(() -> {
            State s = call(STATE, null);
            Ui.main().post(() -> { if (g == gen && shown) apply(s); });
        });
    }

    /** A switch row's OK: the change goes to the receiver; the row shows what it read back. */
    private void set(ToggleRow row, String method, boolean want) {
        final Boolean before = row.isChecked();
        final int g = ++gen;
        Log.i(TAG, "screen mirroring " + method + " " + want);
        post(() -> {
            State s = call(method, want ? "1" : "0");
            Ui.main().post(() -> {
                if (s != null) apply(s);
                else row.setChecked(QpdLayout.settled(null, before));
                if (g == gen && shown && SET_ENABLED.equals(method) && want) {
                    for (long ms : REREAD_MS) Ui.main().postDelayed(() -> { if (g == gen && shown) read(g); }, ms);
                }
            });
        });
    }

    /** Main thread: the rows from the receiver's answer (null = no answer: the switches show unknown). */
    private void apply(State s) {
        on.setChecked(s == null ? null : QpdLayout.settled(s.enabled, null));
        ask.setChecked(s == null ? null : QpdLayout.settled(s.ask, null));
        name.setValue(s == null ? null : s.name);
        String st = s == null ? null : s.status;
        status.setText(st);
        status.setVisibility(st == null || st.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private State call(String method, String arg) {
        try {
            Bundle b = app.getContentResolver().call(URI, method, arg, null);
            if (b == null) return null;
            State s = new State();
            if (b.containsKey("enabled")) s.enabled = b.getBoolean("enabled");
            if (b.containsKey("ask")) s.ask = b.getBoolean("ask");
            s.name = b.getString("name");
            s.status = b.getString("status");
            return s;
        } catch (Throwable t) {
            Log.w(TAG, "screen mirroring " + method + ": " + t);
            return null;
        }
    }

    private static synchronized void post(Runnable r) {
        if (worker == null) worker = SafeHandler.newThread("qpd-mirror");
        worker.post(r);
    }

    // ------------------------------------------------------------------ the receiver's own screen
    /** Name row ({@code editName}) or the full settings row: the panel closes, the receiver's settings open. */
    private void open(boolean editName) {
        if (!host.setupDone("screen mirroring")) return;
        host.closeThen(() -> {
            try {
                Ui.wakeFromDream(app);
                Intent i = new Intent(ACTION_SETTINGS).setPackage(PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (editName) i.putExtra("edit", "name");
                app.startActivity(i);
            } catch (Throwable t) {
                Log.w(TAG, "screen mirroring settings: " + t);
                Ui.toast(app, R.string.lumen_m_toast_failed);
            }
        });
    }
}
