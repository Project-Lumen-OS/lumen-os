// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

import android.content.Context;
import android.media.AudioDeviceVolumeManager;
import android.media.AudioSystem;

import org.lumen.testkit.Check;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Host checks of the stock volume split (ChipTable, ChipLevels, DspOwned) and of every model's chip volume table against
 * its stock AudioService (setDspVolumesToHal keys, threadInit paths, fixed volume devices), then the wiring: every
 * model reaches the vendor only through PathSwitch / StockVolume, no app sends a chip level itself, the images keep the
 * stock "no AVRCP absolute volume" and no click curve copy. Argument: the gsi folder.
 */
public final class ChipVolumeTest {
    private static final Check C = new Check("lumen-audio chip volume");
    /** The Android 14 stock rows (Z9X G0082, HB G0130, X50 G0111 family): type key device path, one per row. */
    private static final List<String> A14 = Arrays.asList("2 spk_volume -1 0", "3 headphone_volume -1 4",
            "4 headphone_volume -1 4", "8 bluetooth_volume 128 3", "8 bluetooth_volume 256 3", "8 bluetooth_volume 512 3",
            "11 usb_volume -1 6", "22 usb_volume -1 6", "6 xgimi_pcm_volume -1 -1", "10 null -1 2", "29 null -1 2");
    /** The Play 6 Android 11 stock rows: bluetooth_volume without a device id. */
    private static final List<String> A11 = Arrays.asList("2 spk_volume -1 0", "3 headphone_volume -1 4",
            "4 headphone_volume -1 4", "8 bluetooth_volume -1 3", "11 usb_volume -1 6", "22 usb_volume -1 6",
            "6 xgimi_pcm_volume -1 -1", "10 null -1 2", "29 null -1 2");
    private static final String[] A14_MODELS = {"horse_popular", "goat_elegant", "g0111", "g0100", "g0155", "h0062", "h0063"};

    public static void main(String[] args) throws Exception {
        File g = new File(args[0]);
        levels();
        parse();
        for (String m : A14_MODELS) model(g, m, A14, "[31, 19]", false);
        model(g, "dragon_healthy", A11, "[13, 31, 19]", true);
        owned();
        wiring(g);
        C.done();
    }

    private static void levels() {
        C.eq("index 17 of 100 is level 17 (stock: the index itself)", 17, ChipLevels.level(17, 100, false));
        C.eq("index 0", 0, ChipLevels.level(0, 100, false));
        C.eq("index 100", 100, ChipLevels.level(100, 100, false));
        C.eq("muted is 0", 0, ChipLevels.level(60, 100, true));
        C.eq("15 steps: 7 rounds to 47", 47, ChipLevels.level(7, 15, false));
        C.eq("above the range is clamped", 100, ChipLevels.level(130, 100, false));
        C.eq("below the range is clamped", 0, ChipLevels.level(-3, 100, false));
        C.eq("max 0 never divides by zero", 100, ChipLevels.level(5, 0, false));
        ChipTable t = ChipTable.parse("out 8 bluetooth_volume 128 3\nout 2 spk_volume - 0\nout 11 usb_volume - 6\nout 22 usb_volume - 6");
        ChipKey a2dp = t.rows().get(0), spk = t.rows().get(1);
        C.eq("A2DP without absolute volume follows the index", 40, ChipLevels.levelOf(a2dp, 40, 100, false, false));
        C.eq("A2DP with absolute volume: the chip at full scale", 100, ChipLevels.levelOf(a2dp, 40, 100, false, true));
        C.eq("A2DP with absolute volume at 0: silent", 0, ChipLevels.levelOf(a2dp, 0, 100, false, true));
        C.eq("A2DP muted with absolute volume: silent", 0, ChipLevels.levelOf(a2dp, 40, 100, true, true));
        C.eq("the speaker ignores absolute volume", 40, ChipLevels.levelOf(spk, 40, 100, false, true));
        C.eq("param with a device id", "bluetooth_volume=15;device=128", a2dp.param(15));
        C.eq("param without", "spk_volume=15", spk.param(15));
        ChipLevels l = new ChipLevels();
        C.eq("first level goes", "spk_volume=15", l.due(spk, 15, false));
        l.sent(spk, 15);
        C.eq("unchanged level skipped", null, l.due(spk, 15, false));
        C.eq("unchanged level forced", "spk_volume=15", l.due(spk, 15, true));
        C.eq("changed level goes", "spk_volume=14", l.due(spk, 14, false));
        C.eq("out of range never", null, l.due(spk, 101, true));
        C.eq("unknown never", null, l.due(spk, -1, true));
        C.eq("last level", 15, l.last(spk));
        l.forget();
        C.eq("audio server down: every level again", "spk_volume=15", l.due(spk, 15, false));
        List<ChipKey> usb = t.slots().get(2);
        C.eq("two USB types share one slot", 2, usb.size());
        C.eq("slot level from the connected type", 22, ChipLevels.pick(usb, Arrays.asList(2, 22)).type);
        C.eq("slot level from the first type when none is connected", 11, ChipLevels.pick(usb, Arrays.asList(2)).type);
        C.eq("slot level with no outputs known", 11, ChipLevels.pick(usb, null).type);
    }

    private static void parse() {
        ChipTable t = ChipTable.parse("# c\nout 2 spk_volume - 0   # speaker\npath 0\nfixed 31\nlevels after-end\n\n");
        C.eq("one row", 1, t.rows().size());
        C.ok("after-end", t.levelsAfterEnd());
        C.eq("fixed", "[31]", Arrays.toString(t.fixed()));
        C.eq("unlisted type takes the table path", 0, t.pathOf(21));
        C.eq("no path directive: unlisted is no path", MediaPath.NONE, ChipTable.parse("out 2 spk_volume - 0").pathOf(21));
        C.ok("empty table owns nothing", ChipTable.empty().owned(false).length == 0 && ChipTable.empty().rows().isEmpty());
        for (String bad : new String[] {"out 2 spk_volume 0", "out 2 spk_volume - 0 1", "outs 2", "fixed", "fixed 31 19", "path 0 2",
                "levels before-end", "levels after-end now", "out x spk - 0", "path"}) {
            boolean threw = false;
            try {
                ChipTable.parse(bad);
            } catch (IllegalArgumentException e) {
                threw = e.getMessage().startsWith("chip table line 1");
            }
            C.ok("bad line refused: " + bad, threw);
        }
    }

    private static void model(File g, String code, List<String> want, String fixed, boolean after) throws Exception {
        String rel = "devices/" + code + "/audio/chip_volume.tsv";
        ChipTable t = ChipTable.parse(Check.read(new File(g, rel)));
        List<String> rows = new ArrayList<>();
        for (ChipKey r : t.rows()) rows.add(r.type + " " + r.key + " " + r.device + " " + r.path);
        C.eq(code + ": the stock setDspVolumesToHal rows", want, rows);
        C.eq(code + ": owned, AVRCP absolute volume off (stock)", "[2, 3, 4, 8, 11, 22, 6]", Arrays.toString(t.owned(false)));
        C.eq(code + ": owned, AVRCP absolute volume on: A2DP stays Android's", "[2, 3, 4, 11, 22, 6]", Arrays.toString(t.owned(true)));
        C.eq(code + ": fixed volume devices", fixed, Arrays.toString(t.fixed()));
        C.eq(code + ": levels again after switch_end", after, t.levelsAfterEnd());
        List<String> slots = new ArrayList<>();
        for (List<ChipKey> slot : t.slots()) slots.add(slot.get(0).slot());
        C.eq(code + ": one HAL slot per key and device id", after ? "[spk_volume, headphone_volume, bluetooth_volume, usb_volume, xgimi_pcm_volume]"
                : "[spk_volume, headphone_volume, bluetooth_volume;device=128, bluetooth_volume;device=256, bluetooth_volume;device=512, usb_volume, xgimi_pcm_volume]",
                slots.toString());
        C.eq(code + ": speaker path stores spk_volume only", "[spk_volume]", slotsOf(t.ofPath(MediaPath.SPEAKER)));
        C.eq(code + ": jack path stores headphone_volume", "[headphone_volume, headphone_volume]", slotsOf(t.ofPath(MediaPath.WIRED)));
        C.eq(code + ": USB path stores usb_volume", "[usb_volume, usb_volume]", slotsOf(t.ofPath(MediaPath.USB)));
        C.eq(code + ": ARC stores nothing", "[]", slotsOf(t.ofPath(MediaPath.ARC)));
        C.eq(code + ": SPDIF is never a path", MediaPath.NONE, t.pathOf(6));
        C.eq(code + ": unlisted (XGIMI wireless speaker bus) is the speaker path", MediaPath.SPEAKER, t.pathOf(21));
        C.eq(code + ": {hdmi_arc, spdif} plays on ARC", MediaPath.ARC, heard(t, 10, 6));
        C.eq(code + ": {speaker, spdif} plays on the speaker", MediaPath.SPEAKER, heard(t, 2, 6));
        C.eq(code + ": {spdif} alone is no path", MediaPath.NONE, heard(t, 6));
        C.eq(code + ": {hdmi_arc, bt} plays on Bluetooth (not heap order)", MediaPath.BT, heard(t, 10, 8));
        C.eq(code + ": {earc, usb headset} plays on USB", MediaPath.USB, heard(t, 29, 22));
        String recipe = Check.read(new File(g, "devices/" + code + "/app/" + (code.equals("horse_popular") ? "Z9xProjector"
                : code.equals("goat_elegant") ? "HbProjector" : code.equals("dragon_healthy") ? "Play6Projector" : "X50Projector") + ".layers"));
        C.ok(code + ": the recipe ships the table as the app asset",
                recipe.contains("file      " + rel + " assets/lumen_audio/chip_volume.tsv\n"));
    }

    private static int heard(ChipTable t, int... types) {
        List<Integer> paths = new ArrayList<>();
        for (int type : types) paths.add(t.pathOf(type));
        return MediaPath.heard(paths);
    }

    private static String slotsOf(List<ChipKey> rows) {
        List<String> out = new ArrayList<>();
        for (ChipKey r : rows) out.add(r.key);
        return out.toString();
    }

    private static void owned() {
        DspOwned.apply(new Context(), null, "no list");
        C.eq("no list: nothing registered", 0, AudioDeviceVolumeManager.CALLS.size());
        Context c = new Context();
        c.audio.systemMuted = true;
        DspOwned.apply(c, new int[] {2, 8}, "test");
        C.eq("every listed type registered, music without an index, AudioService adjusts", Arrays.asList(
                "role 2 type 2 stream 3 index false adjust false listener true executor true",
                "role 2 type 8 stream 3 index false adjust false listener true executor true"),
                AudioDeviceVolumeManager.CALLS);
        List<String> want = new ArrayList<>();
        for (String dev : new String[] {"0x2", "0x80"}) {
            for (int s = 0; s <= 10; s++) if (s != 1) want.add("stream " + s + " index 100 device " + dev);
        }
        C.eq("every stream at the maximum on each device at once (clicks too), a muted stream left alone", want, AudioSystem.INDEX);
        DspOwned.apply(new Context(), new int[] {2}, "again");
        C.eq("once per process", 2, AudioDeviceVolumeManager.CALLS.size());
        C.eq("streams: every stream type 0..10", "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]", Arrays.toString(DspOwned.STREAMS));
    }

    private static void wiring(File g) throws Exception {
        String sv = Check.read(new File(g, "apps/core/lumen-audio/src/org/lumen/audio/StockVolume.java"));
        String ps = Check.read(new File(g, "apps/core/lumen-audio/src/org/lumen/audio/PathSwitch.java"));
        C.ok("StockVolume: the table from the asset the recipes ship", sv.contains("ASSET = \"lumen_audio/chip_volume.tsv\""));
        C.ok("StockVolume: the split for the table's outputs", sv.contains("DspOwned.apply(app, t.owned(avrcpAbsolute()), \"process start\");"));
        C.ok("StockVolume: each slot's level from its connected output's own index", sv.contains("int level = level(am, dvm, r, abs);")
                && sv.contains("ChipKey r = ChipLevels.pick(slot, connected);"));
        C.ok("StockVolume: each output's own index", sv.contains("dvm.getDeviceVolume(new VolumeInfo.Builder(AudioManager.STREAM_MUSIC).build(),"));
        C.ok("StockVolume: music volume and mute broadcasts", sv.contains("i.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return;")
                && sv.contains("sync(\"volume\", false);") && sv.contains("f.addAction(ACTION_MUTE);"));
        C.ok("StockVolume: every level at process start, boot and audio server up", sv.contains("sync(\"process start\", true)")
                && sv.contains("sync(\"boot completed\", true)") && sv.contains("sync(\"audio server up\", true);"));
        C.ok("StockVolume: the HAL lost its levels when the audio server went down", sv.contains("LEVELS.forget();"));
        C.ok("StockVolume: the fixed volume list", sv.contains("am.setDeviceVolumeBehavior(d, AudioManager.DEVICE_VOLUME_BEHAVIOR_FIXED);"));
        String sink = body(ps, "private static void sink(int path, String why)");
        int store = sink.indexOf("StockVolume.store(path, \"before switch_end=\" + path)"), end = sink.indexOf("HalSink.end(am, ticket, path, path, why)");
        C.ok("PathSwitch: the level stored before every switch_end", 0 <= store && store < end);
        C.ok("PathSwitch: the ticket taken where the sink is decided", sink.indexOf("int ticket = HalSink.ticket();") >= 0
                && sink.indexOf("int ticket = HalSink.ticket();") < store);
        C.ok("PathSwitch: levels again after the end where the HAL wants it", sink.contains("if (sTable.levelsAfterEnd()) stored = StockVolume.store(path,"));
        String commit = body(ps, "private static void commit()");
        C.eq("PathSwitch: the sink after 59(P) and when the vendor holds P", 2, Check.count(commit, "sink(target, "));
        C.ok("PathSwitch: 59(P) only after the boot and the vendor", ps.contains("return booted() && sVendor.ready();"));
        C.ok("PathSwitch: confirmed only with the ready prop and the read-back", ps.contains("if (sSwitching || sForce || !\"1\".equals(prop(PROP_READY))) return false;")
                && ps.contains("PROP_READY = \"vender.xgimi.audio.ready\""));
        String ev = body(ps, "private static void evaluate(String why)");
        C.ok("PathSwitch: switch_start, 59(100) and the format at once on a change", ev.contains("if (MediaPath.switchStart(prev, p) && readyForHal()) {")
                && ev.contains("out.select(MediaPath.SWITCHING);") && ev.contains("if (MediaPath.formatAgain(prev, p)) sVendor.formatAgain("));
        C.ok("PathSwitch: the path from the table, MediaPath.heard", Check.count(ps, "MediaPath.heard(paths)") == 2
                && Check.count(ps, "sTable.pathOf(") == 2);
        C.ok("PathSwitch: the sink again when the audio server is back", ps.contains("StockVolume.onAudioServerUp(() -> sH.post(() -> resink(\"audio server up\")));"));
        C.ok("PathSwitch: a safety end after a switch_start", ps.contains("sink(sTarget, \"safety\");"));
        String[][] adapters = {
                {"apps/ProjectorBase/src/org/lumen/projector/audio/AudioPathReporter.java", "AudioPorts.registerDeviceCallback(app, cb, h,"},
                {"apps/HbProjector/src/org/lumen/projector/audio/AudioPathReporter.java", "am.registerAudioDeviceCallback(cb, h);"},
                {"apps/X50Projector/src/org/lumen/x50/projector/AudioPath.java", "am.registerAudioDeviceCallback(cb, h);"},
                {"apps/Play6Projector/src/org/lumen/projector/audio/BtAudio.java", "am.registerAudioDeviceCallback(cb, h);"}};
        for (String[] a : adapters) {
            String s = Check.read(new File(g, a[0]));
            boolean p6 = a[0].contains("Play6Projector");
            C.ok(a[0] + ": the vendor adapter of the shared switch", s.contains("implements VendorAudio") && s.contains("PathSwitch.install(")
                    && s.contains(a[1]) && (p6 || s.contains("PathSwitch.onBootCompleted(ctx, VENDOR)")));
        }
        String p6app = Check.read(new File(g, "apps/Play6Projector/src/org/lumen/projector/Play6App.java"));
        C.ok("Play 6: the switch installed after the start gate (sys.boot_completed), no boot receiver line",
                p6app.contains("step(\"BtAudio.install\", () -> org.lumen.projector.audio.BtAudio.install(app));")
                        && p6app.indexOf("if (!\"1\".equals(SystemProperties.get(\"sys.boot_completed\", \"\"))) return \"sys.boot_completed\";") > 0
                        && !Check.read(new File(g, "apps/Play6Projector/src/org/lumen/play6/BootReceiver.java")).contains("BtAudio"));
        C.ok("PathSwitch: the boot step at install once the boot completed", ps.contains("if (booted()) sH.post(() -> boot(\"process start\"));"));
        String[] keys = {"\"spk_volume", "\"headphone_volume", "\"bluetooth_volume", "\"usb_volume", "\"xgimi_pcm_volume"};
        for (String app : new String[] {"ProjectorBase", "HbProjector", "X50Projector", "Play6Projector"}) {
            List<File> files = new ArrayList<>();
            walk(new File(g, "apps/" + app + "/src"), files);
            int n = 0;
            for (File f : files) {
                String s = Check.read(f);
                for (String k : keys) n += Check.countCode(s, k);
            }
            C.eq(app + ": no chip level sent past StockVolume", 0, n);
        }
        for (String gone : new String[] {"apps/ProjectorBase/src/org/lumen/projector/source/HdmiVolume.java",
                "apps/HbProjector/src/org/lumen/projector/source/HdmiVolume.java", "apps/X50Projector/src/org/lumen/x50/projector/HdmiVolume.java",
                "apps/Play6Projector/src/org/lumen/projector/source/HdmiVolume.java", "apps/ProjectorBase/src/org/lumen/projector/audio/BtDspVolume.java",
                "overlay/v1/z9x_audio.rc", "overlay/v1/audio_policy_engine_stream_volumes.xml"}) {
            C.ok("no copy left: " + gone, !new File(g, gone).exists());
        }
        String base = Check.read(new File(g, "overlay/v1/z9x_base.rc"));
        int eb = base.indexOf("\non early-boot\n"), next = base.indexOf("\non ", eb + 2);
        C.ok("Z9X base: no AVRCP absolute volume at early-boot (stock vendor build.prop)", eb >= 0
                && base.substring(eb, next < 0 ? base.length() : next).contains("\n    setprop persist.bluetooth.disableabsvol true\n")
                && !base.contains("disableabsvol false"));
        C.ok("Z9X product props: no 1.0.6 absolute volume line", !Check.read(new File(g, "overlay/v1/product_prop_v1.txt")).contains("disableabsvol"));
        String p6 = Check.read(new File(g, "overlay/play6/play6_base.rc"));
        C.ok("Play 6: no AVRCP absolute volume at early-boot", p6.contains("on early-boot\n    setprop persist.bluetooth.disableabsvol true\n")
                && !p6.contains("disableabsvol false"));
        C.ok("HB: no AVRCP absolute volume at early-boot", Check.read(new File(g, "devices/goat_elegant/image/hb_bt.rc"))
                .contains("setprop persist.bluetooth.disableabsvol true"));
        for (String pf : new String[] {"devices/g0111/image/x50_prop.txt", "devices/g0111/image/x50_prop_pub.txt",
                "devices/g0111/image/x50max_prop_pub.txt", "devices/h0062/image/h0062_prop_pub.txt", "devices/h0063/image/h0063_prop_pub.txt"}) {
            String t = Check.read(new File(g, pf));
            C.ok(pf + ": no AVRCP absolute volume (stock)", t.contains("\npersist.bluetooth.disableabsvol=true\n") && !t.contains("disableabsvol=false"));
        }
        C.ok("Z9X image recipe: no click curve copy", !Check.read(new File(g, "tools/lumen_v1.sh")).contains("stream_volumes.xml"));
        String steps = Check.read(new File(g, "devices/horse_popular/image/steps.tsv"));
        C.ok("Z9X beta recipe: the tree's z9x_base.rc in the image", steps.contains("\ttar_add\tsystem/etc/init/z9x_base.rc\tz9x_base.rc\t644\n")
                && steps.contains("\ttar_same\t{tar}\tsystem/etc/init/z9x_base.rc\tz9x_base.rc\n"));
        C.ok("Z9X beta recipe: the 1.0.9 click curve copy goes", steps.contains("\ttar_rm\tsystem/etc/init/z9x_audio.rc\n")
                && steps.contains("\ttar_rm\tsystem/etc/xgimi/audio_policy_engine_stream_volumes.xml\n"));
    }

    /** The block that starts at {@code sig} (to its matching brace), "" when absent. */
    private static String body(String src, String sig) {
        int i = src.indexOf(sig);
        if (i < 0) return "";
        int depth = 0;
        for (int k = src.indexOf('{', i); k >= 0 && k < src.length(); k++) {
            char ch = src.charAt(k);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return src.substring(i, k + 1);
        }
        return src.substring(i);
    }

    private static void walk(File d, List<File> out) {
        File[] l = d.listFiles();
        if (l == null) return;
        for (File f : l) {
            if (f.isDirectory()) walk(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }
}
