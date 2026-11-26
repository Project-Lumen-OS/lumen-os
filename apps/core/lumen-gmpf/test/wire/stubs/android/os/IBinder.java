// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

public interface IBinder {
    int FLAG_ONEWAY = 0x00000001;
    interface DeathRecipient { void binderDied(); }
    boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException;
    void linkToDeath(DeathRecipient r, int flags) throws RemoteException;
    boolean unlinkToDeath(DeathRecipient r, int flags);
    boolean isBinderAlive();
    IInterface queryLocalInterface(String d);
}
