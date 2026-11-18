package android.bluetooth.le;
public final class BluetoothLeScanner {
    public void startScan(java.util.List<ScanFilter> f, ScanSettings s, ScanCallback cb) { lumen.fake.World.scanStart(cb, s.mode); }
    public void stopScan(ScanCallback cb) { lumen.fake.World.scanStop(cb); }
}
