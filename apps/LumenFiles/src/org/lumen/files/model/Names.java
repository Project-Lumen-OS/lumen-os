// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Predicate;

/** Row names: which are hidden, free names on a clash, typed names (rename, new folder). */
public final class Names {
    /** FAT and exFAT (USB drives) refuse these, so Files never offers them. */
    static final String BAD = "/\\:*?\"<>|\0";
    static final int MAX_BYTES = 255;
    private static final String[] DOUBLE = {".tar.gz", ".tar.xz", ".tar.bz2"};

    private Names() {}

    /** Not listed: dot files, macOS "._" forks, "$..." and the drive's own system folders. */
    public static boolean hidden(String n) {
        return n == null || n.isEmpty() || n.startsWith(".") || n.startsWith("$")
                || "System Volume Information".equals(n) || "LOST.DIR".equals(n);
    }

    /** "name" when free, else "name (2).ext", "name (3).ext" ...; a double ending stays whole (a (2).tar.gz). */
    public static String free(String name, Predicate<String> taken) {
        if (!taken.test(name)) return name;
        String[] p = split(name);
        for (int i = 2; ; i++) {
            String t = p[0] + " (" + i + ")" + p[1];
            if (!taken.test(t)) return t;
        }
    }

    /** The folder an archive is unpacked into: its name without the ending. */
    public static String stem(String name) {
        String s = split(name)[0];
        return s.isEmpty() ? name : s;
    }

    static String[] split(String name) {
        String low = name.toLowerCase(Locale.ROOT);
        for (String d : DOUBLE) {
            if (low.endsWith(d) && low.length() > d.length()) {
                int i = name.length() - d.length();
                return new String[]{name.substring(0, i), name.substring(i)};
            }
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? new String[]{name.substring(0, dot), name.substring(dot)} : new String[]{name, ""};
    }

    /** Why a typed name cannot be used, null when it can ({@code taken}: another row has it). */
    public static NameProblem check(String raw, Predicate<String> taken) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) return NameProblem.EMPTY;
        if (n.equals(".") || n.equals("..")) return NameProblem.DOTS;
        for (int i = 0; i < n.length(); i++) if (BAD.indexOf(n.charAt(i)) >= 0 || n.charAt(i) < 32) return NameProblem.BAD_CHAR;
        if (n.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return NameProblem.TOO_LONG;
        if (taken != null && taken.test(n)) return NameProblem.TAKEN;
        return null;
    }
}
