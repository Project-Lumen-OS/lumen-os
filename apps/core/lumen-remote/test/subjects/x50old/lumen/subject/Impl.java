// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package lumen.subject;
import org.lumen.x50.remote.RemoteBridge;
/** The X50 / RS30 RemotePair (apps/X50Projector org.lumen.x50.remote, before the shared module). */
public final class Impl implements lumen.fake.Subject {
    public void start() { RemoteBridge.start(lumen.fake.World.context()); RemoteBridge.wake("boot completed"); }
    public void pair() { RemoteBridge.wake("projector menu"); }
    public String state() { android.os.Bundle b = RemoteBridge.remoteState();
        return "connected=" + b.getBoolean("connected") + " bonded=" + b.getInt("bonded") + " mode=" + b.getString("mode"); }
}
