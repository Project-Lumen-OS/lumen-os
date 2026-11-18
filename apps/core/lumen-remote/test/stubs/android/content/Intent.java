package android.content;
public class Intent {
    public static final String ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON";
    public static final String ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF";
    public static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
    public static final int FLAG_RECEIVER_REGISTERED_ONLY = 0x40000000;
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    private String action, pkg;
    private final android.os.Bundle extras = new android.os.Bundle();
    private final java.util.Map<String, Object> objs = new java.util.HashMap<>();
    public Intent() {}
    public Intent(String a) { action = a; }
    public Intent(Context c, Class<?> k) {}
    public String getAction() { return action; }
    public Intent setAction(String a) { action = a; return this; }
    public Intent setPackage(String p) { pkg = p; return this; }
    public String getPackage() { return pkg; }
    public Intent addFlags(int f) { return this; }
    public Intent setFlags(int f) { return this; }
    public Intent putExtra(String k, int v) { extras.putInt(k, v); return this; }
    public Intent putExtra(String k, boolean v) { extras.putBoolean(k, v); return this; }
    public Intent putExtra(String k, String v) { extras.putString(k, v); return this; }
    public Intent putExtra(String k, Object v) { objs.put(k, v); return this; }
    public Intent putExtras(android.os.Bundle b) { for (String k : b.keySet()) { Object o = b.get(k);
        if (o instanceof Integer) extras.putInt(k, (Integer) o); else if (o instanceof Boolean) extras.putBoolean(k, (Boolean) o); else extras.putString(k, String.valueOf(o)); } return this; }
    public android.os.Bundle getExtras() { return extras; }
    public int getIntExtra(String k, int d) { return extras.getInt(k, d); }
    public boolean getBooleanExtra(String k, boolean d) { return extras.getBoolean(k, d); }
    public String getStringExtra(String k) { return extras.getString(k); }
    @SuppressWarnings("unchecked") public <T> T getParcelableExtra(String k, Class<T> c) { return (T) objs.get(k); }
    @SuppressWarnings("unchecked") public <T> T getParcelableExtra(String k) { return (T) objs.get(k); }
}
