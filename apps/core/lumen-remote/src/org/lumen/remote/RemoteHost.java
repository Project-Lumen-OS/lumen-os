// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.remote;

import android.content.Context;

/**
 * The few things lumen-remote asks of the projector app that hosts it (one small implementation per app family:
 * ProjectorRemoteHost for Z9X, Z9X HB and Play 6, X50RemoteHost for the X50 family and RS30). Everything else (scan,
 * bond, HID connect, prompt, cards, state) is the same code on every model.
 */
public interface RemoteHost {
    /** prompt gate answers */
    int FREE = 0, LATER = 1, SKIP = 2;

    /** A remote's bond or connection changed: tell the first-run setup (lossy, the setup also polls remote_state). */
    void remoteChanged(Context app);

    /** This boot followed a restart nobody started at the remote (update, rescue): the remote is only asleep. */
    String unattendedBootReason();

    /**
     * May the remote-lost prompt show now, as far as this app knows (standby, an HDMI-CEC wake when bondedCase, its
     * own full-screen overlays)? FREE, LATER (blocked for now) or SKIP (not this wake). The module itself checks the
     * screen, the setup flags, a dream and the overlay host.
     */
    int promptGate(Context app, boolean bondedCase);

    /** The projector is going to standby or shutting down (a prompt closed now was closed by that, not the user). */
    boolean inStandby();
}
