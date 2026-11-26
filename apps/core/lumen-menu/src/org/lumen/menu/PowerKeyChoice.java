// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * What a short press of the remote power key does (All settings, General, "Short press of the power key"); holding the
 * key does the other one. Stock on every XGIMI family (vendor/etc/xgimi_action_config.xml keycode 2100 KPPOWER,
 * pressBehaver 0 -> com.xgimi.systemui power menu, held to repeat 20 = sleep in kPPowerPress) is {@link #POWER_MENU}:
 * the default of a profile without keys.power_short ({@link MenuProfile#powerShort}). One rule for every power key path
 * ({@link PowerPress}, the gmpf PowerKey and the Play 6 PowerKey).
 */
public enum PowerKeyChoice {
    /** Short press: the power menu, held: sleep (stock). */
    POWER_MENU("power_menu"),
    /** Short press: sleep, held: the power menu (the Lumen rule up to 2026-11-26). */
    SLEEP("sleep");

    /** The token of the profile (keys.power_short), of the setting's options and of the stored value. */
    public final String token;

    PowerKeyChoice(String token) {
        this.token = token;
    }

    /** The choice of {@code token}, null for an unknown or missing one. */
    public static PowerKeyChoice of(String token) {
        for (PowerKeyChoice c : values()) if (c.token.equals(token)) return c;
        return null;
    }

    /** What a short press does. */
    public PowerAction shortPress() {
        return this == POWER_MENU ? PowerAction.MENU : PowerAction.SLEEP;
    }

    /** What holding the key does: the other action. */
    public PowerAction hold() {
        return this == POWER_MENU ? PowerAction.SLEEP : PowerAction.MENU;
    }
}
