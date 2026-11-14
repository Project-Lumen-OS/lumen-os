// SPDX-License-Identifier: Apache-2.0
package org.lumen.stats;

import android.os.SystemProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Host checks of the shared statistics client (test/run.sh). */
public final class PingTest {
    private static int fails, checks;

    private static void eq(String what, Object want, Object got) {
        checks++;
        if (!String.valueOf(want).equals(String.valueOf(got))) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    private static String read(Path p) throws Exception { return new String(Files.readAllBytes(p), StandardCharsets.UTF_8); }

    public static void main(String[] a) throws Exception {
        Path g = Paths.get(a[0]);
        String id = Ping.newId();
        eq("uuid form", true, id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        eq("uuid random", false, id.equals(Ping.newId()));
        // the body: exactly the documented fields, the channel of the build, trial as a JSON boolean
        eq("body", "{\"v\":3,\"id\":\"" + id + "\",\"serial\":\"CZW0001\",\"model\":\"goat_elegant\","
                + "\"xgimi_model\":\"G0130\",\"version\":\"1.0.8-beta3\",\"version_code\":1000803,"
                + "\"build\":\"lumen-hb-1.0.8-beta3\",\"channel\":\"beta\",\"trial\":false,\"prev_version\":\"1.0.8-beta2\","
                + "\"prev_version_code\":1000802,\"method\":\"ota_delta\"}",
                Ping.body(id, "CZW0001", "goat_elegant", "G0130", "1.0.8-beta3", 1000803, "lumen-hb-1.0.8-beta3", false,
                        "1.0.8-beta2", 1000802, "ota_delta"));
        eq("body trial stable", true, Ping.body(id, "", "g0111", "G0111", "1.0.10", 1001099, "b", true, "", 0, "")
                .contains(",\"channel\":\"stable\",\"trial\":true,"));
        eq("escape", "\"a\\\"b\\\\c\\u000a\"", Ping.str("a\"b\\c\n"));
        eq("null", "\"\"", Ping.str(null));
        eq("day utc", "1970-01-01", Ping.day(23L * 3600 * 1000));
        eq("day utc next", "1970-01-02", Ping.day(24L * 3600 * 1000));
        eq("default on", true, Ping.DEFAULT_ON);
        for (String u : Ping.URLS) eq("https " + u, true, u.startsWith("https://"));
        eq("configured = a URL is set", Ping.URLS.length > 0, Ping.configured());
        // the channel is the build's, never a switch
        eq("beta", "beta", Ping.channel("1.0.10-beta9"));
        eq("rc", "beta", Ping.channel("1.0.11-rc2"));
        eq("model prefix beta", "beta", Ping.channel("x50max-1.0.10-beta3"));
        eq("final", "stable", Ping.channel("1.0.10"));
        eq("prefix final", "stable", Ping.channel("rs30-1.0.10"));
        eq("test build", "stable", Ping.channel("play6-t40"));
        eq("null channel", "stable", Ping.channel(null));
        // version code of an image without one (HB scheme)
        eq("vc beta", 1001003, Ping.versionCode("x50-1.0.10-beta3"));
        eq("vc final", 1001099, Ping.versionCode("1.0.10"));
        eq("vc rc", 1001102, Ping.versionCode("rs30-1.0.11-rc2"));
        eq("vc none", 0, Ping.versionCode("play6-t40"));
        eq("vc null", 0, Ping.versionCode(null));
        // allowed(configured, on, setupDone, hold)
        eq("all set", true, Ping.allowed(true, true, true, false));
        eq("held by the host", false, Ping.allowed(true, true, true, true));
        eq("switch off", false, Ping.allowed(true, false, true, false));
        eq("setup running", false, Ping.allowed(true, true, false, false));
        eq("no server", false, Ping.allowed(false, true, true, false));
        // due(updated, newBoot, newDay, sinceTry): once per start, once a day, at once after an update
        long ok = Ping.RETRY_MS;
        eq("new boot", true, Ping.due(false, true, false, ok));
        eq("same boot, same day", false, Ping.due(false, false, false, ok));
        eq("same boot, new day", true, Ping.due(false, false, true, ok));
        eq("updated", true, Ping.due(true, false, false, ok));
        eq("new boot, try just failed", false, Ping.due(false, true, false, ok - 1));
        // update fields: first ping, an update with its recorded method, an update by the installer, no change
        eq("first", "|0|first", String.join("|", Ping.update(0, "", 10012, "b2", "", "")));
        eq("ota", "1.0.9|10011|ota_delta", String.join("|", Ping.update(10011, "1.0.9", 10012, "b2", "b2", "ota_delta")));
        eq("installer", "1.0.9|10011|installer", String.join("|", Ping.update(10011, "1.0.9", 10012, "b2", "b1", "ota_full")));
        eq("no build", "1.0.9|10011|installer", String.join("|", Ping.update(10011, "1.0.9", 10012, "", "", "ota_full")));
        eq("same", "|0|", String.join("|", Ping.update(10012, "1.0.10", 10012, "b2", "b2", "ota_delta")));
        // the model from the registry: the X50 family reports the Z9X's product device
        eq("Z9X", "horse_popular", Device.model("G0082", "", "mt5877"));
        eq("X50 Ultra on the Z9X base", "g0111", Device.model("G0111", "horse_popular", "mt5877"));
        eq("X50 Ultra Max", "g0100", Device.model("G0100", "g0111", ""));
        eq("RS30", "g0155", Device.model("G0155", "g0155", ""));
        eq("Play 6 (image says play6)", "dragon_healthy", Device.model("G0058", "play6", ""));
        eq("HB G0148", "goat_elegant", Device.model("G0148", "goat_elegant", ""));
        eq("unknown model: the image's", "goat_elegant", Device.model("G0118", "goat_elegant", ""));
        eq("nothing: vendor device", "mt5877", Device.model("", "", "mt5877"));
        // the release of an image without an updater
        SystemProperties.VALUES.clear();
        SystemProperties.VALUES.put("ro.z9x.version", "1.0.9");
        SystemProperties.VALUES.put("ro.z9x.version_code", "10011");
        SystemProperties.VALUES.put("ro.z9x.build_id", "lumen-1.0.9-20261106anp");
        Release r = Device.release();
        eq("Z9X props only", "1.0.9|10011|lumen-1.0.9-20261106anp", r.version + "|" + r.versionCode + "|" + r.build);
        // the X50 image: the Z9X 1.0.9 base keeps its ro.z9x names, the X50 sets ro.lumen.version and build id only
        SystemProperties.VALUES.put("ro.lumen.version", "x50-1.0.10-beta3");
        SystemProperties.VALUES.put("ro.lumen.build_id", "lumen-x50-1.0.10-beta3-x");
        r = Device.release();
        eq("X50 over the Z9X base", "x50-1.0.10-beta3|1001003|lumen-x50-1.0.10-beta3-x", r.version + "|" + r.versionCode + "|" + r.build);
        SystemProperties.VALUES.put("ro.lumen.version_code", "1001003");
        SystemProperties.VALUES.put("ro.lumen.version", "hb-1.0.10-beta11");
        eq("own version code wins", 1001003, Device.release().versionCode);
        SystemProperties.VALUES.put("ro.gsid.image_running", "1");
        eq("trial", true, Device.trial());
        SystemProperties.VALUES.put("ro.gsid.image_running", "");
        eq("installed", false, Device.trial());
        // every host: one client, wired the same way
        String z = read(g.resolve("apps/Z9xUpdater/src/org/lumen/updater/Stats.java"));
        eq("updater sends through the module", true, z.contains("StatsClient.maybeSend(c, new State(st), new Release(Ota.currentVersion(), Ota.currentVersionCode(),")
                && z.contains("Ota.buildId()), st.noticeDue());"));
        eq("updater keeps no body of its own", false, z.contains("\"v\\\":") || z.contains("Build.getSerial"));
        for (String rcp : new String[] {"devices/horse_popular/app/Z9xUpdater.layers", "apps/LumenUpdater/LumenUpdater.layers",
                "devices/goat_elegant/app/LumenUpdater.layers", "devices/dragon_healthy/app/Play6Updater.layers",
                "devices/dragon_healthy/app/Play6Projector.layers", "devices/g0111/app/X50Projector.layers",
                "devices/g0100/app/X50Projector.layers", "devices/g0155/app/X50Projector.layers"}) {
            eq(rcp + " names lumen-stats", true, read(g.resolve(rcp)).contains("\nmodule    lumen-stats\n"));
        }
        for (String app : new String[] {"Play6Projector", "X50Projector"}) {
            String mf = read(g.resolve("apps/" + app + "/AndroidManifest.xml"));
            eq(app + " declares StatsJob", true, mf.contains("android:name=\"org.lumen.stats.StatsJob\"")
                    && mf.contains("android:permission=\"android.permission.BIND_JOB_SERVICE\""));
            eq(app + " INTERNET", true, mf.contains("<uses-permission android:name=\"android.permission.INTERNET\" />"));
            eq(app + " serial permission", true, mf.contains("android.permission.READ_PRIVILEGED_PHONE_STATE"));
        }
        eq("Play 6 schedules at boot", true, read(g.resolve("apps/Play6Projector/src/org/lumen/play6/BootReceiver.java"))
                .contains("if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) org.lumen.stats.StatsJob.schedule(ctx);"));
        String xb = read(g.resolve("apps/X50Projector/src/org/lumen/x50/projector/BootReceiver.java"));
        int sched = xb.indexOf("org.lumen.stats.StatsJob.schedule(c);"), gate = xb.indexOf("if (!App.isX50()) return;");
        eq("X50 family schedules at boot, before the model gate", true, sched > 0 && gate > sched);
        eq("Play 6 switch", true, read(g.resolve("apps/Play6Projector/src/org/lumen/projector/SettingsActivity.java"))
                .contains("stats.setOn(!stats.on());"));
        eq("X50 switch", true, read(g.resolve("apps/X50Projector/src/org/lumen/x50/projector/MenuActivity.java"))
                .contains("st.setOn(!st.on());"));
        System.out.println(fails == 0 ? "PingTest: " + checks + " passed" : "PingTest: " + fails + " of " + checks + " failed");
        if (fails != 0) System.exit(1);
    }
}
