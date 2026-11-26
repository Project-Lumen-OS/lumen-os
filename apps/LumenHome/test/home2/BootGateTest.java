// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lumen Home before the user is unlocked (direct boot, model/BootGate): the unlock state machine, the start reads by
 * storage on a fake device that refuses credential protected reads while locked (as Android does), missing files,
 * and the wiring of the Android half (greps of the real sources, RED on the code before direct boot).
 */
public final class BootGateTest {
    static int checks, fails;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] a) {
        stateMachine();
        modes();
        startReads();
        bootWalk();
        wiring();
        System.out.println((fails == 0 ? "ok   " : "FAIL ") + "Home direct boot: " + checks + " checks, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }

    /** A device: device protected files always readable, credential protected ones only once unlocked. */
    static final class Device {
        boolean unlocked;
        final List<String> reads = new ArrayList<>();
        String saved = "last screen", weather = "+3";
        boolean ceFileMissing, deFileMissing;

        String readDe() throws Exception {
            reads.add("de");
            if (deFileMissing) return null; // SnapshotFile: no file = null
            return saved;
        }

        String readCe() throws Exception {
            reads.add("ce");
            if (!unlocked) {
                throw new IllegalStateException("SharedPreferences in credential encrypted storage are not available until after user is unlocked");
            }
            if (ceFileMissing) throw new FileNotFoundException("weather.xml");
            return weather;
        }
    }

    static void stateMachine() {
        List<String> ran = new ArrayList<>();
        List<String> errs = new ArrayList<>();
        BootGate g = new BootGate(false, (step, t) -> errs.add(step));
        check(!g.unlocked(), "locked at boot");
        g.whenUnlocked("import", () -> ran.add("import"));
        g.whenUnlocked("image cache", () -> ran.add("image cache"));
        BootGate.Ticket gone = g.whenUnlocked("old activity", () -> ran.add("old activity"));
        g.whenUnlocked("missing file", () -> {
            throw new RuntimeException(new FileNotFoundException("crash"));
        });
        g.whenUnlocked("home", () -> {
            ran.add("home");
            // a step asked while the unlock runs (a new activity) runs at once
            g.whenUnlocked("late", () -> ran.add("late"));
        });
        gone.cancel();
        check(ran.isEmpty(), "locked: nothing that needs the unlock runs");
        g.unlock();
        check(g.unlocked(), "unlocked after the broadcast");
        check(ran.equals(Arrays.asList("import", "image cache", "home", "late")),
                "unlock: the steps run once, in the order asked, the cancelled one never: " + ran);
        check(errs.equals(Arrays.asList("missing file")), "unlock: a failing step is reported and the next ones still run");
        g.unlock();
        check(ran.size() == 4, "a second unlock broadcast runs nothing again");
        g.whenUnlocked("after", () -> ran.add("after"));
        check(ran.size() == 5 && ran.get(4).equals("after"), "asked after the unlock: runs at once");

        List<String> ran2 = new ArrayList<>();
        BootGate u = new BootGate(true, null);
        u.whenUnlocked("x", () -> ran2.add("x"));
        check(u.unlocked() && ran2.equals(Arrays.asList("x")), "started unlocked: every step at once");
        u.whenUnlocked("throws", () -> {
            throw new IllegalStateException("no");
        });
        check(true, "a failing step without an error sink does not throw");
    }

    static void modes() {
        check(BootGate.mode(true, false) == BootGate.FULL && BootGate.mode(true, true) == BootGate.FULL, "unlocked: full start");
        check(BootGate.mode(false, true) == BootGate.SAVED, "locked, Home is HOME after the unlock: the saved screen");
        check(BootGate.mode(false, false) == BootGate.STAND_IN, "locked, Setup or another launcher after the unlock: stand in");
        check(!BootGate.handOver(BootGate.SAVED, true), "unlock, Home is still HOME: stays");
        check(BootGate.handOver(BootGate.SAVED, false), "unlock, HOME went to another app: hands over");
        check(BootGate.handOver(BootGate.STAND_IN, true) && BootGate.handOver(BootGate.STAND_IN, false),
                "a stand in always hands over (the system resolves HOME again)");
        check(!BootGate.handOver(BootGate.FULL, false), "a full start never hands over by itself");
    }

    static void startReads() {
        Device d = new Device();
        List<String> errs = new ArrayList<>();
        BootGate.Start<String, String> s = BootGate.start(d.unlocked, d::readDe, d::readCe, (step, t) -> errs.add(step));
        check(d.reads.equals(Arrays.asList("de")), "locked: only the device protected snapshot is read: " + d.reads);
        check("last screen".equals(s.saved) && s.weather == null && errs.isEmpty(), "locked: the saved screen, no weather, no error");

        d = new Device();
        d.unlocked = true;
        s = BootGate.start(d.unlocked, d::readDe, d::readCe, null);
        check(d.reads.equals(Arrays.asList("de", "ce")) && "+3".equals(s.weather) && "last screen".equals(s.saved),
                "unlocked: snapshot and stored weather");

        d = new Device();
        d.unlocked = true;
        d.ceFileMissing = true;
        errs.clear();
        s = BootGate.start(d.unlocked, d::readDe, d::readCe, (step, t) -> errs.add(step));
        check("last screen".equals(s.saved) && s.weather == null && errs.equals(Arrays.asList("stored weather")),
                "unlocked, credential protected file missing: the saved screen still comes, no crash");

        d = new Device();
        d.deFileMissing = true;
        s = BootGate.start(false, d::readDe, d::readCe, null);
        check(s.saved == null && s.weather == null && d.reads.equals(Arrays.asList("de")),
                "locked, no snapshot yet (first boot of a new build): empty start, nothing else read");

        s = BootGate.start(false, () -> {
            throw new java.io.IOException("broken");
        }, d::readCe, null);
        check(s.saved == null, "a broken snapshot file leaves the start empty, no crash");
    }

    /** One boot: Home before the unlock, the broadcast, the full start once (as HomeActivity drives it). */
    static void bootWalk() {
        Device d = new Device();
        BootGate g = new BootGate(false, null);
        List<String> log = new ArrayList<>();
        int mode = BootGate.mode(g.unlocked(), true);
        BootGate.Start<String, String> s = BootGate.start(g.unlocked(), d::readDe, d::readCe, null);
        log.add("show " + s.saved);
        final int[] loads = {0};
        g.whenUnlocked("home", () -> {
            if (BootGate.handOver(BootGate.SAVED, true)) {
                log.add("hand over");
                return;
            }
            loads[0]++;
            try {
                log.add("load " + d.readCe());
            } catch (Exception e) {
                log.add("load failed " + e);
            }
        });
        check(mode == BootGate.SAVED && log.equals(Arrays.asList("show last screen")) && !d.reads.contains("ce"),
                "boot walk: the saved screen before the unlock, no credential protected read");
        d.unlocked = true;
        g.unlock();
        g.unlock();
        check(loads[0] == 1 && log.equals(Arrays.asList("show last screen", "load +3")), "boot walk: one full load after the unlock: " + log);
    }

    // ------------------------------------------------------------------ the Android half (greps of the real sources)

    static void wiring() {
        String dir = System.getProperty("home.src");
        if (dir == null) return;
        String root = dir.substring(0, dir.indexOf("/src/org/lumen/home"));
        String man = read(root + "/AndroidManifest.xml"), app = read(dir + "/App.java"), act = read(dir + "/HomeActivity.java");
        String img = read(dir + "/img/ImageLoader.java"), disk = read(dir + "/img/DiskCache.java"), art = read(dir + "/img/AppArt.java");
        String snap = read(dir + "/data/SnapshotFile.java");

        int ha = man.indexOf("android:name=\".HomeActivity\"");
        String hb = ha < 0 ? "" : man.substring(ha, man.indexOf(">", ha));
        check(hb.contains("android:directBootAware=\"true\""), "manifest: HomeActivity is direct boot aware");
        int ap = man.indexOf("<application");
        String apb = ap < 0 ? "" : man.substring(ap, man.indexOf(">", ap));
        check(!apb.contains("directBootAware") && !man.contains("defaultToDeviceProtectedStorage"),
                "manifest: the app stays credential protected (only HOME is direct boot aware)");
        check(count(man, "directBootAware=\"true\"") == 1, "manifest: no provider, receiver or other activity runs before the unlock");

        String attach = between(app, "attachBaseContext(", "\n    }");
        check(attach.contains("new BootGate(um == null || um.isUserUnlocked()") && !attach.contains("LumenImport"),
                "App: the gate starts from the unlock state, no pre-1.0.8 import (gone in 1.1.0)");
        check(between(app, "private File crashFile()", "\n    }").contains("mDe.getFilesDir()"),
                "App: the crash counter is device protected (works before the unlock)");
        check(app.contains("Intent.ACTION_USER_UNLOCKED") && app.contains("mGate.unlock()"), "App: the unlock broadcast opens the gate");
        int wfu = app.indexOf("private void waitForUnlock()");
        String wb = wfu < 0 ? "" : between(app, "private void waitForUnlock()", "\n    }");
        check(wb.indexOf("registerReceiver(") > 0 && wb.indexOf("isUserUnlocked()") > wb.indexOf("registerReceiver("),
                "App: unlocked state checked again after registering (no missed unlock)");

        String oc = between(act, "protected void onCreate(", "\n    }");
        check(oc.indexOf("BootGate.mode(") > 0 && oc.indexOf("BootGate.mode(") < oc.indexOf("readStart();")
                && oc.contains("homeIsUs(true)"), "Home: decides saved screen or stand in before reading anything");
        check(oc.contains("whenUnlocked(\"dream default\", () -> mApp.io().post(() -> DreamDefault.applyOnce(app)))")
                && count(act, "DreamDefault.applyOnce(") == 1,
                "Home: the screensaver default (credential protected prefs) waits for the unlock");
        check(between(act, "private boolean homeIsUs(", "\n    }").contains("MATCH_DIRECT_BOOT_UNAWARE"),
                "Home: HOME after the unlock is resolved with every HOME (Setup, other launchers)");
        check(between(act, "private void readStart()", "\n    }").contains("BootGate.start(mApp.gate().unlocked()"),
                "Home: the start reads go through BootGate.start (stored weather only when unlocked)");
        check(first(between(act, "private void load(boolean network)", "\n    }"), "if (!mApp.gate().unlocked()) return;", "lanes()"),
                "Home: no data load (apps, prefs, caches) before the unlock");
        check(first(between(act, "private void refreshWeather(boolean force)", "\n    }"), "if (!mApp.gate().unlocked()) return;", "prefs()"),
                "Home: no weather before the unlock");
        check(between(act, "private void bindWeather()", "\n    }").contains("mApp.gate().unlocked() && mApp.prefs()"),
                "Home: binding reads the weather switch only when unlocked");
        for (String m : new String[] { "private void activate()", "private void menuForFocus()", "private void openSource()" }) {
            check(between(act, m, "\n    }").contains("mApp.gate().unlocked()"), "Home: " + m + " waits for the unlock");
        }
        check(first(between(act, "private void observeTvp()", "\n    }"), "mApp.gate().unlocked()", "registerContentObserver"),
                "Home: the TvProvider observers are registered only after the unlock");
        check(!between(act, "private void register()", "\n    }").contains("registerContentObserver"),
                "Home: register() leaves the provider observers to observeTvp()");
        check(between(act, "private void unlocked()", "\n    }").contains("observeTvp();")
                && between(act, "private void unlocked()", "\n    }").contains("load(true);")
                && between(act, "private void unlocked()", "\n    }").contains("BootGate.handOver("),
                "Home: the unlock hands over or observes and loads everything");
        check(count(act, "snapshot().write(") == 1 && between(act, "private void fresh(", "\n    }").contains("snapshot().write("),
                "Home: the snapshot is written only from fresh data (after the unlock)");
        check(act.contains("boolean same = !mLimited && HomeSnapshot.same(mData, d);"),
                "Home: the first fresh data after the unlock is bound (art and weather the saved screen lacked)");
        check(!act.contains("getFilesDir()") && !act.contains("getCacheDir()") && !act.contains("getSharedPreferences("),
                "Home: the activity opens no storage itself");

        check(!img.contains("getCacheDir(), UiScale") && img.contains("whenUnlocked(\"image cache\", () -> mDisk.open(app.getCacheDir()))"),
                "images: the disk cache (credential protected) opens at the unlock");
        check(first(between(img, "private File master(", "\n    }"), "if (!mDisk.isOpen()) return null;", "openInputStream"),
                "images: nothing is fetched or read from other apps before the unlock");
        check(between(disk, "File get(", "\n    }").contains("if (dir == null) return null;")
                && between(disk, "File put(", "\n    }").contains("if (dir == null) return null;"),
                "images: the closed disk cache reads and writes nothing");
        check(art.contains("getActivityInfo(cn, PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE)"),
                "images: app banners also before the unlock (PackageManager hides other activities then)");
        check(snap.contains("createDeviceProtectedStorageContext()"), "snapshot: device protected storage");
    }

    static boolean first(String body, String guard, String use) {
        int g = body.indexOf(guard), u = body.indexOf(use);
        return g >= 0 && (u < 0 || g < u);
    }

    static String between(String s, String from, String to) {
        int i = s.indexOf(from);
        if (i < 0) return "";
        int j = s.indexOf(to, i);
        return j < 0 ? s.substring(i) : s.substring(i, j);
    }

    static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) n++;
        return n;
    }

    static String read(String p) {
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(p)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            check(false, "read " + p);
            return "";
        }
    }
}
