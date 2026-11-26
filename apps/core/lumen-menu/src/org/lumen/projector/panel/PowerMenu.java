// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;

import org.lumen.menu.FeatureId;
import org.lumen.menu.PowerItem;
import org.lumen.projector.R;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.PagedPanel;

/**
 * The stock power menu (com.xgimi.shutdown) of a projector whose app receives the remote power key (a short press,
 * {@link org.lumen.menu.PowerPress}), built once for every such projector: the {@link PowerItem}s its menu offers, in
 * the stock order, on one page in the menu's own window host (so a device's gate, e.g. the X50 eye-protection cover,
 * holds for it too). Turn off screen and the sleep timer go the way of the menu's own rows (lamp.off, the device's sleep
 * timer page); Power off and Restart are the device's {@link Power}. Main thread.
 */
public final class PowerMenu {
    /** The device side of the two items no menu feature has. */
    public interface Power {
        /** Stock 关机: the system power key, the projector's standby. */
        void powerOff();

        /** Stock 重启: a restart. */
        void restart();
    }

    private final Context app;
    private final MenuController menu;
    private final Power power;
    private PagedPanel panel;

    public PowerMenu(Context c, MenuController menu, Power power) {
        this.app = c.getApplicationContext();
        this.menu = menu;
        this.power = power;
    }

    /** Opens it in place of the quick menu; false when the host refused it. Nothing new while it is on screen. */
    public boolean show() {
        if (isShowing()) return true;
        Page page = new Page("");
        for (PowerItem item : PowerItem.offered(menu::offers)) {
            page.add(new NavRow(app, app.getString(label(item)), () -> pick(item)).setChevron(item == PowerItem.SLEEP_TIMER));
        }
        panel = new PagedPanel(page);
        // the host replaces the quick menu when it is open
        return menu.host().show(panel);
    }

    public boolean isShowing() {
        return panel != null && panel.isShowing() && menu.host().current() == panel;
    }

    static int label(PowerItem item) {
        switch (item) {
            case SCREEN_OFF: return MenuLabels.title(FeatureId.LAMP_OFF);
            case POWER_OFF: return R.string.lumen_m_power_off;
            case RESTART: return R.string.lumen_m_power_restart;
            default: return MenuLabels.title(FeatureId.SETTINGS_SLEEP_TIMER);
        }
    }

    private void pick(PowerItem item) {
        switch (item) {
            case SCREEN_OFF:
                closeThen(() -> menu.act(FeatureId.LAMP_OFF));
                return;
            case POWER_OFF:
                closeThen(power::powerOff);
                return;
            case RESTART:
                closeThen(power::restart);
                return;
            default:
                Page timer = menu.devicePage(FeatureId.SETTINGS_SLEEP_TIMER);
                if (timer == null) {
                    closeThen(() -> menu.act(FeatureId.SETTINGS_SLEEP_TIMER));
                    return;
                }
                timer.setInitialFocus(-1);
                panel.push(timer);
        }
    }

    /** Closes the power menu and runs {@code r} once its exit animation ended (MenuController.closeThen). */
    private void closeThen(Runnable r) {
        menu.closeThen(panel, r);
    }
}
