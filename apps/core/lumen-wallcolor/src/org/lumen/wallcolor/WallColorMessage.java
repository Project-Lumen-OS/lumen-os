// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.wallcolor;

/**
 * What the flow tells the user: the stock toasts of a failed result (stock WallColorCore.core, code to text) and the one
 * case the stock does not show, a start the vendor refused.
 */
public enum WallColorMessage {
    /** code 1, stock camera_initialization_failed */
    CAMERA(1),
    /** code 2, stock wall_color_initialization_failed */
    START_FAILED(2),
    /** code 3, stock wall_color_distance_failed */
    DISTANCE(3),
    /** code 4, stock ambient_light_stability_failed */
    LIGHT_CHANGED(4),
    /** code 5, stock wall_color_custom_not */
    CUSTOM_COLOUR(5),
    /** code 6, stock wall_color_performance_not */
    PERFORMANCE(6),
    /** code 7, stock close_brightness_adjustment */
    AUTO_BRIGHTNESS(7),
    /** code 8, stock wall_color_3d_mode_not */
    MODE_3D(8),
    /** code 12 (AC_ERRORN_MUTEX05), stock not_support_reset_under_huge_screen */
    BIG_SCREEN(12),
    /** any other code, stock wall_color_filt_tip */
    OUT_OF_RANGE(-1),
    /** startAutoColor() answered false or failed: no stock text, Lumen names it */
    NOT_STARTED(-2);

    public final int code;

    WallColorMessage(int code) {
        this.code = code;
    }

    /** The text of a failed result's code (stock: unknown codes say the wall colour is out of range). */
    public static WallColorMessage failure(int code) {
        for (WallColorMessage m : values()) if (m.code == code && code > 0) return m;
        return OUT_OF_RANGE;
    }
}
