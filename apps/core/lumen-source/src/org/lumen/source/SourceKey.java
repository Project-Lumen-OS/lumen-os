// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.source;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import org.lumen.projector.R;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The remote's Source key on every Lumen projector (gsi-stock analysis): each press goes straight to
 * the next input of the stock cycle and the small card names what is on screen now. The key receivers call
 * {@link #onKey}, the Source request receivers and the quick menu's Input tile call {@link #press}. The inputs and the
 * screen are read on one worker thread, so presses are handled one after the other in order; the card and the switch
 * run on the main thread. {@link #openPort} and {@link #goHome} are the one way the projector app opens an input.
 */
public final class SourceKey {
    static final String TAG = "LumenSource";
    static final String VIEWER = "org.lumen.tvinput.PassthroughActivity";
    /** org.lumen.tvinput HdmiInputs.EXTRA_REASON; {@link #REASON_KEY} keeps the viewer's own banner quiet. */
    static final String EXTRA_REASON = "org.lumen.tvinput.extra.REASON";
    public static final String REASON_KEY = "source_key";
    /** A device's CEC name often arrives a moment after the switch: the card reads it again this late. */
    static final long NAME_AGAIN_MS = 1500;

    private static final SourcePress PRESS = new SourcePress();
    private static Handler work;
    private static SourceRule rule;
    private static SourceOsd osd;
    private static long lastPress;

    private SourceKey() {}

    /** KEYCODE_TV_INPUT: the step on the first DOWN, like stock (no long press action). Main thread. */
    public static void onKey(Context c, KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) press(c, "key");
    }

    /** One press of the Source key. Any thread. */
    public static void press(Context c, String why) {
        final Context app = c.getApplicationContext();
        final long now = SystemClock.uptimeMillis();
        work().post(() -> step(app, now, why));
    }

    /** Opens HDMI {@code port} in org.lumen.tvinput's viewer; false when that port has no input. Main thread. */
    public static boolean openPort(Context c, int port, String reason) {
        String id = SourceInputs.read(c).ids.get(port);
        if (id == null) {
            Log.i(TAG, "open HDMI " + port + ": no such input");
            return false;
        }
        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
                .setComponent(new ComponentName(SourceInputs.TVINPUT_PKG, VIEWER))
                .putExtra(EXTRA_REASON, reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            Log.i(TAG, "open HDMI " + port + " (" + reason + ") -> " + id);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "open HDMI " + port + ": " + t);
            return false;
        }
    }

    /** The home screen. Main thread. */
    public static void goHome(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Log.w(TAG, "home: " + t);
        }
    }

    // ------------------------------------------------------------------ worker

    private static void step(Context app, long now, String why) {
        SourceRule r = rule(app);
        SourceInputs in = SourceInputs.read(app);
        int[] ports = in.ports();
        if (ports.length == 0) {
            Log.w(TAG, why + ": no HDMI input registered by " + SourceInputs.TVINPUT_PKG);
            return;
        }
        int screen = PRESS.burst(now, r) ? SourceCycle.HOME : SourceInputs.onScreen(app);
        SourceStep s = PRESS.press(now, r, ports, in.connected, screen);
        lastPress = now;
        Log.i(TAG, why + ": " + s);
        final String name = s.shown > 0 ? in.name(app, s.shown) : app.getString(R.string.lumen_source_home);
        final String line = s.shown > 0 ? OsdText.line(name, in.device(s.shown)) : name;
        final int icon = s.shown > 0 ? R.drawable.lumen_ic_hdmi : R.drawable.lumen_ic_home;
        // an HDMI step under a viewer that shows its own banner in the card's place: that banner names the input
        final boolean card = s.shown <= 0 || SourceInputs.viewerQuiet(app);
        new Handler(Looper.getMainLooper()).post(() -> {
            if (card) osd(app).show(icon, line, name, r.osdMs);
            else osd(app).hideNow();
            if (s.go > 0) openPort(app, s.go, REASON_KEY);
            else if (s.go == SourceCycle.HOME) goHome(app);
        });
        if (card && s.shown > 0) work().postDelayed(() -> nameAgain(app, now, s.shown, line), NAME_AGAIN_MS);
    }

    /** The card of press {@code at} names the port's device once it is known (no newer press since). Worker. */
    private static void nameAgain(Context app, long at, int port, String shownLine) {
        if (lastPress != at) return;
        SourceInputs in = SourceInputs.read(app);
        String name = in.name(app, port);
        String line = OsdText.line(name, in.device(port));
        if (line.equals(shownLine)) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (lastPress == at) osd(app).update(line, name);
        });
    }

    private static synchronized Handler work() {
        if (work == null) {
            HandlerThread t = new HandlerThread("lumen-source");
            t.start();
            work = new Handler(t.getLooper());
        }
        return work;
    }

    private static SourceOsd osd(Context app) {
        if (osd == null) osd = new SourceOsd(app);
        return osd;
    }

    /** The model's data from the app's assets; the stock rule when it is missing or broken (logged). Worker. */
    private static SourceRule rule(Context app) {
        if (rule != null) return rule;
        try (InputStream in = app.getAssets().open(SourceRule.ASSET)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            rule = SourceRule.parse(new String(b.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Log.w(TAG, SourceRule.ASSET + ": " + t + ", stock rule");
            rule = SourceRule.STOCK;
        }
        return rule;
    }
}
