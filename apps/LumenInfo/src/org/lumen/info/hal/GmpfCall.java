/*
 * GmpfCall: the allow list (every transaction the app can produce) of LumenInfo (HbGmpf.java), a top-level type since the HAL codes became HalCodes constants
 * (devices/goat_elegant/hal/codes.tsv). Each code is a HalCodes constant; tools/hal/check_codes.py proves it is the stock method named here.
 */
package org.lumen.info.hal;

/** Every transaction this app can produce (core.Hb.PLAN, same order). */
enum GmpfCall {
    GET_INTERFACE_VERSION(null, HalCodes.META_GET_INTERFACE_VERSION, "getInterfaceVersion"),
    GET_INTERFACE_HASH(null, HalCodes.META_GET_INTERFACE_HASH, "getInterfaceHash"),
    GET_CURRENT_INPUT_SOURCE(GmpfIface.TV, HalCodes.TV_GET_CURRENT_INPUT_SOURCE, "getCurrentInputSource"),
    GET_PICTURE_MODE(GmpfIface.TV, HalCodes.TV_GET_PICTURE_MODE, "getPictureMode"),
    GET_HDMI_INFO(GmpfIface.TV, HalCodes.TV_GET_HDMI_INFO, "getHdmiInfo"),
    GET_EDID_VERSION(GmpfIface.TV, HalCodes.TV_GET_EDID_VERSION, "getEdidVersion"),
    GET_HDMI_RANGE(GmpfIface.TV, HalCodes.TV_GET_HDMI_RANGE, "getHdmiRange"),
    GET_HDR_TYPE(GmpfIface.TV, HalCodes.TV_GET_HDR_TYPE, "getHdrType"),
    GET_ALLM_STATUS(GmpfIface.TV, HalCodes.TV_GET_ALLM_STATUS, "getAllmStatus"),
    GET_DISP_COLOR_SPACE(GmpfIface.DISPLAY, HalCodes.DM_GET_DISP_COLOR_SPACE, "getDispColorSpace"),
    GET_SYSTEM_TEMPERATURE(GmpfIface.TEMPERATURE, HalCodes.TM_GET_SYSTEM_TEMPERATURE, "getSystemTemperature"),
    GET_HDR_ENABLE(GmpfIface.TV, HalCodes.TV_GET_HDR_ENABLE, "getHdrEnable"),
    GET_HDMI_CONNECT_STATUS(GmpfIface.TV, HalCodes.TV_GET_HDMI_CONNECT_STATUS, "getHdmiConnectStatus"),
    GET_SIGNAL_STATUS(GmpfIface.TV, HalCodes.TV_GET_SIGNAL_STATUS, "getSignalStatus"),
    GET_VIDEO_INFO(GmpfIface.TV, HalCodes.TV_GET_VIDEO_INFO, "getVideoInfo"),
    IS_HDR_VIVID_PLAYING(GmpfIface.TV, HalCodes.TV_IS_HDR_VIVID_PLAYING, "isHdrVividPlaying"),
    GET_HDMI_HW_CONFIG(GmpfIface.TV, HalCodes.TV_GET_HDMI_HW_CONFIG, "getHdmiHwConfig"),
    GET_SCREEN_RESOLUTION(GmpfIface.DISPLAY, HalCodes.DM_GET_SCREEN_RESOLUTION, "getScreenResolution"),
    GET_OUTPUT_TIMING_INFO(GmpfIface.DISPLAY, HalCodes.DM_GET_OUTPUT_TIMING_INFO, "getOutputTimingInfo"),
    GET_COLOR_SPACE_INFO(GmpfIface.DISPLAY, HalCodes.DM_GET_COLOR_SPACE_INFO, "getColorSpaceInfo"),
    GET_AUDIO_DELAY(GmpfIface.AUDIO, HalCodes.AM_GET_AUDIO_DELAY, "getAudioDelay"),
    GET_ARC_TYPE(GmpfIface.AUDIO, HalCodes.AM_GET_ARC_TYPE, "getARCType"),
    GET_SPDIF_TYPE(GmpfIface.AUDIO, HalCodes.AM_GET_SPDIF_TYPE, "getSpdifType"),
    IS_AUDIO_DEVICE_CONNECTED(GmpfIface.AUDIO, HalCodes.AM_IS_AUDIO_DEVICE_CONNECTED, "isAudioDeviceConnected"),
    GET_AUDIO_DEVICE_SUPPORT_TYPE(GmpfIface.AUDIO, HalCodes.AM_GET_AUDIO_DEVICE_SUPPORT_TYPE, "getAudioDeviceSupportType");

    final GmpfIface iface;
    final int code;
    final String method;

    GmpfCall(GmpfIface iface, int code, String method) {
        this.iface = iface;
        this.code = code;
        this.method = method;
    }
}
