// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.content;

import android.os.PowerManager;

import java.io.File;

/**
 * Parity probe stand-in: the files folder is the probe's temp folder, getSystemService(PowerManager) gives the
 * probe's thermal state (null when THERMAL &lt; 0), the sticky battery broadcast is BATTERY (null = none).
 */
public class Context {
    public static File DIR = new File(".");
    public static int THERMAL = -1;
    public static Intent BATTERY;

    public Context createDeviceProtectedStorageContext() { return this; }
    public File getNoBackupFilesDir() { return DIR; }
    public File getFilesDir() { return DIR; }

    @SuppressWarnings("unchecked")
    public <T> T getSystemService(Class<T> c) {
        if (c == PowerManager.class && THERMAL >= 0) return (T) new PowerManager(THERMAL);
        return null;
    }

    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f) {
        return f != null && Intent.ACTION_BATTERY_CHANGED.equals(f.action) ? BATTERY : null;
    }

    public void sendBroadcast(Intent i) {}
    public void sendBroadcast(Intent i, String perm) {}
    public String getString(int id) { return "#" + id; }
}
