// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.content;

import android.media.AudioManager;

/** Host stub. */
public class Context {
    public final AudioManager audio = new AudioManager();

    public Context getApplicationContext() { return this; }

    @SuppressWarnings("unchecked")
    public <T> T getSystemService(Class<T> c) { return c == AudioManager.class ? (T) audio : null; }
}
