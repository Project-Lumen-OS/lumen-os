/*
 * X50GmpfIface: the vendor interfaces of LumenSensors (X50Gmpf.java), a top-level type since the HAL codes became HalCodes constants
 * (devices/g0111/hal/codes.tsv). The transport keeps the binder and guard state it always had.
 */
package org.lumen.sensors.hal;

import android.os.IBinder;

public enum X50GmpfIface {
    TEMPERATURE("xgimi.hardware.gmpf.ITemperatureManager"),
    DISPLAY("xgimi.hardware.gmpf.IDisplayManager"),
    TV("xgimi.hardware.gmpf.IGmTvManager"),
    MOTION("xgimi.hardware.gmpf.IMotionDetectionManager"),
    FOCUS("xgimi.hardware.gmpf.IProjectorFocusManager");

    final String descriptor, instance;
    /** The service name of the code table (descriptor without the package and the leading I). */
    public final String service;
    IBinder binder;
    boolean verified, refused;

    X50GmpfIface(String d) {
        descriptor = d;
        instance = d + "/default";
        service = d.substring(d.lastIndexOf('.') + 2);
    }
}
