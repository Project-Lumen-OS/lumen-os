// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.source;

import android.content.Context;

import org.lumen.hdmi.RelinkHost;
import org.lumen.projector.Hal;
import org.lumen.projector.hal.GmpfBase;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.power.StandbyController;

import java.util.function.Consumer;

/**
 * The gmpf projectors' side of the shared HDMI relink (apps/core/lumen-hdmi HdmiRelink): getSignalStatus (Z9X IGmpf
 * 592, HB TV 106), get / setEdidVersion with the device's stock values (GmpfRules.edidVersions), the "z9x-hal" thread
 * and its connect hook, the lamp-only standby and tvinput's port provider. Data and plumbing only.
 */
public final class GmpfRelinkHost implements RelinkHost {
    private static final String TAG = "Z9xRelink";

    private static Vendor vendor(GmpfClient g) {
        return new Vendor() {
            @Override public int signalStatus() throws Exception { return g.getSignalStatus(); }
            @Override public int edidVersion() throws Exception { return g.getEdidVersion(); }
            @Override public boolean setEdidVersion(int v) throws Exception { return g.setEdidVersion(v); }
            @Override public int[] edidVersions() { return g.edidVersions(); }
        };
    }

    @Override public String tag() { return TAG; }

    @Override public int edidFirst() { return GmpfBase.EDID_14; }

    @Override public int edidSecond() { return GmpfBase.EDID_20; }

    @Override public <T> void query(Read<T> r, Consumer<T> onMain) {
        Hal.query((g, g2) -> r.run(vendor(g)), onMain::accept);
    }

    @Override public boolean run(Job j) { return Hal.run((g, g2) -> j.run(vendor(g))); }

    @Override public boolean runDelayed(Job j, long ms, Runnable ifSkipped) {
        return Hal.runDelayed((g, g2) -> j.run(vendor(g)), ms, ifSkipped);
    }

    @Override public void onConnect(Job j) {
        Hal.addConnectedListener(() -> Hal.run((g, g2) -> j.run(vendor(g))));
    }

    @Override public boolean standby() { return StandbyController.isActive(); }

    @Override public int[] shownPorts(Context ctx) { return TifHdmiState.readPorts(ctx); }

    @Override public boolean inactiveSourceSleep() { return org.lumen.projector.Family.INACTIVE_SOURCE_SLEEP; }
}
