// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/**
 * Basic picture items, 0..100 each, window 0 (TV 5..12): GmpfClient.getPq / setPq. Each client maps an item to its
 * own codes (Z9X IGmpf HIDL, HB AIDL TV calls).
 */
public enum PqItem {
    BRIGHTNESS, CONTRAST, SATURATION, SHARPNESS
}
