package android.text;

import android.graphics.Canvas;

public abstract class Layout {
    public enum Alignment { ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER }

    public abstract int getHeight();

    public abstract void draw(Canvas c);
}
