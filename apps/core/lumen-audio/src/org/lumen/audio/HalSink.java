// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import android.media.AudioManager;
import android.util.Log;

import java.util.function.IntSupplier;

/**
 * HalSink: the one writer of the vendor audio HAL sink on every Lumen projector ("xgimi_audiopath_switch_end=P", and the
 * speaker path pair "xgimi_audiopath_switch_start=P" + end). The one path switch (PathSwitch, for every model's
 * vendor adapter) names its media route once (routeFrom); the place that decides a sink takes a ticket at that moment
 * (ticket) and hands it over with the route it decided for. The levels stored first (StockVolume.store) and a newer
 * decision may meet on another thread: a stale end is dropped here (SinkOrder), never written after a newer one. Check and write share one lock. Log tag LumenHalSink.
 */
public final class HalSink {
    private static final String TAG = "LumenHalSink";
    static final String START = "xgimi_audiopath_switch_start=";
    static final String END = "xgimi_audiopath_switch_end=";

    private static final SinkOrder ORDER = new SinkOrder();
    private static volatile IntSupplier sRoute = () -> SinkOrder.ROUTE_UNKNOWN;

    private HalSink() {}

    /** The path reporter's routed path (-1 while unknown). Once per process. */
    public static void routeFrom(IntSupplier route) {
        if (route != null) sRoute = route;
    }

    /** A sink is decided now (any thread): its ticket. */
    public static int ticket() {
        return ORDER.ticket();
    }

    /** switch_end=path for the ticket decided for route forRoute; false = stale, nothing sent. Throws what the HAL call throws. */
    public static boolean end(AudioManager am, int ticket, int forRoute, int path, String why) {
        return send(am, ticket, forRoute, path, false, why);
    }

    /** switch_start=path then switch_end=path (the stored level applied from now on), same rule as end. */
    public static boolean startEnd(AudioManager am, int ticket, int forRoute, int path, String why) {
        return send(am, ticket, forRoute, path, true, why);
    }

    private static synchronized boolean send(AudioManager am, int ticket, int forRoute, int path, boolean start, String why) {
        int now = sRoute.getAsInt();
        if (!ORDER.claim(ticket, forRoute, now)) {
            Log.i(TAG, "stale switch_end=" + path + " dropped (" + why + "): ticket " + ticket + ", newest sent "
                    + ORDER.written() + ", decided for route " + forRoute + ", route now " + now);
            return false;
        }
        if (start) am.setParameters(START + path);
        am.setParameters(END + path);
        return true;
    }
}
