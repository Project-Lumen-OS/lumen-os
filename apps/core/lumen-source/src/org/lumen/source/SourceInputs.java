// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.source;

import android.content.Context;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.lumen.projector.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Our HDMI inputs as the TV input framework lists them (org.lumen.tvinput's port inputs "HW&lt;port&gt;", CEC devices as
 * child inputs), like the stock jumpToHdmi reads them: type HDMI without a parent, keyed by port, CONNECTED or not.
 * Plus the name to show (the user's own label, else "HDMI n") and the device's CEC name. Any thread, never throws.
 */
final class SourceInputs {
    static final String TVINPUT_PKG = "org.lumen.tvinput";
    private static final Pattern HW = Pattern.compile("/HW(\\d+)$");
    private static final Uri STATE = Uri.parse("content://org.lumen.tvinput.hdmistate");

    /** Port -> input id, ascending. */
    final TreeMap<Integer, String> ids = new TreeMap<>();
    final Set<Integer> connected = new HashSet<>();
    private final Map<Integer, String> labels = new HashMap<>();
    private final Map<Integer, String> devices = new HashMap<>();

    static SourceInputs read(Context c) {
        SourceInputs s = new SourceInputs();
        try {
            TvInputManager tim = c.getSystemService(TvInputManager.class);
            List<TvInputInfo> all = tim == null ? null : tim.getTvInputList();
            if (all == null) return s;
            Map<String, Integer> portOf = new HashMap<>();
            for (TvInputInfo i : all) {
                int port = port(i);
                if (port <= 0 || i.getParentId() != null || i.getType() != TvInputInfo.TYPE_HDMI) continue;
                s.ids.put(port, i.getId());
                portOf.put(i.getId(), port);
                if (tim.getInputState(i.getId()) == TvInputManager.INPUT_STATE_CONNECTED) s.connected.add(port);
                CharSequence custom = i.loadCustomLabel(c);
                if (custom != null && custom.toString().trim().length() > 0) s.labels.put(port, custom.toString().trim());
            }
            for (TvInputInfo i : all) {
                Integer port = i.getParentId() == null ? null : portOf.get(i.getParentId());
                if (port == null || s.devices.containsKey(port)) continue;
                HdmiDeviceInfo d = i.getHdmiDeviceInfo();
                CharSequence n = d != null ? d.getDisplayName() : i.loadLabel(c);
                if (n != null && n.length() > 0) s.devices.put(port, n.toString());
            }
        } catch (Throwable t) {
            Log.w(SourceKey.TAG, "inputs: " + t);
        }
        return s;
    }

    int[] ports() {
        int[] out = new int[ids.size()];
        int k = 0;
        for (int p : ids.keySet()) out[k++] = p;
        return out;
    }

    String name(Context c, int port) {
        String l = labels.get(port);
        return l != null ? l : c.getString(R.string.lumen_source_hdmi, port);
    }

    String device(int port) { return devices.get(port); }

    /**
     * The port shown right now: the first port whose org.lumen.tvinput session holds the HDMI hardware (its
     * hdmi_state provider, "ports"), else {@link SourceCycle#HOME}. Worker thread (one binder call).
     */
    static int onScreen(Context c) {
        try {
            if (c.getPackageManager().resolveContentProvider(STATE.getAuthority(), 0) == null) return SourceCycle.HOME;
            Bundle b = c.getContentResolver().call(STATE, "hdmi_state", null, null);
            int[] open = b == null ? null : b.getIntArray("ports");
            if (open != null) for (int p : open) if (p > 0) return p;
        } catch (Throwable t) {
            Log.w(SourceKey.TAG, "hdmi_state: " + t);
        }
        return SourceCycle.HOME;
    }

    /**
     * The installed HDMI viewer keeps its own banner quiet after a Source key switch (manifest mark of
     * org.lumen.tvinput, Lumen OS 1.0.11 on). An older viewer (an image that still ships an earlier TV input app) shows
     * its banner in the card's place, so for HDMI steps the card stands aside there. Any thread.
     */
    static boolean viewerQuiet(Context c) {
        try {
            android.os.Bundle m = c.getPackageManager().getApplicationInfo(TVINPUT_PKG,
                    android.content.pm.PackageManager.GET_META_DATA).metaData;
            return m != null && m.getBoolean(QUIET_MARK, false);
        } catch (Throwable t) {
            return false;
        }
    }

    static final String QUIET_MARK = "org.lumen.tvinput.source_key_quiet";

    private static int port(TvInputInfo i) {
        if (i == null || i.getId() == null || i.getServiceInfo() == null
                || !TVINPUT_PKG.equals(i.getServiceInfo().packageName)) return -1;
        Matcher m = HW.matcher(i.getId());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
