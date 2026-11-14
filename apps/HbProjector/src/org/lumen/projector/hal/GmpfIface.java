/*
 * GmpfIface: the vendor interfaces of HbProjector (AidlCaller.java), a top-level type since the HAL codes became HalCodes constants
 * (devices/goat_elegant/hal/codes.tsv). The transport keeps the binder and guard state it always had.
 */
package org.lumen.projector.hal;

import android.os.IBinder;

/** One stable AIDL service of the vendor process, with its own guard state (guarded by AidlCaller.class). */
enum GmpfIface {
    SYSTEM("xgimi.hardware.gmpf.ISystemManager"),
    POWER("xgimi.hardware.gmpf.IPowerManager"),
    FOCUS("xgimi.hardware.gmpf.IProjectorFocusManager"),
    DISPLAY("xgimi.hardware.gmpf.IDisplayManager"),
    TV("xgimi.hardware.gmpf.IGmTvManager"),
    AUDIO("xgimi.hardware.gmpf.IGmAudioManager"),
    MOTION("xgimi.hardware.gmpf.IMotionDetectionManager"),
    TEMPERATURE("xgimi.hardware.gmpf.ITemperatureManager"),
    FACTORY("xgimi.hardware.gmpf.IGmFactoryManager");

    /** Interface descriptor, as the stub's DESCRIPTOR. */
    final String descriptor;
    /** servicemanager name (vendor manifest xgimi.hardware.gmpf-service.xml). */
    final String instance;

    IBinder binder;
    boolean verified;      // version and hash checked and equal for this binder
    boolean refused;       // latched: never send anything to this interface again (until a vendor death)
    boolean refusedLogged;

    GmpfIface(String descriptor) {
        this.descriptor = descriptor;
        this.instance = descriptor + "/default";
    }
}
