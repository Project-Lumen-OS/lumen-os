package org.lumen.projector.panel;

/** Host stub (test/gamemenu): the crosshair and frame counter settings in memory. */
final class GmOverlays {
    boolean aim;
    int style = 1, colour, x = 960, y = 540, fps;
    boolean moving;

    boolean aimOn() { return aim; }
    int aimStyle() { return style; }
    int aimColour() { return colour; }
    int aimX() { return x; }
    int aimY() { return y; }
    int fpsPos() { return fps; }
    void setAimOn(boolean on) { aim = on; }
    void setAimStyle(int s, int c) { style = s; colour = c; }
    void setAimPos(int nx, int ny) { x = nx; y = ny; }
    void setFps(int pos) { fps = pos; }
    void setMoving(boolean on) { moving = on; }
    void menuChanged() { }
}
