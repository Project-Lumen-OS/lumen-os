package android.view;

import android.content.Context;
import android.graphics.Canvas;

/** Host stub of a View: measure, layout and draw by hand; counts the redraw and relayout requests. */
public class View {
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    private final Context context;
    private int visibility = VISIBLE, width, height, measuredW, measuredH;
    public int invalidates, layoutRequests;

    public View(Context c) { context = c; }

    public Context getContext() { return context; }

    public void setFocusable(boolean focusable) {}

    public void invalidate() { invalidates++; }

    public void requestLayout() { layoutRequests++; }

    public void setVisibility(int v) { visibility = v; }

    public int getVisibility() { return visibility; }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public int getMeasuredWidth() { return measuredW; }

    public int getMeasuredHeight() { return measuredH; }

    protected void onMeasure(int wSpec, int hSpec) {}

    protected final void setMeasuredDimension(int w, int h) { measuredW = w; measuredH = h; }

    public final void measure(int wSpec, int hSpec) { onMeasure(wSpec, hSpec); }

    public final void layout(int l, int t, int r, int b) { width = r - l; height = b - t; }

    protected void onDraw(Canvas c) {}

    public void draw(Canvas c) { onDraw(c); }

    public static class MeasureSpec {
        public static final int UNSPECIFIED = 0, EXACTLY = 1 << 30, AT_MOST = 2 << 30;

        public static int makeMeasureSpec(int size, int mode) { return (size & 0x3FFFFFFF) | mode; }

        public static int getSize(int spec) { return spec & 0x3FFFFFFF; }

        public static int getMode(int spec) { return spec & 0xC0000000; }
    }
}
