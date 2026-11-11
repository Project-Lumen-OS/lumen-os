package org.lumen.projector.panel;

import android.content.Context;
import android.view.View;

/**
 * Host stub of the game menu view (test/gamemenu): every method GmController calls, recorded. A redraw is any
 * invalidate() (View stub) or a call that the real GmView ends with invalidate().
 */
final class GmView extends View {
    final GmController ctl;
    int popupRows;
    String focus;

    GmView(Context c, GmController ctl) { super(c); this.ctl = ctl; }

    void enter() { }
    void exit(Runnable end) { if (end != null) end.run(); }
    void release() { }
    void onFocusMoved(String from, String to) { focus = to; invalidate(); }
    void syncFocus(String id) { focus = id; }
    void onPopup(boolean open) { invalidate(); }
    void onPopupRow(int from, int to) { popupRows++; invalidate(); }
}
