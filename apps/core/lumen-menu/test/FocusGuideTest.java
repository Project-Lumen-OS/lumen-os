package org.lumen.menu;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The focus page's remote picture is data per model (devices/_remotes/focus_guide.tsv): every profile with a
 * "focus.guide" hint names the token that the stock RemoteFocusCell rule gives for the remote that model ships with,
 * every such model has a remote row, and the rule itself keeps the stock order (the side key only for B990C).
 *   java org.lumen.menu.FocusGuideTest REPO_ROOT
 */
public final class FocusGuideTest {
    private static int fails;

    public static void main(String[] a) throws Exception {
        File root = new File(a[0]);
        List<String[]> rules = new ArrayList<>();
        Map<String, String> remotes = new LinkedHashMap<>();
        for (String line : Files.readAllLines(new File(root, "devices/_remotes/focus_guide.tsv").toPath(), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t");
            if (f[0].equals("rule") && f.length == 6) rules.add(f);
            else if (f[0].equals("model") && f.length == 4) remotes.put(f[1], f[2]);
            else ok("well-formed row: " + line, false);
        }
        ok("rules: the stock RemoteFocusCell order ends with the catch-all", !rules.isEmpty() && rules.get(rules.size() - 1)[1].equals("*"));
        ok("the B990C remote has the side key, its neighbours the front key", token(rules, "B990C_CY01").equals("focus_guide_side")
                && token(rules, "B980C_CY02").equals("focus_guide_front") && token(rules, "B970C_CY07_CY_V1.0.4").equals("focus_guide_front"));
        ok("an unknown or empty version has no picture (stock: the old paddle remote)", token(rules, "").equals("-")
                && token(rules, "X123").equals("-"));
        ok("B995C (a press is manual focus there) has no Lumen text yet", token(rules, "B995C_CY01").equals("-"));
        for (String[] r : rules) {
            String t = r[2];
            ok("rule " + r[1] + ": a token the module draws", t.equals("-") || MenuProfile.HINT_PICTURES.contains(t));
        }
        int checked = 0;
        File[] devs = new File(root, "devices").listFiles();
        for (File d : devs == null ? new File[0] : devs) {
            File j = new File(d, "menu.json");
            if (!j.isFile()) continue;
            MenuProfile p = MenuProfile.parse(new String(Files.readAllBytes(j.toPath()), StandardCharsets.UTF_8));
            String hint = p.hints().get(FeatureId.FOCUS_GUIDE);
            if (hint == null) continue;
            checked++;
            String remote = remotes.get(p.model());
            ok(d.getName() + " (" + p.model() + "): its remote is in focus_guide.tsv", remote != null);
            if (remote == null) continue;
            String want = token(rules, remote);
            ok(d.getName() + ": focus.guide = " + want + " (remote " + remote + "), has " + hint, hint.equals(want));
        }
        ok("the X50 family profiles were checked (" + checked + ")", checked >= 5);
        System.out.println(fails == 0 ? "ok   FocusGuideTest" : "FAIL FocusGuideTest: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    /** Stock RemoteFocusCell: the first rule whose any substring the upper-case version contains, '*' takes the rest. */
    static String token(List<String[]> rules, String version) {
        String v = version.toUpperCase(java.util.Locale.ROOT);
        for (String[] r : rules) {
            if (r[1].equals("*")) return r[2];
            for (String m : r[1].split("\\|")) if (v.contains(m)) return r[2];
        }
        return "-";
    }

    private static void ok(String what, boolean good) {
        if (!good) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }
}
