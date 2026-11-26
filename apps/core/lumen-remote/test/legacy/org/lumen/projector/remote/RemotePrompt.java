// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.remote;
/** Harness stand-in for the full-screen prompt of the old code: records that it would show. */
final class RemotePrompt {
    static final int SHOWN = 0, LATER = 1, SKIP = 2;
    static final String CLOSED_CONNECTED = "connected", CLOSED_USER = "user", CLOSED_OFF = "off";
    static int maybeShow(android.content.Context c, String reason, boolean bonded) {
        lumen.fake.World.event("prompt " + reason); return lumen.fake.World.promptAnswer; }
    static void apply(RemoteAutoPair.State st) { lumen.fake.Hooks.lastState = "connected=" + st.connected + " bonded=" + st.bonded; }
}
