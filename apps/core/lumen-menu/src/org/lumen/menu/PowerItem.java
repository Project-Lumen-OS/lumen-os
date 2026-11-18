// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * The items of the stock power menu (com.xgimi.shutdown res/raw/systemui_9681.json of the X50 family, sorted by
 * PowerViewModel levels): Turn off screen, Power off, Restart, Sleep timer. An item backed by a menu feature is there
 * only where the menu offers that feature; Power off and Restart are on every projector.
 */
public enum PowerItem {
    /** 关屏: the menu's lamp.off. */
    SCREEN_OFF(FeatureId.LAMP_OFF),
    /** 关机: the system power key (PowerUtils.shutDownsendKeyCode), the projector's standby. */
    POWER_OFF(null),
    /** 重启: a restart (PowerUtils.reboot). */
    RESTART(null),
    /** 定时关机: the menu's sleep timer page. */
    SLEEP_TIMER(FeatureId.SETTINGS_SLEEP_TIMER);

    /** The menu feature the item needs, null when every projector has it. */
    public final FeatureId feature;

    PowerItem(FeatureId feature) {
        this.feature = feature;
    }

    /** The items in the stock order, each feature-backed one only where {@code offered} has its feature. */
    public static List<PowerItem> offered(Predicate<FeatureId> offered) {
        List<PowerItem> out = new ArrayList<>();
        for (PowerItem i : values()) if (i.feature == null || offered.test(i.feature)) out.add(i);
        return Collections.unmodifiableList(out);
    }
}
