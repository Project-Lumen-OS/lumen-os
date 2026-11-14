/*
 * GmpfIface: the vendor interfaces of LumenInfo (HbGmpf.java), a top-level type since the HAL codes became HalCodes constants
 * (devices/goat_elegant/hal/codes.tsv). The transport keeps the binder and guard state it always had.
 */
package org.lumen.info.hal;

import android.os.IBinder;

enum GmpfIface {
    DISPLAY("xgimi.hardware.gmpf.IDisplayManager"),
    TV("xgimi.hardware.gmpf.IGmTvManager"),
    TEMPERATURE("xgimi.hardware.gmpf.ITemperatureManager"),
    AUDIO("xgimi.hardware.gmpf.IGmAudioManager");

    final String descriptor, instance;
    IBinder binder;
    boolean verified, refused;

    GmpfIface(String d) {
        descriptor = d;
        instance = d + "/default";
    }
}
