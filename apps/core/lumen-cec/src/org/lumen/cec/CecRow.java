// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.cec;

/** The rows of the HDMI-CEC page (CecPage), in page order; a CecDriver names the ones its projector has. */
public enum CecRow {
    /** HDMI-CEC on or off (stock signal_control_cec). */
    MASTER,
    /** The note under it that HDMI ARC sound needs HDMI-CEC (stock general_set_device_control_cec_tip). */
    MASTER_NOTE,
    /** HDMI devices may turn the projector on (stock signal_auto_startup). */
    WAKE,
    /** The note under it that the same switch turns HDMI devices off with the projector (where the driver couples them). */
    WAKE_NOTE,
    /** The night hours without wake-ups, their note and the note of paused wake-ups (CecPolicy). */
    NIGHT,
    /** Switch to a device when it turns on: the HDMI app's cec_one_touch_play (stock signal_one_key_to_play). */
    SWITCH,
    /** Turn HDMI devices off with the projector, and the note that the projector follows the device. */
    SEND_STANDBY,
    /** Soundbar or AV receiver over HDMI ARC (framework system audio control). */
    SOUNDBAR,
    /** Turn the soundbar on with the projector. */
    SOUNDBAR_ON,
    /** Control HDMI devices with this remote: the HDMI app's cec_control. */
    REMOTE,
    /** Tell the device when leaving HDMI: the HDMI app's cec_internal_on_exit. */
    LEAVE_NOTICE,
    /** The connected devices and the projector's name on HDMI. */
    DEVICES,
    /** The HDMI app's own options page. */
    HDMI_OPTIONS
}
