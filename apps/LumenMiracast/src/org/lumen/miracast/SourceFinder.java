// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.content.Context;
import android.net.LinkAddress;
import android.net.TetheredClient;
import android.net.TetheringManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.util.Log;

import org.lumen.miracast.core.SourceLocator;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Where is the source's RTSP server? A client of the group knows: the group owner's address. As the
 * group owner (the usual case here: the vendor supplicant sets p2p_go_intent=14) only the peer's MAC
 * is known, so, in this order:
 *   1. the tethering service's DHCP lease list for Wi-Fi Direct (TetheringManager clients; needs
 *      NETWORK_SETTINGS, which the platform-signed app has),
 *   2. /proc/net/arp entries on the group interface (what the stock XGIMI sink reads),
 *   3. a TCP connect sweep of the group's /24 on the source's control port. The first socket that
 *      connects IS the session socket (a probe connection that closes again could end the session on
 *      some sources), so this step returns a connected socket.
 * Runs on a worker thread; gives up after the window.
 */
final class SourceFinder {
    private static final String TAG = "LumenMiracast";

    static final class Result {
        final String host;
        final Socket connected;   // non-null only from the sweep

        Result(String host, Socket connected) {
            this.host = host;
            this.connected = connected;
        }
    }

    private SourceFinder() {}

    static Result find(Context c, WifiP2pInfo info, WifiP2pGroup group, int port, long windowMs, AtomicBoolean cancelled) {
        if (info != null && !info.isGroupOwner && info.groupOwnerAddress != null) {
            return new Result(info.groupOwnerAddress.getHostAddress(), null);
        }
        String iface = group != null ? group.getInterface() : null;
        long end = System.currentTimeMillis() + windowMs;
        int round = 0;
        while (!cancelled.get() && System.currentTimeMillis() < end) {
            String ip = fromTethering(c, 1500);
            if (ip != null) {
                Log.i(TAG, "source " + ip + " from the DHCP lease list");
                return new Result(ip, null);
            }
            ip = fromArp(iface);
            if (ip != null) {
                Log.i(TAG, "source " + ip + " from ARP on " + iface);
                return new Result(ip, null);
            }
            if (round++ >= 1) { // give DHCP a moment before sweeping
                String own = ownAddress(iface);
                if (own != null) {
                    Socket s = sweep(own, port, cancelled);
                    if (s != null) {
                        String host = s.getInetAddress().getHostAddress();
                        Log.i(TAG, "source " + host + " found by sweep");
                        return new Result(host, s);
                    }
                }
            }
            sleep(700);
        }
        return null;
    }

    private static String fromTethering(Context c, long waitMs) {
        TetheringManager tm = c.getSystemService(TetheringManager.class);
        if (tm == null) return null;
        AtomicReference<String> found = new AtomicReference<>();
        Object lock = new Object();
        TetheringManager.TetheringEventCallback cb = new TetheringManager.TetheringEventCallback() {
            @Override
            public void onClientsChanged(Collection<TetheredClient> clients) {
                for (TetheredClient tc : clients) {
                    if (tc.getTetheringType() != TetheringManager.TETHERING_WIFI_P2P) continue;
                    for (TetheredClient.AddressInfo ai : tc.getAddresses()) {
                        LinkAddress la = ai.getAddress();
                        if (la != null && la.getAddress() instanceof Inet4Address) {
                            found.compareAndSet(null, la.getAddress().getHostAddress());
                        }
                    }
                }
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        };
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            tm.registerTetheringEventCallback(ex, cb);
            synchronized (lock) {
                if (found.get() == null) lock.wait(waitMs);
            }
        } catch (Throwable t) {
            Log.w(TAG, "tethering clients: " + t);
        } finally {
            try {
                tm.unregisterTetheringEventCallback(cb);
            } catch (Throwable ignored) {
                // not registered
            }
            ex.shutdown();
        }
        return found.get();
    }

    private static String fromArp(String iface) {
        try {
            String text = new String(Files.readAllBytes(Paths.get("/proc/net/arp")), StandardCharsets.US_ASCII);
            List<SourceLocator.Entry> e = SourceLocator.arpEntries(text, iface != null ? iface : "p2p");
            return e.isEmpty() ? null : e.get(e.size() - 1).ip;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    static String ownAddress(String iface) {
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            while (all != null && all.hasMoreElements()) {
                NetworkInterface ni = all.nextElement();
                if (iface != null ? !ni.getName().equals(iface) : !ni.getName().startsWith("p2p")) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address) return a.getHostAddress();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "interfaces: " + e);
        }
        return null;
    }

    private static Socket sweep(String ownIp, int port, AtomicBoolean cancelled) {
        List<String> hosts = SourceLocator.sweepOrder(ownIp);
        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicReference<Socket> winner = new AtomicReference<>();
        for (String h : hosts) {
            pool.execute(() -> {
                if (winner.get() != null || cancelled.get()) return;
                Socket s = new Socket();
                try {
                    s.setTcpNoDelay(true);
                    s.connect(new InetSocketAddress(h, port), 400);
                    if (!winner.compareAndSet(null, s)) s.close();
                } catch (IOException e) {
                    try {
                        s.close();
                    } catch (IOException ignored) {
                        // closed
                    }
                }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return winner.get();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
