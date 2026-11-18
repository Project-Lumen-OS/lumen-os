package android.view;

public final class Gravity {
    public static final int LEFT = 3, RIGHT = 5, TOP = 48, BOTTOM = 80, START = 0x00800003, END = 0x00800005;
    public static final int CENTER_HORIZONTAL = 1, CENTER_VERTICAL = 16, CENTER = 17;
    public static final int HORIZONTAL_GRAVITY_MASK = 7, VERTICAL_GRAVITY_MASK = 112;

    public static int getAbsoluteGravity(int g, int dir) { return g & ~0x00800000; }
}
