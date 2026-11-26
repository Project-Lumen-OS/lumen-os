// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

/** How a version name maps to the version code a manifest is compared by. */
public enum VersionScheme {
    /** x.y.z -> x*10000 + y*100 + z ("1.0" -> 10000): the Z9X and the Play 6. */
    LEGACY {
        @Override
        int code(String v) {
            if (v == null) return 0;
            String[] p = v.trim().split("\\.");
            try {
                int major = p.length > 0 ? Integer.parseInt(p[0]) : 0;
                int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
                int patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
                return major * 10000 + minor * 100 + patch;
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        String name(int mv) {
            return (mv / 10000) + "." + (mv / 100 % 100) + (mv % 100 != 0 ? "." + (mv % 100) : "");
        }
    },
    /**
     * Rule: x.y.z[-betaN|-rcN] -> x*1000000 + y*10000 + z*100 + n, n = N for betaN, 50+N for rcN, 99 for the
     * final. So 1.0.8-beta1 = 1000801, 1.0.8-rc2 = 1000852, 1.0.8 = 1000899 and a final is always newer than its
     * betas. Anything else is 0 (never newer than an update). The HB and the X50 family.
     */
    O1 {
        @Override
        int code(String v) {
            if (v == null) return 0;
            String s = v.trim(), mark = "";
            int dash = s.indexOf('-');
            if (dash >= 0) {
                mark = s.substring(dash + 1);
                s = s.substring(0, dash);
            }
            String[] p = s.split("\\.");
            if (p.length < 1 || p.length > 3) return 0;
            try {
                int major = Integer.parseInt(p[0]);
                int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
                int patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
                if (major < 0 || minor < 0 || minor > 99 || patch < 0 || patch > 99) return 0;
                int n;
                if (mark.isEmpty()) n = 99;
                else if (mark.matches("beta[1-9][0-9]?")) n = Integer.parseInt(mark.substring(4));
                else if (mark.matches("rc[1-9][0-9]?")) n = 50 + Integer.parseInt(mark.substring(2));
                else return 0;
                if (n == 50 || n > 99 || (!mark.isEmpty() && n == 99) || (mark.startsWith("beta") && n > 50)) return 0;
                return major * 1000000 + minor * 10000 + patch * 100 + n;
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        String name(int code) {
            if (code <= 0) return "?";
            int n = code % 100;
            String v = (code / 1000000) + "." + (code / 10000 % 100) + "." + (code / 100 % 100);
            if (n == 99 || n == 0 || n == 50) return v;
            return n > 50 ? v + "-rc" + (n - 50) : v + "-beta" + n;
        }
    };

    /**
     * True for a beta or a release candidate: the version name ends in -betaN or -rcN (1.0.10-beta21,
     * 1.0.11-rc1, x50-1.0.10-beta4). The same on every model whatever the scheme, so the Z9X betas (LEGACY codes)
     * count too. A final (1.0.10) and an unreadable name are no prerelease.
     */
    static boolean prerelease(String version) {
        return version != null && version.trim().matches(".*[0-9]-(beta|rc)[1-9][0-9]?");
    }

    /** The version code of a version name; 0 when it cannot be read. */
    abstract int code(String version);

    /** The version a code stands for, as the refusal text shows it. */
    abstract String name(int code);
}
