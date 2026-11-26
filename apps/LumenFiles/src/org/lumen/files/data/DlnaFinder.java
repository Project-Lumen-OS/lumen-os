// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.data;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.lumen.files.model.Upnp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** DLNA media servers of the network (worker thread): SSDP search for a few seconds, then each description. */
public final class DlnaFinder {
    private static final String TAG = "LumenFiles";

    private DlnaFinder() {}

    public static List<Upnp.Device> find(Context c, int ms) {
        Set<String> locations = new LinkedHashSet<>();
        WifiManager wm = c.getApplicationContext().getSystemService(WifiManager.class);
        WifiManager.MulticastLock lock = wm == null ? null : wm.createMulticastLock("lumen-files-dlna");
        if (lock != null) {
            lock.setReferenceCounted(false);
            lock.acquire();
        }
        try (DatagramSocket s = new DatagramSocket()) {
            s.setSoTimeout(300);
            byte[] q = Upnp.search().getBytes(StandardCharsets.US_ASCII);
            InetAddress group = InetAddress.getByName(Upnp.GROUP);
            long end = System.currentTimeMillis() + ms;
            for (int i = 0; i < 2; i++) s.send(new DatagramPacket(q, q.length, group, Upnp.PORT));
            byte[] buf = new byte[4096];
            while (System.currentTimeMillis() < end) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    s.receive(p);
                } catch (SocketTimeoutException t) {
                    continue;
                }
                String loc = Upnp.location(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8));
                if (loc != null) locations.add(loc);
            }
        } catch (IOException e) {
            Log.i(TAG, "dlna search: " + e);
        } finally {
            if (lock != null) lock.release();
        }
        List<Upnp.Device> out = new ArrayList<>();
        Set<String> udns = new LinkedHashSet<>();
        for (String loc : locations) {
            try {
                Upnp.Device d = Upnp.describe(DlnaFs.get(loc), loc);
                if (d != null && udns.add(d.udn)) out.add(d);
            } catch (Exception e) {
                Log.i(TAG, "dlna description " + loc + ": " + e);
            }
        }
        return out;
    }
}
