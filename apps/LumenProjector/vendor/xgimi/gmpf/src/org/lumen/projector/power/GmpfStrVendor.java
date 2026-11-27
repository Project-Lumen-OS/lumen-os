// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

import org.lumen.projector.hal.GmpfClient;

/**
 * The stock prepare's three vendor calls (power/StrPrepare) over the shared gmpf client, for every XGIMI family of the
 * one projector core: stock services.jar PowerManagerService.goToSleep(100) -> xgimiPrepareSleep, once per sleep
 * (isSuspending): XgimiSmsController.gotoSleep(0, 0), then getScreenOnOff / setScreenOnOff(false). Read in the stock
 * services.jar of the Z9X G0082 (:5094-5113), the HB G0148 6.17.12 (:5015-5034) and the RS 20 1.6.52 (:5246-5265). The
 * code is the family table's goToSleep row (HB IPowerManager 6, Z9X and RS 20 IGmpf 241); null when there is none.
 */
public final class GmpfStrVendor {
    private GmpfStrVendor() {}

    public static StrPrepare.Vendor of(GmpfClient g) {
        if (g == null || !g.hasStrPrepareSleep()) return null;
        return new StrPrepare.Vendor() {
            @Override public void vendorSleep() throws Exception { g.strPrepareSleep(); }
            @Override public boolean screenOn() throws Exception { return g.getScreenOn(); }
            @Override public void setScreen(boolean on) throws Exception { g.setScreenOn(on); }
        };
    }
}
