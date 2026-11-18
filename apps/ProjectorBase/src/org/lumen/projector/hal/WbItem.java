// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** 2-point white balance (TV 17..28): gain and offset per colour, 0..100 sent as shown, window 0. The items of GmpfClient.getPq / setPq (getWb / setWb); each client maps them to its own codes. */
public enum WbItem {
    RED_GAIN, GREEN_GAIN, BLUE_GAIN, RED_OFFSET, GREEN_OFFSET, BLUE_OFFSET
}
