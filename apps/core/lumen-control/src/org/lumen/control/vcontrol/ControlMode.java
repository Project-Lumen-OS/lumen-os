// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control.vcontrol;

/** controlCmd modes of stock MobileControl.controlCmd that Lumen serves; every other mode stays unanswered. */
final class ControlMode {
    /** XgimiKeyEvent.keyMode(type): a key click, no answer. */
    static final int KEY = 0;
    /** shutDownControl(type, time): type 0 is the power key, answered with 20001. */
    static final int POWER = 6;
    static final int POWER_KEY = 0;

    private ControlMode() {}
}
