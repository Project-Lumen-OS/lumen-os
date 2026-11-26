// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.testkit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * lumen-testkit: the pass / fail counter of a host-test suite. ok() counts, a failure prints "FAIL what" at once;
 * done() prints "SUITE: N passed, M failed" and exits 1 on a failure and when nothing ran at all (fail closed: a suite
 * that checked nothing is red). Plus the text helpers of the static checks.
 */
public final class Check {
    private final String suite;
    private int pass, fail;

    public Check(String suite) {
        this.suite = suite;
    }

    public void ok(String what, boolean cond) {
        if (cond) pass++;
        else {
            fail++;
            System.out.println("FAIL " + what);
        }
    }

    public void eq(String what, Object want, Object got) {
        boolean same = want == null ? got == null : want.equals(got);
        ok(what + (same ? "" : ": want " + want + ", got " + got), same);
    }

    public int passed() { return pass; }

    public int failed() { return fail; }

    /** The summary line; exit 1 on a failure or when no check ran. */
    public void done() {
        System.out.println(suite + ": " + pass + " passed, " + fail + " failed");
        if (pass + fail == 0) {
            System.out.println(suite + ": no check ran");
            System.exit(1);
        }
        if (fail != 0) System.exit(1);
    }

    public static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static String run(File gsi, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(gsi).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IllegalStateException(String.join(" ", cmd) + ": " + out);
        return out;
    }

    /** The app recipe a device builds APP with (its own devices/<c>/app/<APP>.layers or its family's), with
     *  {codename} and {device}/ resolved: tools/devices/assemble_app.py --show (devices/README.md). */
    public static String recipe(File gsi, String codename, String app) throws Exception {
        return "\n" + run(gsi, "python3", new File(gsi, "tools/devices/assemble_app.py").getPath(), "--root", gsi.getPath(),
                "--device", codename, "--app", app, "--show");
    }

    /** A device's data file NAME (devices/<c>/NAME, else its nearest family folder's): tools/devices/layered.py --find. */
    public static File deviceFile(File gsi, String codename, String name) throws Exception {
        return new File(run(gsi, "python3", new File(gsi, "tools/devices/layered.py").getPath(), "--find",
                new File(gsi, "devices/" + codename + "/device.conf").getPath(), name).trim());
    }

    /** A strings file as the APK resolves it (tools/i18n/res_strings.py): every alias of its res/values twin
     *  (a text of apps/core/lumen-common-strings, tools/i18n/share.py) resolved in the file's own locale; a locale
     *  file that only had aliases is gone from the tree and still gives its texts. */
    public static String strings(File file) throws Exception {
        String folder = file.getParentFile().getName();
        File twin = new File(new File(file.getParentFile().getParentFile(), "values"), file.getName());
        String own = file.isFile() ? read(file) : "<resources>\n</resources>\n";
        File root = file.getAbsoluteFile().getParentFile();
        while (root != null && !new File(root, "apps/core/lumen-common-strings").isDirectory()) root = root.getParentFile();
        if (root == null || !twin.isFile()) return own;
        java.util.regex.Matcher m = STRING.matcher(read(twin));
        StringBuilder add = new StringBuilder();
        while (m.find()) {
            String raw = m.group(2).trim();
            if (!raw.startsWith("@string/")) continue;
            String t = resolve(root, folder, raw.substring(8), 0);
            if (t == null) continue;
            if (folder.equals("values")) own = own.replace(">" + raw + "</string>", ">" + t + "</string>");
            else if (!own.contains("<string name=\"" + m.group(1) + "\"")) {
                add.append("    <string name=\"").append(m.group(1)).append("\">").append(t).append("</string>\n");
            }
        }
        int i = own.lastIndexOf("</resources>");
        return i < 0 ? own + add : own.substring(0, i) + add + own.substring(i);
    }

    private static final java.util.regex.Pattern STRING =
            java.util.regex.Pattern.compile("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", java.util.regex.Pattern.DOTALL);

    private static String resolve(File root, String folder, String key, int depth) throws Exception {
        File[] mods = new File(root, "apps/core").listFiles();
        if (mods == null || depth > 8) return null;
        java.util.Arrays.sort(mods);
        for (File mod : mods) {
            File[] fs = new File(mod, "res/" + folder).listFiles();
            if (fs == null) continue;
            java.util.Arrays.sort(fs);
            for (File f : fs) {
                if (!f.getName().endsWith(".xml")) continue;
                java.util.regex.Matcher m = STRING.matcher(read(f));
                while (m.find()) {
                    if (!m.group(1).equals(key)) continue;
                    String v = m.group(2);
                    return v.trim().startsWith("@string/") ? resolve(root, folder, v.trim().substring(8), depth + 1) : v;
                }
            }
        }
        return null;
    }

    /** The codenames of the models of FAMILY (devices/_family/<FAMILY>/, every model whose include chain names it),
     *  sorted: tools/devices/layered.py --members, so a new model is checked without editing a test. */
    public static java.util.List<String> members(File gsi, String family) throws Exception {
        return java.util.Arrays.asList(run(gsi, "python3", new File(gsi, "tools/devices/layered.py").getPath(), "--members",
                gsi.getPath(), family).trim().split("\\s+"));
    }

    /** The family folders of a model's device.conf include chain, nearest first (tools/devices/layered.py --files: the
     *  descriptor, then each devices/_family/<f>/ file it reads). */
    public static java.util.List<String> families(File gsi, String codename) throws Exception {
        java.util.List<String> out = new java.util.ArrayList<>();
        String files = run(gsi, "python3", new File(gsi, "tools/devices/layered.py").getPath(), "--files",
                new File(gsi, "devices/" + codename + "/device.conf").getPath());
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("devices/_family/([^/\\s]+)/").matcher(files);
        while (m.find()) if (!out.contains(m.group(1))) out.add(m.group(1));
        return out;
    }

    /** A device descriptor (devices/<c>/build.conf or device.conf) as key=value lines with its family files resolved
     *  (tools/devices/layered.py, the one reader of the format; devices/README.md). */
    public static String descriptor(File gsi, String rel) throws Exception {
        return run(gsi, "python3", new File(gsi, "tools/devices/layered.py").getPath(), new File(gsi, rel).getPath());
    }

    /** Occurrences of what in s. */
    public static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) n++;
        return n;
    }

    /** Occurrences outside comments (lines starting with '*', '//' or '/*'). */
    public static int countCode(String s, String what) {
        int n = 0;
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) continue;
            for (int i = t.indexOf(what); i >= 0; i = t.indexOf(what, i + 1)) n++;
        }
        return n;
    }
}
