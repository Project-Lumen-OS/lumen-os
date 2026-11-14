package android.os;

public abstract class HwBinder implements IHwBinder {
    public HwBinder() { }
    public abstract void onTransact(int code, HwParcel request, HwParcel reply, int flags) throws RemoteException;
    @Override public final void transact(int code, HwParcel request, HwParcel reply, int flags) throws RemoteException { }

    /** The vendor service: records each transaction, answers from Wire. */
    static final class Remote implements IHwBinder {
        final String name;
        Remote(String name) { this.name = name; }
        @Override public void transact(int code, HwParcel q, HwParcel r, int flags) throws RemoteException {
            Wire.log("TX " + name + " " + code + " f" + flags + " " + q.dump());
            if (Wire.failNext) { Wire.failNext = false; throw new RemoteException("transport failure"); }
            r.replyMode = true;
        }
        @Override public IHwInterface queryLocalInterface(String d) { return null; }
        @Override public boolean linkToDeath(DeathRecipient d, long c) { Wire.log("LINK " + name + " " + c); return true; }
        @Override public boolean unlinkToDeath(DeathRecipient d) { return true; }
    }

    public static IHwBinder getService(String iface, String name, boolean retry) throws RemoteException {
        Wire.log("GETSERVICE " + iface + "/" + name + " " + retry);
        if (Wire.PROPS.containsKey("down:" + iface)) return null;
        return new Remote(iface);
    }
}
