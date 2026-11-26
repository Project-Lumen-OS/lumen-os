// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.content;

/** Host stub: the application context and its audio manager. */
public class Context {
    public Context getApplicationContext() { return this; }

    public <T> T getSystemService(Class<T> c) { return c.cast(new android.media.AudioManager()); }
}
