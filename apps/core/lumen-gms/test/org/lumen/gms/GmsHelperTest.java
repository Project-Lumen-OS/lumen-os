// SPDX-License-Identifier: Apache-2.0
package org.lumen.gms;

import android.os.SystemProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Host checks of the Google sign-in helper switch (test/run.sh). */
public final class GmsHelperTest {
    private static int fails, checks;

    private static void eq(String what, Object want, Object got) {
        checks++;
        if (!String.valueOf(want).equals(String.valueOf(got))) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    private static void reset() {
        SystemProperties.VALUES.clear();
        SystemProperties.WRITES.clear();
    }

    public static void main(String[] a) throws Exception {
        // row: hidden without Google services, whatever the switch says
        eq("no add-on", GmsHelperRow.HIDDEN, GmsHelperPolicy.row("", "", "1"));
        eq("add-on bad", GmsHelperRow.HIDDEN, GmsHelperPolicy.row("bad:size", "none", "1"));
        eq("play6 no count", GmsHelperRow.HIDDEN, GmsHelperPolicy.row("", "mounted:", "1"));
        eq("play6 ok is not its value", GmsHelperRow.HIDDEN, GmsHelperPolicy.row("", "ok", "1"));
        eq("z9x add-on, on", GmsHelperRow.ON, GmsHelperPolicy.row("ok", "", "1"));
        eq("google edition, on", GmsHelperRow.ON, GmsHelperPolicy.row("skip", "", "1"));
        eq("play6 add-on, on", GmsHelperRow.ON, GmsHelperPolicy.row("", "mounted:5", "1"));
        eq("unset = on", GmsHelperRow.ON, GmsHelperPolicy.row("ok", "", ""));
        eq("0 = off", GmsHelperRow.OFF, GmsHelperPolicy.row("ok", "", "0"));
        eq("junk = on", GmsHelperRow.ON, GmsHelperPolicy.row("ok", "", "true"));

        // data default: only while the user never chose, only when it changes something
        eq("default on, fresh", null, GmsHelperPolicy.dataDefault("", "", true));
        eq("default off, fresh", "0", GmsHelperPolicy.dataDefault("", "", false));
        eq("default on, already on", null, GmsHelperPolicy.dataDefault("", "1", true));
        eq("default on, was off", "1", GmsHelperPolicy.dataDefault("", "0", true));
        eq("user chose off", null, GmsHelperPolicy.dataDefault("1", "0", true));
        eq("user chose on", null, GmsHelperPolicy.dataDefault("1", "1", false));

        // the switch on the unit: the choice is remembered BEFORE the value
        reset();
        GmsHelperSwitch.setByUser(true);
        eq("user on writes", "[persist.sys.lumen.gms_compat.user=1, persist.sys.lumen.gms_compat=1]",
                SystemProperties.WRITES);
        GmsHelperSwitch.applyDataDefault(false);
        eq("data default ignored after a choice", "1", SystemProperties.get(GmsHelperPolicy.PROP, ""));
        reset();
        SystemProperties.VALUES.put(GmsHelperPolicy.ADDON, "ok");
        eq("fresh unit row", GmsHelperRow.ON, GmsHelperSwitch.row());
        GmsHelperSwitch.applyDataDefault(false);
        eq("data default off", GmsHelperRow.OFF, GmsHelperSwitch.row());
        eq("data default does not mark a choice", "", SystemProperties.get(GmsHelperPolicy.PROP_USER, ""));
        GmsHelperSwitch.setByUser(true);
        eq("user on", GmsHelperRow.ON, GmsHelperSwitch.row());

        // the names the init script, the init helper and the library read are the ones written here
        String rc = new String(Files.readAllBytes(Paths.get(a[0], "devices/_image/gmsprops/lumen_gmsprops.rc")),
                StandardCharsets.UTF_8);
        String hook = new String(Files.readAllBytes(Paths.get(a[0], "lineage/gmsprops/src/gmsprops_hook.c")),
                StandardCharsets.UTF_8);
        String init = new String(Files.readAllBytes(Paths.get(a[0], "lineage/gmsprops/src/gmsprops_init.c")),
                StandardCharsets.UTF_8);
        eq("rc reads PROP", true, rc.contains("on property:" + GmsHelperPolicy.PROP + "=*\n"));
        eq("rc reads ADDON", true, rc.contains("on property:" + GmsHelperPolicy.ADDON + "=*\n"));
        eq("rc reads ADDON_PLAY6", true, rc.contains("on property:" + GmsHelperPolicy.ADDON_PLAY6 + "=*\n"));
        eq("library reads PROP", true, hook.contains("#define SWITCH \"" + GmsHelperPolicy.PROP + "\""));
        eq("helper reads PROP", true, init.contains("#define SWITCH \"" + GmsHelperPolicy.PROP + "\""));
        eq("helper reads ADDON", true, init.contains("\"" + GmsHelperPolicy.ADDON + "\""));
        eq("helper reads ADDON_PLAY6", true, init.contains("\"" + GmsHelperPolicy.ADDON_PLAY6 + "\""));

        System.out.println(checks + " checks, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
