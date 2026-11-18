package android.view;

/** Host stub: the test's fake screen implements it. */
public interface WindowManager {
    void addView(View v, ViewGroup.LayoutParams p);

    void updateViewLayout(View v, ViewGroup.LayoutParams p);

    void removeViewImmediate(View v);

    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int FLAG_NOT_FOCUSABLE = 0x8, FLAG_NOT_TOUCHABLE = 0x10, FLAG_NOT_TOUCH_MODAL = 0x20,
                FLAG_LAYOUT_IN_SCREEN = 0x100, FLAG_LAYOUT_NO_LIMITS = 0x200, FLAG_HARDWARE_ACCELERATED = 0x01000000;
        public static final int TYPE_APPLICATION = 2, TYPE_APPLICATION_OVERLAY = 2038;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS = 3;
        public int type, flags, format, gravity, x, y, windowAnimations, layoutInDisplayCutoutMode;
        private CharSequence title = "";

        public LayoutParams(int w, int h, int type, int flags, int format) {
            super(w, h);
            this.type = type;
            this.flags = flags;
            this.format = format;
        }

        public void setFitInsetsTypes(int t) { }

        public void setTitle(CharSequence t) { title = t; }

        public CharSequence getTitle() { return title; }
    }
}
