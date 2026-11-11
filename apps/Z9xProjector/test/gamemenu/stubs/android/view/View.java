package android.view;

import android.content.Context;

/** Host stub (test/gamemenu): counts redraw requests. */
public class View {
    public int invalidations;

    public View(Context c) { }

    public void invalidate() { invalidations++; }
}
