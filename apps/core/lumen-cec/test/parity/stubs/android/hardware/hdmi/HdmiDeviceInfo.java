// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.hardware.hdmi;

public final class HdmiDeviceInfo {
    public static final int DEVICE_TV = 0, DEVICE_RECORDER = 1, DEVICE_TUNER = 3, DEVICE_PLAYBACK = 4,
            DEVICE_AUDIO_SYSTEM = 5;
    private final int id, la, port, type, power;
    private final String name;
    private final boolean cec;
    public HdmiDeviceInfo(int id, int la, int port, int type, int power, String name, boolean cec) {
        this.id = id; this.la = la; this.port = port; this.type = type; this.power = power; this.name = name; this.cec = cec;
    }
    public int getId() { return id; }
    public int getLogicalAddress() { return la; }
    public int getPortId() { return port; }
    public int getDeviceType() { return type; }
    public int getDevicePowerStatus() { return power; }
    public String getDisplayName() { return name; }
    public boolean isCecDevice() { return cec; }
}
