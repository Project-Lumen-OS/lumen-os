package android.media.tv;

import android.content.pm.ServiceInfo;
import android.hardware.hdmi.HdmiDeviceInfo;

public final class TvInputInfo {
    private final String id, parent;
    private final HdmiDeviceInfo dev;
    private final ServiceInfo service;
    public TvInputInfo(String id, String parent, HdmiDeviceInfo dev, String pkg) {
        this.id = id; this.parent = parent; this.dev = dev; this.service = pkg == null ? null : new ServiceInfo(pkg);
    }
    public String getId() { return id; }
    public String getParentId() { return parent; }
    public HdmiDeviceInfo getHdmiDeviceInfo() { return dev; }
    public ServiceInfo getServiceInfo() { return service; }
}
