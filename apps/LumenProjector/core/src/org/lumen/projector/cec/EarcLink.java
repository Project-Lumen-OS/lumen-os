// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
/*
 * EarcLink: eARC the stock way on the Z9X and the Z9X HB (2026-11-04, devices/goat_elegant/stock analysis). Both
 * vendors run the same libxgimi flow and both stock frameworks carry the same patch. Calls: setEARCEnable = HB TV 65 /
 * Z9X IGmpf 633, getEARCEnableState = TV 66 / 632, getEARCStatus = TV 79 / 619 (GmpfClient setEarc / getEarc /
 * getEarcStatus in each fork).
 *
 * Stock end to end (G0130 release 838; the Z9X stock services.jar has the same seteARCStatus / geteArcStatus):
 * <ul>
 *   <li>The vendor does the whole link. setEARCEnable(true) sets a runtime flag of libxgimi Msrv_Tv_Control; its CEC
 *       thread then sends &lt;Request ARC Termination&gt; to the soundbar, re-pulls the HPD of the ARC port, sets the MI
 *       eARC mode 3 and reads the link after 600 ms. A link change resets the audio output caps and the EDID from the
 *       soundbar's eARC capability data and raises the GmTv event 11 "eARCstate:1|0". getEARCStatus reads the link
 *       (3 = eARC).</li>
 *   <li>XRMService (SignalChangeReceiver) turns event 11, and getEARCStatus == 3 at its start, into
 *       AudioManager.seteARCStatus(true|false); the stock AudioService then connects (or disconnects) the plain HDMI ARC
 *       output, and the stock HdmiCecLocalDeviceTv skips the ARC disconnect "while eARC mode" when the soundbar ends
 *       CEC ARC. The HB audio policy has no HDMI eARC device; the Z9X policy has one (vendor
 *       audio_policy_configuration.xml:276), which stock does not use: eARC audio leaves through the ARC output and
 *       the vendor's ARC path.</li>
 * </ul>
 * Lumen has the same vendor; this module plays the XRMService part and, until the base carries the framework part
 * (frameworks/base com.android.server.hdmi.VendorEarc, which sets sys.lumen.vendor_earc_glue), the AudioService part:
 * <ul>
 *   <li>No setEARCEnable of its own (only the panel row sends it, as stock): a toggle forces &lt;Request ARC
 *       Termination&gt; and an HPD re-pull, so a call at boot could break a working link (HB 2026-10-31, 11-03).</li>
 *   <li>The link comes from the vendor's TV event 11 ({@link #onTvEvent}, the event callback every family registers
 *       already) and ONE getEARCStatus read at the start and after every HAL (re)connect, as stock XRMService. No
 *       polling: the earlier 1 s x 15 s watch after every screen on, hotplug and ARC output change plus the 10 s
 *       follow put up to ~25 vendor calls into the wake window where the CEC HAL and the framework re-initiate ARC
 *       (cec-audit-1127 R5).</li>
 *   <li>Every link change is written to Settings.Global lumen_vendor_earc (1 / 0), what stock passes to
 *       AudioService.seteARCStatus. With the framework part present the framework keeps the HDMI ARC output from it
 *       and the app touches no output. Without it (images of the current base): while the link is up and no HDMI ARC
 *       output is listed, one is connected (AudioManager.setWiredDeviceConnectionState, no descriptors, as stock),
 *       also right after the framework removed CEC ARC's output (the device callback, no read); when the link ends,
 *       the output this module connected is removed unless System Audio is on (stock removes it only while CEC ARC is
 *       not established). Never while the projector sleeps (EarcRules.step); SCREEN_ON applies the last event.</li>
 *   <li>No ARC restart from the app (IHdmiControlService.setArcMode, which stock never sends): it only repaired the
 *       app's own removal of an output that CEC ARC had taken over, which the System Audio rule above and the
 *       framework part avoid.</li>
 * </ul>
 * AudioOutputChoice labels the soundbar "HDMI eARC" while linked(). Decisions in EarcRules (host test test/earc). HAL
 * calls on the family's HAL thread (Family.earcStatus), the rest on Family.EARC_THREAD. Log tag Family.EARC_TAG.
 * One class for every family: the X50 line (MT9681) has the same vendor flow (XRMService hdmi/SignalChangeReceiver,
 * TV 79). "ours" is kept in prefs with the boot id, so a stale output of a crashed process is still removed.
 */
package org.lumen.projector.cec;

import android.content.Context;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.lumen.projector.Family;

import java.util.concurrent.Callable;

public final class EarcLink {
    private static final String TAG = Family.EARC_TAG;
    private static final String PREFS = "earc_state", K_OURS_BOOT = "ours_boot";
    /** Settings.Global the framework part follows (frameworks/base com.android.server.hdmi.VendorEarc.SETTING). */
    static final String SETTING = "lumen_vendor_earc";
    /** Set by the framework part while it runs (VendorEarc.GLUE_PROPERTY): the output is the framework's. */
    static final String GLUE_PROPERTY = "sys.lumen.vendor_earc_glue";
    private static Handler sH;
    private static Context sApp;
    /** EarcLink connected the HDMI ARC output and has not removed it ("z9x-earc"). */
    private static boolean sOurs;
    private static volatile int sLastStatus = -1;

    private EarcLink() {}

    /** App.startModules (main thread, user unlocked); idempotent. */
    public static synchronized void install(Context ctx) {
        if (sApp != null) return;
        sApp = ctx.getApplicationContext();
        Handler h = handler();
        if (h == null) return;
        try {
            AudioManager am = sApp.getSystemService(AudioManager.class);
            if (am != null) {
                am.registerAudioDeviceCallback(new AudioDeviceCallback() {
                    @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d) {
                        if (hasArc(d)) apply("ARC output removed");
                    }
                }, h);
            }
        } catch (Throwable t) {
            Log.w(TAG, "device callback: " + t);
        }
        Family.whenHalConnected(() -> read("HAL connected"));
        try {
            String boot = Family.bootId();
            String stored = sApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_OURS_BOOT, null);
            if (boot != null && boot.equals(stored)) {
                h.post(() -> sOurs = true);
                Log.i(TAG, "our HDMI ARC output of this boot is still connected (process restart)");
            }
        } catch (Throwable t) {
            Log.w(TAG, "restore ours: " + t);
        }
        try {
            android.content.IntentFilter f = new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON);
            sApp.registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context c, android.content.Intent i) { apply("screen on"); }
            }, f, null, h, Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            Log.w(TAG, "screen receiver: " + t);
        }
        if (Family.halReady()) read("start");
    }

    /** The vendor's TV events (the family's event callback, a vendor binder thread: only posts). */
    public static void onTvEvent(int type, String msg) {
        int s = EarcRules.eventStatus(type, msg);
        Handler h = sH;
        if (s < 0 || h == null || sApp == null) return;
        h.post(() -> onStatus(s, "event " + msg));
    }

    /** The last link status (event or read) was 3. Any thread. */
    public static boolean linked() {
        return sLastStatus == EarcRules.EARC_LINKED;
    }

    /** The panel's eARC row (stock "eARC Mode" Auto / Off): send is the family's setEARCEnable. HAL thread. */
    public static boolean setUser(Callable<Boolean> send, boolean on) throws Exception {
        boolean ok = send.call();
        Log.i(TAG, "user: setEARCEnable(" + on + ") -> " + ok + " (the vendor's event reports the link)");
        return ok;
    }

    /** A menu that sent setEARCEnable itself (the X50 eARC row, TV 65): the vendor's event reports the link. */
    public static void userChanged(boolean on) {
        Log.i(TAG, "user: eARC " + (on ? "Auto" : "Off") + " (the vendor's event reports the link)");
    }

    /** One getEARCStatus read (stock XRMService at its start). Any thread. */
    private static void read(String why) {
        Handler h = handler();
        if (h == null) return;
        Family.earcStatus(s -> h.post(() -> onStatus(s, "read, " + why)));
    }

    /** "z9x-earc": a status from the vendor (event or read). */
    private static void onStatus(int status, String why) {
        if (status < 0) return;                                 // a failed read says nothing
        if (status != sLastStatus) {
            Log.i(TAG, "eARC status " + sLastStatus + " -> " + status + " (" + why + ")");
            if (EarcRules.formatAgain(sLastStatus, status)) {
                Family.earcFormatAgain("eARC status " + sLastStatus + " -> " + status);
            }
            report(status == EarcRules.EARC_LINKED);
        }
        sLastStatus = status;
        apply(why);
    }

    /** Settings.Global lumen_vendor_earc: what stock passes to AudioService.seteARCStatus. "z9x-earc". */
    private static void report(boolean on) {
        try {
            android.provider.Settings.Global.putInt(sApp.getContentResolver(), SETTING, on ? 1 : 0);
        } catch (Throwable t) {
            Log.w(TAG, "report " + on + ": " + t);
        }
    }

    /** "z9x-earc": the HDMI ARC output for the last status, only without the framework part. */
    private static void apply(String why) {
        int status = sLastStatus;
        if (status < 0 || frameworkOwns()) return;
        boolean present = arcPresent();
        EarcStep step = EarcRules.step(status, sOurs, present, interactive());
        if (step == EarcStep.DISCONNECT) step = EarcRules.afterLinkEnd(step, systemAudioOn());
        switch (step) {
            case CONNECT:
                if (connect(true)) setOurs(true);
                break;
            case DISCONNECT:
                connect(false);
                setOurs(false);
                break;
            default:
                if (status != EarcRules.EARC_LINKED && sOurs) {
                    Log.i(TAG, "eARC ended, the HDMI ARC output stays: System Audio is on (" + why + ")");
                    setOurs(false);                             // CEC ARC owns it now
                }
        }
    }

    /** The base carries the framework part (VendorEarc): it keeps the HDMI ARC output, the app adds nothing. */
    private static boolean frameworkOwns() {
        try {
            return "1".equals(android.os.SystemProperties.get(GLUE_PROPERTY));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean systemAudioOn() {
        try {
            android.hardware.hdmi.HdmiControlManager m = CecSettings.hm(sApp);
            return m != null && m.getSystemAudioMode();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Our output state, with the boot id ("z9x-earc"). */
    private static void setOurs(boolean on) {
        sOurs = on;
        try {
            android.content.SharedPreferences.Editor e = sApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            String boot = Family.bootId();
            if (on && boot != null) e.putString(K_OURS_BOOT, boot); else e.remove(K_OURS_BOOT);
            e.apply();
        } catch (Throwable t) {
            Log.w(TAG, "store ours: " + t);
        }
    }

    /** Connects or removes the HDMI ARC output (no name, no descriptors: the stock AudioService call). */
    private static boolean connect(boolean on) {
        try {
            AudioManager am = sApp.getSystemService(AudioManager.class);
            AudioDeviceAttributes arc = new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_OUTPUT,
                    AudioDeviceInfo.TYPE_HDMI_ARC, "");
            am.setWiredDeviceConnectionState(arc, on ? 1 : 0);
            Log.i(TAG, "HDMI ARC output " + (on ? "connected (eARC linked)" : "removed (eARC ended)"));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "HDMI ARC output " + on + ": " + t);
            return false;
        }
    }

    private static boolean arcPresent() {
        try {
            AudioManager am = sApp.getSystemService(AudioManager.class);
            return hasArc(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasArc(AudioDeviceInfo[] d) {
        if (d == null) return false;
        for (AudioDeviceInfo i : d) {
            if (i != null && i.getType() == AudioDeviceInfo.TYPE_HDMI_ARC) return true;
        }
        return false;
    }

    private static synchronized Handler handler() {
        if (sH == null) {
            try {
                HandlerThread t = new HandlerThread(Family.EARC_THREAD);
                t.start();
                sH = new Handler(t.getLooper());
            } catch (Throwable e) {
                Log.w(TAG, "thread: " + e);
            }
        }
        return sH;
    }

    /** The projector is awake (PowerManager interactive); true when unknown. */
    private static boolean interactive() {
        try {
            android.os.PowerManager pm = sApp.getSystemService(android.os.PowerManager.class);
            return pm == null || pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }
}
