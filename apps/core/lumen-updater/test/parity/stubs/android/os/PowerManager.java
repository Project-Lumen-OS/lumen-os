// SPDX-License-Identifier: Apache-2.0
package android.os;

/** Parity probe stand-in: a fixed thermal state. */
public final class PowerManager {
    public static final int THERMAL_STATUS_NONE = 0, THERMAL_STATUS_LIGHT = 1, THERMAL_STATUS_MODERATE = 2,
            THERMAL_STATUS_SEVERE = 3, THERMAL_STATUS_CRITICAL = 4, THERMAL_STATUS_EMERGENCY = 5,
            THERMAL_STATUS_SHUTDOWN = 6;
    private final int thermal;
    public PowerManager(int thermal) { this.thermal = thermal; }
    public int getCurrentThermalStatus() { return thermal; }
}
