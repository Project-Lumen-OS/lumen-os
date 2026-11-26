// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package lumen.subject;
import org.lumen.remote.RemoteHost;
import org.lumen.remote.RemotePairing;
import org.lumen.remote.RemoteState;
/** The shared lumen-remote engine (apps/core/lumen-remote) with a test host. */
public final class Impl implements lumen.fake.Subject {
    static final RemoteHost HOST = new RemoteHost() {
        public void remoteChanged(android.content.Context app) { lumen.fake.Hooks.setupEvents++; }
        public String unattendedBootReason() { return lumen.fake.World.bootReason; }
        public int promptGate(android.content.Context app, boolean bondedCase) { return FREE; }
        public boolean inStandby() { return false; }
    };
    public void start() { RemotePairing.install(lumen.fake.World.context(), HOST); }
    public void pair() { RemotePairing.startPairing(lumen.fake.World.context()); }
    public String state() { RemoteState s = RemotePairing.state(lumen.fake.World.context());
        return "connected=" + s.connected + " bonded=" + s.bonded + " mode=" + s.mode; }
}
