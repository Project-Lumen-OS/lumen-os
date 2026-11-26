// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.sensors;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.lumen.signal.PanelMode;

import java.util.function.Consumer;

/**
 * Diagnostics (All settings, For developers): the projector app's own diagnostic lines in the full sheet of
 * the sensor card, read once at opening; OK reads them again, BACK closes. The text comes from the app (its HAL state,
 * versions), this class only shows it.
 */
public final class DiagPanel {
    /** The app's text; {@code cb} on the main thread, may be called again with a longer text (a slow part appended). */
    public interface Source {
        void read(Consumer<String> cb);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static SensorWindow sWindow;
    private static String sTitle;
    private static Source sSource;
    private static int sGen;

    private DiagPanel() {}

    public static void show(Context c, String title, Source source) {
        Context app = c.getApplicationContext();
        SensorPanel.hide();
        org.lumen.signal.SignalPanel.hide();
        sTitle = title;
        sSource = source;
        if (sWindow == null) sWindow = new SensorWindow(app, DiagPanel::hide);
        sWindow.setOk(DiagPanel::refresh);
        sWindow.showText(sTitle, "\u2026");
        refresh();
    }

    public static void hide() {
        sGen++;
        if (sWindow != null) sWindow.hide();
    }

    private static void refresh() {
        if (sSource == null || sWindow == null) return;
        final int gen = ++sGen;
        sSource.read(text -> MAIN.post(() -> {
            if (gen == sGen && sWindow != null && sWindow.mode() == PanelMode.DETAIL) sWindow.showText(sTitle, text == null ? "" : text);
        }));
    }
}
