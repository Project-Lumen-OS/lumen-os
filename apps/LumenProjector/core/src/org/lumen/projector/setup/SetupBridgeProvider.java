// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.setup;

import org.lumen.projector.Family;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import org.lumen.projector.Hal;
import org.lumen.projector.Ui;
import org.lumen.projector.ak.AkOverlay;
import org.lumen.projector.home.LauncherSwitcher;
import org.lumen.projector.panel.QuickPanel;
import org.lumen.remote.RemotePairing;
import org.lumen.remote.RemoteState;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Setup bridge of the gmpf families (Z9X, RS 20, Z9X HB): their hardware methods of the protocol in SetupBridge,
 * on the HAL thread through Hal and its gates (HalController's kstInFlight / AF busy / AkOverlay / motor stop,
 * RemotePairing, the GmpfClient whitelist). Log tag Z9xBridge.
 */
public final class SetupBridgeProvider extends SetupBridge {
    private static final String TAG = Family.TAG_PREFIX + "Bridge";

    private static final long UI_WAIT_MS = 400;
    private static final long TOGGLE_WAIT_MS = 1_200;
    private static final long LAUNCHER_WAIT_MS = 5_500;
    private static final long CURTAIN_CACHE_MS = 1_000;
    private static final long CURTAIN_FIRST_WAIT_MS = 150;


    /** hal_state's curtainFit, read through the hal thread, cached at most CURTAIN_CACHE_MS. */
    private static volatile Boolean sCurtainFit;
    private static volatile long sCurtainAt;
    private static volatile boolean sCurtainReading;
    private static volatile boolean sRemoteStarted;

    public SetupBridgeProvider() {
        super(TAG);
    }

    /** Play6App, once the remote module started: remote_state reports it (a family whose remote starts late). */
    public static void remoteStarted() {
        sRemoteStarted = true;
    }

    @Override
    protected void pingExtras(Bundle b) {
        String[] names = Family.setupNames();
        if (names != null) b.putStringArray("names", names);
    }

    @Override
    protected Bundle hardware(Context app, String method, String arg, Bundle x) {
        switch (method) {
            case "remote_state": {
                Bundle b = remoteBundle(app);
                b.putBoolean("ok", true);
                return b;
            }
            case "hal_state": {
                Bundle b = halBundle(app, true);
                b.putBoolean("ok", true);
                return b;
            }
            case "af_run":
                return request(onMain(() -> Hal.requestAutofocus(app)));
            case "kst_auto":
                return request(onMain(() -> Hal.requestKeystone(app, false)));
            case "kst_fit": {
                if (!Family.setupHas("kst_fit")) return err("unsupported");   // the family's stock lacks it
                if (Boolean.FALSE.equals(curtainFit(app, true))) return err("unsupported");
                return request(onMain(() -> Hal.requestKeystone(app, true)));
            }
            case "kst_manual": {
                if (!Family.setupHas("kst_manual")) return err("unsupported");
                if (!Hal.isReady()) return err("not_ready");
                if (Hal.isKeystoneRunning() || AkOverlay.isActive()) return err("busy");
                Ui.main().post(() -> QuickPanel.show(app, QuickPanel.SECTION_MANUAL_KEYSTONE));
                return ok();
            }
            case "focus_manual": {
                if (!Hal.isReady()) return err("not_ready");
                if (Hal.isKeystoneRunning() || AkOverlay.isActive()) return err("busy");
                Ui.main().post(() -> Hal.openManualFocus(app));
                return ok();
            }
            case "toggles_get":
                return toggles(app, null, false);
            case "toggle_set": {
                String name = x.getString("name");
                if (name == null || !x.containsKey("on")) return err("denied");
                if (!Family.setupHas(name)) return err("unsupported");
                return toggles(app, name, x.getBoolean("on"));
            }
            case "set_launcher": {
                String target = x.getString("target");
                if (!LauncherSwitcher.MODE_LUMEN.equals(target) && !LauncherSwitcher.MODE_CLASSIC.equals(target)) {
                    return err("denied");
                }
                return LauncherSwitcher.applySync(app, target, "setup bridge", LAUNCHER_WAIT_MS);
            }
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ state bundles (also SetupEvents)

    /**
     * remote_state fields: bonded, connected, name, scanning, mode; Lumen OS 1.0 adds pairing and found.
     * connected = a bonded XGIMI remote whose link AND HID input device are up (its keys work). Any thread.
     */
    static Bundle remoteBundle(Context app) {
        if (Family.REMOTE_STARTS_LATE && !sRemoteStarted) {
            Bundle b = new Bundle();
            b.putInt("bonded", 0);
            b.putBoolean("connected", false);
            b.putString("name", "");
            b.putBoolean("scanning", false);
            b.putString("mode", "starting");
            b.putBoolean("pairing", false);
            b.putString("found", "");
            return b;
        }
        return remoteBundle(RemotePairing.state(app));
    }

    static Bundle remoteBundle(RemoteState st) {
        Bundle b = new Bundle();
        b.putInt("bonded", st.bonded);
        b.putBoolean("connected", st.connected);
        b.putString("name", st.name);
        b.putBoolean("scanning", st.scanning);
        b.putString("mode", st.mode);
        b.putBoolean("pairing", st.pairing);
        b.putString("found", st.found);
        return b;
    }

    /** hal_state fields: ready, kstRunning, afBusy, akOverlay, curtainFit (cached, may be absent). */
    static Bundle halBundle(Context app, boolean waitFirst) {
        Bundle b = new Bundle();
        b.putBoolean("ready", Hal.isReady() && Hal.isHandshakeDone());
        b.putBoolean("kstRunning", Hal.isKeystoneRunning());
        b.putBoolean("afBusy", Hal.isAfBusy());
        b.putBoolean("akOverlay", AkOverlay.isActive());
        if (!Family.setupHas("curtain_fit_state")) {              // Setup keeps "Fit to screen" hidden
            b.putBoolean("curtainFit", false);
            return b;
        }
        Boolean cf = curtainFit(app, waitFirst);
        if (cf != null) b.putBoolean("curtainFit", cf);
        return b;
    }

    /** Cached 290 read (through readToggles on the hal thread), refreshed when older than 1 s. */
    private static Boolean curtainFit(Context app, boolean waitFirst) {
        long now = SystemClock.uptimeMillis();
        if (now - sCurtainAt > CURTAIN_CACHE_MS && !sCurtainReading && Hal.isReady()) {
            sCurtainReading = true;
            final CountDownLatch l = new CountDownLatch(1);
            Ui.main().post(() -> {
                try {
                    Hal.readToggles(app, m -> {
                        Boolean v = m.get("CURTAIN_FIT");
                        if (v != null) {
                            sCurtainFit = v;
                            sCurtainAt = SystemClock.uptimeMillis();
                        }
                        sCurtainReading = false;
                        l.countDown();
                    });
                } catch (Throwable t) {
                    sCurtainReading = false;
                    l.countDown();
                }
            });
            if (waitFirst && sCurtainFit == null) {
                try { l.await(CURTAIN_FIRST_WAIT_MS, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { }
            }
        }
        return sCurtainFit;
    }

    // ------------------------------------------------------------------ actions

    /** Runs a REQ_* request on the main thread (as from a key) and waits briefly. */
    private interface Req { int run(); }

    private static int onMain(Req r) {
        final AtomicReference<Integer> out = new AtomicReference<>();
        final CountDownLatch l = new CountDownLatch(1);
        boolean posted = Ui.main().post(() -> {
            try {
                out.set(r.run());
            } catch (Throwable t) {
                Log.w(TAG, "request: " + t);
                out.set(Hal.REQ_NOT_READY);
            } finally {
                l.countDown();
            }
        });
        if (!posted) return Hal.REQ_NOT_READY;
        try {
            if (!l.await(UI_WAIT_MS, TimeUnit.MILLISECONDS)) return Hal.REQ_BUSY;
        } catch (InterruptedException e) {
            return Hal.REQ_BUSY;
        }
        Integer v = out.get();
        return v == null ? Hal.REQ_NOT_READY : v;
    }

    private static Bundle request(int r) {
        switch (r) {
            case Hal.REQ_OK: return ok();
            case Hal.REQ_BUSY: return err("busy");
            case Hal.REQ_UNSUPPORTED: return err("feature_off");
            default: return err("not_ready");
        }
    }

    /** toggles_get (name null) or toggle_set; replies with every toggle read back. */
    private static Bundle toggles(Context app, String setName, boolean on) {
        if (!Hal.isReady()) return err("not_ready");
        final AtomicReference<Map<String, Boolean>> out = new AtomicReference<>();
        final CountDownLatch l = new CountDownLatch(1);
        final boolean[] known = {true};
        Ui.main().post(() -> {
            try {
                if (setName == null) {
                    Hal.readToggles(app, m -> { out.set(m); l.countDown(); });
                } else if (!Hal.setToggle(app, setName, on, m -> { out.set(m); l.countDown(); })) {
                    known[0] = false;
                    l.countDown();
                }
            } catch (Throwable t) {
                Log.w(TAG, "toggles: " + t);
                l.countDown();
            }
        });
        try {
            if (!l.await(TOGGLE_WAIT_MS, TimeUnit.MILLISECONDS)) return err("busy");
        } catch (InterruptedException e) {
            return err("busy");
        }
        if (!known[0]) return err("denied");
        Map<String, Boolean> m = out.get();
        if (m == null) return err("not_ready");
        Bundle b = ok();
        for (Map.Entry<String, Boolean> e : m.entrySet()) {
            if (e.getValue() != null) b.putBoolean(e.getKey(), e.getValue());
        }
        Boolean cf = m.get("CURTAIN_FIT");
        if (cf != null) {
            sCurtainFit = cf;
            sCurtainAt = SystemClock.uptimeMillis();
        }
        if (setName != null && m.get(setName) == null) return err("not_ready");
        return b;
    }
}
