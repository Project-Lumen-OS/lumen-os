// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.util;
/** Host stub: Android logs go to World.log only when World.verbose (the decisions are traced by the fakes). */
public final class Log {
    public static int i(String t, String m) { return lumen.fake.World.androidLog("I", t, m); }
    public static int d(String t, String m) { return lumen.fake.World.androidLog("D", t, m); }
    public static int w(String t, String m) { return lumen.fake.World.androidLog("W", t, m); }
    public static int w(String t, String m, Throwable e) { return lumen.fake.World.androidLog("W", t, m + " " + e); }
    public static int e(String t, String m) { return lumen.fake.World.androidLog("E", t, m); }
    public static int e(String t, String m, Throwable e) { return lumen.fake.World.androidLog("E", t, m + " " + e); }
}
