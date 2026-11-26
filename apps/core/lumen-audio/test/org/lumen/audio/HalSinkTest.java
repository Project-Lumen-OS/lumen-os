// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import android.media.AudioManager;

import org.lumen.testkit.Check;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Host checks of the HAL sink order (SinkOrder, HalSink) and the Bluetooth device of an output choice (A2dpPick), with
 * the device logs they replay, then the wiring of every projector: no switch_end past HalSink, the ticket taken where
 * the sink is decided, each output choice asking A2dpPick through BtA2dp. Argument: the gsi folder.
 */
public final class HalSinkTest {
    private static final Check C = new Check("lumen-audio sink");
    private static int route = SinkOrder.ROUTE_UNKNOWN;

    public static void main(String[] args) throws Exception {
        order();
        replays();
        a2dp();
        wiring(new File(args[0]));
        C.done();
    }

    private static List<String> l(String... a) { return Arrays.asList(a); }

    private static void order() {
        SinkOrder o = new SinkOrder();
        int a = o.ticket(), b = o.ticket();
        C.ok("tickets grow", b > a);
        C.ok("newer ticket goes", o.claim(b, 3, 3));
        C.ok("older ticket after a newer one sent: dropped", !o.claim(a, 3, 3));
        C.eq("newest sent", b, o.written());
        int c = o.ticket();
        C.ok("decided for another route: dropped", !o.claim(c, 0, 3));
        C.eq("a dropped ticket is not recorded", b, o.written());
        C.ok("the same ticket twice: once", o.claim(o.ticket(), 3, 3) && !o.claim(o.written(), 3, 3));
        SinkOrder u = new SinkOrder();
        C.ok("unknown route (first report of the process) does not block", u.claim(u.ticket(), 0, SinkOrder.ROUTE_UNKNOWN));
        SinkOrder w = new SinkOrder();
        int s = w.ticket();
        int t = w.ticket();
        C.ok("a newer ticket that was dropped does not block an older valid one", !w.claim(t, 0, 3) && w.claim(s, 3, 3));
    }

    /** The HB rig logs of 2026-11-14 through HalSink (route = AudioPathReporter's target). */
    private static void replays() {
        HalSink.routeFrom(() -> route);
        // 17:49:21-23: ARC -> speaker step decided switch_end=0 (posted to the main thread), the route moved to
        // Bluetooth and switch_end=3 went out at .136, the late speaker end ran at .250
        AudioManager am = new AudioManager();
        route = 0;
        int speaker = HalSink.ticket();
        route = 3;
        int a2dp = HalSink.ticket();
        C.ok("17:49 A2DP end sent", HalSink.end(am, a2dp, 3, 3, "A2DP sink"));
        C.ok("17:49 late speaker end dropped", !HalSink.end(am, speaker, 0, 0, "switch_end after path 0"));
        C.eq("17:49 HAL sink stays A2DP", 3, am.sink());
        C.eq("17:49 only the A2DP end reached the HAL (no switch_start with an end)", l("xgimi_audiopath_switch_end=3"), am.sent);
        // 18:41:49: speaker sink selected (left Bluetooth for path 0) after switch_start=2 moved the route to ARC
        AudioManager am2 = new AudioManager();
        route = 0;
        int left = HalSink.ticket();
        route = 2;
        int arc = HalSink.ticket();
        C.ok("18:41 the ARC end goes", HalSink.end(am2, arc, 2, 2, "after 59(2)"));
        C.ok("18:41 the speaker end of the old route dropped", !HalSink.end(am2, left, 0, 0, "left Bluetooth for path 0"));
        C.eq("18:41 HAL sink stays ARC", 2, am2.sink());
        // 2026-11-15 15:02:54-55 (HB rig, beta15, Auto, Bluetooth disconnected under a tone): the route went 3 -> 0 at
        // .987 and 0 -> 2 at .613; beta15's own reporter sent switch_start=2 and no switch_end=2, the HAL sink stayed
        // a2dp and the soundbar was silent. Through HalSink the ARC end of the newest route always goes, whichever end
        // of the passing speaker step arrives first.
        for (boolean speakerLate : new boolean[] {false, true}) {
            AudioManager am4 = new AudioManager();
            route = 3;
            HalSink.end(am4, HalSink.ticket(), 3, 3, "Bluetooth");
            route = 0;
            int pass = HalSink.ticket();
            boolean early = !speakerLate && HalSink.end(am4, pass, 0, 0, "after 59(0)");
            route = 2;
            int soundbar = HalSink.ticket();
            C.ok("15:02 the ARC end goes (speaker end " + (speakerLate ? "late" : "first") + ")",
                    HalSink.end(am4, soundbar, 2, 2, "after 59(2)"));
            if (speakerLate) C.ok("15:02 the late speaker end dropped", !HalSink.end(am4, pass, 0, 0, "after 59(0)"));
            else C.ok("15:02 the speaker end of the passing route went first", early);
            C.eq("15:02 HAL sink ARC, not a2dp (speaker end " + (speakerLate ? "late" : "first") + ")", 2, am4.sink());
        }
        // the speaker path of HDMI start (switch_start + end) while the route is Bluetooth: never sent
        AudioManager am3 = new AudioManager();
        route = 3;
        C.ok("speaker path on a Bluetooth route dropped", !HalSink.startEnd(am3, HalSink.ticket(), 0, 0, "speaker path"));
        C.eq("nothing reached the HAL (no switch_start either)", Collections.emptyList(), am3.sent);
        route = 0;
        C.ok("speaker path on the speaker route goes", HalSink.startEnd(am3, HalSink.ticket(), 0, 0, "speaker path"));
        C.eq("start then end", l("xgimi_audiopath_switch_start=0", "xgimi_audiopath_switch_end=0"), am3.sent);
        // in order: each decision for the route it was made for reaches the HAL
        AudioManager am4 = new AudioManager();
        route = 4;
        C.ok("wired end goes", HalSink.end(am4, HalSink.ticket(), 4, 4, "after 59(4)"));
        route = 3;
        C.ok("then A2DP goes", HalSink.end(am4, HalSink.ticket(), 3, 3, "A2DP sink"));
        C.eq("HAL sink A2DP", 3, am4.sink());
        // the soundbar chosen again after Bluetooth and the jack (beta14 report): the ARC end decided last reaches the
        // HAL, the ends of the steps before it land late and are dropped, nothing drops the ARC end
        AudioManager am5 = new AudioManager();
        route = 3;
        C.ok("return: A2DP end", HalSink.end(am5, HalSink.ticket(), 3, 3, "A2DP sink"));
        route = 0;
        int leftBt = HalSink.ticket();
        route = 4;
        int jack = HalSink.ticket();
        route = 2;
        int back = HalSink.ticket();
        C.ok("return: the ARC end goes", HalSink.end(am5, back, 2, 2, "after 59(2)"));
        C.ok("return: the late speaker end dropped", !HalSink.end(am5, leftBt, 0, 0, "left Bluetooth for path 0"));
        C.ok("return: the late jack end dropped", !HalSink.end(am5, jack, 4, 4, "after 59(4)"));
        C.eq("return: HAL sink ARC", 2, am5.sink());
        route = SinkOrder.ROUTE_UNKNOWN;
    }

    private static void a2dp() {
        String ugreen = "AA:BB:CC:DD:5F:30", lg = "<mac>";
        // Slava 2026-11-14: the stored LG SK8 was unpaired, the UGREEN is connected
        C.eq("stale stored address: the connected device", ugreen, A2dpPick.device(lg, l(ugreen)));
        C.eq("stored device connected: it", lg, A2dpPick.device(lg, l(ugreen, lg)));
        C.eq("stored address in other case", lg, A2dpPick.device(lg.toLowerCase(), l(ugreen, lg)));
        C.eq("nothing stored: the first connected", ugreen, A2dpPick.device("", l(ugreen, lg)));
        C.eq("nothing connected: none", null, A2dpPick.device(lg, l()));
        C.eq("no list: none", null, A2dpPick.device(lg, null));
        // 18:42:30: the wired output made the stack drop the active A2DP device, the soundbar stayed connected
        C.ok("connected, not active: made active", A2dpPick.activate(lg, l()));
        C.ok("another one active: made active", A2dpPick.activate(lg, l(ugreen)));
        C.ok("already active: nothing", !A2dpPick.activate(lg, l(lg)));
        C.ok("active in other case: nothing", !A2dpPick.activate(lg, l(lg.toLowerCase())));
        C.ok("no device: nothing", !A2dpPick.activate(null, l()));
    }

    private static String read(File g, String rel) throws Exception { return Check.read(new File(g, rel)); }

    /** Statements that start the shared switch: a line of its own, not a comment and not behind a condition. */
    private static int installs(String s) {
        int n = 0;
        for (String line : s.split("\n")) if (line.trim().startsWith("PathSwitch.install(")) n++;
        return n;
    }

    private static void wiring(File g) throws Exception {
        String ps = read(g, "apps/core/lumen-audio/src/org/lumen/audio/PathSwitch.java");
        C.ok("PathSwitch names its route for HalSink", Check.countCode(ps, "HalSink.routeFrom(") == 1);
        C.eq("PathSwitch: one ticket, where the sink is decided", 1, Check.countCode(ps, "HalSink.ticket()"));
        C.eq("PathSwitch: one switch_end writer", 1, Check.countCode(ps, "HalSink.end("));
        String[] reporters = {
                "apps/HbProjector/src/org/lumen/projector/audio/AudioPathReporter.java",
                "apps/ProjectorBase/src/org/lumen/projector/audio/AudioPathReporter.java",
                "apps/X50Projector/src/org/lumen/x50/projector/AudioPath.java",
                "apps/Play6Projector/src/org/lumen/projector/audio/BtAudio.java"};
        for (String r : reporters) {
            String s = read(g, r);
            C.ok(r + ": no own sink (PathSwitch)", Check.countCode(s, "HalSink.") == 0 && installs(s) == 1);
        }
        // no switch_end / switch_start written past HalSink anywhere in the apps
        for (String app : new String[] {"HbProjector", "ProjectorBase", "X50Projector", "Play6Projector"}) {
            List<File> files = new java.util.ArrayList<>();
            walk(new File(g, "apps/" + app + "/src"), files);
            for (File f : files) {
                String s = Check.read(f);
                C.ok(app + "/" + f.getName() + ": no switch_end past HalSink", Check.countCode(s, "\"xgimi_audiopath_switch_end=") == 0);
                C.ok(app + "/" + f.getName() + ": no switch_start=0 past HalSink",
                        Check.countCode(s, "setParameters(\"xgimi_audiopath_switch_start=\" + PATH_SPEAKER)") == 0
                                && Check.countCode(s, "setParameters(\"xgimi_audiopath_switch_start=\" + HdmiHal.PATH_SPEAKER)") == 0);
            }
        }
        // the Bluetooth device of every output choice (A2dpPick through BtA2dp), made active when it is not
        String[] choices = {
                "apps/HbProjector/src/org/lumen/projector/audio/AudioOutputChoice.java",
                "apps/ProjectorBase/src/org/lumen/projector/audio/AudioOutputChoice.java",
                "apps/Play6Projector/src/org/lumen/projector/audio/AudioOutputChoice.java",
                "apps/X50Projector/src/org/lumen/x50/projector/AudioOutput.java"};
        for (String c : choices) {
            String s = read(g, c);
            C.ok(c + ": hears the Bluetooth stack", Check.countCode(s, "BtA2dp.install(sApp,") == 1);
            C.ok(c + ": a stale address falls back to the connected device", Check.countCode(s, "String pick = BtA2dp.pick(addr);") == 1);
            C.ok(c + ": a connected inactive device is made active", Check.countCode(s, "if (BtA2dp.activateIfNeeded(pick, why)) return") == 1);
            C.ok(c + ": the route uses the picked device", Check.countCode(s, "find(am, k, addr)") == 1);
        }
        for (int i = 0; i < 3; i++) {
            String s = read(g, choices[i]);
            C.ok(choices[i] + ": the Bluetooth row from the stack", Check.countCode(s, "Option bt = btOption(ctx, am, k == kind(ctx) ? addr : \"\");") == 1
                    && Check.countCode(s, "String pick = BtA2dp.pick(stored);") == 1);
        }
        String xo = read(g, choices[3]);
        C.ok("X50: the Bluetooth row from the stack", Check.countCode(xo, "if (!out.contains(AudioRules.BT) && !BtA2dp.connected().isEmpty()) out.add(AudioRules.BT);") == 1);
        C.ok("X50: a Bluetooth choice stores the connected device", Check.countCode(xo, "String pick = BtA2dp.pick(prefs(ctx).getString(KEY_ADDR, \"\"));") == 1);
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
