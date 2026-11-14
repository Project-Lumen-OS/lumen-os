package org.lumen.projector.hal;

/** setDlpLumensMode values of the Play 6: the picture style of the Custom picture mode (stock color_style_*). */
public enum ColorTemp {
    STANDARD(0), MOVIE_D65(16), OFFICE(12), CUSTOM(6);

    public final int wire;

    ColorTemp(int v) { wire = v; }
}
