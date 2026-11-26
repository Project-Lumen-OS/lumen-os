// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Host tests of Customize Home (4PDA 2026-11-22): the stored layout (JSON, repair, folders), the looks as data, the
 * top level with folders, where the parts go for each layout, free up memory, the network mark, the settings backup
 * file, and the wiring greps that keep the rules in their one place.
 */
public final class LayoutTest {
    static int checks, fails;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static void eq(Object a, Object b, String what) {
        check(a == null ? b == null : a.equals(b), what + ": got [" + a + "] want [" + b + "]");
    }

    static String read(String rel) throws Exception {
        return new String(Files.readAllBytes(new File(System.getProperty("home.dir"), rel).toPath()), "UTF-8");
    }

    public static void main(String[] a) throws Exception {
        json();
        layout();
        folders();
        looks();
        dock();
        plan();
        memory();
        net();
        backup();
        wiring();
        System.out.println("layout: " + checks + " checks, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }

    static void json() {
        Object o = Json.parse("{\"a\": [1, \"x\\n\\\"y\", true, null], \"b\": {\"c\": -7}}");
        Map<?, ?> m = (Map<?, ?>) o;
        eq(((List<?>) m.get("a")).get(1), "x\n\"y", "escapes read");
        eq(((Map<?, ?>) m.get("b")).get("c"), -7L, "whole numbers are Long");
        eq(Json.write(o), "{\"a\":[1,\"x\\n\\\"y\",true,null],\"b\":{\"c\":-7}}", "written back the same");
        for (String bad : new String[]{"{", "[1,]", "{\"a\" 1}", "1.5", "{} x", "\"open"}) {
            boolean threw = false;
            try {
                Json.parse(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(threw, "broken json refused: " + bad);
        }
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 100; i++) deep.append('[');
        boolean threw = false;
        try {
            Json.parse(deep.toString());
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "too deep refused (no stack overflow)");
    }

    static void layout() {
        HomeLayout d = new HomeLayout();
        eq(d.look, Looks.CLASSIC, "default look Classic");
        check(d.hero == Looks.FULL && d.clock && d.cont && d.showcase && d.weather && d.rows == 1, "default = the shipped Витрина");
        HomeLayout old = HomeLayout.fromOld(Arrays.asList("a.b", "c.d", "a.b"), true, Collections.singleton("x.y"),
                Collections.singleton("org.lumen.setup"), false, false);
        eq(old.order, Arrays.asList("a.b", "c.d"), "old order taken once per package");
        check(old.orderSet && !old.cont && !old.weather && old.hidden.contains("x.y") && old.shown.contains("org.lumen.setup"),
                "old switches and sets taken");
        HomeLayout back = HomeLayout.parse(old.write());
        check(back != null && back.write().equals(old.write()), "write then parse gives the same layout");
        check(HomeLayout.parse(null) == null && HomeLayout.parse("") == null && HomeLayout.parse("[]") == null, "nothing stored = null");
        check(HomeLayout.parse("{\"v\":2}") == null, "a newer version is not read (never written over)");
        check(HomeLayout.parse("{\"v\":1,\"order\":[\"a\"") == null, "broken text = null");
        HomeLayout r = HomeLayout.parse("{\"v\":1,\"rows\":99,\"hero\":\"huge\",\"look\":\"Bad Look!\",\"folders\":["
                + "{\"id\":\"f1\",\"name\":\"  Видео  \",\"apps\":[\"p.a\",\"p.b\"]},"
                + "{\"id\":\"f2\",\"name\":\"Twice\",\"apps\":[\"p.a\",\"p.c\"]},"
                + "{\"id\":\"f1\",\"name\":\"Same id\",\"apps\":[]},{\"id\":\"f3\",\"name\":\"\",\"apps\":[]}],"
                + "\"order\":[\"p.a\",\"folder:f9\",\"p.z\",\"p.z\"]}");
        check(r != null, "repairable value read");
        eq(r.rows, HomeLayout.MAX_ROWS, "rows clamped");
        eq(r.hero, Looks.FULL, "unknown hero = full");
        eq(r.look, Looks.CLASSIC, "a bad look id = Classic");
        eq(r.folders.size(), 2, "a second folder with the same id and a nameless folder go");
        eq(r.folder("f1").name, "Видео", "folder name trimmed");
        eq(r.folder("f2").apps, Collections.singletonList("p.c"), "a package stays in its first folder only");
        eq(r.order, Arrays.asList("p.z", "folder:f1", "folder:f2"), "order: apps in folders, unknown folders and repeats go, every folder has a place");
        HomeLayout c = r.copy();
        c.rows = 1;
        eq(r.rows, HomeLayout.MAX_ROWS, "a copy is its own");
        HomeLayout cy = new HomeLayout();
        cy.cycleRows();
        cy.cycleRows();
        eq(cy.rows, 3, "rows cycle up");
        cy.cycleRows();
        eq(cy.rows, 1, "after the last the first");
    }

    static void folders() {
        HomeLayout l = new HomeLayout();
        List<String> dock = Arrays.asList("yt", "kp", "play", "vlc", "files");
        HomeLayout.Folder f = l.newFolder("Видео", "kp", dock);
        check(f != null, "a folder is made");
        eq(l.order, Arrays.asList("yt", "folder:" + f.id, "play", "vlc", "files"), "the folder stands where the app stood");
        eq(f.apps, Collections.singletonList("kp"), "the app is inside");
        check(l.orderSet, "making a folder keeps the order the user saw");
        check(l.moveToFolder("vlc", f.id), "another app in");
        eq(f.apps, Arrays.asList("kp", "vlc"), "last in the folder");
        check(!l.order.contains("vlc"), "an app in a folder is not on the top level");
        check(!l.moveToFolder("vlc", "nope"), "no such folder");
        List<String> now = Dock.tokens(Arrays.asList("yt", "kp", "play", "vlc", "files"), l);
        check(l.removeFromFolder("kp", now), "an app out");
        eq(l.order.indexOf("kp"), l.order.indexOf("folder:" + f.id) + 1, "right after its folder");
        check(l.rename(f.id, "  Кино  ") && f.name.equals("Кино"), "renamed and trimmed");
        check(!l.rename(f.id, "   "), "an empty name is refused");
        now = Dock.tokens(Arrays.asList("yt", "kp", "play", "vlc", "files"), l);
        check(l.removeFromFolder("vlc", now), "the last app out");
        check(l.folder(f.id) == null, "a folder left empty goes");
        HomeLayout g = new HomeLayout();
        HomeLayout.Folder h = g.newFolder("Игры", "a", Arrays.asList("x", "a", "y"));
        g.moveToFolder("b", h.id);
        now = Dock.tokens(Arrays.asList("x", "a", "y", "b"), g);
        check(g.deleteFolder(h.id, now), "deleted");
        eq(g.order, Arrays.asList("x", "a", "b", "y"), "its apps come back where it stood, in its order");
        HomeLayout full = new HomeLayout();
        for (int i = 0; i < HomeLayout.MAX_FOLDERS; i++) full.newFolder("F" + i, "p" + i, null);
        check(full.newFolder("one more", "q", null) == null, "no more than MAX_FOLDERS");
        HomeLayout n = new HomeLayout();
        HomeLayout.Folder a1 = n.newFolder("A", "p1", null);
        HomeLayout.Folder a2 = n.newFolder("B", "p2", null);
        check(!a1.id.equals(a2.id), "folder ids differ");
        String longName = "x".repeat(100);
        eq(new HomeLayout.Folder("f9", longName).name.length(), HomeLayout.MAX_NAME, "a long name is cut");
    }

    static void looks() throws Exception {
        List<Looks.Look> shipped = Looks.parse(read("assets/home/looks.tsv"));
        eq(shipped.size(), 3, "three looks shipped");
        eq(shipped.get(0).id, Looks.CLASSIC, "Classic first");
        Looks.Look cl = shipped.get(0);
        check(cl.hero == Looks.FULL && cl.clock && cl.cont && cl.showcase && cl.rows == 1, "Classic is the shipped Витрина");
        Looks.Look comp = Looks.of(shipped, "compact"), min = Looks.of(shipped, "minimal");
        check(comp != null && comp.hero == Looks.SMALL && comp.showcase && comp.rows == 2, "Compact: small hero, two rows");
        check(min != null && min.hero == Looks.NONE && min.clock && !min.cont && !min.showcase && min.rows == 3,
                "Minimal: the big clock and the apps, three rows");
        List<Looks.Look> broken = Looks.parse("# x\nfoo big 1 1 1 1\nbar full 2 1 1 1\nbaz full 1 1 1 9\nqux none 0 0 0 2\nqux full 1 1 1 1\n");
        eq(broken.size(), 2, "broken and repeated rows go, Classic always there");
        eq(Looks.parse(null).size(), 1, "no data file: Classic only");
        HomeLayout l = new HomeLayout();
        l.apply(min);
        check(Looks.matching(shipped, l) == min, "a picked look matches");
        l.clock = !l.clock;
        check(Looks.matching(shipped, l) == null, "a changed value: the user's own layout");
        l.apply(min);
        l.rows = 1;
        check(Looks.matching(shipped, l) == null, "another row count: the user's own layout");
        // every shipped look has its name in English and in every language of Home
        File res = new File(System.getProperty("home.dir"), "res");
        for (File dir : res.listFiles()) {
            if (!dir.getName().startsWith("values")) continue;
            File s = new File(dir, "strings_layout.xml");
            if (!dir.getName().equals("values") && !new File(dir, "strings.xml").exists()) continue;
            String t = s.exists() ? new String(Files.readAllBytes(s.toPath()), "UTF-8") : "";
            for (Looks.Look k : shipped) check(t.contains("name=\"home_look_" + k.id + "\""), dir.getName() + " names look " + k.id);
        }
    }

    static void dock() {
        HomeLayout l = new HomeLayout();
        l.order.addAll(Arrays.asList("b", "folder:f1", "a"));
        HomeLayout.Folder f = new HomeLayout.Folder("f1", "F");
        f.apps.addAll(Arrays.asList("c", "gone"));
        l.folders.add(f);
        eq(Dock.tokens(Arrays.asList("b", "a", "c", "d"), l), Arrays.asList("b", "folder:f1", "a", "d"), "folder in its place, the rest after");
        eq(Dock.members(f, new HashSet<>(Arrays.asList("c"))), Collections.singletonList("c"), "only visible members");
        eq(Dock.tokens(Arrays.asList("b", "a", "d"), l), Arrays.asList("b", "a", "d"), "a folder without a visible app is not shown");
        HomeLayout none = new HomeLayout();
        eq(Dock.tokens(Arrays.asList("x", "y"), none), Arrays.asList("x", "y"), "no folders: the apps as they come");
    }

    static void plan() {
        LayoutPlan c = LayoutPlan.of(Looks.FULL, true, false, true, false, 1, 9);
        check(c.hero == Looks.FULL && c.shelfBottom == org.lumen.ui.Grid.SHELF_BOTTOM && c.upnextY == -1, "Classic: the shipped shelf");
        eq(c.dockY.length, 1, "one row");
        eq(c.dockY[0], org.lumen.ui.Grid.DOCK_Y, "the shipped dock");
        eq(c.rowCount[0], 9, "the single row holds all and slides");
        LayoutPlan cr = LayoutPlan.of(Looks.FULL, true, false, true, true, 1, 9);
        check(cr.hero == Looks.FULL && cr.upnextY == org.lumen.ui.Grid.UPNEXT_Y && cr.shelfBottom == org.lumen.ui.Grid.SHELF_ROW_BOTTOM,
                "Classic with a Continue row: the shipped places");
        LayoutPlan two = LayoutPlan.of(Looks.FULL, true, false, true, false, 2, 14);
        eq(two.dockY.length, 2, "two rows");
        eq(two.hero, Looks.SMALL, "two rows leave room for the small shelf only");
        eq(two.dockY[1] - two.dockY[0], org.lumen.ui.Grid.APPS_PITCH, "rows one pitch apart");
        eq(two.rowCount[0], LayoutPlan.PER_LINE, "first row full");
        eq(two.rowCount[1], 14 - LayoutPlan.PER_LINE, "the last row holds the rest");
        eq(two.rowOf(LayoutPlan.PER_LINE), 1, "item 7 is on row 2");
        LayoutPlan three = LayoutPlan.of(Looks.FULL, true, false, true, false, 3, 30);
        check(three.dockY.length == 3 && three.hero == Looks.NONE, "three rows: no hero");
        check(three.dockY[0] >= LayoutPlan.TOP_ROOM, "the top row stays below the corner");
        LayoutPlan few = LayoutPlan.of(Looks.NONE, false, false, false, false, 3, 4);
        eq(few.dockY.length, 1, "four apps make one row, never an empty one");
        LayoutPlan threeCont = LayoutPlan.of(Looks.NONE, true, false, true, true, 3, 30);
        check(threeCont.upnextY >= LayoutPlan.TOP_ROOM, "the Continue row always fits");
        eq(threeCont.dockY.length, 2, "a row of apps gives way to the Continue row");
        LayoutPlan calm = LayoutPlan.of(Looks.FULL, false, true, true, false, 1, 6);
        check(calm.calmClock && calm.hero == Looks.FULL, "calm screen with the big clock");
        LayoutPlan calmOff = LayoutPlan.of(Looks.FULL, false, true, false, false, 1, 6);
        check(!calmOff.calmClock && calmOff.hero == Looks.NONE, "the big clock off: nothing in its place");
        LayoutPlan calm2 = LayoutPlan.of(Looks.FULL, false, true, true, false, 2, 12);
        check(calm2.calmClock && calm2.hero == Looks.NONE && calm2.calmTop >= LayoutPlan.TOP_ROOM
                && calm2.calmTop + LayoutPlan.CALM_H <= calm2.dockY[0] - LayoutPlan.STILL_GAP, "two rows: the big clock moves up, above the rows");
        LayoutPlan calm3 = LayoutPlan.of(Looks.FULL, false, true, true, false, 3, 30);
        check(!calm3.calmClock, "three rows of apps leave no room for the big clock");
        eq(calm.calmTop, LayoutPlan.CALM_TOP, "the shipped calm screen keeps its clock where it was");
        LayoutPlan minimal = LayoutPlan.of(Looks.NONE, false, true, true, false, 3, 9);
        check(minimal.calmClock && minimal.hero == Looks.NONE && minimal.dockY.length == 2, "Minimal with nine apps: the big clock over two rows");
        LayoutPlan smallBusy = LayoutPlan.of(Looks.SMALL, true, false, true, false, 1, 6);
        check(!smallBusy.calmClock, "never the big clock next to a shelf");
        LayoutPlan nothing = LayoutPlan.of(Looks.FULL, false, false, true, false, 1, 6);
        eq(nothing.hero, Looks.NONE, "no pages: no hero");
        LayoutPlan small = LayoutPlan.of(Looks.SMALL, true, false, true, false, 1, 6);
        eq(small.hero, Looks.SMALL, "a small look stays small with room to spare");
        LayoutPlan empty = LayoutPlan.of(Looks.FULL, true, false, true, false, 2, 0);
        eq(empty.dockY.length, 0, "no apps: no rows");
        check(two.stillY(false) >= LayoutPlan.TOP_ROOM && two.stillY(false) + 302 <= two.dockY[0] - LayoutPlan.STILL_GAP + 1,
                "the small art frame stays below the corner and above the rows");
        LayoutPlan twoCont = LayoutPlan.of(Looks.FULL, true, false, true, true, 2, 14);
        check(twoCont.stillY(true) >= LayoutPlan.TOP_ROOM, "the small art frame never goes into the corner");
        eq(c.stillY(false), Vitrina.stillY(false), "Classic keeps the shipped frame place");
    }

    static void memory() {
        List<MemoryRules.Proc> p = new ArrayList<>();
        p.add(new MemoryRules.Proc(Arrays.asList("cached.app"), 400, false, 0));
        p.add(new MemoryRules.Proc(Arrays.asList("service.app"), 300, false, 0));
        p.add(new MemoryRules.Proc(Arrays.asList("player.app"), 125, false, 0));          // foreground service: playing
        p.add(new MemoryRules.Proc(Arrays.asList("visible.app"), 200, false, 0));
        p.add(new MemoryRules.Proc(Arrays.asList("org.lumen.tvinput"), 400, true, 0));    // system: never
        p.add(new MemoryRules.Proc(Arrays.asList("ime.app"), 400, false, 0));
        p.add(new MemoryRules.Proc(Arrays.asList("two.app"), 400, false, 0));
        p.add(new MemoryRules.Proc(Arrays.asList("two.app"), 100, false, 0));             // its other process is in front
        List<String> v = MemoryRules.victims(p, new HashSet<>(Arrays.asList("ime.app")));
        eq(v, Arrays.asList("cached.app", "service.app"), "only background processes of user apps, never a protected one");
        eq(MemoryRules.freedMb(100L << 20, 612L << 20), 512L, "freed MB");
        eq(MemoryRules.freedMb(600L << 20, 500L << 20), 0L, "never below zero");
    }

    static void net() {
        eq(NetIcon.of(false, false, false, false, -1), NetIcon.OFF, "no network");
        eq(NetIcon.of(true, true, true, true, 2), NetIcon.ETHERNET, "the cable wins");
        eq(NetIcon.of(true, true, false, true, 3), NetIcon.WIFI_3, "Wi-Fi bars");
        eq(NetIcon.of(true, true, false, true, 9), NetIcon.WIFI_4, "bars clamped");
        eq(NetIcon.of(true, true, false, false, 4), NetIcon.WIFI_1, "no internet: the weakest bars");
        eq(NetIcon.of(true, true, false, true, -1), NetIcon.WIFI_4, "unknown strength: full");
        check(NetIcon.vpn(true, true) && !NetIcon.vpn(false, true) && !NetIcon.vpn(true, false), "the key only with a VPN on a network");
    }

    static void backup() {
        Map<String, String> v = new HashMap<>();
        v.put("lumen_quick_menu", "{\"v\":1}");
        v.put(HomeLayout.KEY, new HomeLayout().write());
        v.put("other_key", "never");
        String text = BackupFile.write(v, "G0082", 1700000000000L);
        BackupFile.Backup b = BackupFile.read(text);
        check(b != null, "a written backup reads back");
        eq(b.parts.keySet(), new HashSet<>(BackupFile.KEYS), "every part, no other key");
        eq(b.parts.get("lumen_quick_menu"), "{\"v\":1}", "a part as it was");
        check(b.parts.containsKey("lumen_full_menu") && b.parts.get("lumen_full_menu") == null, "a default part is null (restore removes it)");
        eq(b.model, "G0082", "the model");
        check(BackupFile.read("{\"lumen_backup\":2,\"parts\":{\"lumen_full_menu\":null}}") == null, "a newer file is refused");
        check(BackupFile.read("{\"lumen_backup\":1,\"parts\":{\"lumen_full_menu\":5}}") == null, "a part that is not text: the whole file refused");
        check(BackupFile.read("{\"lumen_backup\":1,\"parts\":{\"lumen_quick_menu\":\"x\",\"lumen_full_menu\":5}}") == null,
                "a good part next to one that is not text: the whole file refused");
        check(BackupFile.read("{\"lumen_backup\":1,\"parts\":{\"evil\":\"x\"}}") == null, "only foreign keys: refused");
        check(BackupFile.read("not json") == null && BackupFile.read(null) == null, "not a backup");
        BackupFile.Backup f = BackupFile.read("{\"lumen_backup\":1,\"parts\":{\"evil\":\"x\",\"lumen_quick_menu\":\"q\"}}");
        check(f != null && !f.parts.containsKey("evil"), "a foreign key never travels");
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < BackupFile.MAX_PART + 10; i++) big.append('x');
        check(BackupFile.read("{\"lumen_backup\":1,\"parts\":{\"lumen_quick_menu\":\"" + big + "\"}}") == null, "an oversized part refused");
    }

    static void wiring() throws Exception {
        String act = read("src/org/lumen/home/HomeActivity.java");
        String store = read("src/org/lumen/home/data/LayoutStore.java");
        String clean = read("src/org/lumen/home/data/MemoryCleaner.java");
        String loader = read("src/org/lumen/home/data/HomeLoader.java");
        String man = read("AndroidManifest.xml");
        check(store.contains("Settings.Secure.putString(mApp.getContentResolver(), HomeLayout.KEY"), "LayoutStore writes the layout");
        for (String f : new String[]{"src/org/lumen/home/HomeActivity.java", "src/org/lumen/home/data/HomeLoader.java",
                "src/org/lumen/home/Prefs.java"}) {
            check(!read(f).contains("HomeLayout.KEY, ") && !read(f).contains("putString(getContentResolver(), HomeLayout.KEY"),
                    f + " never writes the layout itself");
        }
        check(clean.contains("killBackgroundProcesses") && !clean.contains("forceStopPackage"), "free memory never force stops");
        check(clean.contains("MemoryRules.victims"), "free memory asks the pure rule");
        check(clean.contains("VpnService.SERVICE_INTERFACE"), "VPN apps are protected");
        check(loader.contains("lay.showcase ? Trending.load(c)"), "recommendations off: no showcase read");
        check(loader.contains("lay.cont ? TvpSource.load"), "Continue off: TvProvider not read");
        check(act.contains("if (!mApp.layout().current().showcase) return;"), "recommendations off: nothing fetched");
        check(act.contains("Vitrina.ROUND_LAYOUT") && act.contains("lumen_ic_layout"), "Customize Home has its round button");
        check(act.contains("mStatus.setNet(NetIcon.of("), "the corner shows the network mark");
        check(act.contains("registerContentObserver(Settings.Secure.getUriFor(HomeLayout.KEY)"), "a restored layout shows at once");
        check(man.contains("android.permission.FORCE_STOP_PACKAGES") && man.contains("android.permission.REAL_GET_TASKS"),
                "the permissions free memory needs");
        check(man.contains("org.lumen.home.action.CUSTOMIZE"), "the projector menu can open the backup page");
        check(act.contains("R.string.apps_hint"), "the held OK is taught on the Apps page");
    }
}
