package android.view;

/** Host stub (test/gamemenu): the key codes and fields GmController reads (values of android.view.KeyEvent). */
public class KeyEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1;
    public static final int KEYCODE_BACK = 4, KEYCODE_DPAD_UP = 19, KEYCODE_DPAD_DOWN = 20, KEYCODE_DPAD_LEFT = 21,
            KEYCODE_DPAD_RIGHT = 22, KEYCODE_DPAD_CENTER = 23, KEYCODE_ENTER = 66, KEYCODE_BUTTON_A = 96,
            KEYCODE_NUMPAD_ENTER = 160;
    private final int action, code, repeat;

    public KeyEvent(int action, int code) { this(action, code, 0); }

    public KeyEvent(int action, int code, int repeat) { this.action = action; this.code = code; this.repeat = repeat; }

    public final int getAction() { return action; }
    public final int getKeyCode() { return code; }
    public final int getRepeatCount() { return repeat; }
}
