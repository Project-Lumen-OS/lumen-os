// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;

/**
 * Z9X (horse_popular): the owner of the shared projector menu (apps/core/lumen-menu). One
 * MenuController (quick panel D) with the Z9X adapter (Z9xFeatures) and pages (Z9xMenuPages), built on first use and
 * again after a locale change. Main thread only. The game menu is installed by App (GameMenu.install with
 * Z9xGameIo) and opened by the gear long press in KeyReceiver, as in 1.0.10.
 */
final class Z9xMenu {
    private static MenuController sMenu;

    private Z9xMenu() {}

    static MenuController get(Context c) {
        if (sMenu == null) {
            Context app = c.getApplicationContext();
            Z9xFeatures f = new Z9xFeatures(app);
            sMenu = new MenuController(app, f, new Z9xMenuPages(app, f));
        }
        return sMenu;
    }

    static MenuController built() { return sMenu; }

    static void reset() { sMenu = null; }
}
