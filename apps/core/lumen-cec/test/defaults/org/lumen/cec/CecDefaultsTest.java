// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.cec;

import org.lumen.testkit.Check;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Host checks of the stock HDMI-CEC defaults (apps/core/lumen-cec CecDefaults, CecSetting): the real table of every
 * image (devices/_image/cec_defaults.tsv) and its stock values per device, the parser's refusals, the one-time step
 * over a fake unit, the same settings and switches as tools/devices/cec_defaults.py, and the wiring (the menu marks
 * the user's rows, both projector apps run the step at start, the Android glue reads the menu profile).
 * java org.lumen.cec.CecDefaultsTest GSI_ROOT
 */
public final class CecDefaultsTest {
    private static final Check C = new Check("cec defaults");

    /** A unit with one value per switch, the calls recorded. */
    private static final class Fake implements CecDefaults.Unit {
        final Map<CecSetting, Boolean> on = new EnumMap<>(CecSetting.class);
        final Set<CecSetting> held = EnumSet.noneOf(CecSetting.class);
        final Set<CecRow> chose = EnumSet.noneOf(CecRow.class);
        boolean refuse;
        final List<String> calls = new ArrayList<>();

        @Override public Boolean on(CecSetting s) { calls.add("on " + s.key); return on.get(s); }
        @Override public boolean set(CecSetting s, boolean v) {
            calls.add("set " + s.key + " " + v);
            if (refuse) return false;
            on.put(s, v);
            return true;
        }
        @Override public boolean heldOff(CecSetting s) { return held.contains(s); }
        @Override public void dropHold(CecSetting s) { calls.add("drop " + s.key); held.remove(s); }
        @Override public boolean userChose(CecRow r) { return chose.contains(r); }
    }

    private static boolean refused(String text) {
        try {
            CecDefaults.parse(text);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static String base() {
        StringBuilder b = new StringBuilder();
        for (CecSetting s : CecSetting.values()) b.append("default ").append(s.key).append(s.writable() ? " 0\n" : " x\n");
        return b.toString();
    }

    public static void main(String[] a) throws Exception {
        File g = new File(a[0]);
        CecDefaults t = CecDefaults.parse(Check.read(new File(g, "devices/_image/cec_defaults.tsv")));
        CecSetting wake = CecSetting.TV_WAKE_ON_ONE_TOUCH_PLAY, standby = CecSetting.TV_SEND_STANDBY_ON_SLEEP;

        // ---- the real table: the stock values every image gets
        for (String d : new String[]{"horse_popular", "goat_elegant", "g0111", "g0100", "g0155", "h0061", "h0062", "h0063"}) {
            C.eq(d + ": no <Standby> to the HDMI devices when it sleeps (stock)", "0", t.stock(d, standby));
            C.eq(d + ": no wake on <Image View On> (stock)", "0", t.stock(d, wake));
            C.eq(d + ": HDMI-CEC on (stock)", "1", t.stock(d, CecSetting.HDMI_CEC_ENABLED));
            C.eq(d + ": set menu language as stock", "1", t.stock(d, CecSetting.SET_MENU_LANGUAGE));
            C.eq(d + ": power control mode as stock", "to_tv_and_audio_system", t.stock(d, CecSetting.POWER_CONTROL_MODE));
        }
        C.eq("Play 6 (Android 11 stock): wakes on <Image View On>", "1", t.stock("dragon_healthy", wake));
        C.eq("Play 6: no <Standby> when it sleeps", "0", t.stock("dragon_healthy", standby));
        C.eq("HB: the wake switch goes back to stock once", Arrays.asList(wake), t.resets("goat_elegant"));
        for (String d : new String[]{"horse_popular", "g0111", "g0100", "g0155", "h0061", "h0062", "h0063", "dragon_healthy"}) {
            C.eq(d + ": nothing to reset (a stored value is the user's)", 0, t.resets(d).size());
        }

        // ---- the parser
        C.ok("parse: the base table", !refused(base()));
        C.ok("parse: a missing default is refused", refused(base().replace("default set_menu_language x\n", "")));
        C.ok("parse: a default twice is refused", refused(base() + "default tv_wake_on_one_touch_play 1\n"));
        C.ok("parse: an unknown setting is refused", refused(base() + "default tv_auto_power 1\n"));
        C.ok("parse: an unknown directive is refused", refused(base() + "value tv_wake_on_one_touch_play 1\n"));
        C.ok("parse: a switch value other than 0 / 1 is refused", refused(base() + "device x tv_wake_on_one_touch_play on\n"));
        C.ok("parse: a reset of a setting the app never writes is refused", refused(base() + "reset x power_control_mode\n"));
        C.ok("parse: a reset twice is refused", refused(base() + "reset x tv_wake_on_one_touch_play\nreset x tv_wake_on_one_touch_play\n"));
        C.ok("parse: a field too many is refused", refused(base() + "reset x tv_wake_on_one_touch_play now\n"));
        C.ok("parse: a comment and blank lines", !refused("# x\n\n" + base() + "  # y\n"));
        C.eq("a device line wins over the default", "1",
                CecDefaults.parse(base() + "device p6 tv_wake_on_one_touch_play 1\n").stock("p6", wake));
        C.eq("another device keeps the default", "0",
                CecDefaults.parse(base() + "device p6 tv_wake_on_one_touch_play 1\n").stock("hb", wake));

        // ---- the one-time step (the HB line of the real table)
        Fake u = new Fake();
        u.on.put(wake, true);
        List<String> log = new ArrayList<>();
        C.ok("HB, wake stored on by the old image: done", t.apply("goat_elegant", u, log));
        C.eq("HB: switched to stock off", Arrays.asList("on tv_wake_on_one_touch_play", "set tv_wake_on_one_touch_play false"), u.calls);
        C.ok("HB: logged", log.size() == 1 && log.get(0).contains("1 -> stock 0"));

        u = new Fake();
        u.on.put(wake, false);
        C.ok("HB, already off: done", t.apply("goat_elegant", u, new ArrayList<>()));
        C.eq("HB, already off: nothing written", Arrays.asList("on tv_wake_on_one_touch_play"), u.calls);

        u = new Fake();
        u.on.put(wake, false);
        u.held.add(wake);
        C.ok("HB, off for a guarded sleep with a restore pending: done", t.apply("goat_elegant", u, new ArrayList<>()));
        C.eq("HB, held: the restore is dropped, nothing else", Arrays.asList("on tv_wake_on_one_touch_play", "drop tv_wake_on_one_touch_play"), u.calls);

        u = new Fake();
        u.on.put(wake, true);
        u.chose.add(CecRow.WAKE);
        C.ok("HB, the user switched the row in the menu: done", t.apply("goat_elegant", u, new ArrayList<>()));
        C.eq("HB, the user's choice: not even read", 0, u.calls.size());

        u = new Fake();
        u.chose.add(CecRow.SEND_STANDBY);
        u.on.put(wake, true);
        t.apply("goat_elegant", u, new ArrayList<>());
        C.ok("HB: a choice on another row does not keep the wake", u.calls.contains("set tv_wake_on_one_touch_play false"));

        u = new Fake();
        C.ok("HB, no CEC service yet: not done", !t.apply("goat_elegant", u, new ArrayList<>()));
        C.eq("HB, no CEC service: nothing written", Arrays.asList("on tv_wake_on_one_touch_play"), u.calls);

        u = new Fake();
        u.on.put(wake, true);
        u.held.add(wake);
        u.refuse = true;
        C.ok("HB, the framework refuses: not done", !t.apply("goat_elegant", u, new ArrayList<>()));
        C.ok("HB, refused: the pending restore stays", u.held.contains(wake));

        u = new Fake();
        u.on.put(wake, true);
        u.on.put(standby, true);
        C.ok("Z9X: done", t.apply("horse_popular", u, new ArrayList<>()));
        C.eq("Z9X: no reset line, no call", 0, u.calls.size());

        CecDefaults p6 = CecDefaults.parse(base() + "device p6 tv_wake_on_one_touch_play 1\nreset p6 tv_wake_on_one_touch_play\n"
                + "reset p6 tv_send_standby_on_sleep\n");
        u = new Fake();
        u.on.put(wake, false);
        u.on.put(standby, true);
        C.ok("a stock-on device: done", p6.apply("p6", u, new ArrayList<>()));
        C.eq("a stock-on device: wake on, standby off, in table order",
                Arrays.asList("on tv_wake_on_one_touch_play", "set tv_wake_on_one_touch_play true",
                        "on tv_send_standby_on_sleep", "set tv_send_standby_on_sleep false"), u.calls);
        u = new Fake();
        u.on.put(wake, false);
        u.held.add(wake);
        u.on.put(standby, false);
        p6.apply("p6", u, new ArrayList<>());
        C.eq("a stock-on device, wake held off: already stock, the restore stays",
                Arrays.asList("on tv_wake_on_one_touch_play", "on tv_send_standby_on_sleep"), u.calls);

        // ---- the same settings and switches as the image tool
        String tool = Check.read(new File(g, "tools/devices/cec_defaults.py"));
        Set<String> py = new LinkedHashSet<>();
        Matcher m = Pattern.compile("^    \\(\"([a-z_]+)\", \\(", Pattern.MULTILINE).matcher(tool);
        while (m.find()) py.add(m.group(1));
        Set<String> java = new LinkedHashSet<>();
        for (CecSetting s : CecSetting.values()) java.add(s.key);
        C.eq("CecSetting = the settings of cec_defaults.py", py, java);
        Matcher r = Pattern.compile("RESETTABLE = \\(([^)]*)\\)").matcher(tool);
        Set<String> pyw = new LinkedHashSet<>();
        if (r.find()) for (String x : r.group(1).split(",")) if (!x.trim().isEmpty()) pyw.add(x.trim().replace("\"", ""));
        Set<String> jw = new LinkedHashSet<>();
        for (CecSetting s : CecSetting.values()) if (s.writable()) jw.add(s.key);
        C.eq("CecSetting.writable = RESETTABLE of cec_defaults.py", pyw, jw);
        C.eq("the rows of the two switches", Arrays.asList(CecRow.SEND_STANDBY, CecRow.WAKE),
                Arrays.asList(standby.row, wake.row));

        // ---- the wiring
        String page = Check.read(new File(g, "apps/core/lumen-cec/src/org/lumen/projector/cec/CecPage.java"));
        C.ok("CecPage: a change that went through marks the row as the user's",
                page.contains("if (ok) CecSettings.setUserChose(app, r);"));
        C.ok("CecPage: the master switch marks MASTER", page.contains("change(CecRow.MASTER, () -> driver.setMaster(app, w))"));
        C.ok("CecPage: every toggle marks its own row", page.contains("(row, w) -> change(r, () -> write.set(w))"));
        String glue = Check.read(new File(g, "apps/core/lumen-cec/src/org/lumen/projector/cec/CecStockDefaults.java"));
        String menu = Check.read(new File(g, "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java"));
        C.ok("CecStockDefaults reads the menu profile the recipes ship",
                glue.contains("MENU_ASSET = \"lumen_menu/menu.json\"") && menu.contains("PROFILE_ASSET = \"lumen_menu/menu.json\""));
        C.ok("CecStockDefaults marks the step done only when complete",
                glue.contains("if (done) p.edit().putInt(K_DONE, CecDefaults.VERSION).commit();"));
        C.ok("CecStockDefaults: the held wake is CecPolicy's restore pref",
                glue.contains("CecSettings.restoreOtpPending(c)") && glue.contains("CecSettings.setRestoreOtpPending(c, false)"));
        String policy = Check.read(new File(g, "apps/ProjectorBase/src/org/lumen/projector/cec/CecPolicy.java"));
        int at = policy.indexOf("private static void startup() {");
        C.ok("Z9X / HB: the step runs first at the start of the power policy", at > 0
                && policy.indexOf("CecStockDefaults.applyOnce(sApp);", at) > at
                && policy.indexOf("CecStockDefaults.applyOnce(sApp);", at) < policy.indexOf("restoreOtpPending(sApp)", at));
        String x50 = Check.read(new File(g, "apps/X50Projector/src/org/lumen/x50/projector/App.java"));
        C.ok("X50 family: the step runs at the app start, off the main thread",
                x50.contains("new Thread(() -> org.lumen.projector.cec.CecStockDefaults.applyOnce(this), \"x50-cec-defaults\").start();"));
        C.done();
    }
}
