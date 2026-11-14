package android.os;

public final class ServiceManager {
    /** The vendor services: each records its transactions; the meta calls answer version 1 and the V6.16.24 hash. */
    static final class Remote implements IBinder {
        final String name;
        Remote(String name) { this.name = name; }
        @Override public boolean transact(int code, Parcel q, Parcel r, int flags) throws RemoteException {
            String d = q.descriptor;
            Wire.log("TX " + d + " " + code + " f" + flags + " " + q.payload());
            if (Wire.failNext) { Wire.failNext = false; throw new RemoteException("transport failure"); }
            r.writeInt(0);
            if (code == 0x00FFFFFF) { r.writeInt(1); r.setDataPosition(0); return true; }
            if (code == 0x00FFFFFE) { r.writeString(Wire.PROPS.getOrDefault("hash", "908de964b7da427ad21df846e629b80ffdc183ab")); r.setDataPosition(0); return true; }
            if (Wire.unimplementedNext) { Wire.unimplementedNext = false; return false; }
            r.generate();
            return true;
        }
        @Override public void linkToDeath(DeathRecipient x, int f) { Wire.log("LINK " + name); }
        @Override public boolean unlinkToDeath(DeathRecipient x, int f) { return true; }
        @Override public boolean isBinderAlive() { return true; }
        @Override public IInterface queryLocalInterface(String d) { return null; }
    }

    public static IBinder checkService(String name) {
        Wire.log("CHECKSERVICE " + name);
        if (Wire.PROPS.containsKey("down:" + name)) return null;
        return new Remote(name);
    }
}
