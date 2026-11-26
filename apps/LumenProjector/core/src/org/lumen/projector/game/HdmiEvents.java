// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.game;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * What an HDMI_EVENT of org.lumen.tvinput does on the gmpf families (game/HdmiEventReceiver through Family.hdmiEvent):
 * "session": one of tvinput's HdmiSessions got (active) or lost its HDMI hardware, i.e. a TvView started / stopped
 * showing that HDMI port. Main thread; hands over to StandbyController (v6.5: a session that starts during the
 * lamp-only standby never wakes it: the black screen comes back and the lamp stays off), source.HdmiSource (Lumen
 * OS 1.0.3: the vendor source switch, IGmpf 698), lumen-hdmi HdmiRelink (Lumen OS 1.0.11: the EDID flip when a
 * powered source gives no picture) and GameProfile.
 */
public final class HdmiEvents {
    private HdmiEvents() {}

    public static void onEvent(Context ctx, Intent intent) {
        // v6.5: during the lamp-only standby an HDMI session is covered again, never a wake
        try {
            org.lumen.projector.power.StandbyController.onHdmiEvent(intent.getIntExtra("port", -1),
                    intent.getBooleanExtra("active", false), intent.getStringExtra("reason"));
        } catch (Throwable t) {
            Log.w("Z9xGame", "standby HDMI event: " + t);
        }
        // Lumen OS 1.0.3: the HDMI picture (IGmpf 698 follows tvinput's sessions)
        if ("session".equals(intent.getStringExtra("reason"))) {
            try {
                org.lumen.projector.source.HdmiSource.onSessionEvent(intent.getIntExtra("port", -1),
                        intent.getBooleanExtra("active", false));
            } catch (Throwable t) {
                Log.w("Z9xGame", "HDMI source event: " + t);
            }
            // Lumen OS 1.0.11: no picture from a powered source -> one EDID flip (the replug)
            try {
                org.lumen.hdmi.HdmiRelink.onSessionEvent(intent.getIntExtra("port", -1),
                        intent.getBooleanExtra("active", false));
            } catch (Throwable t) {
                Log.w("Z9xGame", "HDMI relink event: " + t);
            }
        }
        GameProfile.onHdmiEvent(ctx, intent);
    }
}
