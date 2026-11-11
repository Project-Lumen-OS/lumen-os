// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.MacAddress;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Wi-Fi Direct side of the receiver, on the main thread.
 *
 * - Announces the projector as a WFD primary sink (setWfdInfo: CONFIGURE_WIFI_DISPLAY) under the
 *   chosen name (setDeviceName: NETWORK_SETTINGS), and stays discoverable with extended listen
 *   (startListening; falls back to periodic discoverPeers like the stock XGIMI service).
 * - Takes over the framework's "accept invitation?" dialog with an external approver bound to the
 *   broadcast address (Android 13+ treats it as the approver for every peer), so the receiver shows
 *   its own prompt or accepts by itself (MANAGE_WIFI_NETWORK_SELECTION).
 * - Reports group formation / loss to the service, which then runs the RTSP session.
 * All three permissions are signature permissions: the platform-signed APK holds them.
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
    }

    enum State { UNSUPPORTED, WIFI_OFF, STARTING, VISIBLE, CONNECTED, STOPPED }

    private static final long REASSERT_MS = 60_000;
    private static final long DISCOVER_RETRY_MS = 8_000;

    private final Context ctx;
    private final Callback cb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WifiP2pManager mgr;
    private WifiP2pManager.Channel channel;
    private boolean receiverOn, p2pEnabled, groupFormed, running, useDiscovery, approverAttached;
    private String name;
    private State state = State.STOPPED;
    private String lastError;

    P2pController(Context ctx, Callback cb) {
        this.ctx = ctx;
        this.cb = cb;
    }

    State state() {
        return state;
    }

    String lastError() {
        return lastError;
    }

    void start(String deviceName) {
        name = deviceName;
        running = true;
        mgr = ctx.getSystemService(WifiP2pManager.class);
        if (mgr == null) {
            setState(State.UNSUPPORTED);
            Log.w(TAG, "no WifiP2pManager: android.hardware.wifi.direct missing");
            return;
        }
        openChannel();
        if (!receiverOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            f.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            f.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
            f.addAction(WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED);
            ctx.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
            receiverOn = true;
        }
        setState(State.STARTING);
        main.postDelayed(reassert, REASSERT_MS);
    }

    void stop() {
        running = false;
        main.removeCallbacksAndMessages(null);
        if (mgr != null && channel != null) {
            try {
                mgr.stopListening(channel, null);
                mgr.stopPeerDiscovery(channel, null);
                if (approverAttached) mgr.removeExternalApprover(channel, MacAddress.BROADCAST_ADDRESS, null);
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
        setState(State.STOPPED);
    }

    void rename(String deviceName) {
        name = deviceName;
        if (running && p2pEnabled) setName();
    }

    /** Answer for a pending request. accept=false declines. */
    void answer(WifiP2pDevice dev, boolean accept) {
        if (mgr == null || channel == null || dev == null) return;
        try {
            mgr.setConnectionRequestResult(channel, MacAddress.fromString(dev.deviceAddress),
                    accept ? WifiP2pManager.CONNECTION_REQUEST_ACCEPT : WifiP2pManager.CONNECTION_REQUEST_REJECT,
                    listener("answer " + (accept ? "accept" : "reject")));
        } catch (Throwable t) {
            Log.w(TAG, "answer: " + t);
        }
    }

    /** Let the framework show its own dialog for this request (PIN entry on our side). */
    void deferToSystem(WifiP2pDevice dev) {
        if (mgr == null || channel == null || dev == null) return;
        try {
            mgr.setConnectionRequestResult(channel, MacAddress.fromString(dev.deviceAddress),
                    WifiP2pManager.CONNECTION_REQUEST_DEFER_TO_SERVICE, listener("defer"));
        } catch (Throwable t) {
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
            setState(State.UNSUPPORTED);
        }
    }

    private void configure() {
        if (mgr == null || channel == null) return;
        setName();
        WifiP2pWfdInfo wfd = new WifiP2pWfdInfo();
        wfd.setEnabled(true);
        wfd.setDeviceType(1);           // WFD primary sink (WifiP2pWfdInfo.DEVICE_TYPE_PRIMARY_SINK)
        wfd.setSessionAvailable(true);
        wfd.setControlPort(Prefs.CONTROL_PORT);
        wfd.setMaxThroughput(50);       // Mbit/s, as the stock XGIMI sink
        wfd.setContentProtectionSupported(false);
        mgr.setWfdInfo(channel, wfd, listener("setWfdInfo"));
        if (!approverAttached) {
            mgr.addExternalApprover(channel, MacAddress.BROADCAST_ADDRESS, approver);
        }
        becomeVisible();
    }

    private void setName() {
        mgr.setDeviceName(channel, name, listener("setDeviceName " + name));
    }

    private void becomeVisible() {
        if (mgr == null || channel == null || groupFormed || !running) return;
        if (useDiscovery) {
            mgr.discoverPeers(channel, listener("discoverPeers"));
            return;
        }
        mgr.startListening(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "listening (discoverable)");
                setState(State.VISIBLE);
            }

            @Override
            public void onFailure(int reason) {
                Log.w(TAG, "startListening failed " + reason + "; using discoverPeers");
                useDiscovery = true;
                mgr.discoverPeers(channel, listener("discoverPeers"));
            }
        });
    }

    /** Periodically re-announce: some framework paths drop listen mode silently (scan, group removal). */
    private final Runnable reassert = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (p2pEnabled && !groupFormed) becomeVisible();
            main.postDelayed(this, REASSERT_MS);
        }
    };

    private final WifiP2pManager.ExternalApproverRequestListener approver = new WifiP2pManager.ExternalApproverRequestListener() {
        @Override
        public void onAttached(MacAddress addr) {
            approverAttached = true;
            Log.i(TAG, "approver attached " + addr);
        }

        @Override
        public void onDetached(MacAddress addr, int reason) {
            Log.w(TAG, "approver detached " + addr + " reason " + reason);
            if (MacAddress.BROADCAST_ADDRESS.equals(addr)) {
                approverAttached = false;
                if (running && reason != APPROVER_DETACH_REASON_REMOVE && reason != APPROVER_DETACH_REASON_CLOSE) {
                    main.postDelayed(() -> {
                        if (running && !approverAttached && mgr != null && channel != null) {
                            mgr.addExternalApprover(channel, MacAddress.BROADCAST_ADDRESS, approver);
                        }
                    }, 1000);
                }
            }
        }

        @Override
        public void onConnectionRequested(int type, WifiP2pConfig config, WifiP2pDevice device) {
            Log.i(TAG, "connection request type=" + type + " from " + (device != null ? device.deviceName + " " + device.deviceAddress : "?")
                    + " wps=" + (config != null && config.wps != null ? config.wps.setup : -1));
            if (device == null) return;
            if (config != null && config.wps != null
                    && (config.wps.setup == WpsInfo.KEYPAD || config.wps.setup == WpsInfo.DISPLAY)) {
                // a PIN method (Windows may ask for one): the framework's own dialog has the PIN entry
                // (KEYPAD: type the peer's PIN here) and the PIN display (DISPLAY: show ours), and drives
                // the matching supplicant calls. Push button (PBC, what phones use) gets our prompt.
                deferToSystem(device);
                return;
            }
            cb.onConnectionRequest(device, config, type);
        }

        @Override
        public void onPinGenerated(MacAddress addr, String pin) {
            Log.i(TAG, "pin for " + addr);
            cb.onPin(addr != null ? addr.toString() : "", pin);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            switch (a) {
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION: {
                    boolean on = i.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED)
                            == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                    Log.i(TAG, "p2p " + (on ? "enabled" : "disabled"));
                    p2pEnabled = on;
                    if (on) {
                        configure();
                    } else {
                        setState(State.WIFI_OFF);
                    }
                    break;
                }
                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION: {
                    WifiP2pInfo info = i.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo.class);
                    WifiP2pGroup group = i.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP, WifiP2pGroup.class);
                    boolean formed = info != null && info.groupFormed;
                    if (formed && !groupFormed) {
                        groupFormed = true;
                        Log.i(TAG, "group formed: go=" + info.isGroupOwner + " goAddr=" + info.groupOwnerAddress
                                + " iface=" + (group != null ? group.getInterface() : null));
                        setState(State.CONNECTED);
                        cb.onGroupFormed(info, group);
                    } else if (!formed && groupFormed) {
                        groupFormed = false;
                        Log.i(TAG, "group gone");
                        cb.onGroupGone();
                        setState(State.STARTING);
                        main.postDelayed(P2pController.this::becomeVisible, 1000);
                    }
                    break;
                }
                case WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION: {
                    int st = i.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                    if (useDiscovery && st == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED && running && !groupFormed) {
                        main.postDelayed(P2pController.this::becomeVisible, DISCOVER_RETRY_MS);
                    } else if (st == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED && useDiscovery) {
                        setState(State.VISIBLE);
                    }
                    break;
                }
                case WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED: {
                    int st = i.getIntExtra(WifiP2pManager.EXTRA_LISTEN_STATE, WifiP2pManager.WIFI_P2P_LISTEN_STOPPED);
                    Log.i(TAG, "listen state " + st);
                    if (st == WifiP2pManager.WIFI_P2P_LISTEN_STARTED) setState(State.VISIBLE);
                    else if (!groupFormed && running && !useDiscovery) setState(State.STARTING);
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
            }

            @Override
            public void onFailure(int reason) {
                lastError = what + " failed (" + reason + ")";
                Log.w(TAG, lastError);
            }
        };
    }

    private void setState(State s) {
        if (state == s) return;
        state = s;
        cb.onStateChanged();
    }
}
