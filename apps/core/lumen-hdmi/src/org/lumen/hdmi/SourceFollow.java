// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

import android.content.Context;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Debug;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The projector sleeps when the HDMI device being watched turns off (SourceOff decides), on every model whose app
 * runs HdmiRelink: the session events reach it through {@link HdmiRelink#onSessionEvent} (Z9X, HB, RS 20 through
 * HDMI_EVENT, the X50 family through the polled port provider). Only while the stock switch is on: XGIMI's "HDMI
 * devices control the projector" writes tv_send_standby_on_sleep with the wake (newsettings
 * SignalRepository.setDeviceAutoStart -> GimiCommonService.setHdmiCecAutoDeviceOffEnabled), Lumen's "Turn off HDMI
 * devices with the projector" row writes the same setting. The sleep is the call an incoming {@code <Standby>} makes
 * (HdmiControlService.standby: goToSleep with reason HDMI), so the projector's sleep path is the same (lamp off, STR,
 * and the framework's {@code <Standby>} to the other devices that this switch asks for). Main thread for the events,
 * one worker for the binder reads. Never throws.
 */
public final class SourceFollow {
    private static Handler sMain, sWork;
    private static Context sApp;
    // main thread state
    private static volatile int sPort;
    private static int sGen;
    private static final SourceOff RULE = new SourceOff();
    /** The stock switch (CEC on and tv_send_standby_on_sleep), read once per viewing and on a change; null = read. */
    private static volatile Boolean sFollow;
    /** Uptime of the last read of the switch: read again after {@link SourceOff#SWITCH_READ_MS}. */
    private static volatile long sFollowAt;

    /** The rule behind the family flag (RelinkHost.inactiveSourceSleep, off everywhere): main thread. */
    private static final InactiveSleep INACTIVE = new InactiveSleep();

    private SourceFollow() {}

    /** The HDMI port on screen now, 0 = none (any thread). */
    static int shownPort() {
        return sPort;
    }

    /** A key of the projector app (main thread): nothing pending of the {@code <Inactive Source>} rule sleeps. */
    public static void onUserKey() {
        INACTIVE.key();
    }

    /** HdmiRelink (main thread): tvinput shows {@code port} now (active) or gave it back. */
    static void onSession(Context app, int port, boolean active) {
        if (app == null || port <= 0) return;
        if (sMain == null) {
            sApp = app.getApplicationContext();
            sMain = new Handler(Looper.getMainLooper());
            HandlerThread t = new HandlerThread("lumen-follow");
            t.start();
            sWork = new Handler(t.getLooper());
            sWork.post(SourceFollow::listen);
        }
        if (active) {
            if (port == sPort) return;
            sGen++;
            sPort = port;
            RULE.reset();
            INACTIVE.reset();
            sFollow = null;
            sMain.removeCallbacks(TICK);
            sMain.postDelayed(TICK, SourceOff.FIRST_READ_MS);
            sMain.removeCallbacks(ITICK);
            if (HdmiRelink.inactiveSourceSleep()) sMain.postDelayed(ITICK, InactiveSleep.READ_MS);
        } else if (port == sPort) {
            sGen++;
            sPort = 0;
            INACTIVE.reset();
            sMain.removeCallbacks(TICK);
            sMain.removeCallbacks(ITICK);
        }
    }

    private static final Runnable TICK = SourceFollow::tick;

    private static void tick() {
        final int port = sPort, gen = sGen;
        if (port == 0) return;
        sMain.postDelayed(TICK, SourceOff.READ_MS);
        sWork.post(() -> {
            try {
                final int[] powers = powers(port);
                final boolean follow = follow();
                sMain.post(() -> decide(port, gen, powers, follow));
            } catch (Throwable t) {
                Log.w(HdmiRelink.tag(), "follow read: " + t);
            }
        });
    }

    private static void decide(int port, int gen, int[] powers, boolean follow) {
        if (gen != sGen || port != sPort) return;
        if (!RULE.read(powers, follow, HdmiRelink.awake())) return;
        Log.i(HdmiRelink.tag(), "HDMI " + port + ": the device being watched turned off (CEC power standby after on):"
                + " the projector follows it");
        sleep();
    }

    private static void sleep() {
        sWork.post(() -> {
            try {
                SleepSource.goToSleep(sApp, PowerManager.GO_TO_SLEEP_REASON_HDMI, 0);
            } catch (Throwable t) {
                Log.w(HdmiRelink.tag(), "follow sleep: " + t);
            }
        });
    }

    private static final Runnable ITICK = SourceFollow::inactiveTick;

    /** Main, only with the family flag on: one read of the CEC history on the worker, InactiveSleep decides. */
    private static void inactiveTick() {
        final int port = sPort, gen = sGen;
        if (port == 0 || !HdmiRelink.inactiveSourceSleep()) return;
        sMain.postDelayed(ITICK, InactiveSleep.READ_MS);
        final boolean awake = HdmiRelink.awake();
        sWork.post(() -> {
            try {
                final boolean on = follow();
                final String dump = on && awake ? hdmiDump() : null;
                final int[] behind = on && awake ? sources(port) : new int[0];
                sMain.post(() -> {
                    if (gen != sGen || port != sPort) return;
                    if (!INACTIVE.read(SystemClock.uptimeMillis(), dump, behind, on, awake)) return;
                    Log.i(HdmiRelink.tag(), "HDMI " + port + ": <Inactive Source> and not back in "
                            + InactiveSleep.WAIT_MS / 1000 + " s, no key: the projector follows it");
                    sleep();
                });
            } catch (Throwable t) {
                Log.w(HdmiRelink.tag(), "follow history: " + t);
            }
        });
    }

    /** Worker: the logical addresses of the sources behind {@code port} (not us, no audio system). */
    private static int[] sources(int port) {
        HdmiControlManager m = sApp.getSystemService(HdmiControlManager.class);
        List<HdmiDeviceInfo> list = m == null ? null : m.getConnectedDevices();
        if (list == null) return new int[0];
        int[] out = new int[list.size()];
        int n = 0;
        for (HdmiDeviceInfo d : list) {
            if (d == null || !d.isCecDevice() || d.getPortId() != port || d.getLogicalAddress() == 0) continue;
            if (SourceOff.source(d.getDeviceType())) out[n++] = d.getLogicalAddress();
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** Worker: the text of dumpsys hdmi_control (DUMP), null when it fails or takes over 2 s. */
    private static String hdmiDump() {
        ParcelFileDescriptor[] pipe = null;
        try {
            pipe = ParcelFileDescriptor.createPipe();
            final ParcelFileDescriptor write = pipe[1];
            // the public dump call (no binder of our own): it blocks until the service wrote everything, so it runs on
            // its own thread while this one reads, and the pipe closes behind it
            Thread dump = new Thread(() -> {
                try {
                    Debug.dumpService("hdmi_control", write.getFileDescriptor(), new String[0]);
                } finally {
                    try {
                        write.close();
                    } catch (IOException ignored) {
                    }
                }
            }, "lumen-hdmi-dump");
            dump.setDaemon(true);
            dump.start();
            final ParcelFileDescriptor read = pipe[0];
            Runnable stop = () -> {
                try {
                    read.close();
                } catch (IOException ignored) {
                }
            };
            sMain.postDelayed(stop, 2_000);
            StringBuilder sb = new StringBuilder();
            try (Reader r = new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(read),
                    StandardCharsets.UTF_8)) {
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0 && sb.length() < 512 * 1024) sb.append(buf, 0, n);
            } finally {
                sMain.removeCallbacks(stop);
            }
            return sb.toString();
        } catch (Throwable t) {
            Log.w(HdmiRelink.tag(), "follow dump: " + t);
            return null;
        } finally {
            if (pipe != null) {
                try {
                    pipe[0].close();                                  // the write end is the dump thread's
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Worker: the cached CEC power status of every source behind {@code port} (not us, no audio system). */
    private static int[] powers(int port) {
        HdmiControlManager m = sApp.getSystemService(HdmiControlManager.class);
        List<HdmiDeviceInfo> list = m == null ? null : m.getConnectedDevices();
        if (list == null) return new int[0];
        int[] out = new int[list.size()];
        int n = 0;
        for (HdmiDeviceInfo d : list) {
            if (d == null || !d.isCecDevice() || d.getPortId() != port || d.getLogicalAddress() == 0) continue;
            if (!SourceOff.source(d.getDeviceType())) continue;
            out[n++] = d.getDevicePowerStatus();
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** Worker: the stock switch, tv_send_standby_on_sleep (HdmiCecConfig), from the cache when it is still valid. */
    private static boolean follow() {
        Boolean f = sFollow;
        if (f != null && SystemClock.uptimeMillis() - sFollowAt < SourceOff.SWITCH_READ_MS) return f;
        HdmiControlManager m = sApp.getSystemService(HdmiControlManager.class);
        boolean on = m != null && m.getHdmiCecEnabled() == HdmiControlManager.HDMI_CEC_CONTROL_ENABLED
                && m.getTvSendStandbyOnSleep() == HdmiControlManager.TV_SEND_STANDBY_ON_SLEEP_ENABLED;
        sFollow = on;
        sFollowAt = SystemClock.uptimeMillis();
        return on;
    }

    /** Worker, once: CEC switched on or off makes the next tick read the switch again (Android 14 has no listener
     *  for tv_send_standby_on_sleep: that one is read again after SourceOff.SWITCH_READ_MS, not every 5 s). */
    private static void listen() {
        try {
            HdmiControlManager m = sApp.getSystemService(HdmiControlManager.class);
            if (m != null) m.addHdmiCecEnabledChangeListener(sWork::post, name -> sFollow = null);
        } catch (Throwable t) {
            Log.w(HdmiRelink.tag(), "follow listener: " + t);
        }
    }
}
