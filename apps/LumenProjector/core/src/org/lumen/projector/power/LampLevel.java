// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

import android.os.RemoteException;

/**
 * The light level (1..10) of a projector's light source as a page that dims it for a while sees it (the Play 6
 * ambient light and lens mode): read and write on the family's HAL thread, the family's Hal facade gives it
 * (Hal.lampLevel).
 */
public interface LampLevel {
    int get() throws RemoteException;

    void set(int level) throws RemoteException;
}
