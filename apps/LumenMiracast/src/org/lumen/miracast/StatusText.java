// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.content.Context;

import org.lumen.miracast.core.Control;
import org.lumen.miracast.core.ReceiverStatus;

/** The status line of screen mirroring, the same on the settings screen and in the projector's quick menu. */
final class StatusText {
    private StatusText() {}

    static ReceiverStatus status(Context c) {
        MiracastService s = MiracastService.instance();
        if (s == null && MiracastService.noWifiDirect()) {
            return Control.status(Prefs.enabled(c), true, false, P2pState.UNSUPPORTED.name());
        }
        return Control.status(Prefs.enabled(c), s != null, s != null && s.mirroring(),
                s == null ? null : s.p2pState().name());
    }

    static String of(Context c, ReceiverStatus st) {
        MiracastService s = MiracastService.instance();
        switch (st) {
            case OFF: return c.getString(R.string.status_off);
            case IN_USE: return c.getString(R.string.status_in_use, s != null ? s.peerName() : "");
            case UNSUPPORTED: return c.getString(R.string.status_unsupported);
            case NO_WIFI: return c.getString(R.string.status_no_wifi);
            case READY: return c.getString(R.string.status_ready, Prefs.name(c));
            default: return c.getString(R.string.status_starting);
        }
    }
}
