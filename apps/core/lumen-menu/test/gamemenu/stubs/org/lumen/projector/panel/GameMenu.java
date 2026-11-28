// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import java.util.function.Consumer;

/** Host stub (test/gamemenu): the GameMenu members GmController uses, same shapes as the real class. */
final class GameMenu {
    static final String TAG = "Z9xGameMenu";
    static GmModel.Caps CAPS = new GmModel.Caps();

    private GameMenu() {}

    static final class Snap {
        int opt = GmModel.BAD;
        boolean running;
        int width, height, frameCenti;
        int hdr = -1;
        Boolean allm;
        int sr = GmModel.BAD;
        int ar = GmModel.BAD;
        Integer vrr, pic, dark, snd;
        Integer cs, hdrMode;
        Integer fs;
    }

    interface Io {
        void read(int port, Consumer<Snap> cb);
        void apply(String key, int value, int port, Snap now, ApplyResult cb);
        void fullFrame(Consumer<Boolean> cb);
        default void shift(int op, int port, int top, Consumer<int[]> cb) { cb.accept(null); }
    }

    interface ApplyResult {
        void done(int rawBack, Snap after);
    }
}
