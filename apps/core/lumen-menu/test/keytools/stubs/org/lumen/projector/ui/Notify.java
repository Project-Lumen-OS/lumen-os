package org.lumen.projector.ui;

import android.content.Context;

/** Host stub: the notification card's relayout. */
public final class Notify {
    public static int relayouts;

    public static void relayout(Context c, boolean raise) { relayouts++; }
}
