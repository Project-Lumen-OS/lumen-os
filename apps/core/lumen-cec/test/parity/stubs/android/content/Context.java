// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.content;

import android.content.pm.PackageManager;
import android.hardware.hdmi.HdmiControlManager;
import android.media.tv.TvInputManager;
import parity.World;

/** The one application context of the parity world. */
public class Context {
    public static final int MODE_PRIVATE = 0;
    public Context getApplicationContext() { return this; }
    public String getString(int id) { return World.text(id); }
    public String getString(int id, Object... args) { return String.format(World.text(id), args); }
    @SuppressWarnings("unchecked")
    public <T> T getSystemService(Class<T> c) {
        if (c == HdmiControlManager.class) return (T) World.hdmi;
        if (c == TvInputManager.class) return (T) World.tim;
        return null;
    }
    public SharedPreferences getSharedPreferences(String name, int mode) { return World.prefs(name); }
    public ContentResolver getContentResolver() { return World.resolver; }
    public PackageManager getPackageManager() { return World.pm; }
    public void startActivity(Intent i) { World.event("startActivity " + i); }
}
