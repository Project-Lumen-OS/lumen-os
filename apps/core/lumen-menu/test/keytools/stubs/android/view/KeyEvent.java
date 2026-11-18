package android.view;

public class KeyEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1;
    public static final int KEYCODE_BACK = 4, KEYCODE_DPAD_UP = 19, KEYCODE_DPAD_DOWN = 20, KEYCODE_DPAD_LEFT = 21,
            KEYCODE_DPAD_RIGHT = 22, KEYCODE_DPAD_CENTER = 23, KEYCODE_ENTER = 66, KEYCODE_MENU = 82,
            KEYCODE_ESCAPE = 111, KEYCODE_NUMPAD_ENTER = 160, KEYCODE_MEDIA_PLAY_PAUSE = 85;
    private final long downTime, eventTime;
    private final int action, code, repeat;

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat) {
        this.downTime = downTime;
        this.eventTime = eventTime;
        this.action = action;
        this.code = code;
        this.repeat = repeat;
    }

    public static boolean isMediaSessionKey(int code) { return code == KEYCODE_MEDIA_PLAY_PAUSE; }

    public final int getKeyCode() { return code; }

    public final int getAction() { return action; }

    public final int getRepeatCount() { return repeat; }

    public final long getDownTime() { return downTime; }

    public final long getEventTime() { return eventTime; }

    public final boolean isCanceled() { return false; }

    @Override public String toString() { return (action == ACTION_DOWN ? "DOWN " : "UP ") + code + (repeat > 0 ? " r" + repeat : ""); }
}
