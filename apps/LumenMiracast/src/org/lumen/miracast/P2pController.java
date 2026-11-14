// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.lumen.miracast.core.Diag;
import org.lumen.miracast.core.P2pCapability;
import org.lumen.miracast.core.P2pRules;
import org.lumen.miracast.core.P2pVerdict;

import java.util.Locale;

/**
 * Wi-Fi Direct side of the receiver, on the main thread.
 *
 * - Announces the projector as a WFD primary sink (setWfdInfo: CONFIGURE_WIFI_DISPLAY) under the
 *   chosen name (setDeviceName: NETWORK_SETTINGS), and stays discoverable with extended listen
 *   (startListening; falls back to periodic discoverPeers like the stock XGIMI service).
 * - Takes over the framework's "accept invitation?" dialog with an external approver bound to the
 *   broadcast address (Android 13+ treats it as the approver for every peer), so the receiver shows
 *   its own prompt or accepts by itself (MANAGE_WIFI_NETWORK_SELECTION). The framework drops that
 *   approver after every answer ({@link P2pRules#reattach}), so it is attached again each time.
 * - Reports group formation / loss to the service, which then runs the RTSP session.
 * - Finds out whether this unit can do Wi-Fi Direct at all ({@link P2pCapability}: what the framework answered, never
 *   the model or the chip) and reports the verdict, so the service shows or hides the quick menu tile.
 * Every step goes to the {@link Diag} stages (logcat tag LumenMiracastDiag).
 */
final class P2pController {
    static final String TAG = "LumenMiracast";

    interface Callback {
        /** A peer asks to connect: answer with {@link #answer}. type = WifiP2pManager.ExternalApproverRequestListener.REQUEST_TYPE_*. */
        void onConnectionRequest(WifiP2pDevice device, WifiP2pConfig config, int type);

        /** The framework made a PIN the peer must type (we show it). */
        void onPin(String peerAddress, String pin);

        void onGroupFormed(WifiP2pInfo info, WifiP2pGroup group);

        void onGroupGone();

        void onStateChanged();

        /** Wi-Fi Direct proven (YES) or missing (NO); definitive = NO that nothing can change on this boot. */
        void onCapability(P2pVerdict verdict, String why, boolean definitive);
    }

    private static final long REASSERT_MS = 60_000;
    private static final long DISCOVER_RETRY_MS = 8_000;
    private static final long REATTACH_MS = 500;
    /** With discovery as the fallback, listen mode is tried again this often (a refusal at boot can be transient). */
    private static final int LISTEN_RETRY_EVERY = 10;

    private final Context ctx;
    private final Callback cb;
    private final Diag diag;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WifiP2pManager mgr;
    private WifiP2pManager.Channel channel;
    private boolean receiverOn, p2pEnabled, groupFormed, running, useDiscovery, approverAttached, removingApprover;
    private int reasserts;
    private String name;
    private P2pState state = P2pState.STOPPED;
    private String lastError;
    private final P2pCapability cap = new P2pCapability();
    private P2pVerdict reported = P2pVerdict.UNKNOWN;
    private boolean wifiOn;

    P2pController(Context ctx, Callback cb, Diag diag) {
        this.ctx = ctx;
        this.cb = cb;
        this.diag = diag;
    }

    P2pState state() {
        return state;
    }

    String lastError() {
        return lastError;
    }

    /** One line for dumpsys and the status page. */
    String summary() {
        return "state=" + state + " p2p=" + p2pEnabled + " group=" + groupFormed + " approver=" + approverAttached
                + " visible=" + (useDiscovery ? "discovery (listen refused)" : "listen") + " lastError=" + lastError
                + " wifi=" + wifiOn + " wifiDirect=" + cap.verdict() + " (" + cap.why() + ")";
    }

    void start(String deviceName) {
        name = deviceName;
        running = true;
        mgr = ctx.getSystemService(WifiP2pManager.class);
        if (mgr == null || !ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            mgr = null;
            setState(P2pState.UNSUPPORTED);
            diag.fail(Diag.Stage.P2P, "no WifiP2pManager: android.hardware.wifi.direct missing");
            Log.w(TAG, "no WifiP2pManager: android.hardware.wifi.direct missing");
            decide(cap.noFramework("android.hardware.wifi.direct missing"));
            return;
        }
        openChannel();
        if (channel == null) return;
        WifiManager wm = ctx.getSystemService(WifiManager.class);
        onWifi(wm != null && wm.isWifiEnabled());
        if (!receiverOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            f.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            f.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            f.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
            f.addAction(WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED);
            ctx.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
            receiverOn = true;
        }
        setState(P2pState.STARTING);
        main.postDelayed(reassert, REASSERT_MS);
    }

    void stop() {
        running = false;
        main.removeCallbacksAndMessages(null);
        cap.wifi(false, now());   // a stopped receiver makes no Wi-Fi Direct call: its time proves nothing
        if (mgr != null && channel != null) {
            try {
                mgr.stopListening(channel, null);
                mgr.stopPeerDiscovery(channel, null);
                if (approverAttached) {
                    removingApprover = true;
                    mgr.removeExternalApprover(channel, MacAddress.BROADCAST_ADDRESS, null);
                }
                WifiP2pWfdInfo off = new WifiP2pWfdInfo();
                off.setEnabled(false);
                mgr.setWfdInfo(channel, off, null);
                if (groupFormed) mgr.removeGroup(channel, null);
            } catch (Throwable t) {
                Log.w(TAG, "p2p stop: " + t);
            }
            channel.close();
        }
        channel = null;
        approverAttached = false;
        if (receiverOn) {
            try {
                ctx.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
                // not registered
            }
            receiverOn = false;
        }
        setState(P2pState.STOPPED);
    }

    void rename(String deviceName) {
        name = deviceName;
        if (running && p2pEnabled) setName();
    }

    /** Answer for a pending request by the peer's device address. accept=false declines; how: for the diagnostics. */
    void answer(String deviceAddress, boolean accept, String how) {
        String what = (accept ? "accepted" : "declined") + " (" + how + ")";
        if (mgr == null || channel == null || deviceAddress == null) {
            diag.fail(Diag.Stage.ANSWER, what + " but there is no channel to answer " + deviceAddress);
            return;
        }
        try {
            mgr.setConnectionRequestResult(channel, MacAddress.fromString(deviceAddress),
                    accept ? WifiP2pManager.CONNECTION_REQUEST_ACCEPT : WifiP2pManager.CONNECTION_REQUEST_REJECT,
                    stageListener("answer " + what, Diag.Stage.ANSWER, what));
        } catch (Throwable t) {
            diag.fail(Diag.Stage.ANSWER, what + ": " + t);
            Log.w(TAG, "answer: " + t);
        }
    }

    /** Let the framework show its own dialog for this request (PIN entry or PIN display on our side). */
    void deferToSystem(String deviceAddress, String why) {
        if (mgr == null || channel == null || deviceAddress == null) return;
        try {
            mgr.setConnectionRequestResult(channel, MacAddress.fromString(deviceAddress),
                    WifiP2pManager.CONNECTION_REQUEST_DEFER_TO_SERVICE,
                    stageListener("defer", Diag.Stage.ANSWER, "given to the system PIN dialog (" + why + ")"));
        } catch (Throwable t) {
            diag.fail(Diag.Stage.ANSWER, "defer: " + t);
            Log.w(TAG, "defer: " + t);
        }
    }

    /** Ends the Wi-Fi Direct group (after the session or when the user stops). */
    void disconnect() {
        if (mgr != null && channel != null) mgr.removeGroup(channel, listener("removeGroup"));
    }

    // ------------------------------------------------------------------ setup

    private void openChannel() {
        channel = mgr.initialize(ctx, Looper.getMainLooper(), () -> {
            Log.w(TAG, "p2p channel lost; reopening");
            diag.waiting(Diag.Stage.P2P, "channel to the Wi-Fi Direct service lost, reopening");
            channel = null;
            approverAttached = false;
            if (running) main.postDelayed(() -> {
                if (running && channel == null) {
                    openChannel();
                    if (p2pEnabled) configure();
                }
            }, 2000);
        });
        if (channel == null) {
            lastError = "initialize failed";
            diag.fail(Diag.Stage.P2P, "WifiP2pManager.initialize returned no channel");
            setState(P2pState.UNSUPPORTED);
            decide(cap.noFramework("WifiP2pManager.initialize returned no channel"));
        }
    }

    // ------------------------------------------------------------------ capability

    private static long now() {
        return SystemClock.elapsedRealtime();
    }

    private void onWifi(boolean on) {
        wifiOn = on;
        decide(cap.wifi(on, now()));
        main.removeCallbacks(capCheck);
        long in = cap.nextCheckIn(now());
        if (in >= 0) main.postDelayed(capCheck, in + 100);
    }

    private final Runnable capCheck = () -> decide(cap.check(now()));

    /** A new verdict goes to the diagnostics, the status and the service (once per change). */
    private void decide(P2pVerdict v) {
        if (v == reported) return;
        reported = v;
        if (v == P2pVerdict.NO) {
            Log.w(TAG, "no Wi-Fi Direct on this unit: " + cap.why());
            diag.fail(Diag.Stage.P2P, "no Wi-Fi Direct on this unit: " + cap.why());
            setState(P2pState.UNSUPPORTED);
        } else if (v == P2pVerdict.YES) {
            Log.i(TAG, "Wi-Fi Direct works: " + cap.why());
            if (state == P2pState.UNSUPPORTED) setState(P2pState.STARTING);
        }
        cb.onCapability(v, cap.why(), cap.definitive());
    }

    private void configure() {
        if (mgr == null || channel == null) return;
        setName();
        WifiP2pWfdInfo wfd = new WifiP2pWfdInfo();
        wfd.setEnabled(true);
        wfd.setDeviceType(WifiP2pWfdInfo.DEVICE_TYPE_PRIMARY_SINK);
        wfd.setSessionAvailable(true);
        wfd.setControlPort(Prefs.CONTROL_PORT);
        wfd.setMaxThroughput(50);       // Mbit/s, as the stock XGIMI sink
        wfd.setContentProtectionSupported(false);
        String ie = String.format(Locale.ROOT, "subelement 0 = %04x%04x%04x (primary sink, available, port %d)",
                wfd.getDeviceInfo(), wfd.getControlPort(), wfd.getMaxThroughput(), wfd.getControlPort());
        mgr.setWfdInfo(channel, wfd, stageListener("setWfdInfo", Diag.Stage.WFD_IE, ie));
        attachApprover();
        becomeVisible();
    }

    private void attachApprover() {
        if (approverAttached || mgr == null || channel == null || !running) return;
        removingApprover = false;
        try {
            mgr.addExternalApprover(channel, MacAddress.BROADCAST_ADDRESS, approver);
        } catch (Throwable t) {
            diag.fail(Diag.Stage.APPROVER, "addExternalApprover: " + t);
            Log.w(TAG, "addExternalApprover: " + t);
        }
    }

    private void setName() {
        mgr.setDeviceName(channel, name, stageListener("setDeviceName " + name, Diag.Stage.NAME, name));
    }

    private void becomeVisible() {
        if (mgr == null || channel == null || groupFormed || !running) return;
        if (useDiscovery && reasserts % LISTEN_RETRY_EVERY != 0) {
            mgr.discoverPeers(channel, stageListener("discoverPeers", Diag.Stage.VISIBLE, "discovery (listen refused earlier)"));
            return;
        }
        mgr.startListening(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "listening (discoverable)");
                decide(cap.proven("startListening ok"));
                useDiscovery = false;
                diag.ok(Diag.Stage.VISIBLE, "listen");
                setState(P2pState.VISIBLE);
            }

            @Override
            public void onFailure(int reason) {
                Log.w(TAG, "startListening failed " + reason + "; using discoverPeers");
                decide(cap.callFailed(reason, "startListening"));
                diag.waiting(Diag.Stage.VISIBLE, "startListening refused (" + reasonName(reason) + "), trying discovery");
                useDiscovery = true;
                if (mgr != null && channel != null) {
                    mgr.discoverPeers(channel, stageListener("discoverPeers", Diag.Stage.VISIBLE, "discovery (listen refused)"));
                }
            }
        });
    }

    /** Periodically re-announce: some framework paths drop listen mode silently (scan, group removal). */
    private final Runnable reassert = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            reasserts++;
            if (p2pEnabled && !groupFormed) {
                attachApprover();
                becomeVisible();
            }
            main.postDelayed(this, REASSERT_MS);
        }
    };

    private final WifiP2pManager.ExternalApproverRequestListener approver = new WifiP2pManager.ExternalApproverRequestListener() {
        @Override
        public void onAttached(MacAddress addr) {
            approverAttached = true;
            Log.i(TAG, "approver attached " + addr);
            diag.ok(Diag.Stage.APPROVER, "attached for " + addr);
        }

        @Override
        public void onDetached(MacAddress addr, int reason) {
            Log.i(TAG, "approver detached " + addr + " reason " + P2pRules.detachName(reason));
            if (!MacAddress.BROADCAST_ADDRESS.equals(addr)) return;
            approverAttached = false;
            if (P2pRules.reattach(reason, running, removingApprover)) {
                // the framework's normal step after every answer: the next request must reach us again
                diag.waiting(Diag.Stage.APPROVER, "detached (" + P2pRules.detachName(reason) + "), attaching again");
                main.postDelayed(P2pController.this::attachApprover, REATTACH_MS);
            } else if (running) {
                diag.fail(Diag.Stage.APPROVER, "detached (" + P2pRules.detachName(reason) + "): the system dialog asks instead");
            }
        }

        @Override
        public void onConnectionRequested(int type, WifiP2pConfig config, WifiP2pDevice device) {
            int wps = config != null && config.wps != null ? config.wps.setup : -1;
            // the framework passes mPeers.get(address): null when the sender was never seen in a scan (listen
            // mode), so build the device from the request's address instead of leaving the request unanswered
            WifiP2pDevice dev = device;
            if (dev == null && config != null && config.deviceAddress != null) {
                dev = new WifiP2pDevice();
                dev.deviceAddress = config.deviceAddress;
            }
            String who = dev == null ? "?"
                    : (dev.deviceName != null && !dev.deviceName.isEmpty() ? dev.deviceName + " " : "") + dev.deviceAddress;
            Log.i(TAG, "connection request type=" + type + " from " + who + " wps=" + wps);
            diag.newAttempt(who + " type=" + typeName(type) + " wps=" + P2pRules.wpsName(wps)
                    + " wfd=" + (dev != null && dev.getWfdInfo() != null ? "port " + dev.getWfdInfo().getControlPort() : "none")
                    + (device == null ? " (sender not in the scan list)" : ""));
            if (dev == null) {
                diag.fail(Diag.Stage.ANSWER, "request without a sender address");
                return;
            }
            if (!P2pRules.ownPrompt(wps)) {
                // a PIN method (Windows may ask for one): the framework's own dialog has the PIN entry
                // (KEYPAD: the sender has the keypad, this side shows the PIN) and the PIN display, and
                // drives the matching supplicant calls. Push button (PBC, what phones use) gets our prompt.
                deferToSystem(dev.deviceAddress, P2pRules.wpsName(wps));
                return;
            }
            cb.onConnectionRequest(dev, config, type);
        }

        @Override
        public void onPinGenerated(MacAddress addr, String pin) {
            Log.i(TAG, "pin for " + addr);
            diag.ok(Diag.Stage.ANSWER, "PIN shown for " + addr);
            cb.onPin(addr != null ? addr.toString() : "", pin);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            switch (a) {
                case WifiManager.WIFI_STATE_CHANGED_ACTION: {
                    boolean on = i.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                            == WifiManager.WIFI_STATE_ENABLED;
                    if (on != wifiOn) Log.i(TAG, "wifi " + (on ? "on" : "off"));
                    onWifi(on);
                    break;
                }
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION: {
                    boolean on = i.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED)
                            == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                    Log.i(TAG, "p2p " + (on ? "enabled" : "disabled"));
                    p2pEnabled = on;
                    if (on) {
                        diag.ok(Diag.Stage.P2P, "enabled");
                        configure();
                    } else if (cap.verdict() == P2pVerdict.NO) {
                        setState(P2pState.UNSUPPORTED);
                    } else if (wifiOn) {
                        // Wi-Fi is on: the framework sets the Wi-Fi Direct interface up for our channel (or cannot)
                        diag.waiting(Diag.Stage.P2P, "disabled while Wi-Fi is on, waiting for the Wi-Fi Direct interface");
                        setState(P2pState.STARTING);
                    } else {
                        diag.fail(Diag.Stage.P2P, "disabled (Wi-Fi is off)");
                        setState(P2pState.WIFI_OFF);
                    }
                    break;
                }
                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION: {
                    WifiP2pInfo info = i.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo.class);
                    WifiP2pGroup group = i.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP, WifiP2pGroup.class);
                    boolean formed = info != null && info.groupFormed;
                    if (formed && !groupFormed) {
                        groupFormed = true;
                        String g = "go=" + info.isGroupOwner + " goAddr=" + info.groupOwnerAddress
                                + " iface=" + (group != null ? group.getInterface() : null)
                                + " freq=" + (group != null ? group.getFrequency() : 0)
                                + " clients=" + (group != null ? group.getClientList().size() : 0);
                        Log.i(TAG, "group formed: " + g);
                        diag.ok(Diag.Stage.GROUP, g);
                        setState(P2pState.CONNECTED);
                        cb.onGroupFormed(info, group);
                    } else if (!formed && groupFormed) {
                        groupFormed = false;
                        Log.i(TAG, "group gone");
                        cb.onGroupGone();
                        setState(P2pState.STARTING);
                        main.postDelayed(P2pController.this::becomeVisible, 1000);
                    }
                    break;
                }
                case WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION: {
                    int st = i.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                    if (useDiscovery && st == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED && running && !groupFormed) {
                        main.postDelayed(P2pController.this::becomeVisible, DISCOVER_RETRY_MS);
                    } else if (st == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED && useDiscovery) {
                        setState(P2pState.VISIBLE);
                    }
                    break;
                }
                case WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED: {
                    int st = i.getIntExtra(WifiP2pManager.EXTRA_LISTEN_STATE, WifiP2pManager.WIFI_P2P_LISTEN_STOPPED);
                    Log.i(TAG, "listen state " + st);
                    if (st == WifiP2pManager.WIFI_P2P_LISTEN_STARTED) setState(P2pState.VISIBLE);
                    else if (!groupFormed && running && !useDiscovery) setState(P2pState.STARTING);
                    break;
                }
                default:
                    break;
            }
        }
    };

    private WifiP2pManager.ActionListener listener(String what) {
        return new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, what + " ok");
                decide(cap.proven(what + " ok"));
            }

            @Override
            public void onFailure(int reason) {
                lastError = what + " failed (" + reasonName(reason) + ")";
                Log.w(TAG, lastError);
                decide(cap.callFailed(reason, what));
            }
        };
    }

    /** As {@link #listener}, and the result goes to one diagnostics stage. */
    private WifiP2pManager.ActionListener stageListener(String what, Diag.Stage stage, String okDetail) {
        return new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, what + " ok");
                diag.ok(stage, okDetail);
                decide(cap.proven(what + " ok"));
            }

            @Override
            public void onFailure(int reason) {
                lastError = what + " failed (" + reasonName(reason) + ")";
                Log.w(TAG, lastError);
                diag.fail(stage, lastError);
                decide(cap.callFailed(reason, what));
            }
        };
    }

    static String reasonName(int reason) {
        switch (reason) {
            case WifiP2pManager.ERROR: return "error";
            case WifiP2pManager.P2P_UNSUPPORTED: return "unsupported";
            case WifiP2pManager.BUSY: return "busy";
            case WifiP2pManager.NO_SERVICE_REQUESTS: return "no service requests";
            default: return "reason " + reason;
        }
    }

    private static String typeName(int type) {
        switch (type) {
            case WifiP2pManager.ExternalApproverRequestListener.REQUEST_TYPE_NEGOTIATION: return "negotiation";
            case WifiP2pManager.ExternalApproverRequestListener.REQUEST_TYPE_INVITATION: return "invitation";
            case WifiP2pManager.ExternalApproverRequestListener.REQUEST_TYPE_JOIN: return "join";
            default: return String.valueOf(type);
        }
    }

    private void setState(P2pState s) {
        if (state == s) return;
        state = s;
        cb.onStateChanged();
    }
}
