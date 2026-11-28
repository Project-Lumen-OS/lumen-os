// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.PowerItem;
import org.lumen.projector.R;
import org.lumen.projector.ui.RoundTile;

/**
 * The round tile of each {@link PowerItem}, its icon and its label, for both power menus: {@link PowerMenu} in the
 * menu's host and the gmpf / Play 6 power key's menu (apps/common/projector power.PowerMenu). Turn off screen and the
 * sleep timer carry the labels of their menu rows.
 */
public final class PowerTiles {
    private PowerTiles() { }

    public static int label(PowerItem item) {
        switch (item) {
            case SCREEN_OFF: return MenuLabels.title(FeatureId.LAMP_OFF);
            case SLEEP: return R.string.lumen_m_power_sleep;
            case POWER_OFF: return R.string.lumen_m_power_off;
            case RESTART: return R.string.lumen_m_power_restart;
            default: return MenuLabels.title(FeatureId.SETTINGS_SLEEP_TIMER);
        }
    }

    public static int icon(PowerItem item) {
        switch (item) {
            case SCREEN_OFF: return RoundTile.ICON_SCREEN_OFF;
            case SLEEP: return RoundTile.ICON_SLEEP;
            case POWER_OFF: return RoundTile.ICON_POWER;
            case RESTART: return RoundTile.ICON_RESTART;
            default: return RoundTile.ICON_TIMER;
        }
    }
}
