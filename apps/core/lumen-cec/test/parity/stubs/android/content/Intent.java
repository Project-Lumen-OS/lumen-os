package android.content;

import android.net.Uri;

public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    private String action, pkg, cls;
    private Uri data;
    private int flags;
    public Intent() { }
    public Intent(String action, Uri data) { this.action = action; this.data = data; }
    public Intent setClassName(String p, String c) { pkg = p; cls = c; return this; }
    public Intent addFlags(int f) { flags |= f; return this; }
    @Override public String toString() {
        return "{act=" + action + " dat=" + data + " cmp=" + pkg + "/" + cls + " flg=0x" + Integer.toHexString(flags) + "}";
    }
}
