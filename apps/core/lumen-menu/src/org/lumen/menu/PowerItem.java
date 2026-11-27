// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * The items of the power menu of every projector, in this order: the stock ones (com.xgimi.shutdown res/raw
 * systemui_*.json, the same four on every family: 关屏 Turn off screen, 关机 Power off, 重启 Restart, 定时关机 Sleep timer)
 * plus Sleep (stock 关机 only sleeps; Lumen's Power off really powers off, decision, so the sleep keeps its own
 * item). An item backed by a menu feature is there only where the menu offers that feature; Sleep, Power off and Restart
 * are on every projector.
 */
public enum PowerItem {
    /** 关屏: the menu's lamp.off. */
    SCREEN_OFF(FeatureId.LAMP_OFF),
    /** Sleep: what stock 关机 does (the power key, PowerUtils.shutDownsendKeyCode = the projector's standby). */
    SLEEP(null),
    /** 关机: Android's real power off (panel Halt, decision), the item the {@link PowerCountdown} runs on. */
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
