// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
/*
 * GmpfIface: the vendor interfaces of HbProjector (AidlWire), a top-level type since the HAL codes became HalCodes constants
 * (devices/goat_elegant/hal/codes.tsv). Their binder and guard state is the shared transport's
 * (lumen-hal AidlTransport, one per descriptor and process).
 */
package org.lumen.projector.hal;

import org.lumen.hal.AidlTransport;

/** One stable AIDL service of the vendor process. */
enum GmpfIface implements AidlTransport.Service {
    SYSTEM("xgimi.hardware.gmpf.ISystemManager"),
    POWER("xgimi.hardware.gmpf.IPowerManager"),
    FOCUS("xgimi.hardware.gmpf.IProjectorFocusManager"),
    DISPLAY("xgimi.hardware.gmpf.IDisplayManager"),
    TV("xgimi.hardware.gmpf.IGmTvManager"),
    AUDIO("xgimi.hardware.gmpf.IGmAudioManager"),
    MOTION("xgimi.hardware.gmpf.IMotionDetectionManager"),
    TEMPERATURE("xgimi.hardware.gmpf.ITemperatureManager"),
    FACTORY("xgimi.hardware.gmpf.IGmFactoryManager");

    /** Interface descriptor, as the stub's DESCRIPTOR (servicemanager name: + "/default"). */
    final String descriptor;

    GmpfIface(String descriptor) {
        this.descriptor = descriptor;
    }

    @Override public String descriptor() { return descriptor; }
}
