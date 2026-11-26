// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.content;

import android.content.res.Resources;

/** Host stub (test/keytools): one app context, its system services set by the test. */
public class Context {
    public static final int RECEIVER_EXPORTED = 2;
    public static final String POWER_SERVICE = "power";
    public static final java.util.Map<Class<?>, Object> SERVICES = new java.util.HashMap<>();
    public static final java.util.List<Intent> STARTED = new java.util.ArrayList<>();

    public Context getApplicationContext() { return this; }

    @SuppressWarnings("unchecked")
    public <T> T getSystemService(Class<T> c) { return (T) SERVICES.get(c); }

    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f, int flags) { return null; }

    public void startActivity(Intent i) { STARTED.add(i); }

    public Resources getResources() { return new Resources(); }

    public String getString(int res) { return "s" + res; }

    /** By name (ActivityPanelHost asks the power service this way): none here, the screen reads as off. */
    public Object getSystemService(String name) { return null; }

    public String getPackageName() { return "org.lumen.test"; }
}
