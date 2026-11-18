package lumen.subject;
import org.lumen.projector.remote.RemoteAutoPair;
/** The Z9X / HB / Play 6 RemoteAutoPair (apps/common/projector, before the shared module). */
public final class Impl implements lumen.fake.Subject {
    public void start() { RemoteAutoPair.install(lumen.fake.World.context()); }
    public void pair() { RemoteAutoPair.startPairing(lumen.fake.World.context()); }
    public String state() { RemoteAutoPair.State s = RemoteAutoPair.state(lumen.fake.World.context());
        return "connected=" + s.connected + " bonded=" + s.bonded + " mode=" + s.mode; }
}
