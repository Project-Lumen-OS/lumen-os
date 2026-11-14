package org.lumen.projector.hal;

/** setPictureMode values of the Play 6 (stock dayao tree); the MStar Game mode 4 is set by the vendor only, never sent. */
public enum PictureMode {
    AI(10), MOVIE(1), SPORT(9), TV(7), CUSTOM(3), OFFICE(25), PERFORMANCE(5);

    public final int wire;

    PictureMode(int v) { wire = v; }
}
