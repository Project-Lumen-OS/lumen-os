package android.content.pm;
public class PackageManager {
    public static final int PERMISSION_GRANTED = 0, PERMISSION_DENIED = -1;
    public void grantRuntimePermission(String pkg, String perm, android.os.UserHandle u) { lumen.fake.World.selfGrant(perm); }
}
