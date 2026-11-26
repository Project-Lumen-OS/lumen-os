// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.about;

/**
 * What the projector app that carries this module can add to the About rows beyond properties and files: its
 * Application implements it. dlpImageLine is the projector HAL's light engine line (DisplayManager getDlpImageVersion,
 * \"CRISubType:255,ext-fpga version:1.3.5.8 flash version:9.4.128\" on the X50 Ultra G0111), null when it cannot be read.
 * It is asked only when the model's own config names an FPGA (XgimiFacts.read), an app without it shows the type alone.
 */
public interface AboutHost {
    String dlpImageLine();
}
