// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector;

import android.content.Context;
import android.os.Handler;
import parity.World;

/** The app shell the page uses: the main thread queue, toasts, leaving the screen saver. */
public final class Ui {
    private static final Handler MAIN = new Handler("main");
    private Ui() { }
    public static Handler main() { return MAIN; }
    public static void toast(Context c, int res) { World.event("toast \"" + c.getString(res) + "\""); }
    public static void wakeFromDream(Context c) { World.event("wakeFromDream"); }
}
