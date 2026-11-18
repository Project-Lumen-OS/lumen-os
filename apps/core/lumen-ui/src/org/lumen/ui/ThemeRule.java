package org.lumen.ui;

import java.util.TimeZone;

/**
 * Which palette is on screen, plain Java (host tests: test/ThemeTest). Авто is light from sunrise to sunset, like the
 * stock skin: the sun is taken at the last known place ({@link LumenTheme#KEY_GEO}, written by whoever learns it,
 * Home's weather or GeoIp); with no place yet it is light from 07:00 to 19:00 local time.
 */
public final class ThemeRule {
    public static final int DAY_START_MIN = 7 * 60, DAY_END_MIN = 19 * 60;
    private static final long MIN_MS = 60_000L;
    /** The Auto flip is searched this far ahead (minutes); further away means "check again then". */
    private static final int SCAN_MIN = 26 * 60;

    private ThemeRule() {}

    /** True when the dark palette is on screen. {@code geo} is {lat, lon} or null. */
    public static boolean dark(ThemeMode m, long utcMs, TimeZone tz, double[] geo) {
        if (m == ThemeMode.DARK) return true;
        if (m == ThemeMode.LIGHT) return false;
        if (geo != null) {
            double[] tmp = new double[2];
            return SolarCalc.sunElevation(utcMs, geo[0], geo[1], tmp) < SolarCalc.RISE_SET_DEG;
        }
        int min = localMinute(utcMs, tz);
        return min < DAY_START_MIN || min >= DAY_END_MIN;
    }

    /**
     * The next instant (UTC ms, on a whole minute) at which {@link #dark} changes, or Long.MAX_VALUE for Светлая and
     * Тёмная. Polar day or night: the end of the scanned window, so the caller simply checks again then.
     */
    public static long nextChange(ThemeMode m, long utcMs, TimeZone tz, double[] geo) {
        if (m != ThemeMode.AUTO) return Long.MAX_VALUE;
        boolean now = dark(m, utcMs, tz, geo);
        long t = (utcMs / MIN_MS + 1) * MIN_MS;
        for (int i = 0; i < SCAN_MIN; i++, t += MIN_MS) {
            if (dark(m, t, tz, geo) != now) return t;
        }
        return t;
    }

    /** "lat,lon" back to {lat, lon}; null when missing or out of range. */
    public static double[] parseGeo(String s) {
        if (s == null) return null;
        int c = s.indexOf(',');
        if (c <= 0) return null;
        try {
            double la = Double.parseDouble(s.substring(0, c).trim()), lo = Double.parseDouble(s.substring(c + 1).trim());
            if (Double.isNaN(la) || Double.isNaN(lo) || Math.abs(la) > 90 || Math.abs(lo) > 180) return null;
            return new double[] { la, lo };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The stored form of a place: two decimals (about 1 km), enough for sunrise and no more. */
    public static String geo(double lat, double lon) {
        return String.format(java.util.Locale.US, "%.2f,%.2f", lat, lon);
    }

    static int localMinute(long utcMs, TimeZone tz) {
        long local = utcMs + tz.getOffset(utcMs);
        return (int) (Math.floorMod(local, 86_400_000L) / MIN_MS);
    }
}
