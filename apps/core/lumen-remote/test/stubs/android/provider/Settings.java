package android.provider;
public final class Settings { public static final class Secure {
    public static int getInt(android.content.ContentResolver r, String k, int d) { Integer v = lumen.fake.World.secure.get(k); return v == null ? d : v; } }
    public static final class Global {
    public static int getInt(android.content.ContentResolver r, String k, int d) { Integer v = lumen.fake.World.secure.get(k); return v == null ? d : v; } } }
