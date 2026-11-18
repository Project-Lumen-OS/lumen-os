// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import org.lumen.stats.Ping;

/** Host check of the updater's statistics adapter: the module's switch default and server, the install id form (the
 * protocol itself: apps/core/lumen-stats/test). */
public final class StatsTest {
    private static int fails;

    private static void eq(String what, Object want, Object got) {
        if (!String.valueOf(want).equals(String.valueOf(got))) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    public static void main(String[] a) throws Exception {
        String id = Stats.newId();
        eq("uuid form", true, id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        eq("default on", Ping.DEFAULT_ON, Stats.DEFAULT_ON);
        eq("the module's servers", true, Stats.URLS == Ping.URLS);
        eq("configured = a URL is set", Ping.URLS.length > 0, Stats.configured());
        eq("no switch in the updater", false, Stats.SWITCH_IN_UPDATER);
        System.out.println(fails == 0 ? "StatsTest: all passed" : "StatsTest: " + fails + " failed");
        if (fails != 0) System.exit(1);
    }
}
