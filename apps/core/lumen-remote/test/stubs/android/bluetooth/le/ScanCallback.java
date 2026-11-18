package android.bluetooth.le;
public abstract class ScanCallback {
    public static final int SCAN_FAILED_ALREADY_STARTED = 1;
    public void onScanResult(int type, ScanResult r) {}
    public void onBatchScanResults(java.util.List<ScanResult> rs) {}
    public void onScanFailed(int err) {}
}
