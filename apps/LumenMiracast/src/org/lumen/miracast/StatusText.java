// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.miracast;

import android.content.Context;

import org.lumen.miracast.core.Control;
import org.lumen.miracast.core.P2pHealth;
import org.lumen.miracast.core.ReceiverStatus;

/** The status line of screen mirroring, the same on the casting screen and in the projector's quick menu. */
final class StatusText {
    private StatusText() {}

    static ReceiverStatus status(Context c) {
        MiracastService s = MiracastService.instance();
        if (s == null && MiracastService.noWifiDirect()) {
            return Control.status(Prefs.enabled(c), true, false, P2pState.UNSUPPORTED.name());
        }
        return Control.status(Prefs.enabled(c), s != null, s != null && s.mirroring(),
                s == null ? null : s.p2pState().name(), s == null ? null : s.fault().name());
    }

    /** The picture quality as the casting screen's row shows it. */
    static String quality(Context c, org.lumen.miracast.core.WfdQuality q) {
        switch (q) {
            case P1080_30: return c.getString(R.string.quality_1080_30);
            case P720_60: return c.getString(R.string.quality_720);
            default: return c.getString(R.string.quality_1080_60);
        }
    }

    /** The cause the status line names for a link fault, null when there is none to name. */
    static String detail(Context c, P2pHealth.Fault f) {
        switch (f) {
            case WAITING_WIFI: return c.getString(R.string.status_waiting_wifi);
            case RESTARTING: return c.getString(R.string.status_restarting);
            case NOT_ANSWERING: return c.getString(R.string.status_no_answer);
            case CONNECT_BROKEN: return c.getString(R.string.status_connect_broken);
            default: return null;
        }
    }

    /** The cause behind a STARTING or FAULT status (the casting screen shows it on the card), null when none. */
    static String detail(Context c, ReceiverStatus st) {
        MiracastService s = MiracastService.instance();
        if (s == null || (st != ReceiverStatus.STARTING && st != ReceiverStatus.FAULT)) return null;
        return detail(c, s.fault());
    }

    static String of(Context c, ReceiverStatus st) {
        MiracastService s = MiracastService.instance();
        switch (st) {
            case OFF: return c.getString(R.string.status_off);
            case IN_USE: return c.getString(R.string.status_in_use, s != null ? s.peerName() : "");
            case UNSUPPORTED: return c.getString(R.string.status_unsupported);
            case NO_WIFI: return c.getString(R.string.status_no_wifi);
            case READY: return c.getString(R.string.status_ready, Prefs.name(c));
            case FAULT: {
                String d = s != null ? detail(c, s.fault()) : null;
                return d != null ? d : c.getString(R.string.status_no_answer);
            }
            default: {
                String d = s != null ? detail(c, s.fault()) : null;
                return d != null ? d : c.getString(R.string.status_starting);
            }
        }
    }
}
