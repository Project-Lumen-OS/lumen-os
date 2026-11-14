/*
 * X50GmpfCall: the allow list (every transaction the app can produce) of LumenSensors (X50Gmpf.java), a top-level type since the HAL codes became HalCodes constants
 * (devices/g0111/hal/codes.tsv). Each code is a HalCodes constant; tools/hal/check_codes.py proves it is the stock method named here.
 */
package org.lumen.sensors.hal;

/** Every transaction this app can produce on the X50 (= research/allowed_lumensensors.tsv). */
public enum X50GmpfCall {
    GET_INTERFACE_VERSION(null, X50HalCodes.META_GET_INTERFACE_VERSION, "getInterfaceVersion"),
    GET_INTERFACE_HASH(null, X50HalCodes.META_GET_INTERFACE_HASH, "getInterfaceHash"),
    GET_SCREEN_ON_OFF(X50GmpfIface.DISPLAY, X50HalCodes.DM_GET_SCREEN_ON_OFF, "getScreenOnOff"),
    GET_SYSTEM_TEMPERATURE(X50GmpfIface.TEMPERATURE, X50HalCodes.TM_GET_SYSTEM_TEMPERATURE, "getSystemTemperature"),
    GET_TEMPERATURE_FROM_SENSOR(X50GmpfIface.TEMPERATURE, X50HalCodes.TM_GET_TEMPERATURE_FROM_SENSOR, "getTemperatureFromSensor"),
    IS_TEMP_ULTRA_HIGH(X50GmpfIface.TEMPERATURE, X50HalCodes.TM_IS_TEMP_ULTRA_HIGH, "isTempUltraHigh"),
    GET_FAN_MODE(X50GmpfIface.TEMPERATURE, X50HalCodes.TM_GET_FAN_MODE, "getFanMode"),
    GET_FAN_ROTATION_RATE(X50GmpfIface.TEMPERATURE, X50HalCodes.TM_GET_FAN_ROTATION_RATE, "getFanRotationRate"),
    GET_DLP_LUMENS_LEVEL(X50GmpfIface.DISPLAY, X50HalCodes.DM_GET_DLP_LUMENS_LEVEL, "getDlpLumensLevel"),
    GET_DLP_LUMENS_MODE(X50GmpfIface.DISPLAY, X50HalCodes.DM_GET_DLP_LUMENS_MODE, "getDlpLumensMode"),
    GET_LENS_DISP_MODE(X50GmpfIface.DISPLAY, X50HalCodes.DM_GET_LENS_DISP_MODE, "getLensDispMode"),
    GET_DISP_LUMENS_LEVEL(X50GmpfIface.DISPLAY, X50HalCodes.DM_GET_DISP_LUMENS_LEVEL, "getDispLumensLevel"),
    GET_LIGHT_USAGE_INFO(X50GmpfIface.DISPLAY, X50HalCodes.DM_GET_LIGHT_USAGE_INFO, "getLightUsageInfo"),
    GET_OUTPUT_TIMING_INFO(X50GmpfIface.DISPLAY, X50HalCodes.DM_GET_OUTPUT_TIMING_INFO, "getOutputTimingInfo"),
    GET_DISP_OUTPUT_FPS(X50GmpfIface.DISPLAY, X50HalCodes.DM_GET_DISP_OUTPUT_FPS, "getDispOutputFps"),
    GET_CURRENT_INPUT_SOURCE(X50GmpfIface.TV, X50HalCodes.TV_GET_CURRENT_INPUT_SOURCE, "getCurrentInputSource"),
    GET_HDR_TYPE(X50GmpfIface.TV, X50HalCodes.TV_GET_HDR_TYPE, "getHdrType"),
    GET_HDMI_INFO(X50GmpfIface.TV, X50HalCodes.TV_GET_HDMI_INFO, "getHdmiInfo"),
    GET_VIDEO_INFO(X50GmpfIface.TV, X50HalCodes.TV_GET_VIDEO_INFO, "getVideoInfo"),
    GET_MOTION_DETECTION(X50GmpfIface.MOTION, X50HalCodes.MD_GET_MOTION_DETECTION, "getMotionDetection"),
    GET_FOCUS_MOTOR_CURRENT_STEP(X50GmpfIface.FOCUS, X50HalCodes.PF_GET_FOCUS_MOTOR_CURRENT_STEP, "getFocusMotorCurrentStep");

    public final X50GmpfIface iface;
    public final int code;
    public final String method;

    X50GmpfCall(X50GmpfIface iface, int code, String method) {
        this.iface = iface;
        this.code = code;
        this.method = method;
    }

    /** Service name of the code table ("TemperatureManager"), or "-" for a meta call. */
    public String service() { return iface == null ? "-" : iface.service; }
}
