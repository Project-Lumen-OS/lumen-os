package android.content;
/** Host stub of the app context: everything is answered by lumen.fake.World. */
public class Context {
    public static final int RECEIVER_EXPORTED = 2, RECEIVER_NOT_EXPORTED = 4, MODE_PRIVATE = 0;
    public Context getApplicationContext() { return this; }
    public <T> T getSystemService(Class<T> c) { return lumen.fake.World.service(c); }
    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f, String perm, android.os.Handler h, int flags) {
        lumen.fake.World.register(r, f); return null; }
    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f) { lumen.fake.World.register(r, f); return null; }
    public void unregisterReceiver(BroadcastReceiver r) { lumen.fake.World.unregister(r); }
    public SharedPreferences getSharedPreferences(String n, int m) { return lumen.fake.World.prefs(n); }
    public ContentResolver getContentResolver() { return new ContentResolver(); }
    public int checkSelfPermission(String p) { return lumen.fake.World.granted(p) ? 0 : -1; }
    public android.content.pm.PackageManager getPackageManager() { return new android.content.pm.PackageManager(); }
    public String getPackageName() { return "org.lumen.test"; }
    public String getString(int id) { return lumen.fake.World.string(id); }
    public String getString(int id, Object... a) { return lumen.fake.World.string(id); }
    public void sendBroadcast(Intent i) { lumen.fake.World.broadcast(i); }
    public void sendBroadcast(Intent i, String perm) { sendBroadcast(i); }
    public ComponentName startService(Intent i) { return null; }
}
