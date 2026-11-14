// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/**
 * ResetPictureToDefault triples used by stock (FS 3.4/3.5); no other combination is sent. Z9X HB adds the three
 * reset buttons of the dapengse tree (newsettings PictureViewModel.postAction): two_point_white_balance_reset_btn
 * (0, 0, 458752) :3283, eleven_point_white_balance_reset_btn (0, 0, 393216) :3558,
 * image_mode_custom_color_correction_reset (0, 0, 851968) :3544.
 *
 * <p>The HB values of Gmpf2Client (each device app has its own value list of this name).
 */
public enum PictureReset {
    BASIC(0, 0, 1), PROFESSIONAL(0, 0, 2), ALL(0, -8, 0),
    WB_2_POINT(0, 0, 458752), WB_11_POINT(0, 0, 393216), COLOR_CORRECTION(0, 0, 851968);
    final int a, b, c;
    PictureReset(int a, int b, int c) { this.a = a; this.b = b; this.c = c; }
}
