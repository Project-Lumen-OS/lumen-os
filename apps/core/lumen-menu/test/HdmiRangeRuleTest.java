// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/** HdmiRangeRule: Auto names the range in use only when it is known and Auto is chosen. java org.lumen.menu.HdmiRangeRuleTest */
public final class HdmiRangeRuleTest {
    private static int pass, fail;

    private static void ok(String what, boolean cond) {
        if (cond) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    private static String shown(String stored, String inUse) {
        MenuState.Builder b = MenuState.builder().put(FeatureId.HDMI_RANGE, stored);
        return HdmiRangeRule.applied(b, stored, inUse).build().shownAs(FeatureId.HDMI_RANGE, HdmiRangeRule.AUTO);
    }

    public static void main(String[] a) {
        ok("Auto with Limited in use shows Auto (Limited)", "auto_limited".equals(shown("auto", "limited")));
        ok("Auto with Full in use shows Auto (Full)", "auto_full".equals(shown("auto", "full")));
        ok("Auto with the range unknown stays plain Auto", shown("auto", null) == null);
        ok("an odd range value stays plain Auto", shown("auto", "auto") == null && shown("auto", "") == null);
        ok("Full chosen: the Auto option keeps its own label", shown("full", "limited") == null);
        ok("Limited chosen: the Auto option keeps its own label", shown("limited", "full") == null);
        MenuState st = HdmiRangeRule.applied(MenuState.builder().put(FeatureId.HDMI_RANGE, "auto"), "auto", "full").build();
        ok("the written value stays the stored choice", "auto".equals(st.get(FeatureId.HDMI_RANGE)));
        ok("only the Auto option is renamed", st.shownAs(FeatureId.HDMI_RANGE, "full") == null
                && st.shownAs(FeatureId.HDMI_EDID, "auto") == null);
        MenuState live = st.with(MenuState.builder().put(FeatureId.HDMI_RANGE, "auto").build());
        ok("a live update keeps the shown label", "auto_full".equals(live.shownAs(FeatureId.HDMI_RANGE, "auto")));
        System.out.println("HdmiRangeRuleTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
