// SPDX-License-Identifier: Apache-2.0
package org.lumen.aipq;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Android side of the stock AI picture quality (org.lumen.aipq.AiPq) in a projector app: XRMService start at the
 * first HAL connection (a reset at every later one: the vendor restarted), reset at every screen on, the GmTvManager
 * events from the vendor callback, the video types of com.xgimi.ai.NOTIFICATION and the Youku bluray state
 * (AIReceiver), and the 1.5 s video monitor of AIPQ.startVideoPlayMonitor. The monitor starts with the first video
 * type: before one, every pass of it writes nothing (curPic is the default scene), so the vendor sees the same writes.
 * Every AiPq call runs through the app's HAL runner (its HAL thread with its vendor) or, for the app's own picture mode
 * write, on the thread that wrote; one lock keeps them in order. This class never blocks a binder or the main thread.
 * The two Android reads of stock AIPQ (multi window, top package) are answered here for every model.
 */
public final class AiPqDriver {
    /** Runs one task on the app's HAL thread with that projector's vendor calls; drops it when the HAL is down. */
    public interface HalRunner { void run(AiPqTask task); }

    public interface AiPqTask { void run(AiPqVendor v) throws Exception; }

    public static final String ACTION_AI = "com.xgimi.ai.NOTIFICATION";
    public static final String ACTION_YOUKU_BLURAY = "com.youku.tv.video.play.bluray";
    static final String PROP_RO = "ro.xgimi.default.aipq", PROP_PERSIST = "persist.xgimi.default.aipq";
    /** Stock MultiWindowConstants.PROP_MULTIWINDOW_ENABLE. */
    static final String PROP_MULTIWINDOW = "sys.mw.enable";
    private static final long MONITOR_MS = 1500;

    private final String tag;
    private final HalRunner hal;
    private final AiPq aipq;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean monitor = new AtomicBoolean();
    private volatile Context ctx;

    /**
     * @param fallbackScene the per-model scene name when the vendor sets neither property (the model file of every
     *                      current model says film)
     */
    public AiPqDriver(String tag, HalRunner hal, String fallbackScene, int multiPqType) {
        this.tag = tag;
        this.hal = hal;
        String ro = SystemProperties.get(PROP_RO, "");
        int persist = SystemProperties.getInt(PROP_PERSIST, -1);
        if (AiPq.mode(ro) == null && persist < 0) ro = fallbackScene;
        this.aipq = new AiPq(ro, persist < 0 ? AiPq.PERSIST_DEFAULT : persist, multiPqType, s -> Log.i(tag, s));
        Log.i(tag, "AI picture quality: ro=" + SystemProperties.get(PROP_RO, "") + " persist=" + persist
                + " default scene " + aipq.defaultScene());
    }

    private void post(String what, AiPqTask t) {
        hal.run(v -> locked(what, v, t));
    }

    private void locked(String what, AiPqVendor v, AiPqTask t) {
        synchronized (aipq) {
            try {
                t.run(new AndroidSide(v));
            } catch (Throwable e) {
                Log.w(tag, what + ": " + e);
            }
        }
    }

    /** The video type receiver (XRMService.onCreate AIReceiver.register). Main thread, once. */
    public void install(Context c) {
        ctx = c.getApplicationContext();
        IntentFilter f = new IntentFilter(ACTION_AI);
        f.addAction(ACTION_YOUKU_BLURAY);
        c.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context x, Intent i) { onBroadcast(i); }
        }, f, Context.RECEIVER_EXPORTED);
    }

    /** After every HAL connection, on the HAL thread or posted: start once, a reset after a vendor restart. */
    public void connected() {
        boolean first = started.compareAndSet(false, true);
        post(first ? "start" : "reset", v -> {
            if (first) aipq.start(v); else aipq.reset(v);
        });
    }

    /** Screen on (XRMService.onScreenOn). */
    public void screenOn() { post("screen on", aipq::reset); }

    /** A GmTvManager event from the vendor callback (binder thread): posted. */
    public void onTvEvent(int type, String msg) {
        if (type != AiPq.EVENT_PICTURE_MODE) return;
        post("tv event " + msg, v -> aipq.onTvEvent(v, type, msg));
    }

    /**
     * After the app itself wrote a picture mode (menu, quick panel, reset): the event the vendor sends for that write,
     * handled now so the scene does not depend on the callback; the vendor's own copy is then a no-op (AiPq keeps the
     * mode it saw last). On the thread that wrote, right after the write, with the vendor of that thread's client.
     */
    public void afterOwnWrite(AiPqVendor v, int mode) {
        locked("own write " + mode, v, x -> aipq.onTvEvent(x, AiPq.EVENT_PICTURE_MODE, AiPq.EVENT_PREFIX + ":" + mode));
    }

    void onBroadcast(Intent i) {
        String type = i.getStringExtra("video_type");
        String pkg = i.getStringExtra("target_pkgName");
        if (ACTION_YOUKU_BLURAY.equals(i.getAction())) {
            int st = i.getIntExtra("video_status", 5);
            type = st == 2 ? AiPq.YOUKU_PLAY : st == 5 ? AiPq.YOUKU_STOP : "";
        }
        final String t = type, p = pkg != null ? pkg : "";
        Log.i(tag, "video type " + t + " from " + p);
        if (TextUtils.isEmpty(t)) return;
        post("video type " + t, v -> aipq.onVideoType(v, t, p, v.videoPlaying()));
        startMonitor();
    }

    private void startMonitor() {
        if (!monitor.compareAndSet(false, true)) return;
        Thread th = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(MONITOR_MS);
                    boolean playing = readPlaying();
                    if (!playing) {
                        Thread.sleep(MONITOR_MS);
                        playing = readPlaying();
                    }
                    final boolean p = playing;
                    post("monitor", v -> aipq.monitorStep(v, p));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, tag + "-aipq-monitor");
        th.setDaemon(true);
        th.start();
    }

    private boolean readPlaying() throws InterruptedException {
        boolean[] r = new boolean[1];
        CountDownLatch done = new CountDownLatch(1);
        hal.run(v -> {
            try {
                r[0] = v.videoPlaying();
            } finally {
                done.countDown();
            }
        });
        done.await(5, TimeUnit.SECONDS);
        return r[0];
    }

    /** The HAL calls of one projector plus the two Android reads of stock AIPQ, the same on every model. */
    private final class AndroidSide implements AiPqVendor {
        private final AiPqVendor v;

        AndroidSide(AiPqVendor v) { this.v = v; }

        @Override public int pictureMode() throws Exception { return v.pictureMode(); }
        @Override public void setPictureMode(int mode) throws Exception { v.setPictureMode(mode); }
        @Override public int hdrType() throws Exception { return v.hdrType(); }
        @Override public boolean hdrVividAuto(int pictureMode) throws Exception { return v.hdrVividAuto(pictureMode); }
        @Override public boolean hdrVividPlaying() throws Exception { return v.hdrVividPlaying(); }
        @Override public boolean imaxPlaying() throws Exception { return v.imaxPlaying(); }
        @Override public int current3DFormat() throws Exception { return v.current3DFormat(); }
        @Override public int lumensMode() throws Exception { return v.lumensMode(); }
        @Override public boolean videoPlaying() throws Exception { return v.videoPlaying(); }

        @Override public boolean multiWindow() { return SystemProperties.getBoolean(PROP_MULTIWINDOW, false); }

        @Override public String topPackage() {
            Context c = ctx;
            try {
                ActivityManager am = c != null ? c.getSystemService(ActivityManager.class) : null;
                List<ActivityManager.RunningTaskInfo> t = am != null ? am.getRunningTasks(1) : null;
                return t != null && !t.isEmpty() && t.get(0).topActivity != null
                        ? t.get(0).topActivity.getPackageName() : "";
            } catch (Throwable e) {
                return "";
            }
        }
    }

    /** For a test: the core. */
    AiPq core() { return aipq; }
}
