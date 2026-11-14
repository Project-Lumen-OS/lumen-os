package org.lumen.projector.panel;

import android.content.Context;
import android.view.View;

/** Host stub of the game menu's window content (test/gamemenu): the view alone, as without the real-time model. */
final class GmStage {
    private GmStage() {}

    static View wrap(Context c, GmView v) { return v; }
}
