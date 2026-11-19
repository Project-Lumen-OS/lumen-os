// SPDX-License-Identifier: Apache-2.0
package org.lumen.sensors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.lumen.signal.CardHold;
import org.lumen.signal.PanelMode;
import org.lumen.signal.PanelToggle;
import org.lumen.signal.SignalPanel;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The sensor card of every Lumen projector (README.md), a developer overlay like the signal panel: first press the
 * small card top left, second press the full sheet on the right side, third press hides it. The projector app installs
 * its {@link SensorReader} at start; the menu row and the remote action call {@link #press}. Vendor reads run only while
 * it is on screen, at most SensorBudget.MAX_PER_SECOND a second; the signal panel and this card never show together.
 * Holding OK on the card opens the full sheet, holding it again or BACK folds it back (lumen-signal CardHold).
 */
public final class SensorPanel {
    static final String TAG = "LumenSensors";
    /** The intent of the remote action (lumen-remote key_actions.tsv "sensors"). */
    public static final String ACTION = "org.lumen.projector.action.SENSORS_PANEL";
    private static final long TICK_MS = 500L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final PanelToggle TOGGLE = new PanelToggle();
    private static final SystemStats STATS = new SystemStats();
    private static Handler sWorker;
    private static Context sApp;
    private static SensorReader sReader;
    private static Set<SensorItem> sItems = EnumSet.noneOf(SensorItem.class);
    private static SensorBudget sBudget;
    private static SensorWindow sWindow;
    private static SensorValues sValues = new SensorValues();
    private static String sDrawn;
    private static long sSystemAt;
    private static boolean sReading;
    /** the small card shows the hold hint this time (CardHold: the first few cards only) */
    private static boolean sHint;

    private static final CardHold.Card CARD = new CardHold.Card() {
        @Override public PanelMode mode() { return TOGGLE.mode(); }

        @Override public void expand() {
            sHint = false;
            show(TOGGLE.expand());
        }

        @Override public void collapse() { show(TOGGLE.collapse()); }

        @Override public void sheetOk() {}
    };

    private SensorPanel() {}

    /** App start, once: the model's reader (null = only the system values). */
    public static void install(Context c, SensorReader reader) {
        sApp = c.getApplicationContext();
        sReader = reader;
        Set<SensorItem> model = SensorItems.parse(asset(sApp, SensorItems.ASSET));
        sItems = SensorItems.shown(model, reader == null ? EnumSet.noneOf(SensorItem.class) : reader.items());
        Set<SensorItem> hal = EnumSet.noneOf(SensorItem.class);
        for (SensorItem i : sItems) if (i.hal) hal.add(i);
        sBudget = new SensorBudget(hal);
        Log.i(TAG, "items " + sItems);
        SignalPanel.onOpen(SensorPanel::hide);
        try {
            sApp.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context ctx, Intent i) { hide(); }
            }, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
        } catch (Throwable t) {
            Log.w(TAG, "screen off: " + t);
        }
    }

    /** True once the app installed the card (the menu shows its row). */
    public static boolean available(Context c) { return sApp != null; }

    /** The one button: card, full sheet, hidden. False when the app has not installed the card. */
    public static boolean press(Context c) {
        if (!available(c)) return false;
        PanelMode m = TOGGLE.press();
        if (m.visible()) SignalPanel.hide();
        show(m);
        return true;
    }

    /** Closes the card (standby, light off, the screen goes off, the signal panel opens). */
    public static void hide() {
        if (TOGGLE.mode() == PanelMode.HIDDEN && (sWindow == null || !sWindow.shown())) return;
        TOGGLE.hide();
        show(PanelMode.HIDDEN);
    }

    private static void show(PanelMode mode) {
        MAIN.removeCallbacks(TICK);
        if (sApp != null) CardHold.shown(sApp, CARD, mode);
        if (!mode.visible()) {
            if (sWindow != null) sWindow.hide();
            sDrawn = null;
            return;
        }
        if (sWindow == null) {
            sWindow = new SensorWindow(sApp, () -> show(TOGGLE.back()));
            sWindow.setCollapse(CARD::collapse);
        }
        if (!sWindow.shown()) {
            sHint = mode.card() && CardHold.hint(sApp);
            if (sHint) CardHold.hintShown(sApp);
            sValues = new SensorValues();
            sBudget.reset();
            sSystemAt = 0;
        }
        draw(true);
        TICK.run();
    }

    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            PanelMode mode = TOGGLE.mode();
            if (!mode.visible()) return;
            long now = SystemClock.uptimeMillis();
            if (now - sSystemAt >= SensorItem.CPU.periodMs) {
                sSystemAt = now;
                worker().post(() -> {
                    SensorValues v = new SensorValues();
                    STATS.read(v);
                    MAIN.post(() -> {
                        if (!TOGGLE.mode().visible()) return;
                        sValues.merge(v, EnumSet.of(SensorItem.TEMP_SOC, SensorItem.CPU, SensorItem.RAM));
                        draw(false);
                    });
                });
            }
            List<SensorItem> due = sReading || sReader == null ? null : sBudget.due(now, true, mode == PanelMode.DETAIL);
            if (due != null && !due.isEmpty()) {
                sReading = true;
                final List<SensorItem> asked = due;
                sReader.read(asked, fresh -> {
                    sReading = false;
                    if (fresh != null && TOGGLE.mode().visible()) {
                        sValues.merge(fresh, asked);
                        draw(false);
                    }
                });
            }
            MAIN.postDelayed(this, TICK_MS);
        }
    };

    private static Handler worker() {
        if (sWorker == null) {
            HandlerThread t = new HandlerThread("lumen-sensors");
            t.start();
            sWorker = new Handler(t.getLooper());
        }
        return sWorker;
    }

    private static void draw(boolean force) {
        PanelMode mode = TOGGLE.mode();
        SensorSheet s = SensorSheet.build(sValues, sItems, words(sApp));
        boolean hint = sHint && mode.card(), held = TOGGLE.held();
        String key = mode + "|" + hint + held + "|" + s.rows;
        if (!force && key.equals(sDrawn)) return;
        sDrawn = key;
        sWindow.show(mode, s, hint, held);
    }

    /** The texts of the current language (string resources lumen_sensors_*). */
    static SensorSheet.Words words(Context c) {
        return new SensorSheet.Words() {
            @Override public String get(String key, Object... args) {
                int id = c.getResources().getIdentifier("lumen_sensors_" + key, "string", c.getPackageName());
                if (id == 0) return key;
                return args.length == 0 ? c.getString(id) : c.getString(id, args);
            }

            @Override public char decimal() {
                Locale l = c.getResources().getConfiguration().getLocales().get(0);
                return new java.text.DecimalFormatSymbols(l).getDecimalSeparator();
            }
        };
    }

    private static String asset(Context c, String name) {
        try (InputStream in = c.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Throwable t) {
            Log.i(TAG, "no " + name + ": " + t);
            return null;
        }
    }
}
