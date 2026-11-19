// SPDX-License-Identifier: Apache-2.0
package org.lumen.wallcolor;

/**
 * The four stock vendor calls of the wall colour adaptation, in the order the stock flow makes them. Each projector's
 * WallColorHal maps them onto its own HAL client (codes in devices/&lt;c&gt;/hal/codes.tsv). No other vendor call is part
 * of the feature: never the factory calibration (startAutoColorCalibration, its check, its data).
 */
public enum WallColorStep {
    /** setAEOnOff(false): stock newsettings brightnessWallColorCalibration, right before the start. */
    AE_OFF,
    /** startAutoColor(): the vendor shows the pattern (event 11001 show), measures the wall and reports the result. */
    START,
    /** setAutoColorCalibration(): the stock use the matched colour (libxgimi: select the effect after AC). */
    USE_MATCHED,
    /** setAutoColorDefault(): the stock use the original colour and the card's reset (select the effect before AC). */
    USE_ORIGINAL
}
