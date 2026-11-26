// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.source;

/**
 * One model's Source key data (devices/&lt;c&gt;/source.tsv, in the app as {@value #ASSET}): where the cycle goes after
 * the last HDMI port and how long the card stays. Lines "key&lt;TAB&gt;value&lt;TAB&gt;evidence", '#' comments. Pure.
 */
public final class SourceRule {
    public static final String ASSET = "lumen_source/source.tsv";
    /** XRMService jumpToHdmi on Z9X, HB, X50, RS30, Titan; xhplayer SIGNAL_DIALOG_SHOW_TIME. */
    public static final SourceRule STOCK = new SourceRule(true, 3000);
    static final long MIN_MS = 1000, MAX_MS = 10_000;

    /** After the last port: the home screen (true) or the first port again (false, Play 6). */
    public final boolean homeAfterLast;
    /** Time on screen after the last press (ms). */
    public final long osdMs;

    SourceRule(boolean homeAfterLast, long osdMs) {
        this.homeAfterLast = homeAfterLast;
        this.osdMs = osdMs;
    }

    /** Parses the data file; an unknown key, a bad value or a missing key is an error (fail closed). */
    public static SourceRule parse(String text) {
        Boolean home = null;
        Long ms = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = raw.split("\t");
            if (f.length < 2) throw new IllegalArgumentException("no value: " + line);
            String k = f[0].trim(), v = f[1].trim();
            if (k.equals("after_last")) {
                if (!v.equals("home") && !v.equals("first")) throw new IllegalArgumentException("after_last " + v);
                home = v.equals("home");
            } else if (k.equals("osd_ms")) {
                long n = Long.parseLong(v);
                if (n < MIN_MS || n > MAX_MS) throw new IllegalArgumentException("osd_ms " + v);
                ms = n;
            } else {
                throw new IllegalArgumentException("unknown key " + k);
            }
        }
        if (home == null || ms == null) throw new IllegalArgumentException("after_last and osd_ms are both needed");
        return new SourceRule(home, ms);
    }

    @Override public String toString() { return (homeAfterLast ? "home" : "first") + " " + osdMs; }
}
