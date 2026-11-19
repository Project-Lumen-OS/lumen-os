// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.sound;

import org.lumen.audio.OutputDevice;
import org.lumen.audio.SoundbarMode;
import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuProfile;
import org.lumen.testkit.Check;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Host checks of the sound output page (owner variant C, 2026-11-18): the rows of every model from its menu.json, the
 * output the card names, the Auto row, offline paired Bluetooth devices, Speaker keeping HDMI ARC, the HB jack that is
 * chosen by hand. Then the wiring: the HB profile's jack matches HbWiredJack, Auto never takes an unsensed jack, the
 * menu draws the page from SoundOutputs. Argument: the gsi folder.
 */
public final class SoundOutputsTest {
    private static final Check C = new Check("lumen-menu sound output page");
    private static final List<String> ALL = Arrays.asList("auto", "speaker", "arc", "wired", "bt", "usb");

    public static void main(String[] args) throws Exception {
        File g = new File(args[0]);
        scenes();
        models(g);
        wiring(g);
        C.done();
    }

    private static OutputDevice dev(String kind, String addr, String name) { return new OutputDevice(kind, addr, name, false, true); }

    private static OutputDevice paired(String addr, String name) { return new OutputDevice(OutputDevice.BT, addr, name, false, false); }

    private static String rows(SoundOutputs o) {
        List<String> l = new ArrayList<>();
        for (SoundOutputs.Row r : o.rows) l.add(r.toString());
        return String.join(" | ", l);
    }

    private static SoundOutputs.Row row(SoundOutputs o, String kind) {
        for (SoundOutputs.Row r : o.rows) if (kind.equals(r.kind) && !r.offline()) return r;
        return null;
    }

    private static void scenes() {
        // the approved picture C1: LG soundbar on ARC under Auto, UGREEN headphones paired but off, HB (jack not sensed)
        OutputDevice.Scan lg = new OutputDevice.Scan(Collections.singletonList(dev(OutputDevice.ARC, "", null)),
                Collections.singletonList(paired("AA:BB:CC:00:11:22", "UGREEN Max5s")), OutputDevice.ARC, "", "LG SOUND BAR");
        SoundOutputs c1 = SoundOutputs.of(ALL, Arrays.asList("auto", "speaker", "arc", "wired"), "auto", lg, false, "pcm");
        C.eq("C1 rows: Auto, Speaker, the soundbar by its CEC name, the jack by hand, the paired headphones offline",
                "auto [x] | speaker | arc 'LG SOUND BAR' arc connected playing | wired jack by_hand"
                        + " | (bt) 'UGREEN Max5s' bt offline", rows(c1));
        C.eq("C1 card: the soundbar", "LG SOUND BAR", c1.now == null ? null : c1.now.name);
        C.ok("C1 card: picked automatically", c1.nowAuto);
        C.eq("C1 card: the format while the sound goes to ARC", "pcm", c1.nowFormat);
        C.eq("C1 selected: Auto", "auto", c1.selected().token);
        SoundOutputs.Row off = c1.rows.get(c1.rows.size() - 1);
        C.ok("offline row: no choice token, only Connect", off.offline() && off.token == null);
        C.ok("offline row: never selected, never playing", !off.selected && !off.playing);

        // C2: headphones on the jack chosen by hand, the soundbar stays connected and listed
        OutputDevice.Scan hp = new OutputDevice.Scan(Arrays.asList(dev(OutputDevice.ARC, "", null), dev(OutputDevice.WIRED, "", null)),
                Collections.singletonList(paired("AA:BB:CC:00:11:22", "UGREEN Max5s")), OutputDevice.WIRED, "", "LG SOUND BAR");
        SoundOutputs c2 = SoundOutputs.of(ALL, Arrays.asList("auto", "speaker", "arc", "wired"), "wired", hp, false, "pcm");
        C.eq("C2 card: headphones (the module's label)", "wired", c2.now == null ? null : c2.now.kind);
        C.ok("C2 card: picked by hand", !c2.nowAuto);
        C.eq("C2 card: no format off ARC", null, c2.nowFormat);
        C.ok("C2: the soundbar row stays", row(c2, "arc") != null && !row(c2, "arc").playing);
        C.ok("C2: one playing mark", count(c2) == 1 && row(c2, "wired").playing && row(c2, "wired").selected);

        // Speaker keeps ARC (stock: an output choice is only FOR_MEDIA): the soundbar row stays and System Audio is untouched
        OutputDevice.Scan sp = new OutputDevice.Scan(Collections.singletonList(dev(OutputDevice.ARC, "", null)), null,
                OutputDevice.SPEAKER, "", "LG SOUND BAR");
        SoundOutputs s = SoundOutputs.of(ALL, Arrays.asList("auto", "speaker", "arc", "wired"), "speaker", sp, false, "pcm");
        C.ok("Speaker chosen: the soundbar row stays, connected", row(s, "arc") != null && SoundOutputs.CONNECTED.equals(row(s, "arc").state));
        C.ok("Speaker chosen: card = speaker by hand, no format", s.now != null && "speaker".equals(s.now.kind) && !s.nowAuto
                && s.nowFormat == null);
        C.eq("Speaker chosen: CEC System Audio unchanged (ARC stays up)", null, SoundbarMode.systemAudioFor("SPEAKER"));
        C.eq("Auto: System Audio on (the soundbar may play)", Boolean.TRUE, SoundbarMode.systemAudioFor("AUTO"));

        // Auto label and the selected row
        for (String ch : new String[]{null, "", "bogus", "AUTO", "auto"}) {
            C.eq("choice " + ch + " = Auto selected", "auto", SoundOutputs.of(ALL, null, ch, sp, true, null).selected().token);
        }
        C.eq("a runtime token with a number is its kind (Z9X bt2)", "bt", SoundOutputs.kindOf("bt2"));
        OutputDevice.Scan bt = new OutputDevice.Scan(Collections.singletonList(dev(OutputDevice.BT, "11:22", "Sony WH")), null,
                OutputDevice.BT, "11:22", null);
        SoundOutputs b = SoundOutputs.of(ALL, Arrays.asList("auto", "speaker", "bt2"), "bt2", bt, true, null);
        C.ok("Bluetooth by its name, selected and playing", row(b, "bt") != null && "Sony WH".equals(row(b, "bt").name)
                && row(b, "bt").selected && row(b, "bt").playing && !b.nowAuto);
        C.eq("a choice row carries the device's own token (the adapter's set path)", "bt2", row(b, "bt").token);
        SoundOutputs ba = SoundOutputs.of(ALL, Arrays.asList("auto", "speaker", "bt"), "auto", bt, true, null);
        C.ok("Auto on Bluetooth: Auto selected, the headphones play, picked automatically",
                ba.selected().token.equals("auto") && row(ba, "bt").playing && ba.nowAuto);

        // the chosen output is not reachable now: its row stays selected, the card says the system picked
        OutputDevice.Scan gone = new OutputDevice.Scan(null, Collections.singletonList(paired("11:22", "Sony WH")),
                OutputDevice.SPEAKER, "", null);
        SoundOutputs g = SoundOutputs.of(ALL, Arrays.asList("auto", "speaker", "bt"), "bt", gone, true, null);
        C.ok("chosen Bluetooth away: its row selected, not connected", row(g, "bt") != null && row(g, "bt").selected
                && row(g, "bt").state == null);
        C.ok("chosen Bluetooth away: the speaker plays, picked automatically", "speaker".equals(g.now.kind) && g.nowAuto);

        // offline rules: shown without a connected Bluetooth device, never on a model without Bluetooth
        OutputDevice.Scan only = new OutputDevice.Scan(null, Arrays.asList(paired("01", "A"), paired("02", "B")),
                OutputDevice.SPEAKER, "", null);
        SoundOutputs o1 = SoundOutputs.of(ALL, null, "auto", only, true, null);
        C.eq("two paired devices off: two offline rows, no Bluetooth choice row", "auto [x] | speaker playing | (bt) 'A' bt offline"
                + " | (bt) 'B' bt offline", rows(o1));
        SoundOutputs o2 = SoundOutputs.of(Arrays.asList("auto", "speaker", "arc"), null, "auto", only, true, null);
        C.eq("a model without Bluetooth: no offline rows", "auto [x] | speaker playing", rows(o2));
        C.ok("never a playing mark on Auto", !SoundOutputs.of(ALL, null, "auto", only, true, null).rows.get(0).playing);

        // the jack: sensed (X50, Z9X) = listed while plugged; not sensed (HB) = always listed, chosen by hand
        OutputDevice.Scan none = new OutputDevice.Scan(null, null, OutputDevice.SPEAKER, "", null);
        C.ok("jack sensed, nothing plugged: no headphones row", row(SoundOutputs.of(ALL, null, "auto", none, true, null), "wired") == null);
        SoundOutputs hb = SoundOutputs.of(ALL, null, "auto", none, false, null);
        C.ok("jack not sensed: the headphones row, chosen by hand", row(hb, "wired") != null
                && SoundOutputs.BY_HAND.equals(row(hb, "wired").state));
        C.ok("jack not sensed: Auto never shows the headphones playing", !row(hb, "wired").playing);
        C.ok("jack sensed and plugged: no by hand line", row(SoundOutputs.of(ALL, null, "auto", hp, true, null), "wired").state == null);
        C.ok("a model without a jack: no headphones row even plugged",
                row(SoundOutputs.of(Arrays.asList("auto", "speaker", "arc", "bt", "usb"), null, "wired", hp, false, null), "wired") == null);
    }

    private static int count(SoundOutputs o) {
        int n = 0;
        for (SoundOutputs.Row r : o.rows) if (r.playing) n++;
        return n;
    }

    /** Every profile: with everything connected, the page lists exactly the model's stock outputs, in its order. */
    private static void models(File g) throws Exception {
        OutputDevice.Scan full = new OutputDevice.Scan(Arrays.asList(dev(OutputDevice.ARC, "", null), dev(OutputDevice.WIRED, "", null),
                dev(OutputDevice.BT, "11:22", "Sony WH"), dev(OutputDevice.USB, "1", "DAC")),
                Collections.singletonList(paired("33:44", "JBL")), OutputDevice.BT, "11:22", "LG SOUND BAR");
        File[] devs = new File(g, "devices").listFiles();
        int n = 0;
        for (File d : devs == null ? new File[0] : devs) {
            File j = new File(d, "menu.json");
            if (!j.isFile()) continue;
            MenuProfile p = MenuProfile.parse(new String(Files.readAllBytes(j.toPath()), StandardCharsets.UTF_8));
            if (!p.has(FeatureId.SOUND_OUTPUT)) continue;
            n++;
            List<String> model = p.options(FeatureId.SOUND_OUTPUT);
            SoundOutputs o = SoundOutputs.of(model, null, "auto", full, p.jackSensed(), null);
            List<String> kinds = new ArrayList<>();
            for (SoundOutputs.Row r : o.rows) if (!r.offline()) kinds.add(r.kind);
            C.eq(d.getName() + ": the stock outputs, in the stock order", model, kinds);
            C.ok(d.getName() + ": the paired device offline only where Bluetooth is stock", model.contains("bt")
                    == (o.rows.size() > kinds.size()));
            C.ok(d.getName() + ": Auto and Speaker first", model.size() >= 2 && model.get(0).equals("auto") && model.get(1).equals("speaker"));
            C.ok(d.getName() + ": the jack is manual only on the HB", p.jackSensed() == !"goat_elegant".equals(d.getName()));
        }
        C.ok("profiles with an output page: 9", n == 9);
        C.ok("Play 6 has no jack (stock dayao tree)", !profile(g, "dragon_healthy").options(FeatureId.SOUND_OUTPUT).contains("wired"));
        C.ok("Z9X lists ARC, headphones, Bluetooth, USB (stock voice_output)",
                profile(g, "horse_popular").options(FeatureId.SOUND_OUTPUT).equals(ALL));
    }

    private static MenuProfile profile(File g, String c) throws Exception {
        return MenuProfile.parse(new String(Files.readAllBytes(new File(g, "devices/" + c + "/menu.json").toPath()), StandardCharsets.UTF_8));
    }

    /** The data and the code that use it agree (one source per fact, checked here). */
    private static void wiring(File g) throws Exception {
        String jack = Check.read(new File(g, "apps/HbProjector/src/org/lumen/projector/audio/HbWiredJack.java"));
        C.ok("HB senses no plug (HbWiredJack.SENSED false) and its profile says jack manual",
                jack.contains("static final boolean SENSED = false;") && !profile(g, "goat_elegant").jackSensed());
        String auto = Check.read(new File(g, "apps/core/lumen-audio/src/org/lumen/audio/AutoOutput.java"));
        C.ok("Auto never takes an unsensed jack (AutoOutput.path)",
                auto.contains("return p == MediaPath.WIRED && !jackSensed ? MediaPath.NONE : p;"));
        String hb = Check.read(new File(g, "apps/HbProjector/src/org/lumen/projector/audio/AudioOutputChoice.java"));
        C.ok("HB Auto uses HbWiredJack.SENSED", hb.contains("AutoOutput.device(am, HbWiredJack.SENSED)")
                && hb.contains("AutoOutput.added(d, kind(sApp) == AudioOutputKind.AUTO, HbWiredJack.SENSED)"));
        String pages = Check.read(new File(g, "apps/core/lumen-menu/src/org/lumen/projector/panel/OutputPages.java"));
        C.ok("the page is drawn from SoundOutputs over OutputScan and the profile's jack",
                pages.contains("SoundOutputs o = SoundOutputs.of(profile.options(FeatureId.SOUND_OUTPUT),")
                        && pages.contains("OutputScan.read(app), profile.jackSensed(),"));
        C.ok("Connect is a normal Bluetooth connect", pages.contains("BtA2dp.connect(addr, \"output menu\")"));
        String rows = Check.read(new File(g, "apps/HbProjector/src/org/lumen/projector/audio/HbOutputRows.java"));
        C.ok("HbOutputRows no longer says Speaker turns System Audio off", !rows.contains("\"Speaker\" turns CEC System"));
        String mc = Check.read(new File(g, "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java"));
        C.ok("the format row sits on the output page", mc.contains("if (MenuSpec.onOutputPage(f, profile)) return;"));
    }
}
