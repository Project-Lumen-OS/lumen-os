// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.data;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** SMB servers announced on the network (mDNS _smb._tcp, as Home's network folders did), main thread only. */
public final class SmbFinder {
    /** A server seen on the network. */
    public static final class Found {
        public String name = "", host = "";
        public int port;
    }

    /** Called on the main thread for every new server. */
    public interface Listener {
        void found(Found f);
    }

    private static final String TAG = "LumenFiles";
    private final Context mC;
    private final Listener mL;
    private final List<NsdServiceInfo> mQueue = new ArrayList<>();
    private final List<Found> mFound = new ArrayList<>();
    private NsdManager mNsd;
    private NsdManager.DiscoveryListener mDisc;
    private boolean mResolving;

    public SmbFinder(Context c, Listener l) {
        mC = c;
        mL = l;
    }

    public List<Found> found() {
        return new ArrayList<>(mFound);
    }

    public void start() {
        if (mDisc != null) return;
        if (mNsd == null) mNsd = mC.getSystemService(NsdManager.class);
        if (mNsd == null) return;
        NsdManager.DiscoveryListener l = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String type, int code) {
                if (mDisc == this) mDisc = null;
            }

            @Override
            public void onStopDiscoveryFailed(String type, int code) {
            }

            @Override
            public void onDiscoveryStarted(String type) {
            }

            @Override
            public void onDiscoveryStopped(String type) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo info) {
                if (mDisc != this) return;
                for (Found f : mFound) if (f.name.equals(info.getServiceName())) return;
                mQueue.add(info);
                next();
            }

            @Override
            public void onServiceLost(NsdServiceInfo info) {
            }
        };
        try {
            mNsd.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, (android.net.Network) null, mC.getMainExecutor(), l);
            mDisc = l;
        } catch (RuntimeException e) {
            Log.i(TAG, "smb discovery: " + e);
        }
    }

    public void stop() {
        mQueue.clear();
        if (mDisc == null || mNsd == null) return;
        try {
            mNsd.stopServiceDiscovery(mDisc);
        } catch (RuntimeException ignored) {
        }
        mDisc = null;
    }

    private void next() {
        if (mResolving || mQueue.isEmpty() || mNsd == null) return;
        NsdServiceInfo info = mQueue.remove(0);
        mResolving = true;
        try {
            mNsd.resolveService(info, mC.getMainExecutor(), new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo i, int code) {
                    mResolving = false;
                    next();
                }

                @Override
                public void onServiceResolved(NsdServiceInfo i) {
                    mResolving = false;
                    add(i);
                    next();
                }
            });
        } catch (RuntimeException e) {
            mResolving = false;
        }
    }

    private void add(NsdServiceInfo i) {
        InetAddress pick = null;
        for (InetAddress a : i.getHostAddresses()) {
            if (a instanceof Inet4Address) {
                pick = a;
                break;
            }
            if (pick == null) pick = a;
        }
        if (pick == null) return;
        Found f = new Found();
        f.name = i.getServiceName();
        f.host = pick.getHostAddress();
        f.port = i.getPort();
        for (Found x : mFound) if (x.host.equals(f.host)) return;
        mFound.add(f);
        if (mDisc != null) mL.found(f);
    }
}
