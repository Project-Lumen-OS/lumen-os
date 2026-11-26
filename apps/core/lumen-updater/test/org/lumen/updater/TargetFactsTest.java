// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The model table of README.md, pinned per assembled updater: test/run.sh passes the expected facts of each recipe
 * as key=value arguments and this compares them with the recipe's Target.SPEC. A changed model value fails here even
 * where no behaviour test reaches it (the hidden settings row, the DSU hand-over).
 */
public final class TargetFactsTest {
    public static void main(String[] a) {
        UpdateTarget t = Target.SPEC;
        Map<String, String> got = new LinkedHashMap<>();
        got.put("props", t.props);
        got.put("device", t.device != null ? t.device : "prop:" + t.deviceProp);
        got.put("versions", t.versions.name());
        got.put("channels", t.channels.name());
        got.put("default", String.valueOf(t.defaultManifest));
        got.put("beta", String.valueOf(t.betaUrl));
        got.put("guide", String.valueOf(t.guide));
        got.put("dir", t.dir.name());
        got.put("install", t.install.name());
        got.put("checks", t.gateProp != null ? "prop:" + t.gateProp + ":" + t.checks.name() : t.checks.name());
        got.put("dsu", String.valueOf(t.dsuHandover));
        got.put("hide", String.valueOf(t.hideWithoutDevice));
        got.put("vabc", String.join(",", t.compressedSnapshots));
        int pass = 0, fail = 0;
        for (String kv : a) {
            int e = kv.indexOf('=');
            String k = kv.substring(0, e), want = kv.substring(e + 1);
            if (!got.containsKey(k)) {
                fail++;
                System.out.println("FAIL unknown fact " + k);
            } else if (want.equals(got.get(k))) {
                pass++;
            } else {
                fail++;
                System.out.println("FAIL " + k + ": want " + want + ", got " + got.get(k));
            }
        }
        if (pass + fail != got.size()) {
            fail++;
            System.out.println("FAIL " + got.size() + " facts, " + (pass + fail - 1) + " given");
        }
        System.out.println("target facts passed=" + pass + " failed=" + fail);
        if (fail > 0) System.exit(1);
    }
}
