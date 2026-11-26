// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Behaviour transcript of one assembled updater (test/parity/run.sh): the same scenarios go through the public
 * entry points every updater of every model has (Ota, Channels, Preflight, UpdateManifest), and the printed lines
 * are compared with the transcript recorded from the app before the shared module existed (golden/&lt;app&gt;.txt).
 * Only entry points that stay are used, so this file never changes with the code under test.
 * Usage: java org.lumen.updater.Probe TEMPDIR
 */
public final class Probe {
    private Probe() {}

    static final String R = "https://github.com/Project-Lumen-OS/lumen-os/releases/download/";
    static final StringBuilder OUT = new StringBuilder();
    static String tmp;

    static void p(String s) { OUT.append(s.replace(tmp, "<TMP>")).append('\n'); }

    interface Call { Object run() throws Exception; }

    static String show(Call c) {
        try {
            return String.valueOf(fmt(c.run()));
        } catch (Throwable t) {
            Throwable e = t instanceof java.lang.reflect.InvocationTargetException ? t.getCause() : t;
            String m = e.getMessage();
            return "EXC " + e.getClass().getSimpleName() + (m == null ? "" : ": " + m);
        }
    }

    static Object fmt(Object o) {
        if (o == null) return "null";
        if (o instanceof String[]) return Arrays.toString((String[]) o);
        if (o instanceof Preflight.Block) {
            Preflight.Block b = (Preflight.Block) o;
            return "Block(" + b.res + ", " + b.arg + ")";
        }
        if (o instanceof File) return ((File) o).getPath();
        return o;
    }

    /** A clean property set: the scenario's values only. */
    static void props(String... kv) {
        SystemProperties.VALUES.clear();
        SystemProperties.DENY.clear();
        SystemProperties.onSet = () -> { };
        for (int i = 0; i + 1 < kv.length; i += 2) SystemProperties.VALUES.put(kv[i], kv[i + 1]);
        Ota.setBeta(null);
    }

    static final Map<String, String[]> SCENARIOS = new LinkedHashMap<>();

    static {
        SCENARIOS.put("empty", new String[] {});
        SCENARIOS.put("z9x-stable", new String[] {"ro.z9x.version", "1.0.10", "ro.z9x.version_code", "10012",
                "ro.z9x.build_id", "lumen-1.0.10-20261111bnp", "ro.z9x.ota.manifest_url", R + "z9x/update-public-nogms.json",
                "ro.vendor.build.version.incremental", "v6.15.58"});
        SCENARIOS.put("z9x-beta-image", new String[] {"ro.z9x.version", "1.0.11-beta3", "ro.z9x.version_code", "10013",
                "ro.z9x.build_id", "lumen-1.0.11-beta3-20261113anp", "ro.z9x.ota.manifest_url", R + "z9x/update-public-beta.json"});
        SCENARIOS.put("z9x-http", new String[] {"ro.z9x.version", "1.0.9", "ro.z9x.ota.manifest_url",
                "http://example.org/update-public-nogms.json"});
        SCENARIOS.put("z9x-public-gms", new String[] {"ro.z9x.version", "1.0.9", "ro.z9x.version_code", "10011",
                "ro.z9x.ota.manifest_url", R + "z9x/update-public.json"});
        SCENARIOS.put("hb-stable", new String[] {"ro.lumen.version", "1.0.10-beta12", "ro.lumen.version_code", "1001012",
                "ro.lumen.build_id", "lumen-hb-1.0.10-beta12-202611131410", "ro.lumen.ota.device", "hb",
                "ro.lumen.ota.manifest_url", R + "z9x-hb/update-hb-nogms.json", "ro.vendor.build.version.incremental", "v6.17.12"});
        SCENARIOS.put("hb-test-image", new String[] {"ro.lumen.version", "1.0.10-beta12", "ro.lumen.ota.device", "hb",
                "ro.lumen.ota.manifest_url", R + "z9x-hb/update-hb-test.json?x=1", "ro.lumen.ota.guide",
                "https://example.org/guide/hb//"});
        SCENARIOS.put("hb-no-device", new String[] {"ro.lumen.version", "1.0.10", "ro.lumen.ota.manifest_url",
                R + "z9x-hb/update-hb-nogms.json"});
        SCENARIOS.put("hb-odd-device", new String[] {"ro.lumen.version", "1.0.10-rc2", "ro.lumen.ota.device", "Hb X",
                "ro.lumen.ota.manifest_url", R + "z9x-hb/update-other.json"});
        SCENARIOS.put("play6", new String[] {"ro.lumen.version", "1.0.10", "ro.lumen.version_code", "10010",
                "ro.lumen.build_id", "lumen-play6-1.0.10", "ro.lumen.ota.manifest_url", R + "play6/update-play6.json"});
        SCENARIOS.put("play6-no-url", new String[] {"ro.lumen.version", "1.0.9"});
        SCENARIOS.put("mixed", new String[] {"ro.z9x.version", "1.0.9", "ro.z9x.version_code", "10011",
                "ro.lumen.version", "1.0.10-beta4", "ro.lumen.version_code", "1001004", "ro.lumen.ota.device", "x50",
                "ro.z9x.ota.manifest_url", R + "z9x/update-public-nogms.json",
                "ro.lumen.ota.manifest_url", R + "x50/update-x50-nogms.json"});
        SCENARIOS.put("x50-beta4", new String[] {"ro.lumen.version", "x50-1.0.10-beta4", "ro.z9x.version",
                "x50-1.0.10-beta4", "ro.z9x.version_code", "0", "ro.lumen.device", "g0111", "ro.boot.slot_suffix", "_b",
                "sys.z9x.vbmeta_other.flags", "0"});
    }

    static final String[] CHANNELS = {"public-nogms", "public-beta", "public", "stable", "hb-nogms", "hb-test", "play6",
            "x50-beta", "x50-nogms", "", "update"};

    static void channels() {
        p("## channels");
        for (Map.Entry<String, String[]> e : SCENARIOS.entrySet()) {
            for (Boolean beta : new Boolean[] {null, Boolean.TRUE, Boolean.FALSE}) {
                props(e.getValue());
                Ota.setBeta(beta);
                p("[" + e.getKey() + " beta=" + beta + "]");
                p("  image=" + show(Ota::imageManifestUrl) + " url=" + show(Ota::manifestUrl)
                        + " file=" + show(Ota::channelFile));
                p("  betaAvailable=" + show(Ota::betaAvailable) + " betaOn=" + show(Ota::betaOn));
                p("  read=" + show(Channels::read) + " files=" + show(Channels::files));
                StringBuilder a = new StringBuilder();
                for (String c : CHANNELS) a.append(c.isEmpty() ? "''" : c).append('=').append(show(() -> Channels.accepts(c))).append(' ');
                p("  accepts " + a.toString().trim());
                if (beta == null) {
                    p("  version=" + show(Ota::currentVersion) + " code=" + show(Ota::currentVersionCode)
                            + " build=" + show(Ota::buildId));
                    p("  guides=" + show(() -> Ota.GUIDE_VBMETA_EN) + " " + show(() -> Ota.GUIDE_VBMETA_RU) + " "
                            + show(() -> Ota.GUIDE_RESCUE_EN) + " " + show(() -> Ota.GUIDE_RESCUE_RU));
                }
            }
        }
        for (String u : new String[] {null, "", "x", R + "z9x/update-public-nogms.json", R + "a/update-x.json?q=1",
                R + "a/update-.json", R + "a/update-hb-test.json", R + "a/notes.txt", "update-a.json"}) {
            props();
            p("channelFile(" + u + ")=" + show(() -> Ota.channelFile(u)));
        }
        p("pick " + show(() -> Channels.pick(new int[] {})) + " " + show(() -> Channels.pick(new int[] {5, 7}))
                + " " + show(() -> Channels.pick(new int[] {Channels.MISSING, 3})) + " "
                + show(() -> Channels.pick(new int[] {9, Channels.MISSING})) + " " + show(() -> Channels.pick(new int[] {4, 4})));
    }

    static void versions() {
        p("## versions");
        String[] vs = {null, "", "1", "1.0", "1.0.9", "1.0.10", "1.0.11-beta3", "1.0.8-rc2", "1.0.8-rc49", "1.0.8-beta50",
                "1.0.10-beta0", "1.0.10-beta99", "x50-1.0.10-beta4", "1.2.3.4", "1.100.0", "1.0.100", "-1.0.0", "abc",
                " 1.0.7 ", "1.0.9-final", "2.0"};
        props();
        for (String v : vs) p("versionCode(" + v + ")=" + show(() -> Ota.versionCode(v)));
        String[][] cur = {{"ro.z9x.version_code", "10012"}, {"ro.z9x.version_code", " 10012 "}, {"ro.z9x.version_code", "x"},
                {"ro.z9x.version", "1.0.9"}, {"ro.lumen.version_code", "1001012"}, {"ro.lumen.version", "1.0.10-beta12"},
                {"ro.lumen.version", "1.0.10"}};
        for (String[] c : cur) {
            props(c);
            p("current " + c[0] + "=" + c[1] + " -> " + show(Ota::currentVersionCode) + " " + show(Ota::currentVersion));
        }
    }

    static void slots() {
        p("## slots");
        String[][] cases = {{}, {"ro.boot.slot_suffix", "_a"}, {"ro.boot.slot_suffix", "_b"}, {"sys.z9x.slot", "_b"},
                {"ro.boot.slot_suffix", "_c"}, {"ro.boot.slot_suffix", "_a", "sys.z9x.vbmeta_other.flags", "3"},
                {"sys.z9x.vbmeta_other.flags", " 2 "}, {"sys.z9x.vbmeta_other.flags", "x"}};
        for (String[] c : cases) {
            props(c);
            p(Arrays.toString(c) + " slot=" + show(Ota::slot) + " other=" + show(Ota::otherSlot) + " letter="
                    + show(() -> Ota.slotLetter(Ota.slot())) + " flags=" + show(Ota::otherVbmetaFlags));
        }
        for (String s : new String[] {null, "", "_a", "_b", "a", "_ab"}) p("slotLetter(" + s + ")=" + show(() -> Ota.slotLetter(s)));
    }

    static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    static String pkg(String type, String from, String url, long size, long off, long psize, String meta, boolean props) {
        StringBuilder b = new StringBuilder("{\"type\":\"" + type + "\"");
        if (from != null) b.append(",\"from_build_id\":\"").append(from).append('"');
        b.append(",\"url\":\"").append(url).append("\",\"size\":").append(size).append(",\"sha256\":\"").append(SHA.toUpperCase(Locale.ROOT)).append('"');
        if (off >= 0) b.append(",\"payload_offset\":").append(off).append(",\"payload_size\":").append(psize);
        if (meta != null) b.append(",\"metadata_url\":\"").append(meta).append("\",\"metadata_size\":77,\"metadata_sha256\":\"").append(SHA).append('"');
        if (props) b.append(",\"payload_properties\":[\"FILE_HASH=x\",\"FILE_SIZE=9\",\"METADATA_HASH=y\",\"METADATA_SIZE=7\",\"OTHER=1\"]");
        return b.append('}').toString();
    }

    static String manifest(int schema, String device, String channel, int vc, String req, String pkgs) {
        return "{\"schema\":" + schema + ",\"device\":\"" + device + "\"" + (channel == null ? "" : ",\"channel\":\"" + channel + "\"")
                + ",\"version\":\"9.9.9\",\"version_code\":" + vc + ",\"build_id\":\"lumen-new\",\"build_utc\":1793911544"
                + (req == null ? "" : ",\"requires\":" + req)
                + ",\"changelog\":{\"en\":\"English\",\"ru\":\"Русский\",\"pt-BR\":\"Português\"},\"packages\":[" + pkgs + "]}";
    }

    static final String AB_FULL = pkg("full", null, R + "z9x-1.0.10/lumen-os-1.0.10-ota-full.zip", 1000, 100, 800,
            R + "z9x-1.0.10/payload_metadata.bin", true);
    static final String AB_DELTA = pkg("delta", "lumen-1.0.9-20261106anp", R + "z9x-1.0.10/ota delta.zip", 500, 50, 400,
            R + "z9x-1.0.10/m.bin", true);
    static final String REC_FULL = pkg("full", null, R + "play6-1.0.10/lumen-play6-ota.zip", 1000, -1, 0, null, false);

    static void manifests() {
        p("## manifest parsing");
        String[] corpus = {
            manifest(1, "z9x", "public-nogms", 10013, "{\"vendor_incremental\":[\"v6.15.58\",\"v6.15.62\"],\"blobs_set\":[\"z9x-v61558-b\"],\"min_version_code\":10000}", AB_FULL + "," + AB_DELTA),
            manifest(1, "play6", "play6", 10011, null, REC_FULL),
            manifest(1, "hb", null, 1001013, "{}", AB_FULL),
            manifest(2, "z9x", "public-nogms", 1, null, AB_FULL),
            manifest(1, "z9x", "public-nogms", 1, null, ""),
            "{\"schema\":1,\"device\":\"z9x\",\"version\":\"1\",\"build_id\":\"b\",\"packages\":[]}",
            "{\"schema\":\"1\",\"device\":\"z9x\",\"version\":\"1\",\"version_code\":\"12\",\"build_id\":\"b\",\"packages\":[]}",
            manifest(1, "z9x", "x", 1, null, pkg("full", null, "http://x/a.zip", 1000, 100, 800, R + "m", true)),
            manifest(1, "z9x", "x", 1, null, pkg("full", null, R + "a.zip", 1000, 100, 800, "http://m", true)),
            manifest(1, "z9x", "x", 1, null, pkg("full", null, R + "a.zip", 1000, 900, 800, R + "m", true)),
            manifest(1, "z9x", "x", 1, null, pkg("full", null, R + "a.zip", 1000, 0, 800, R + "m", true)),
            manifest(1, "z9x", "x", 1, null, pkg("partial", null, R + "a.zip", 1000, 100, 800, R + "m", true)),
            manifest(1, "z9x", "x", 1, null, pkg("delta", null, R + "a.zip", 0, -1, 0, null, false)),
            manifest(1, "z9x", "x", 1, null, pkg("full", null, R + "a.zip", 0, -1, 0, null, false)),
            manifest(1, "z9x", "x", 1, null, pkg("full", null, R + "a.zip", 1000, 100, 800, R + "m", false)),
            manifest(1, "z9x", "x", 1, null, "{\"type\":\"full\",\"url\":\"" + R + "a.zip\",\"size\":5,\"sha256\":\"xyz\"}"),
            "not json",
        };
        int i = 0;
        for (String j : corpus) {
            final String json = j;
            p("[" + (i++) + "] " + show(() -> describe(new UpdateManifest(json))));
        }
    }

    static String describe(UpdateManifest m) {
        StringBuilder b = new StringBuilder();
        b.append("schema=").append(m.schema).append(" device=").append(m.device).append(" channel=").append(m.channel)
                .append(" version=").append(m.version).append(" vc=").append(m.versionCode).append(" min=").append(m.minVersionCode)
                .append(" build=").append(m.buildId).append(" utc=").append(m.buildUtc).append(" vendors=").append(m.vendorIncremental)
                .append(" blobs=").append(m.blobsSets);
        for (UpdateManifest.Pkg k : m.packages) {
            b.append("\n    pkg type=").append(k.type).append(" from=").append(k.fromBuildId).append(" url=").append(k.url)
                    .append(" size=").append(k.size).append(" sha=").append(k.sha256.substring(0, 8)).append(" off=").append(k.payloadOffset)
                    .append(" psize=").append(k.payloadSize).append(" meta=").append(k.metadataUrl).append(" msize=").append(k.metadataSize)
                    .append(" msha=").append(k.metadataSha256.isEmpty() ? "" : k.metadataSha256.substring(0, 8))
                    .append(" props=").append(Arrays.toString(k.properties)).append(" file=").append(k.fileName())
                    .append(" delta=").append(k.isDelta());
        }
        UpdateManifest.Pkg c1 = m.choose("lumen-1.0.9-20261106anp"), c2 = m.choose(""), c3 = m.choose("other");
        b.append("\n    choose ").append(c1 == null ? "null" : c1.type).append(' ').append(c2 == null ? "null" : c2.type)
                .append(' ').append(c3 == null ? "null" : c3.type);
        b.append("\n    changelog ").append(m.changelogFor(Locale.ENGLISH)).append('|').append(m.changelogFor(new Locale("ru", "RU")))
                .append('|').append(m.changelogFor(new Locale("pt", "BR"))).append('|').append(m.changelogFor(Locale.JAPANESE));
        return b.toString();
    }

    static void applicable() throws Exception {
        p("## applicable");
        String[] devices = {"z9x", "hb", "play6", "x50", ""};
        String[] chans = {"public-nogms", "public-beta", "hb-nogms", "hb-test", "play6", null};
        String[] scen = {"z9x-stable", "z9x-beta-image", "hb-stable", "hb-test-image", "hb-no-device", "play6", "mixed", "empty"};
        for (String s : scen) {
            for (Boolean beta : new Boolean[] {null, Boolean.TRUE}) {
                StringBuilder b = new StringBuilder();
                for (String d : devices) {
                    for (String c : chans) {
                        props(SCENARIOS.get(s));
                        Ota.setBeta(beta);
                        UpdateManifest m = new UpdateManifest(manifest(1, d, c, 99999999, null, AB_FULL));
                        b.append(' ').append(d.isEmpty() ? "''" : d).append('/').append(c).append('=').append(show(() -> Preflight.applicable(m)));
                    }
                }
                p("[" + s + " beta=" + beta + "]" + b);
            }
        }
        // versions, vendor and min version under each namespace
        String[][] vcases = {{"z9x-stable", "z9x", "public-nogms"}, {"hb-stable", "hb", "hb-nogms"}, {"play6", "play6", "play6"}};
        int[][] codes = {{10012, 0}, {10011, 0}, {10013, 0}, {10013, 10012}, {10013, 10013}, {10013, 10101}, {1001013, 1001012},
                {1001013, 1001013}, {1001013, 1001099}, {1000000, 0}, {1001012, 0}, {10010, 10010}, {10011, 10011}, {99999999, 1000852}};
        String[] vendors = {null, "[]", "[\"v6.15.58\"]", "[\"v6.17.12\",\"v6.16.24\"]", "[\"v1.2.64\"]"};
        for (String[] v : vcases) {
            for (int[] cv : codes) {
                StringBuilder b = new StringBuilder();
                for (String ven : vendors) {
                    props(SCENARIOS.get(v[0]));
                    String req = "{" + (ven == null ? "" : "\"vendor_incremental\":" + ven + ",") + "\"min_version_code\":" + cv[1] + "}";
                    UpdateManifest m = new UpdateManifest(manifest(1, v[1], v[2], cv[0], req, AB_FULL));
                    b.append(' ').append(ven).append('=').append(show(() -> Preflight.applicable(m)));
                }
                p("[" + v[0] + " vc=" + cv[0] + " min=" + cv[1] + "]" + b);
            }
        }
        p("## blobs");
        String[][] bl = {{}, {"sys.z9x.blobs", "ok", "sys.z9x.blobs.set", "z9x-v61558-b"}, {"sys.z9x.blobs", "ok", "sys.z9x.blobs.set", "other"},
                {"sys.z9x.blobs", "missing", "sys.z9x.blobs.set", "z9x-v61558-b"}};
        for (String[] b : bl) {
            for (String sets : new String[] {"[]", "[\"z9x-v61558-b\"]"}) {
                props(b);
                UpdateManifest m = new UpdateManifest(manifest(1, "z9x", "public-nogms", 1, "{\"blobs_set\":" + sets + "}", AB_FULL));
                p(Arrays.toString(b) + " sets=" + sets + " missing=" + show(() -> Preflight.blobsMissing(m)));
            }
        }
    }

    /** The test seam of the gate probe (a fake boot gate steps here), when the updater has one. */
    static void seam(Runnable tick) {
        try {
            Field f = Preflight.class.getDeclaredField("pause");
            f.setAccessible(true);
            Class<?> t = f.getType();
            Object o = Proxy.newProxyInstance(t.getClassLoader(), new Class<?>[] {t}, (pr, meth, args) -> {
                if (meth.getName().equals("ms")) { tick.run(); return null; }
                return meth.getDeclaringClass() == Object.class ? meth.invoke(new Object(), args) : null;
            });
            f.set(null, o);
        } catch (NoSuchFieldException e) {
            // an updater without the gate probe: nothing to step
        } catch (ReflectiveOperationException e) {
            p("  (seam error " + e + ")");
        }
    }

    static Intent battery(boolean present, int plugged, int level, int scale) {
        return new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra("present", present).putExtra("plugged", plugged)
                .putExtra("level", level).putExtra("scale", scale);
    }

    static void install() throws Exception {
        p("## install");
        Context ctx = new Context();
        String[] gateOk = {"ro.boot.slot_suffix", "_a", "sys.z9x.vbmeta_other.flags", "3", "sys.z9x.ota", "none",
                "ro.virtual_ab.enabled", "true", "sys.z9x.ota.active", "_a", "sys.z9x.ota.marked", "1", "sys.z9x.ota.merge", "none",
                "sys.lumen.recovery_cert", "ok"};
        List<String[]> cases = new ArrayList<>();
        cases.add(new String[] {"none"});
        cases.add(new String[] {"vbmeta-unknown", "ro.boot.slot_suffix", "_a"});
        cases.add(new String[] {"vbmeta-off", "ro.boot.slot_suffix", "_a", "sys.z9x.vbmeta_other.flags", "1"});
        cases.add(new String[] {"vbmeta-off-b", "ro.boot.slot_suffix", "_b", "sys.z9x.vbmeta_other.flags", "0"});
        cases.add(new String[] {"pending", "ro.boot.slot_suffix", "_a", "sys.z9x.vbmeta_other.flags", "2", "sys.z9x.ota", "pending"});
        cases.add(new String[] {"vab-off", "ro.boot.slot_suffix", "_a", "sys.z9x.vbmeta_other.flags", "3"});
        cases.add(new String[] {"vabc", "ro.boot.slot_suffix", "_a", "sys.z9x.vbmeta_other.flags", "3", "ro.virtual_ab.enabled", "true",
                "ro.virtual_ab.compression.enabled", "true"});
        cases.add(new String[] {"vendor-list", "ro.boot.slot_suffix", "_a", "sys.z9x.vbmeta_other.flags", "3", "ro.virtual_ab.enabled", "true",
                "ro.vendor.build.ab_ota_partitions", "boot"});
        cases.add(new String[] {"recovery-stock", "sys.lumen.recovery_cert", "stock"});
        cases.add(new String[] {"recovery-ok", "sys.lumen.recovery_cert", "ok"});
        cases.add(concat(new String[] {"all-ok"}, gateOk));
        for (String[] c : cases) {
            for (int therm : new int[] {-1, 3}) {
                for (int bat = 0; bat < 3; bat++) {
                    props(Arrays.copyOfRange(c, 1, c.length));
                    Context.THERMAL = therm;
                    Context.BATTERY = bat == 0 ? null : bat == 1 ? battery(true, 0, 30, 100) : battery(true, 1, 30, 100);
                    p("[" + c[0] + " thermal=" + therm + " battery=" + bat + "]");
                    seam(() -> { });
                    p("  -> " + show(() -> Preflight.install(ctx)));
                }
            }
        }
        // the gate probe: answers ok, answers bad, never answers, setprop refused
        String[][] gate = {{"ok", "ok"}, {"bad", "bad:no bootctl"}, {"silent", ""}, {"refused", null}};
        for (String[] g : gate) {
            String[] base = concat(new String[] {}, gateOk);
            props(base);
            Context.THERMAL = -1;
            Context.BATTERY = null;
            if (g[1] == null) SystemProperties.DENY.add("sys.z9x.ota.request");
            final String answer = g[1];
            SystemProperties.onSet = () -> {
                if ("probe".equals(SystemProperties.VALUES.get("sys.z9x.ota.request")) && answer != null && !answer.isEmpty()
                        && !answer.equals(SystemProperties.VALUES.get("sys.z9x.ota.hal"))) {
                    SystemProperties.VALUES.put("sys.z9x.ota.hal", answer);
                }
            };
            p("[gate " + g[0] + "]");
            seam(() -> { });
            p("  -> " + show(() -> Preflight.install(ctx)));
            p("  request=" + SystemProperties.VALUES.get("sys.z9x.ota.request") + " hal=" + SystemProperties.VALUES.get("sys.z9x.ota.hal"));
        }
        p("## download space");
        props();
        UpdateManifest m = new UpdateManifest(manifest(1, "z9x", "public-nogms", 1, null, AB_FULL));
        p("dir=" + show(() -> Ota.dir(ctx)));
        p("space small=" + show(() -> Preflight.downloadSpace(ctx, m.packages.get(0), 0) == null)
                + " huge=" + show(() -> {
                    Preflight.Block b = Preflight.downloadSpace(ctx, new UpdateManifest(manifest(1, "z9x", "x", 1, null,
                            pkg("full", null, R + "a.zip", Long.MAX_VALUE / 4, 100, 800, R + "m", true))).packages.get(0), 0);
                    return b == null ? "null" : b.res;
                }));
    }

    static String[] concat(String[] a, String[] b) {
        String[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    static void constants() {
        p("## constants");
        p("TAG=" + Ota.TAG + " VERSION=" + Ota.VERSION + " REPO=" + Ota.REPO + " UA=" + Ota.USER_AGENT + " OTACERTS=" + Ota.OTACERTS);
        p("PERM=" + Ota.PERM_STATE + " ACTION=" + Ota.ACTION_STATE + " REBOOT=" + Ota.REBOOT_REASON + " NOTIF=" + Ota.NOTIF_CHANNEL
                + " PROJ=" + Ota.PROJECTOR_PKG + " " + Ota.PROJECTOR_ACTION_OTA_STATE);
        p("UE " + Ota.UE_IDLE + Ota.UE_CHECKING + Ota.UE_AVAILABLE + Ota.UE_DOWNLOADING + Ota.UE_VERIFYING + Ota.UE_FINALIZING
                + Ota.UE_NEED_REBOOT + Ota.UE_REPORTING + Ota.UE_ROLLBACK + Ota.UE_DISABLED + Ota.UE_CLEANUP
                + " ERR " + Ota.ERR_SUCCESS + "," + Ota.ERR_NOT_ENOUGH_SPACE + "," + Ota.ERR_UPDATED_BUT_NOT_ACTIVE + ","
                + Ota.ERR_TIMESTAMP + "," + Ota.ERR_USER_CANCELED);
    }

    public static void main(String[] a) throws Exception {
        tmp = a[0];
        Context.DIR = new File(tmp, "files");
        Context.DIR.mkdirs();
        constants();
        channels();
        versions();
        slots();
        manifests();
        applicable();
        install();
        System.out.print(OUT);
    }
}
