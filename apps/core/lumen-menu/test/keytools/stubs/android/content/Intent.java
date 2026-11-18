package android.content;

public class Intent {
    public static final String ACTION_CLOSE_SYSTEM_DIALOGS = "android.intent.action.CLOSE_SYSTEM_DIALOGS";
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000, FLAG_ACTIVITY_NO_ANIMATION = 0x00010000;
    public final Class<?> target;

    public Intent(Context c, Class<?> target) { this.target = target; }

    public Intent addFlags(int f) { return this; }

    public String getStringExtra(String k) { return null; }
}
