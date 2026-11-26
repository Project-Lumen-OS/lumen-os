// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One XGIMI set per model in the whole tree: every place that names the list update_engine copies (the images'
 * ro.product.ab_ota_partitions, devices/&lt;c&gt;/ota.conf, the image checks) carries exactly the module's set of
 * that model, and the apps take it from the module instead of a copy of their own. Run with -Dgsi=TREE.
 */
public final class FwListsTest {
    static final List<String> BAD = new ArrayList<>();
    static Path g;

    static String read(String rel) throws IOException { return new String(Files.readAllBytes(g.resolve(rel)), StandardCharsets.UTF_8); }

    /** every value of KEY= in the file (comma or space list), as a comma list */
    static List<String> values(String rel, String key) throws IOException {
        List<String> r = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "=([a-z0-9_, ]+)$").matcher(read(rel));
        while (m.find()) r.add(m.group(1).trim().replace(' ', ','));
        return r;
    }

    static void want(String rel, String key, FwModel m) throws IOException {
        List<String> v = values(rel, key);
        String set = FwSet.updateList(m);
        if (v.isEmpty()) BAD.add(rel + ": no " + key + "=");
        for (String x : v) if (!x.equals(set)) BAD.add(rel + ": " + key + "=" + x + ", the " + m.family + " set is " + set);
        if (BAD.isEmpty() || !BAD.get(BAD.size() - 1).startsWith(rel)) System.out.println("ok   " + rel + " " + key + " = the " + m.family + " set");
    }

    static void has(String rel, String text) throws IOException {
        if (read(rel).contains(text)) System.out.println("ok   " + rel + " uses the module (" + text + ")");
        else BAD.add(rel + ": does not contain " + text);
    }

    static void hasNot(String rel, String text) throws IOException {
        if (!read(rel).contains(text)) System.out.println("ok   " + rel + " keeps no copy of its own (" + text + ")");
        else BAD.add(rel + ": still contains " + text);
    }

    public static void main(String[] a) throws IOException {
        g = Paths.get(System.getProperty("gsi"));
        String pr = "ro.product.ab_ota_partitions";
        want("tools/ota/image/ota_props.txt", pr, FwModel.Z9X);
        want("tools/lumen_v1.sh", "AB_OTA", FwModel.Z9X);
        want("devices/goat_elegant/ota.conf", "ab_ota_partitions", FwModel.HB);
        want("devices/goat_elegant/image/hb_ota_props.txt", pr, FwModel.HB);
        want("devices/g0111/ota.conf", "ab_ota_partitions", FwModel.X50);
        want("devices/g0100/ota.conf", "ab_ota_partitions", FwModel.X50);
        want("devices/g0111/image/x50_prop_pub.txt", pr, FwModel.X50);
        want("devices/g0111/image/x50max_prop_pub.txt", pr, FwModel.X50);
        has("devices/goat_elegant/image/verify_tree.py", "\"" + FwSet.updateList(FwModel.HB) + "\"");
        has("devices/goat_elegant/image/checks/ota_props.py", "\"" + FwSet.updateList(FwModel.HB) + "\"");
        has("devices/g0111/image/verify_tree_x50.py", "\"" + FwSet.updateList(FwModel.X50) + "\"");
        has("tools/devices/test/x50_z9x_separation.sh", pr + "=" + FwSet.updateList(FwModel.X50));
        // the apps: the installer and the updater take the set from the module
        has("apps/LumenDsuInstall/app.layers", "module    lumen-fwcopy");
        has("apps/LumenUpdater/LumenUpdater.layers", "module    lumen-fwcopy");
        has("apps/core/lumen-updater/src/org/lumen/updater/Preflight.java", "FwSet.checkUpdateList(");
        has("apps/LumenDsuInstall/src/org/lumen/dsuinstall/Engine.java", "new FwCopy(");
        hasNot("apps/LumenDsuInstall/src/org/lumen/dsuinstall/Profile.java", "MT5877_COPY");
        hasNot("apps/LumenDsuInstall/src/org/lumen/dsuinstall/Engine.java", "void copy(String from, String to");
        System.out.println(BAD.isEmpty() ? "ALL LISTS ARE THE MODULE'S" : "FAIL\n" + String.join("\n", BAD));
        if (!BAD.isEmpty()) System.exit(1);
    }
}
