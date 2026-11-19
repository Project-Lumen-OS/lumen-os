// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

/**
 * The one vendor audio path switch of every Lumen projector: what the stock audio server's libxgimiaudiopolicy does and
 * Lumen's AOSP audio server does not. The per-model part is only the VendorAudio adapter (how to reach 59 / 60).
 *
 * Stock (Z9X / HB / X50 libxgimiaudiopolicy.so, the Play 6 one has the same strings): Egn_getDeviceForMedia calls
 * setXgimiPathForDeviceDelay when the media device maps to another EN_XGIMI_AUDIO_PATH: at once
 * "xgimi_audiopath_switch_start=P" (the HAL zeroes the sink level), 59 setAudioOutput(100) (Msrv mutes every TV path)
 * and the digital output format; threadInit then waits for sys.boot_completed and 50 x 20 ms without a further change,
 * sends 59(P) (Msrv SetOutputType: path, amp mute per path, sound effects from its DB, vender.xgimi.audio.ready=1) and
 * "xgimi_audiopath_switch_end=P" for every P: the HAL's sink becomes P's device and its stored level is restored. The
 * HAL writes A2DP only while its sink is A2DP, so the end matters for every path, not only Bluetooth.
 * Here, the same on every model:
 *  - the path of the media route is MediaPath.heard over the table's paths (never the heap order of AOSP's list);
 *  - a change: switch_start=P, 59(100) and the format again at once; 59(P) after MediaPath.switchDelayMs;
 *  - after 59(P) (or when the vendor already holds P): the levels of P stored (StockVolume.store), then switch_end=P
 *    through HalSink (a stale end never lands after a newer one), again after it where the HAL wants that
 *    (ChipTable.levelsAfterEnd); a switch_start without its end gets the end after MediaPath.SWITCH_END_SAFETY_MS;
 *  - nothing is sent when ready == 1 and 60 already reads P; 1.5 s after 59(P) the same check, else retries with
 *    backoff; SCREEN_ON and a HAL reconnect check again (Msrv GoToShutdown resets the path), the audio server coming
 *    back and an A2DP sink added again select the sink again;
 *  - never 1 (SPDIF), never on the main thread.
 * Threads: device callbacks, the debounce and the sink on "lumen-audiopath"; vendor calls on the model's HAL thread.
 * Log tag LumenPathSwitch.
 */
public final class PathSwitch {
    private static final String TAG = "LumenPathSwitch";
    static final String PROP_READY = "vender.xgimi.audio.ready";
    private static final long VERIFY_DELAY_MS = 1_500L;
    private static final long WAIT_POLL_MS = 1_000L;
    private static final long WAIT_MAX_MS = 5 * 60_000L;
    private static final long SCREEN_ON_CHECK_MS = 3_000L;
    /** After an A2DP sink appears: AudioService has routed media by then. */
    private static final long A2DP_RESINK_MS = 800L;
    private static final long[] RETRY_MS = {2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 60_000L};
    private static final AudioAttributes MEDIA = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();

    private static Context sApp;
    private static VendorAudio sVendor;
    private static ChipTable sTable;
    private static Handler sH;
    private static long sWaitStart;
    /** lumen-audiopath writes, any thread reads: the path to report (latest evaluation), -1 none yet. */
    private static volatile int sTarget = MediaPath.NONE;
    /** The path last sent with 59 (not 100), -1 none. */
    private static volatile int sSent = MediaPath.NONE;
    /** The path confirmed by ready + 60 this process, -1 none. */
    private static volatile int sConfirmed = MediaPath.NONE;
    /** The path of a switch_start whose switch_end is still due, -1 none. */
    private static volatile int sStartPending = MediaPath.NONE;
    /** 59(100) was sent and no 59(P) since (60 still reads the old path): the next commit sends 59(P). */
    private static volatile boolean sSwitching;
    /** The vendor HAL reconnected (gmpf restarted: path 100, the ready prop stays 1). */
    private static volatile boolean sForce;
    private static int sConnects;
    private static int sRetry;
    private static boolean sVerifying;
    private static int sGen;
    private static final Runnable COMMIT = PathSwitch::commit;
    private static final CopyOnWriteArrayList<IntConsumer> CONFIRMED = new CopyOnWriteArrayList<>();

    private PathSwitch() {}

    /** Application.onCreate: the callbacks and the levels (StockVolume). Idempotent, never throws, no vendor call here. */
    public static synchronized void install(Context ctx, VendorAudio vendor) {
        if (sApp != null || vendor == null) return;
        try {
            sApp = ctx.getApplicationContext();
            sVendor = vendor;
            sTable = StockVolume.table(sApp);
            HandlerThread t = new HandlerThread("lumen-audiopath");
            t.start();
            sH = new Handler(t.getLooper());
            HalSink.routeFrom(() -> sTarget);
            StockVolume.install(sApp);
            BootSound.install(sApp);
            StockVolume.onAudioServerUp(() -> sH.post(() -> resink("audio server up")));
            vendor.registerDevices(sApp, new AudioDeviceCallback() {
                @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                    evaluate("devices added");
                    for (AudioDeviceInfo d : added) {
                        if (d.isSink() && d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                            // a speaker that comes back: the HAL dropped its A2DP sink, the route may read A2DP before and after
                            sH.postDelayed(() -> { if (sTarget == MediaPath.BT) resink("A2DP sink added"); }, A2DP_RESINK_MS);
                            break;
                        }
                    }
                }

                @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
                    evaluate("devices removed");
                }
            }, sH);
            AudioManager am = sApp.getSystemService(AudioManager.class);
            try {
                am.addOnDevicesForAttributesChangedListener(MEDIA, sH::post, (attrs, devices) -> evaluate("media routing " + devices));
            } catch (Throwable e) {
                Log.w(TAG, "routing listener not available (device callback only): " + e);
            }
            vendor.onConnected(() -> {
                if (++sConnects > 1) {
                    sForce = true;
                    setConfirmed(MediaPath.NONE);
                }
                Handler h = sH;
                if (h != null) h.post(() -> check(sConnects > 1 ? "HAL reconnected" : "HAL connected"));
            });
            if (booted()) sH.post(() -> boot("process start"));
            Log.i(TAG, "installed, " + sTable.rows().size() + " chip outputs");
        } catch (Throwable t) {
            Log.e(TAG, "install", t);
        }
    }

    /** BOOT_COMPLETED receiver of the model. */
    public static void onBootCompleted(Context ctx, VendorAudio vendor) {
        install(ctx, vendor);
        StockVolume.onBootCompleted(ctx);
        Handler h = sH;
        if (h != null) h.post(() -> boot("boot completed"));
    }

    /** SCREEN_ON: check again after a wake (Msrv GoToShutdown resets the path to 100). */
    public static void onScreenOn() {
        Handler h = sH;
        if (h != null) h.postDelayed(() -> check("screen on"), SCREEN_ON_CHECK_MS);
    }

    /** The path confirmed last (MediaPath values), -1 none. Any thread. */
    public static int confirmed() {
        return sConfirmed;
    }

    /** The routed path of the latest evaluation, -1 none. Any thread. */
    public static int target() {
        return sTarget;
    }

    /** True when path reporting is active this boot: the path confirmed last is what the vendor holds. HAL thread. */
    public static boolean active(VendorOutput out) {
        int p = sConfirmed;
        if (p < 0 || !"1".equals(prop(PROP_READY))) return false;
        try {
            return out.read() == p;
        } catch (Throwable t) {
            Log.w(TAG, "read: " + t);
            return false;
        }
    }

    /** {@code l} on the HAL thread after a 59(P) of this process was confirmed (sound effects re-apply). */
    public static void addConfirmedListener(IntConsumer l) {
        if (l != null) CONFIRMED.addIfAbsent(l);
    }

    // ------------------------------------------------------------------ lumen-audiopath thread
    private static void boot(String why) {
        try {
            sVendor.onBoot(why);
        } catch (Throwable t) {
            Log.w(TAG, "vendor boot hook: " + t);
        }
        evaluate(why);
        bootCheck(SystemClock.elapsedRealtime());
    }

    /** Recomputes the media path; a change starts the stock switch. */
    private static void evaluate(String why) {
        try {
            int p = currentPath();
            if (p == MediaPath.NONE) {
                Log.i(TAG, why + ": no media output device known, nothing reported");
                return;
            }
            if (p == sTarget) {
                if (sConfirmed != p && !sVerifying && !sH.hasCallbacks(COMMIT)) {
                    sRetry = 0;
                    schedule(0);
                }
                return;
            }
            int prev = sConfirmed >= 0 ? sConfirmed : sSent;
            Log.i(TAG, why + ": media output -> path " + p + ", was " + sTarget);
            sTarget = p;
            if (MediaPath.formatAgain(prev, p)) sVendor.formatAgain("media path " + prev + " -> " + p);
            sGen++;
            sRetry = 0;
            sVerifying = false;
            if (MediaPath.switchStart(prev, p) && readyForHal()) {
                switchStart(p);
                final int gen = sGen;
                sVendor.post("59(100)", out -> {
                    if (gen != sGen) return;
                    sSwitching = true;                      // set first: a failed call may still have muted
                    setConfirmed(MediaPath.NONE);
                    out.select(MediaPath.SWITCHING);
                    Log.i(TAG, "59(100) switch start (from " + prev + ")");
                }, null);
            }
            schedule(MediaPath.switchDelayMs(prev, p));
        } catch (Throwable t) {
            Log.w(TAG, "evaluate: " + t);
        }
    }

    /** switch_start=P now (the HAL zeroes the sink level), switch_end due after 59(P) or after the safety time. */
    private static void switchStart(int path) {
        try {
            sApp.getSystemService(AudioManager.class).setParameters(HalSink.START + path);
            sStartPending = path;
            Log.i(TAG, "audio HAL switch_start=" + path);
            final int gen = sGen;
            sH.postDelayed(() -> {
                if (sStartPending < 0 || gen != sGen) return;
                Log.w(TAG, "switch_end not sent " + MediaPath.SWITCH_END_SAFETY_MS + " ms after switch_start=" + sStartPending
                        + ": switch_end=" + sTarget + " now");
                sink(sTarget, "safety");
            }, MediaPath.SWITCH_END_SAFETY_MS);
        } catch (Throwable t) {
            Log.w(TAG, "switch_start=" + path + ": " + t);
        }
    }

    /**
     * The stock end of a switch for the media route {@code path} (any thread): the levels of the path stored first,
     * then switch_end=path (the HAL restores the stored level of its new sink), ticketed when decided (HalSink).
     */
    private static void sink(int path, String why) {
        if (path < 0) return;
        sStartPending = MediaPath.NONE;
        int ticket = HalSink.ticket();
        try {
            AudioManager am = sApp.getSystemService(AudioManager.class);
            String stored = StockVolume.store(path, "before switch_end=" + path);
            if (!HalSink.end(am, ticket, path, path, why)) return;
            if (sTable.levelsAfterEnd()) stored = StockVolume.store(path, "after switch_end=" + path);
            Log.i(TAG, "audio HAL sink: switch_end=" + path + (stored.isEmpty() ? "" : ", level" + stored) + " (" + why + ")");
        } catch (Throwable t) {
            Log.w(TAG, "switch_end=" + path + ": " + t);
        }
    }

    /** The sink of the current route again (audio server back, A2DP sink added): the HAL lost it. */
    private static void resink(String why) {
        int t = sTarget;
        if (t >= 0) sink(t, why);
    }

    private static void schedule(long delayMs) {
        sH.removeCallbacks(COMMIT);
        sH.postDelayed(COMMIT, delayMs);
    }

    /** SCREEN_ON / HAL (re)connect: verify the confirmed path, report again when the vendor lost it. */
    private static void check(String why) {
        if (sTarget < 0) {
            evaluate(why);
            return;
        }
        final int gen = sGen;
        final int target = sTarget;
        sVendor.post("check", out -> {
            if (gen != sGen) return;
            if (vendorHas(out, target)) {
                setConfirmed(target);
                return;
            }
            Log.i(TAG, why + ": vendor path is not " + target + " any more, reporting again");
            setConfirmed(MediaPath.NONE);
            sH.post(() -> {
                if (gen != sGen || sVerifying) return;
                sRetry = 0;
                schedule(0);
            });
        }, null);
    }

    /** After the debounce: 59(P) on the HAL thread once the boot and the vendor are ready. */
    private static void commit() {
        final int target = sTarget;
        if (target < 0) return;
        if (!readyForHal()) {
            long now = SystemClock.elapsedRealtime();
            if (sWaitStart == 0) sWaitStart = now;
            if (now - sWaitStart < WAIT_MAX_MS) {
                sH.postDelayed(COMMIT, WAIT_POLL_MS);
            } else {
                Log.w(TAG, "vendor not ready after " + WAIT_MAX_MS + " ms: path " + target + " not reported");
                sWaitStart = 0;
            }
            return;
        }
        sWaitStart = 0;
        final int gen = sGen;
        sVerifying = true;
        boolean queued = sVendor.post("59(" + target + ")", out -> {
            if (gen != sGen) return;
            out.beforeReport();
            if (vendorHas(out, target)) {
                sSent = target;
                setConfirmed(target);
                Log.i(TAG, "path " + target + " already set in the vendor (ready=1, 60=" + target + "): not sent");
                sink(target, "path " + target + " held");
                sH.post(() -> { if (gen == sGen) sVerifying = false; });
                return;
            }
            try {
                out.select(target);
            } catch (Throwable e) {
                sH.post(() -> retry(gen, target, "59: " + e));
                return;
            }
            sSent = target;
            sSwitching = false;
            sForce = false;
            Log.i(TAG, "59(" + target + ") sent");
            sink(target, "after 59(" + target + ")");
            sH.postDelayed(() -> verify(gen, target), VERIFY_DELAY_MS);
        }, () -> sH.post(() -> retry(gen, target, "HAL not connected")));
        if (!queued) retry(gen, target, "HAL thread not available");
    }

    /** 1.5 s after 59(P): confirmed, or retried with backoff. */
    private static void verify(final int gen, final int target) {
        if (gen != sGen) return;
        boolean queued = sVendor.post("verify " + target, out -> {
            if (gen != sGen) return;
            if (vendorHas(out, target)) {
                setConfirmed(target);
                Log.i(TAG, "path " + target + " confirmed (ready=1, 60=" + target + ")");
                sH.post(() -> { if (gen == sGen) sVerifying = false; });
                for (IntConsumer l : CONFIRMED) {
                    try { l.accept(target); } catch (Throwable t) { Log.w(TAG, "path listener: " + t); }
                }
            } else {
                sH.post(() -> retry(gen, target, "not confirmed"));
            }
        }, () -> sH.post(() -> retry(gen, target, "HAL not connected")));
        if (!queued) retry(gen, target, "HAL thread not available");
    }

    private static void retry(int gen, int target, String why) {
        if (gen != sGen) return;
        sVerifying = false;
        if (sRetry >= RETRY_MS.length) {
            Log.w(TAG, "path " + target + ": " + why + ", giving up until the next change / screen on");
            return;
        }
        long d = RETRY_MS[sRetry++];
        Log.i(TAG, "path " + target + ": " + why + ", retry " + sRetry + " in " + d + " ms");
        schedule(d);
    }

    /** The one-time / per-boot vendor check of the model (VendorOutput.beforeReport), also without any output path. */
    private static void bootCheck(final long started) {
        if (!readyForHal()) {
            if (SystemClock.elapsedRealtime() - started < WAIT_MAX_MS) sH.postDelayed(() -> bootCheck(started), WAIT_POLL_MS);
            return;
        }
        sVendor.post("boot check", VendorOutput::beforeReport, () -> sH.postDelayed(() -> bootCheck(started), WAIT_POLL_MS));
    }

    // ------------------------------------------------------------------ helpers
    private static void setConfirmed(int p) {
        sConfirmed = p;
    }

    private static boolean readyForHal() {
        return booted() && sVendor.ready();
    }

    private static boolean booted() {
        return "1".equals(prop("sys.boot_completed"));
    }

    /** HAL thread: the vendor holds {@code p} (no switch start pending, no vendor restart since the last 59, both reads agree). */
    private static boolean vendorHas(VendorOutput out, int p) {
        if (sSwitching || sForce || !"1".equals(prop(PROP_READY))) return false;
        try {
            return out.read() == p;
        } catch (Throwable t) {
            Log.w(TAG, "60: " + t);
            return false;
        }
    }

    /** The path of the media route (getDevicesForAttributes), else of the connected outputs; NONE when unknown. */
    private static int currentPath() {
        AudioManager am = sApp == null ? null : sApp.getSystemService(AudioManager.class);
        if (am == null) return MediaPath.NONE;
        List<Integer> paths = new ArrayList<>();
        try {
            for (AudioDeviceAttributes a : am.getDevicesForAttributes(MEDIA)) paths.add(sTable.pathOf(a.getType()));
        } catch (Throwable t) {
            Log.w(TAG, "getDevicesForAttributes: " + t);
            paths.clear();
        }
        int p = MediaPath.heard(paths);
        if (p != MediaPath.NONE) return p;
        paths.clear();
        try {
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) paths.add(sTable.pathOf(d.getType()));
        } catch (Throwable t) {
            Log.w(TAG, "getDevices: " + t);
            return MediaPath.NONE;
        }
        return MediaPath.heard(paths);
    }

    private static String prop(String k) {
        try {
            return SystemProperties.get(k, "");
        } catch (Throwable t) {
            return "";
        }
    }
}
