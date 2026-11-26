// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.hardware.hdmi;

import java.util.List;
import parity.World;

/** The framework's CEC settings of the parity world: every call is an event, World.hdmiThrows makes each one throw. */
public class HdmiControlManager {
    public static final int HDMI_CEC_CONTROL_ENABLED = 1, HDMI_CEC_CONTROL_DISABLED = 0;
    public static final int TV_WAKE_ON_ONE_TOUCH_PLAY_ENABLED = 1, TV_WAKE_ON_ONE_TOUCH_PLAY_DISABLED = 0;
    public static final int TV_SEND_STANDBY_ON_SLEEP_ENABLED = 1, TV_SEND_STANDBY_ON_SLEEP_DISABLED = 0;
    public static final int SYSTEM_AUDIO_CONTROL_ENABLED = 1, SYSTEM_AUDIO_CONTROL_DISABLED = 0;
    public static final int VOLUME_CONTROL_ENABLED = 1, VOLUME_CONTROL_DISABLED = 0;
    public static final int POWER_STATUS_UNKNOWN = -1, POWER_STATUS_ON = 0, POWER_STATUS_STANDBY = 1,
            POWER_STATUS_TRANSIENT_TO_ON = 2, POWER_STATUS_TRANSIENT_TO_STANDBY = 3;
    public int cec = 1, wake = 1, standby = 1, sysAudio = 1, volume = 1;

    private int get(String what, int v) {
        World.event("hdmi." + what + "()");
        if (World.hdmiThrows) throw new IllegalStateException(what);
        return v;
    }

    private void set(String what, int v) {
        World.event("hdmi." + what + "(" + v + ")");
        if (World.hdmiThrows || World.hdmiSetThrows.contains(what)) throw new IllegalArgumentException(what);
    }

    public int getHdmiCecEnabled() { return get("getHdmiCecEnabled", cec); }
    public void setHdmiCecEnabled(int v) { set("setHdmiCecEnabled", v); cec = v; }
    public int getTvWakeOnOneTouchPlay() { return get("getTvWakeOnOneTouchPlay", wake); }
    public void setTvWakeOnOneTouchPlay(int v) { set("setTvWakeOnOneTouchPlay", v); wake = v; }
    public int getTvSendStandbyOnSleep() { return get("getTvSendStandbyOnSleep", standby); }
    public void setTvSendStandbyOnSleep(int v) { set("setTvSendStandbyOnSleep", v); standby = v; }
    public int getSystemAudioControl() { return get("getSystemAudioControl", sysAudio); }
    public void setSystemAudioControl(int v) { set("setSystemAudioControl", v); sysAudio = v; }
    public void setHdmiCecVolumeControlEnabled(int v) { set("setHdmiCecVolumeControlEnabled", v); volume = v; }
    public List<HdmiDeviceInfo> getConnectedDevices() {
        World.event("hdmi.getConnectedDevices()");
        if (World.hdmiThrows) throw new IllegalStateException("getConnectedDevices");
        return World.devices;
    }
}
