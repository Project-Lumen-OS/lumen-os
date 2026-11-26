// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * The HDMI colour range row of every model: Auto, Full, Limited (stock order and values 0, 1, 2). While Auto is chosen,
 * the Auto option names the range the projector applies now, "Auto (Limited)" or "Auto (Full)", when the model's
 * adapter reads it, and stays plain Auto otherwise. The written value is always the stored choice.
 *
 * <p>No model reads the range in use yet: the stock HAL returns only the stored choice (Z9X and Play 6 IGmpf 634, HB
 * and the X50 family GmTvManager 64), the vendor keeps the Auto decision inside Device_HdmiInput::setHdmiRange and no
 * stock app shows more (devices/horse_popular/stock analysis D.1, devices/goat_elegant/stock analysis
 * D.1). An adapter that gains a reader calls {@link #applied} in its read.
 */
public final class HdmiRangeRule {
    public static final String AUTO = "auto", FULL = "full", LIMITED = "limited";

    private HdmiRangeRule() { }

    /** The token whose label the Auto option shows for the range in use ("full" or "limited"), null = plain Auto. */
    public static String autoShown(String inUse) {
        return FULL.equals(inUse) || LIMITED.equals(inUse) ? AUTO + "_" + inUse : null;
    }

    /** The Auto option of {@code b} names {@code inUse} when the stored choice is Auto and the range is known. */
    public static MenuState.Builder applied(MenuState.Builder b, String stored, String inUse) {
        String as = AUTO.equals(stored) ? autoShown(inUse) : null;
        return as == null ? b : b.shownAs(FeatureId.HDMI_RANGE, AUTO, as);
    }
}
