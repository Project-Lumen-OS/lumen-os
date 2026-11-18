// SPDX-License-Identifier: Apache-2.0
package android.content;

/** Parity probe stand-in. */
public abstract class BroadcastReceiver {
    public abstract void onReceive(Context c, Intent i);
}
