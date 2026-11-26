// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control.android;

import android.content.Context;
import android.util.Log;
import org.lumen.control.vcontrol.Endpoints;
import org.lumen.control.vcontrol.Vcontrol;
import org.lumen.control.vcontrol.VcontrolModels;
import org.lumen.control.vcontrol.VcontrolServer;
import org.lumen.stats.Device;

/**
 * The XGIMI phone app's remote on Lumen: the stock com.xgimi.vcontrol answers (discovery, connect, heartbeat, the
 * remote's commands) for a model whose stock has them. Started once from the projector app's persistent process.
 */
public final class PhoneRemote {
    private static final String TAG = "LumenPhoneRemote";
    private static VcontrolServer server;

    private PhoneRemote() {}

    /** Starts the inputs on their own thread; never throws (a crash here would loop the persistent process). */
    public static synchronized void start(Context c) {
        if (server != null) return;
        try {
            String model = Device.xgimiModel();
            VcontrolModels m = VcontrolModels.of(model);
            if (m == null) {
                Log.i(TAG, "no stock phone remote on " + model);
                return;
            }
            Context app = c.getApplicationContext();
            Vcontrol v = new Vcontrol(m, new AndroidFacts(app), new AndroidSink(app));
            VcontrolServer s = new VcontrolServer(v, Endpoints.STOCK, (what, t) -> Log.w(TAG, what, t));
            server = s;
            Thread t = new Thread(s::start, "vcontrol-start");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {
            Log.e(TAG, "start", t);
        }
    }
}
