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
