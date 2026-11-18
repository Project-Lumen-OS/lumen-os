// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.android;

import android.content.Context;
import android.provider.Settings;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import org.lumen.control.utils.HashedId;
import org.lumen.control.vcontrol.Facts;
import org.lumen.stats.Device;

/**
 * What the projector tells the phone about itself. The id is a hash of the serial, not the serial stock sends: any
 * LAN host may ask, and the remote needs only a stable id. Bluetooth address and Lebo NFC stay out.
 */
public final class AndroidFacts implements Facts {
    private static final String PRODUCT_NAME = "ro.xgimi.product.name";
    /** Stock DeviceNameUtils falls back to this name. */
    private static final String DEFAULT_NAME = "xgimi";
    private static final String PID_SALT = "org.lumen.control.vcontrol.pid:";
    private static final int PID_BYTES = 8;

    private final Context context;

    public AndroidFacts(Context c) {
        context = c;
    }

    @Override public String ip() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // no network
        }
        return "";
    }

    @Override public String name() {
        String n = Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
        return n == null || n.trim().isEmpty() ? DEFAULT_NAME : n;
    }

    @Override public String productName() {
        return SysProp.get(PRODUCT_NAME);
    }

    @Override public String pid() {
        return HashedId.of(PID_SALT, Device.serial(), PID_BYTES);
    }

    @Override public String bluetoothMac() {
        return null;
    }

    @Override public String mac() {
        return "";
    }
}
