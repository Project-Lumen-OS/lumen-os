// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.remote;

import android.os.Bundle;

/** Snapshot of the remotes for the first-run setup (remote_state of the Setup bridge) and the menus. Any thread. */
public final class RemoteState {
    /** XGIMI remotes bonded */
    public int bonded;
    /** a bonded remote is linked AND its HID input device is up: its keys reach Android */
    public boolean connected;
    public String name = "";
    public boolean scanning;
    public String mode = "idle";
    /** a bond (or a re-pair) of a remote in pairing mode is in progress */
    public boolean pairing;
    /** name of the remote being paired, "" when unknown or not pairing */
    public String found = "";
    /** battery of the connected remote in percent, -1 = unknown */
    public int battery = -1;
    /** the Bluetooth runtime permissions are missing (scan and bond cannot work) */
    public boolean missingPermission;

    /** The remote_state fields of the Setup bridge (same keys on every model). */
    public Bundle bundle() {
        Bundle b = new Bundle();
        b.putBoolean("ok", true);
        b.putInt("bonded", bonded);
        b.putBoolean("connected", connected);
        b.putString("name", name);
        b.putBoolean("scanning", scanning);
        b.putString("mode", mode);
        b.putBoolean("pairing", pairing);
        b.putString("found", found);
        b.putInt("battery", battery);
        if (missingPermission) b.putString("err", "missing_permission");
        return b;
    }
}
