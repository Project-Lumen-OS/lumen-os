// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.app.DreamManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;

import org.lumen.miracast.core.Diag;
import org.lumen.miracast.core.P2pCapability;
import org.lumen.miracast.core.P2pRules;
import org.lumen.miracast.core.P2pVerdict;
import org.lumen.miracast.core.WfdSession;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The screen mirroring receiver (Miracast / Wi-Fi Display sink) as a system service: started at boot
 * when enabled, foreground (type connectedDevice), no launcher entry. It keeps the projector visible
 * over Wi-Fi Direct ({@link P2pController}), asks (or not) when a phone or computer connects
 * ({@link PromptOverlay}), runs one {@link SinkSession} at a time and shows it in {@link MirrorActivity}.
 *
 * A unit without Wi-Fi Direct ({@link P2pCapability}, decided at run time for every model and chip) loses the control
 * provider ({@link ControlGate}: no quick menu tile, no Lumen Home tile) and, when nothing can change that on this
 * boot, the service stops.
 *
 * Diagnostics: every step goes to {@link #diag()} (logcat tag LumenMiracastDiag, one line per step), the
 * report of the last finished attempt is kept in diag-last.txt, and both print with
 * {@code adb shell dumpsys activity service org.lumen.miracast/.MiracastService} or on the hidden
 * {@link DiagActivity} page.
 */
public final class MiracastService extends Service implements P2pController.Callback, SinkSession.Host {
    static final String TAG = "LumenMiracast";
    static final String ACTION_RELOAD = "org.lumen.miracast.action.RELOAD";
    private static final String CHANNEL = "miracast";
    private static final int NOTIF_ID = 1;
    static final String DIAG_FILE = "diag-last.txt";

    interface UiListener {
        void onMiracastState();
    }

    private static volatile MiracastService instance;
    /** This boot found no Wi-Fi Direct at all (the service stopped itself): the status line says so. */
    private static volatile boolean noWifiDirect;
    private static final Set<UiListener> listeners = new CopyOnWriteArraySet<>();
    private static final Diag DIAG = new Diag(System::currentTimeMillis, line -> Log.i(Diag.TAG, line));

    private final Handler main = new Handler(Looper.getMainLooper());
    private P2pController p2p;
    private PromptOverlay prompt;
    private SinkSession session;
    private String peerName = "";
    /** Name from the connection request: group client entries often carry only the address. */
    private String requestName;
    /** RTSP port from the sender's WFD element in the request (the group record usually has none). */
    private int requestPort;
    private boolean firstFrame;
    private int videoW, videoH;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wake;
    private boolean screenReceiverOn;
    /** Settings.Global device_name changes (Lumen Setup, Android Settings): the default name follows, as AirPlay's. */
    private static final long NAME_DEBOUNCE_MS = 500;
    private static final long NAME_SESSION_RETRY_MS = 30_000;
    private ContentObserver nameObserver;
    private final Runnable nameChanged = this::onDeviceNameChanged;

    static MiracastService instance() {
        return instance;
    }

    static boolean noWifiDirect() {
        return noWifiDirect;
    }

    /** The receiver's stage diagnostics (one per process, also before the service runs). */
    static Diag diag() {
        return DIAG;
    }

    static void addListener(UiListener l) {
        listeners.add(l);
    }

    static void removeListener(UiListener l) {
        listeners.remove(l);
    }

    /** Starts or stops the service to match the setting (settings screen, boot, control broadcast). */
    static void sync(Context c) {
        Intent i = new Intent(c, MiracastService.class).setAction(ACTION_RELOAD);
        if (Prefs.enabled(c)) {
            c.startForegroundService(i);
        } else {
            c.stopService(new Intent(c, MiracastService.class));
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        DIAG.ok(Diag.Stage.SERVICE, "started, version " + versionName() + ", ask=" + Prefs.ask(this));
        prompt = new PromptOverlay(this);
        p2p = new P2pController(this, this, DIAG);
        p2p.start(Prefs.name(this));
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenOff, f, Context.RECEIVER_NOT_EXPORTED);
        screenReceiverOn = true;
        watchDeviceName();
        Log.i(TAG, "receiver started as " + Prefs.name(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_RELOAD.equals(intent.getAction()) && p2p != null) p2p.rename(Prefs.name(this));
        notifyUi();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "receiver stopping");
        endSession("service stopped", true);
        if (p2p != null) p2p.stop();
        if (prompt != null) prompt.hide();
        if (screenReceiverOn) unregisterReceiver(screenOff);
        main.removeCallbacks(nameChanged);
        if (nameObserver != null) getContentResolver().unregisterContentObserver(nameObserver);
        DIAG.waiting(Diag.Stage.SERVICE, "stopped (switched off or the system ended it)");
        instance = null;
        notifyUi();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** adb shell dumpsys activity service org.lumen.miracast/.MiracastService */
    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Lumen screen mirroring " + versionName() + " as \"" + Prefs.name(this) + "\" enabled=" + Prefs.enabled(this)
                + " ask=" + Prefs.ask(this) + " quality=" + Prefs.quality(this));
        pw.println("p2p: " + (p2p != null ? p2p.summary() : "not started"));
        pw.println("session: " + (session != null ? "with " + peerName + " playing=" + session.playing() + " firstFrame=" + firstFrame
                + " video=" + videoW + "x" + videoH : "none"));
        pw.println("dreaming: " + dreaming());
        pw.println("---- current");
        pw.print(DIAG.report());
        pw.println("---- last finished attempt (" + DIAG_FILE + ")");
        pw.println(lastReport(this));
    }

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            stopMirroring("standby");
        }
    };

    private void watchDeviceName() {
        try {
            nameObserver = new ContentObserver(main) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    main.removeCallbacks(nameChanged);
                    main.postDelayed(nameChanged, NAME_DEBOUNCE_MS);
                }
            };
            getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.DEVICE_NAME),
                    false, nameObserver);
        } catch (Throwable t) {
            nameObserver = null;
            Log.w(TAG, "device name observer: " + t);
        }
    }

    /** Main thread: the device name changed. Phones see the new one unless an own name is set; never mid-session. */
    private void onDeviceNameChanged() {
        if (p2p == null) return;
        if (session != null) {
            main.postDelayed(nameChanged, NAME_SESSION_RETRY_MS);
            return;
        }
        Log.i(TAG, "device name changed, listed as " + Prefs.name(this));
        p2p.rename(Prefs.name(this));
        notifyUi();
    }

    // ------------------------------------------------------------------ state for the UI

    boolean mirroring() {
        return session != null;
    }

    boolean firstFrameShown() {
        return firstFrame;
    }

    String peerName() {
        return peerName;
    }

    int videoWidth() {
        return videoW;
    }

    int videoHeight() {
        return videoH;
    }

    P2pState p2pState() {
        return p2p != null ? p2p.state() : P2pState.STOPPED;
    }

    String lastError() {
        return p2p != null ? p2p.lastError() : null;
    }

    void attachSurface(Surface s) {
        SinkSession ss = session;
        if (ss != null) ss.setSurface(s);
    }

    /** The user ended it (BACK, left the screen, standby): TEARDOWN, then drop the Wi-Fi Direct group. */
    void stopMirroring(String why) {
        main.post(() -> {
            if (session == null) return;
            endSession(why, true);
            // give the TEARDOWN (800 ms budget in SinkConnection.teardown) the link before the group goes
            main.postDelayed(() -> {
                if (session == null && p2p != null) p2p.disconnect();
            }, 900);
        });
    }

    // ------------------------------------------------------------------ P2pController.Callback (main thread)

    @Override
    public void onConnectionRequest(WifiP2pDevice device, WifiP2pConfig config, int type) {
        String who = label(device);
        requestName = device != null && device.deviceName != null && !device.deviceName.trim().isEmpty()
                ? device.deviceName.trim() : null;
        WifiP2pWfdInfo w = device != null ? device.getWfdInfo() : null;
        requestPort = w != null ? w.getControlPort() : 0;
        String address = device != null ? device.deviceAddress : null;
        if (session != null) {
            Log.i(TAG, "busy: declining " + who);
            p2p.answer(address, false, "busy with " + peerName);
            return;
        }
        // the card and later the picture must not sit under the screensaver (a dream task stays on top)
        wakeFromScreensaver("connection request");
        if (!Prefs.ask(this)) {
            p2p.answer(address, true, "ask is off");
            return;
        }
        DIAG.waiting(Diag.Stage.ANSWER, "asking on screen (30 s)");
        prompt.ask(who, allow -> {
            Log.i(TAG, (allow ? "allowed " : "declined ") + who + " (" + prompt.lastHow() + ")");
            p2p.answer(address, allow, "on screen: " + prompt.lastHow());
        });
    }

    @Override
    public void onPin(String peerAddress, String pin) {
        wakeFromScreensaver("PIN");
        prompt.showPin(peerName.isEmpty() ? getString(R.string.unknown_device) : peerName, pin);
    }

    @Override
    public void onGroupFormed(WifiP2pInfo info, WifiP2pGroup group) {
        prompt.hide();
        if (session != null) return;
        WifiP2pDevice peer = peer(info, group);
        peerName = peer != null && peer.deviceName != null && !peer.deviceName.trim().isEmpty() ? peer.deviceName.trim()
                : requestName != null ? requestName : getString(R.string.unknown_device);
        requestName = null;
        WifiP2pWfdInfo w = peer != null ? peer.getWfdInfo() : null;
        int port = P2pRules.controlPort(w != null ? w.getControlPort() : 0, requestPort);
        requestPort = 0;
        Log.i(TAG, "session with " + peerName + " control port " + port + " wfd=" + (w != null));
        WfdSession.Config cfg = new WfdSession.Config();
        cfg.friendlyName = Prefs.name(this);
        cfg.model = Build.MODEL;
        cfg.version = versionName();
        cfg.rtpPort = Prefs.RTP_PORT;
        cfg.quality = Prefs.quality(this);
        firstFrame = false;
        videoW = videoH = 0;
        session = new SinkSession(this, this, peerName, DIAG);
        session.start(info, group, port, cfg);
        notifyUi();
    }

    @Override
    public void onGroupGone() {
        endSession("wifi direct group gone", false);
    }

    @Override
    public void onStateChanged() {
        updateNotification();
        notifyUi();
    }

    @Override
    public void onCapability(P2pVerdict verdict, String why, boolean definitive) {
        ControlGate.apply(this, P2pCapability.providerOn(verdict));
        if (verdict == P2pVerdict.NO && definitive) {
            noWifiDirect = true;
            Log.i(TAG, "no Wi-Fi Direct on this unit, the receiver stops: " + why);
            main.post(this::stopSelf);
        }
    }

    // ------------------------------------------------------------------ SinkSession.Host (any thread)

    @Override
    public void onSessionPlaying(SinkSession s) {
        main.post(() -> {
            if (s != session) return;
            takeFocus();
            wakeFromScreensaver("picture");
            Intent i = new Intent(this, MirrorActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            try {
                startActivity(i);
                DIAG.waiting(Diag.Stage.SCREEN, "mirror screen started");
            } catch (RuntimeException e) {
                DIAG.fail(Diag.Stage.SCREEN, "cannot start the mirror screen: " + e);
                Log.e(TAG, "cannot show the picture", e);
            }
            updateNotification();
            notifyUi();
        });
    }

    @Override
    public void onSessionFirstFrame(SinkSession s) {
        main.post(() -> {
            if (s != session) return;
            firstFrame = true;
            notifyUi();
        });
    }

    @Override
    public void onSessionVideoSize(SinkSession s, int w, int h) {
        main.post(() -> {
            if (s != session) return;
            videoW = w;
            videoH = h;
            notifyUi();
        });
    }

    @Override
    public void onSessionEnded(SinkSession s, String reason) {
        saveReport(this);
        main.post(() -> {
            if (s != session) return;
            Log.i(TAG, "session ended: " + reason);
            session = null;
            releaseFocus();
            if (p2p != null) p2p.disconnect();
            if (!reason.startsWith("cancelled") && !reason.equals("stopped") && prompt != null) {
                prompt.toast(getString(R.string.ended));
            }
            updateNotification();
            notifyUi();
        });
    }

    // ------------------------------------------------------------------ helpers

    private void endSession(String why, boolean polite) {
        SinkSession s = session;
        if (s == null) return;
        Log.i(TAG, "ending session: " + why);
        session = null;
        s.stop(polite, why);
        releaseFocus();
        updateNotification();
        notifyUi();
    }

    /**
     * Ends the screensaver: a dream task stays above every other task (TaskDisplayArea.getPriority), so
     * the mirror screen would start behind it, stop at once and end the session. WRITE_DREAM_STATE.
     */
    private void wakeFromScreensaver(String why) {
        try {
            DreamManager dm = getSystemService(DreamManager.class);
            if (dm != null && dm.isDreaming()) {
                dm.stopDream();
                Log.i(TAG, "screensaver stopped for the " + why);
                DIAG.ok(Diag.Stage.SCREEN, "screensaver stopped for the " + why);
            }
        } catch (Throwable t) {
            Log.w(TAG, "stopDream: " + t);
            DIAG.fail(Diag.Stage.SCREEN, "cannot stop the screensaver: " + t);
        }
    }

    private boolean dreaming() {
        try {
            DreamManager dm = getSystemService(DreamManager.class);
            return dm != null && dm.isDreaming();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Also called again when a paused source resumes (PLAY): keeps the one request. */
    private void takeFocus() {
        if (focus == null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    .setOnAudioFocusChangeListener(ch -> { })
                    .build();
            getSystemService(AudioManager.class).requestAudioFocus(focus);
        }
        if (wake == null) {
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LumenMiracast:session");
            wake.setReferenceCounted(false);
        }
        wake.acquire(4 * 60 * 60 * 1000L);
    }

    private void releaseFocus() {
        if (focus != null) {
            getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);
            focus = null;
        }
        if (wake != null && wake.isHeld()) wake.release();
    }

    /** The finished attempt's report survives a restart of the receiver (device-protected storage). */
    static void saveReport(Context c) {
        try {
            File f = new File(c.createDeviceProtectedStorageContext().getFilesDir(), DIAG_FILE);
            Files.write(f.toPath(), DIAG.report().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "diag save: " + e);
        }
    }

    static String lastReport(Context c) {
        try {
            File f = new File(c.createDeviceProtectedStorageContext().getFilesDir(), DIAG_FILE);
            return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "(none yet)";
        } catch (IOException | RuntimeException e) {
            return "(unreadable: " + e + ")";
        }
    }

    private static WifiP2pDevice peer(WifiP2pInfo info, WifiP2pGroup group) {
        if (group == null) return null;
        if (info != null && !info.isGroupOwner) return group.getOwner();
        for (WifiP2pDevice d : group.getClientList()) return d;
        return null;
    }

    private String label(WifiP2pDevice d) {
        if (d != null && d.deviceName != null && !d.deviceName.trim().isEmpty()) return d.deviceName.trim();
        return getString(R.string.unknown_device);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1";
        }
    }

    private void notifyUi() {
        for (UiListener l : listeners) l.onMiracastState();
    }

    private Notification notification() {
        String text;
        if (session != null) {
            text = getString(R.string.notif_in_use, peerName);
        } else if (p2p != null && p2p.state() == P2pState.WIFI_OFF) {
            text = getString(R.string.status_no_wifi);
        } else if (p2p != null && p2p.state() == P2pState.UNSUPPORTED) {
            text = getString(R.string.status_unsupported);
        } else {
            text = getString(R.string.notif_ready, Prefs.name(this));
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_mirroring)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, notification());
    }
}
