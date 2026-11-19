// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/** The settings.temperature row text of every model: the vendor's system temperature, an unsigned byte of whole degrees C. */
public final class TemperatureText {
    private TemperatureText() {}

    public static String of(int vendorByte) {
        return (vendorByte & 0xFF) + " \u00B0C";
    }

    /** Null (the row left out) when nothing was read. */
    public static String of(Integer vendorByte) {
        return vendorByte == null ? null : of(vendorByte.intValue());
    }
}
