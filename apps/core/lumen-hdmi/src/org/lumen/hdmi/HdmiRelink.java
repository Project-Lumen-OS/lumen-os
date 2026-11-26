// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

/**
 * Lumen OS 1.0.11: the HDMI cable replug, made by the projector, the same on every model (one per-model
 * {@link RelinkHost}: its HAL thread, its codes, its stock EDID values). On 2026-11-14 the owner's PS5 woke after the
 * Z9X rebooted and never got a picture (the vendor's getSignalStatus said no picture, getHdmiInfo failed with
 * fail[3], CEC worked: power on, Active Source, remote keys) until the cable was pulled out and put back. Stock has no
 * call that pulses HPD on its own (HdmiLink): its HDMI player only switches the source, which Lumen does too
 * (HdmiSource). The vendor pulls HPD low when it writes another EDID, so this class writes the other stock EDID
 * version and, {@value HdmiLink#FLIP_HOLD_MS} ms later, the user's version again: the source sees two HPD pulses,
 * reads the EDID again and authenticates again, as after a replug.
 *
 * <p>While tvinput shows an HDMI port, getSignalStatus (Z9X IGmpf 592, HB and X50 family TV 106) is read every
 * {@value #TICK_MS} ms on the host's HAL thread. One relink per session, at most {@value HdmiLink#MAX_PER_BOOT} per
 * boot, only when {@link HdmiLink#relinkNow}: no picture for {@value HdmiLink#NO_LOCK_MS} ms and a CEC device behind
 * the port reporting power on for as long (read every tick while there is no picture, so a console that just woke
 * gets its own 10 s and a source that is off or not there gets nothing), the screen on and out of the lamp-only
 * standby, tvinput still showing the port. The user's version is saved before the flip (commit) and written back
 * after every HAL connect, so a crash or a HAL restart in between leaves no foreign EDID. Sessions come from tvinput's
 * HDMI_EVENT broadcast ({@link #onSessionEvent}, Z9X and HB) or from its polled port provider ({@link #onShownPort},
 * X50 family). Log tag: the host's.
 */
public final class HdmiRelink {
    static final long TICK_MS = 2_000;
    private static final String PREFS = "hdmi_relink";
    private static final String K_PENDING = "pending", K_USER = "user";

    private static volatile Context sApp;
    private static volatile RelinkHost sHost;
    private static Handler sMain, sWork;
    // main thread state
    private static int sPort;
    private static int sGen;
    private static long sNoPictureSince;
    private static long sSourceOnSince;
    private static int sSessionRelinks;
    private static int sBootRelinks;
    private static int sLastStatus = Integer.MIN_VALUE;
    private static boolean sChecking;

    private HdmiRelink() {}

    /** App.onCreate (main thread): the model's host, and the connect hook that writes back a user EDID left flipped. */
    public static void install(Context ctx, RelinkHost host) {
        if (sApp != null || host == null) return;
        sHost = host;
        sMain = new Handler(Looper.getMainLooper());
        HandlerThread t = new HandlerThread("lumen-relink");
        t.start();
        sWork = new Handler(t.getLooper());
        sApp = ctx.getApplicationContext();
        host.onConnect(v -> restorePending(v, "HAL connect"));
        final Context app = sApp;
        work(() -> {                                            // a session opened before this process started
            final int[] ports = host.shownPorts(app);
            if (ports != null && ports.length > 0) sMain.post(() -> {
                if (sPort == 0) begin(ports[0]);
            });
        });
        Log.i(tag(), "armed: one EDID flip when a powered CEC source gives no picture for " + HdmiLink.NO_LOCK_MS / 1000
                + " s");
    }

    // ================================================================== events (main thread)

    /** game/HdmiEventReceiver: tvinput's session took (active) or released the HDMI hardware of {@code port}. */
    public static void onSessionEvent(int port, boolean active) {
        if (sApp == null) return;
        if (active) {
            if (port == sPort) return;
            begin(port);
        } else if (port == sPort) {
            sGen++;
            sPort = 0;
            sMain.removeCallbacks(TICK);
        }
    }

    /**
     * Hosts without the session broadcast (X50 family: tvinput's port provider, polled): the HDMI port on the video
     * window now, -1 for none. Main thread.
     */
    public static void onShownPort(int port) {
        if (sApp == null) return;
        if (port > 0) onSessionEvent(port, true);
        else if (sPort != 0) onSessionEvent(sPort, false);
    }

    private static void begin(int port) {
        sGen++;
        sPort = port;
        sNoPictureSince = 0;
        sSourceOnSince = 0;
        sSessionRelinks = 0;
        sLastStatus = Integer.MIN_VALUE;
        sChecking = false;
        restartTick(TICK_MS);
    }

    private static void restartTick(long delay) {
        sMain.removeCallbacks(TICK);
        if (sPort != 0) sMain.postDelayed(TICK, delay);
    }

    private static final Runnable TICK = () -> {
        try {
            tick();
        } catch (Throwable t) {
            Log.w(tag(), "tick: " + t);
        }
    };

    private static void tick() {
        final int port = sPort;
        final int gen = sGen;
        if (port == 0) return;
        sMain.postDelayed(TICK, TICK_MS);
        if (!awake()) {                                         // asleep or in the standby: the clocks start again
            sNoPictureSince = 0;
            sSourceOnSince = 0;
            return;
        }
        sHost.query(HdmiRelink::readStatus, st -> {
            if (gen != sGen || port != sPort) return;
            onStatus(port, gen, st == null ? HdmiLink.UNKNOWN : st);
        });
    }

    private static void onStatus(int port, int gen, int status) {
        long now = SystemClock.uptimeMillis();
        if (status != sLastStatus) {
            Log.i(tag(), "HDMI " + port + " signal status " + status + (HdmiLink.stable(status) ? " (stable)" : ""));
            sLastStatus = status;
        }
        sNoPictureSince = HdmiLink.noPictureSince(sNoPictureSince, status, now);
        if (sNoPictureSince == 0) {
            sSourceOnSince = 0;
            return;
        }
        if (sChecking || sSessionRelinks > 0 || sBootRelinks >= HdmiLink.MAX_PER_BOOT) return;
        // while there is no picture: the CEC power of the source every tick (system_server, cheap)
        sChecking = true;
        final Context app = sApp;
        work(() -> {
            final boolean on = sourceOn(app, port);
            sMain.post(() -> onSource(app, port, gen, on));
        });
    }

    private static void onSource(Context app, int port, int gen, boolean on) {
        sChecking = false;
        if (gen != sGen || port != sPort || sNoPictureSince == 0) return;
        long now = SystemClock.uptimeMillis();
        sSourceOnSince = HdmiLink.since(sSourceOnSince, on, now);
        final long noPictureMs = now - sNoPictureSince;
        final long onMs = sSourceOnSince == 0 ? -1 : now - sSourceOnSince;
        if (!HdmiLink.relinkNow(noPictureMs, onMs, awake(), true, sSessionRelinks, sBootRelinks)) return;
        // the tvinput provider only when everything else says relink
        sChecking = true;
        work(() -> {
            final boolean streaming = contains(sHost.shownPorts(app), port);
            sMain.post(() -> {
                sChecking = false;
                if (gen != sGen || port != sPort || sNoPictureSince == 0) return;
                if (!HdmiLink.relinkNow(noPictureMs, onMs, awake(), streaming, sSessionRelinks, sBootRelinks)) {
                    Log.i(tag(), "HDMI " + port + " no picture for " + noPictureMs / 1000 + " s, no relink (tvinput shows it "
                            + streaming + ")");
                    return;
                }
                sSessionRelinks++;
                sBootRelinks++;
                Log.i(tag(), "HDMI " + port + " no picture for " + noPictureMs / 1000 + " s, CEC source on for " + onMs / 1000
                        + " s: relink " + sBootRelinks + "/" + HdmiLink.MAX_PER_BOOT);
                sHost.run(v -> flip(v, port));
            });
        });
    }

    // ================================================================== HAL (the host's HAL thread)

    private static Integer readStatus(RelinkHost.Vendor v) {
        try {
            return v.signalStatus();
        } catch (Throwable t) {
            Log.d(tag(), "signal status: " + t);
            return null;
        }
    }

    /** HAL thread: save the user's version, write the other one, the user's back {@value HdmiLink#FLIP_HOLD_MS} ms later. */
    private static void flip(RelinkHost.Vendor g, int port) {
        final int user;
        final int to;
        try {
            user = g.edidVersion();
            to = HdmiLink.flipTo(user, g.edidVersions(), sHost.edidFirst(), sHost.edidSecond());
        } catch (Throwable t) {
            Log.w(tag(), "HDMI " + port + " relink: EDID version unreadable, nothing written: " + t);
            return;
        }
        if (to < 0) {
            Log.w(tag(), "HDMI " + port + " relink: EDID version " + user + " is not one to write back, nothing written");
            return;
        }
        if (!prefs().edit().putBoolean(K_PENDING, true).putInt(K_USER, user).commit()) {
            Log.w(tag(), "HDMI " + port + " relink: the user's EDID version not saved, nothing written");
            return;
        }
        try {
            boolean r = g.setEdidVersion(to);
            Log.i(tag(), "HDMI " + port + " relink: EDID " + user + " -> " + to + " (" + r + "), back in "
                    + HdmiLink.FLIP_HOLD_MS + " ms");
        } catch (Throwable t) {
            Log.w(tag(), "HDMI " + port + " relink: EDID " + to + " not written: " + t);
        }
        // always write the user's version back (also after a failed write: it may have reached the vendor)
        boolean queued = sHost.runDelayed(v -> restorePending(v, "relink"), HdmiLink.FLIP_HOLD_MS,
                () -> Log.w(tag(), "HAL not connected: the user's EDID goes back at the next connect"));
        if (!queued) Log.w(tag(), "restore not queued: the user's EDID goes back at the next connect");
    }

    /** HAL thread: the saved user version back (read back), then the mark is cleared. */
    private static void restorePending(RelinkHost.Vendor g, String why) {
        SharedPreferences p = prefs();
        if (p == null || !p.getBoolean(K_PENDING, false)) return;
        int user = p.getInt(K_USER, -1);
        if (!HdmiLink.restorable(user, g.edidVersions())) {
            p.edit().putBoolean(K_PENDING, false).commit();
            Log.w(tag(), "EDID " + user + " saved by an older build is no stock value here: not written back (" + why + ")");
            return;
        }
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                int now = g.edidVersion();
                if (now != user) g.setEdidVersion(user);
                int back = g.edidVersion();
                if (back == user) {
                    p.edit().putBoolean(K_PENDING, false).commit();
                    Log.i(tag(), "EDID " + user + " back (" + why + (now != user ? ", was " + now : "") + ")");
                    return;
                }
                Log.w(tag(), "EDID reads " + back + " after writing " + user + " (" + why + ", attempt " + attempt + ")");
            } catch (Throwable t) {
                Log.w(tag(), "EDID " + user + " not written back (" + why + ", attempt " + attempt + "): " + t);
            }
            SystemClock.sleep(500);
        }
        Log.w(tag(), "EDID " + user + " still pending: written back at the next HAL connect");
    }

    // ================================================================== helpers

    private static String tag() {
        RelinkHost h = sHost;
        return h == null ? "LumenRelink" : h.tag();
    }

    private static void work(Runnable r) {
        sWork.post(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                Log.w(tag(), "worker: " + t);
            }
        });
    }

    private static SharedPreferences prefs() {
        Context app = sApp;
        return app == null ? null : app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The screen is on and the lamp-only standby is not running. */
    private static boolean awake() {
        try {
            PowerManager pm = sApp.getSystemService(PowerManager.class);
            if (pm != null && !pm.isInteractive()) return false;
        } catch (Throwable ignored) {
        }
        return !sHost.standby();
    }

    /** Worker thread: a CEC device behind {@code port} (not us) reports power on or turning on. */
    private static boolean sourceOn(Context app, int port) {
        try {
            HdmiControlManager m = app.getSystemService(HdmiControlManager.class);
            List<HdmiDeviceInfo> list = m == null ? null : m.getConnectedDevices();
            if (list == null) return false;
            for (HdmiDeviceInfo d : list) {
                if (d == null || !d.isCecDevice() || d.getPortId() != port || d.getLogicalAddress() == 0) continue;
                int s = d.getDevicePowerStatus();
                if (s == HdmiControlManager.POWER_STATUS_ON || s == HdmiControlManager.POWER_STATUS_TRANSIENT_TO_ON) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(tag(), "CEC devices: " + t);
        }
        return false;
    }

    private static boolean contains(int[] a, int v) {
        if (a == null) return false;
        for (int x : a) if (x == v) return true;
        return false;
    }
}
