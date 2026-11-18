package org.lumen.remote;
/** Harness stand-in for the module's full-screen prompt (views do not run on the host): records that it would show. */
final class RemotePrompt {
    static final int SHOWN = 0, LATER = 1, SKIP = 2;
    static final String CLOSED_CONNECTED = "connected", CLOSED_USER = "user", CLOSED_OFF = "off";
    static int maybeShow(android.content.Context c, String reason, boolean bonded, RemoteHost host) {
        if (host != null && host.promptGate(c, bonded) == RemoteHost.SKIP) return SKIP;
        lumen.fake.World.event("prompt " + reason); return lumen.fake.World.promptAnswer; }
    static void apply(RemoteState st) { lumen.fake.Hooks.lastState = "connected=" + st.connected + " bonded=" + st.bonded; }
}
