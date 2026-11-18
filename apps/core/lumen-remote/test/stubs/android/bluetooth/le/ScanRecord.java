package android.bluetooth.le;
public final class ScanRecord {
    final String name; final int flags; final java.util.List<android.os.ParcelUuid> uuids; final android.util.SparseArray<byte[]> mfr;
    public ScanRecord(String name, int flags, java.util.List<android.os.ParcelUuid> uuids, android.util.SparseArray<byte[]> mfr) {
        this.name = name; this.flags = flags; this.uuids = uuids; this.mfr = mfr; }
    public String getDeviceName() { return name; }
    public int getAdvertiseFlags() { return flags; }
    public java.util.List<android.os.ParcelUuid> getServiceUuids() { return uuids; }
    public android.util.SparseArray<byte[]> getManufacturerSpecificData() { return mfr; }
    public byte[] getManufacturerSpecificData(int id) { return mfr == null ? null : mfr.get(id); }
}
