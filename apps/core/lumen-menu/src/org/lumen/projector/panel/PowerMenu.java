// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.util.List;

import org.lumen.menu.FeatureId;
import org.lumen.menu.PowerItem;
import org.lumen.projector.R;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.PagedPanel;
import org.lumen.projector.ui.RoundTile;
import org.lumen.projector.ui.RoundTilePanel;
import org.lumen.projector.ui.Theme;

/**
 * The stock power menu (com.xgimi.shutdown) of a projector whose app receives the remote power key (a short press,
 * {@link org.lumen.menu.PowerPress}), built once for every such projector: the {@link PowerItem}s its menu offers, in
 * the stock order, as the round power menu of every Lumen projector ({@link RoundTilePanel}: round tiles centred over
 * the dimmed picture, the Z9X / HB look) in the menu's own window host (so a device's gate, e.g. the X50
 * eye-protection cover, holds for it too). Turn off screen and the sleep timer go the way of the menu's own rows
 * (lamp.off, the device's sleep timer page, shown as a centred card; Back returns to the tiles); Power off and Restart
 * are the device's {@link Power}. Main thread.
 */
public final class PowerMenu {
    /** The device side of the two items no menu feature has. */
    public interface Power {
        /** Power off (stock 关机): Android's real power off ({@link Halt#powerOff}), never the power key's sleep. */
        void powerOff();

        /** Stock 重启: a restart ({@link Halt#restart}). */
        void restart();
    }

    private static final String TAG = "PowerMenu";

    private final Context app;
    private final MenuController menu;
    private final Power power;
    private Tiles panel;

    public PowerMenu(Context c, MenuController menu, Power power) {
        this.app = c.getApplicationContext();
        this.menu = menu;
        this.power = power;
    }

    /** Opens it in place of the quick menu; false when the host refused it. Nothing new while it is on screen. */
    public boolean show() {
        if (isShowing()) return true;
        panel = new Tiles(PowerItem.offered(menu::offers));
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

    static int icon(PowerItem item) {
        switch (item) {
            case SCREEN_OFF: return RoundTile.ICON_SCREEN_OFF;
            case POWER_OFF: return RoundTile.ICON_POWER;
            case RESTART: return RoundTile.ICON_RESTART;
            default: return RoundTile.ICON_TIMER;
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
                menu.host().show(new TimerCard(timer));
        }
    }

    /** Closes the power menu and runs {@code r} once its exit animation ended (MenuController.closeThen). */
    private void closeThen(Runnable r) {
        menu.closeThen(panel, r);
    }

    /** The round tiles of the offered items, in the stock order. */
    private final class Tiles extends RoundTilePanel {
        private final List<PowerItem> items;

        Tiles(List<PowerItem> items) {
            this.items = items;
        }

        @Override protected String tag() { return TAG; }

        @Override protected void onAddTiles(Context ctx) {
            for (PowerItem item : items) addTile(ctx, icon(item), ctx.getString(label(item)), guarded(() -> pick(item)));
        }
    }

    /** The device's sleep timer page as a centred card over the picture; Back goes back to the tiles. */
    private final class TimerCard extends PagedPanel {
        TimerCard(Page timer) {
            super(timer);
        }

        @Override protected WindowManager.LayoutParams onCreateLayoutParams(Context ctx) {
            WindowManager.LayoutParams lp = super.onCreateLayoutParams(ctx);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.width = Theme.px(ctx, 640);
            lp.gravity = Gravity.CENTER;
            return lp;
        }

        @Override protected boolean onBack() {
            if (super.onBack()) return true;
            menu.host().show(panel = new Tiles(panel.items));
            return true;
        }
    }
}
