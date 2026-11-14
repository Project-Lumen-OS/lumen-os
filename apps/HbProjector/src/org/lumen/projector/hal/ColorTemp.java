// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/**
 * Colour temperature = DLP lumens mode. Z9X HB: the stock list of the dapengse tree, node
 * image_mode_4_color_temp_dapengse (newsettings res/raw/picture.json map {"0": led_standard_mode, "16": led_movie_mode},
 * sent by BrightnessViewModel.postArrowAbout -> BrightnessRepository.choiceLedMode -> setDlpLumensMode). The Z9X
 * values 16 / 17 are not this list.
 *
 * <p>The HB values of GmpfClient (each device app has its own value list of this name).
 */
public enum ColorTemp {
    STANDARD(0), MOVIE(16);
    public final int wire;
    ColorTemp(int v) { wire = v; }
}
