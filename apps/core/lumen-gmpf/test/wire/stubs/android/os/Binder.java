package android.os;

public class Binder implements IBinder {
    public Binder() { }
    public final void markVintfStability() { }
    public void attachInterface(IInterface owner, String d) { }
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException { return false; }
    @Override public boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException { return onTransact(code, data, reply, flags); }
    @Override public void linkToDeath(DeathRecipient r, int flags) { }
    @Override public boolean unlinkToDeath(DeathRecipient r, int flags) { return true; }
    @Override public boolean isBinderAlive() { return true; }
    @Override public IInterface queryLocalInterface(String d) { return null; }
}
