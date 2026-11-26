// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.game;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.lumen.projector.Family;

/**
 * MODULE "game" (v6.2). Explicit broadcasts from org.lumen.tvinput (its ProjectorEvents), guarded by the
 * signature permission org.lumen.projector.permission.HDMI_EVENT (manifest):
 *   action org.lumen.projector.action.HDMI_EVENT, extras
 *   port (int 1|2), active (boolean), reason (String: "session" | "cec_added" | "cec_removed").
 * What an event does is the HAL family's (Family.hdmiEvent): the gmpf families run game/HdmiEvents, the Play 6
 * its source/HdmiSource.
 */
public final class HdmiEventReceiver extends BroadcastReceiver {
    public static final String ACTION = "org.lumen.projector.action.HDMI_EVENT";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            if (intent == null || !ACTION.equals(intent.getAction())) return;
            Family.hdmiEvent(ctx, intent);
        } catch (Throwable t) {
            Log.e(Family.TAG_PREFIX + "Game", "HdmiEventReceiver", t);
        }
    }
}
