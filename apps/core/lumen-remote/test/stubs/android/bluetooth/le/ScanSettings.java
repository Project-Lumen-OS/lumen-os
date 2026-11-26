// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.bluetooth.le;
public final class ScanSettings {
    public static final int SCAN_MODE_OPPORTUNISTIC = -1, SCAN_MODE_LOW_POWER = 0, SCAN_MODE_BALANCED = 1, SCAN_MODE_LOW_LATENCY = 2;
    public static final int CALLBACK_TYPE_ALL_MATCHES = 1;
    public final int mode;
    private ScanSettings(int m) { mode = m; }
    public int getScanMode() { return mode; }
    public static final class Builder { int m;
        public Builder setScanMode(int m) { this.m = m; return this; }
        public Builder setCallbackType(int t) { return this; }
        public Builder setReportDelay(long d) { return this; }
        public Builder setLegacy(boolean l) { return this; }
        public ScanSettings build() { return new ScanSettings(m); } }
}
