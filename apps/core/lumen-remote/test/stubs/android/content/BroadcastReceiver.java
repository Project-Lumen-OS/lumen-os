// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.content;
public abstract class BroadcastReceiver {
    boolean ordered, aborted;
    public abstract void onReceive(Context c, Intent i);
    public final boolean isOrderedBroadcast() { return ordered; }
    public final void abortBroadcast() { aborted = true; }
    /** test hook */
    public final boolean deliver(Context c, Intent i, boolean ordered) { this.ordered = ordered; aborted = false; onReceive(c, i); return aborted; }
}
