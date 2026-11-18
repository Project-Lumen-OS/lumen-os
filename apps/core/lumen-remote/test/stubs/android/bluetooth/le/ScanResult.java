package android.bluetooth.le;
public final class ScanResult {
    final android.bluetooth.BluetoothDevice d; final ScanRecord rec; final int rssi; final boolean conn;
    public ScanResult(android.bluetooth.BluetoothDevice d, ScanRecord rec, int rssi, boolean conn) { this.d = d; this.rec = rec; this.rssi = rssi; this.conn = conn; }
    public android.bluetooth.BluetoothDevice getDevice() { return d; }
    public ScanRecord getScanRecord() { return rec; }
    public int getRssi() { return rssi; }
    public boolean isConnectable() { return conn; }
}
