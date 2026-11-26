// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.lumen.projector.Hal;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;

/**
 * The gmpf HAL steps of the screen rotation card (RotatePanel, Lumen OS 1.0.4, the stock RotateWind of the Z9X).
 * Open: IGmpf2 194(1) and, while real-time keystone is on, IGmpf 573(2, false); IGmpf2 199 getScreenRotationInfo
 * {min, max, current, start} (checked by the client before anything moves); a turn is IGmpf2 207 setScreenRotation(5
 * clockwise | 6 counter-clockwise, deg); close: 158(0) when the angle changed, 194(0), 573(2, true). A marker in
 * device-protected storage says a card is open: after every HAL (re)connect a card a dead process left open is closed.
 */
public final class GmpfRotateIo implements RotateIo {
    private static final String TAG = RotatePanel.TAG;
    private static final String PREFS = "z9x_rotate";
    private static final String K_OPEN = "session_open";
    private static final long RECOVER_DELAY_MS = 6_000;

    /** The open card paused real-time keystone (one card at a time). */
    private static volatile boolean sAkPaused;

    private final Context app;
    private final GmpfClient g;
    private final Gmpf2Client g2;

    public GmpfRotateIo(Context app, GmpfClient g, Gmpf2Client g2) {
        this.app = app;
        this.g = g;
        this.g2 = g2;
    }

    /** App start: closes a rotation screen a dead process left open (marker), after every HAL (re)connect. */
    public static void install(Context app) {
        Hal.addConnectedListener(() -> Hal.runDelayed((g, g2) -> {
            if (!RotatePanel.isOpen() && marker(app)) {
                Log.i(TAG, "connect: closing a rotation screen left open");
                sAkPaused = true;
                new GmpfRotateIo(app, g, g2).close("connect", false);
            }
        }, RECOVER_DELAY_MS));
    }

    @Override
    public boolean open(String who) throws Exception {
        setMarker(app, true);
        sAkPaused = false;
        Log.i(TAG, "enter: 194(1) -> " + g2.setAdjustScreenOpen(true));
        if (g.getMoveAk()) {
            sAkPaused = true;
            Log.i(TAG, "573(2,false) -> " + g.setRealtimeAkTemporarily(false));
        }
        return true;
    }

    @Override
    public boolean close(String who, boolean moved) {
        if (moved) {
            try { Log.i(TAG, "158(0) -> " + g.regenerateBootLogo()); } catch (Throwable t) { Log.w(TAG, "158: " + t); }
        }
        boolean ok = true;
        try { Log.i(TAG, "194(0) -> " + g2.setAdjustScreenOpen(false)); } catch (Throwable t) { ok = false; Log.w(TAG, "194: " + t); }
        if (sAkPaused) {
            try { Log.i(TAG, "573(2,true) -> " + g.setRealtimeAkTemporarily(true)); } catch (Throwable t) { Log.w(TAG, "573: " + t); }
        }
        if (ok) setMarker(app, false);
        return ok;
    }

    @Override
    public float[] rotation() throws Exception { return g2.getScreenRotation(); }

    @Override
    public boolean rotate(boolean clockwise, float deg) throws Exception { return g2.rotateScreen(clockwise, deg); }

    @Override
    public void supported() { }

    @Override
    public boolean refused(Throwable t) { return false; }

    private static SharedPreferences prefs(Context app) {
        return app.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean marker(Context app) {
        try { return prefs(app).getBoolean(K_OPEN, false); } catch (Throwable t) { return false; }
    }

    private static void setMarker(Context app, boolean on) {
        try { prefs(app).edit().putBoolean(K_OPEN, on).commit(); } catch (Throwable t) { Log.w(TAG, "marker: " + t); }
    }
}
