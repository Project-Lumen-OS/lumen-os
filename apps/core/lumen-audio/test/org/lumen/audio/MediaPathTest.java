// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

import org.lumen.testkit.Check;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Host checks of apps/core/lumen-audio: the MediaPath rules and the wiring of every projector that plays media through
 * the vendor paths (each path picker asks MediaPath.heard, each output choice takes Auto from AutoOutput and each
 * recipe names the module), then the stock Auto rule (AutoPick) with the scenes it replays. Argument: the gsi folder.
 */
public final class MediaPathTest {
    private static final Check C = new Check("lumen-audio");

    public static void main(String[] args) throws Exception {
        rules();
        auto();
        wiring(new File(args[0]));
        C.done();
    }

    private static List<Integer> l(Integer... p) { return Arrays.asList(p); }

    private static void rules() {
        int S = MediaPath.SPEAKER, A = MediaPath.ARC, B = MediaPath.BT, W = MediaPath.WIRED, U = MediaPath.USB;
        C.eq("stock wires", "0 2 3 4 6 -1", S + " " + A + " " + B + " " + W + " " + U + " " + MediaPath.NONE);
        // the tester's case: headphones and the soundbar in the route, in both heap orders
        C.eq("bt before arc", B, MediaPath.heard(l(B, A)));
        C.eq("arc first in the list, bt still wins", B, MediaPath.heard(l(A, B)));
        C.eq("bt with spdif skipped and arc", B, MediaPath.heard(l(MediaPath.NONE, A, B)));
        C.eq("wired over arc", W, MediaPath.heard(l(A, W)));
        C.eq("usb over arc", U, MediaPath.heard(l(A, U)));
        C.eq("bt over wired", B, MediaPath.heard(l(W, B)));
        C.eq("bt over usb", B, MediaPath.heard(l(U, B)));
        C.eq("wired over usb", W, MediaPath.heard(l(U, W)));
        C.eq("arc over the speaker (no System Audio: {speaker, arc})", A, MediaPath.heard(l(S, A)));
        C.eq("speaker alone", S, MediaPath.heard(l(S)));
        C.eq("speaker and a skipped spdif", S, MediaPath.heard(l(S, MediaPath.NONE)));
        C.eq("nothing known", MediaPath.NONE, MediaPath.heard(l(MediaPath.NONE)));
        C.eq("empty", MediaPath.NONE, MediaPath.heard(Collections.emptyList()));
        C.eq("null", MediaPath.NONE, MediaPath.heard(null));
        C.eq("an unknown wire is no path", MediaPath.NONE, MediaPath.heard(l(1, 7, 100)));

        C.ok("format again on a switch to arc", MediaPath.formatAgain(S, A));
        C.ok("format again on a switch away from arc", MediaPath.formatAgain(A, B));
        C.ok("no format on the same path", !MediaPath.formatAgain(A, A));
        C.ok("no format at the first report of a boot", !MediaPath.formatAgain(MediaPath.NONE, A));
        C.ok("no format without a path", !MediaPath.formatAgain(A, MediaPath.NONE));
        C.ok("switch start on every change", MediaPath.switchStart(S, B) && MediaPath.switchStart(B, S) && MediaPath.switchStart(S, A));
        C.ok("no switch start for the first report or the same path", !MediaPath.switchStart(MediaPath.NONE, S)
                && !MediaPath.switchStart(B, B) && !MediaPath.switchStart(B, MediaPath.NONE));
        C.eq("1 s around ARC (stock 50 x 20 ms)", 1000L, MediaPath.switchDelayMs(S, A));
        C.eq("1 s away from ARC", 1000L, MediaPath.switchDelayMs(A, B));
        C.eq("300 ms otherwise (1.0.6)", 300L, MediaPath.switchDelayMs(B, S));
        C.eq("300 ms for the first report", 300L, MediaPath.switchDelayMs(MediaPath.NONE, A));
        C.ok("safety end after the 1 s wait and the 1.5 s check", MediaPath.SWITCH_END_SAFETY_MS > MediaPath.STOCK_SWITCH_MS + 1500L);
        C.eq("switch start value (Msrv mutes every TV path)", 100, MediaPath.SWITCHING);
    }

    /** Stock Auto (libxgimiaudiopolicy lastAccessDevice, egnForceUse, the fallback chain), AutoPick. */
    private static void auto() {
        int S = MediaPath.SPEAKER, A = MediaPath.ARC, B = MediaPath.BT, W = MediaPath.WIRED, U = MediaPath.USB;
        AutoPick p = new AutoPick();
        C.eq("auto, fresh process: A2DP first", B, p.pick(l(S, A, B)));
        C.eq("auto, fresh: wired over USB, ARC, speaker", W, p.pick(l(S, A, U, W)));
        C.eq("auto, fresh: USB over ARC", U, p.pick(l(S, A, U)));
        C.eq("auto, fresh: ARC over the speaker", A, p.pick(l(S, A)));
        C.eq("auto, fresh: the speaker alone", S, p.pick(l(S)));
        C.eq("auto, nothing", MediaPath.NONE, p.pick(l(MediaPath.NONE)));
        C.eq("auto, null", MediaPath.NONE, p.pick(null));
        // headphones on, then the soundbar's ARC comes up: stock plays the soundbar (last accessed)
        p.added(B, true);
        p.added(A, true);
        C.eq("auto: ARC up after Bluetooth takes the sound", A, p.pick(l(S, A, B)));
        p.removed(B);
        p.added(B, true);
        C.eq("auto: Bluetooth connected again takes it back", B, p.pick(l(S, A, B)));
        // HB rig 15:02:55: Bluetooth disconnected in Auto with the soundbar on ARC
        p.removed(B);
        C.eq("auto: Bluetooth gone, the soundbar", A, p.pick(l(S, A)));
        // a removal of another device keeps the last accessed one
        AutoPick q = new AutoPick();
        q.added(B, true);
        q.added(U, true);
        q.removed(B);
        C.eq("auto: another device gone, the last accessed stays", U, q.pick(l(S, A, W, U)));
        // a manual choice keeps playing after Auto is chosen, while it is connected (egnForceUse not reset)
        AutoPick m = new AutoPick();
        m.chose(S);
        m.chose(MediaPath.NONE);
        C.eq("auto after Speaker: the speaker stays with Bluetooth on", S, m.pick(l(S, A, B)));
        m.added(B, false);
        C.eq("a device connected outside Auto changes nothing", S, m.pick(l(S, A, B)));
        m.added(U, true);
        C.eq("a device connected in Auto plays", U, m.pick(l(S, A, B, U)));
        m.removed(U);
        C.eq("and it ended the manual remnant: gone, the fallback", B, m.pick(l(S, A, B)));
        AutoPick f = new AutoPick();
        f.chose(A);
        C.eq("manual output gone: fallback", B, f.pick(l(S, B)));
        C.eq("manual output back later: forgotten, fallback order", B, f.pick(l(S, A, B)));
        AutoPick c = new AutoPick();
        c.added(A, true);
        c.chose(MediaPath.NONE);
        C.eq("choosing Auto keeps the last accessed device", A, c.pick(l(S, A, B)));
        c.chose(S);
        C.eq("a manual choice clears the last accessed device", S, c.pick(l(S, A, B)));
        AutoPick n = new AutoPick();
        n.added(A, true);
        n.added(MediaPath.NONE, true);
        n.added(7, true);
        C.eq("SPDIF / unknown never last accessed", A, n.pick(l(S, A, B)));
        C.eq("fallback chain is the stock one", "3 4 6 2 0", AutoPick.FALLBACK[0] + " " + AutoPick.FALLBACK[1] + " "
                + AutoPick.FALLBACK[2] + " " + AutoPick.FALLBACK[3] + " " + AutoPick.FALLBACK[4]);
    }

    private static String read(File g, String path) throws Exception {
        File f = new File(g, path);
        C.ok("exists " + path, f.isFile());
        return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
    }

    private static void wiring(File g) throws Exception {
        C.ok("the one path picker (PathSwitch) asks MediaPath over the table paths",
                read(g, "apps/core/lumen-audio/src/org/lumen/audio/PathSwitch.java").contains("MediaPath.heard(paths)"));
        String[] choices = {
                "apps/ProjectorBase/src/org/lumen/projector/audio/AudioOutputChoice.java",
                "apps/HbProjector/src/org/lumen/projector/audio/AudioOutputChoice.java",
                "apps/Play6Projector/src/org/lumen/projector/audio/AudioOutputChoice.java",
                "apps/X50Projector/src/org/lumen/x50/projector/AudioOutput.java",
        };
        for (String c : choices) {
            String s = read(g, c);
            C.ok("output choice: Auto from AutoOutput: " + c, Check.countCode(s, "? AutoOutput.device(am, ") == 1);
            C.ok("output choice: device changes reported to AutoOutput: " + c, Check.countCode(s, "AutoOutput.added(d, ") == 1
                    && Check.countCode(s, "AutoOutput.removed(d);") == 1);
            C.ok("output choice: a user choice and the stored one reported: " + c, Check.countCode(s, "AutoOutput.chose(") == 2);
            C.ok("output choice: no ArcAside (a preferred Auto device and a disabled ARC would fight): " + c,
                    !s.contains("ArcAside"));
        }
        C.ok("HB: the jack is no Auto candidate", Check.countCode(read(g, choices[1]), "AutoOutput.device(am, HbWiredJack.SENSED)") == 1
                && read(g, "apps/HbProjector/src/org/lumen/projector/audio/HbWiredJack.java").contains("static final boolean SENSED = false;"));
        String jack = read(g, "apps/core/lumen-audio/src/org/lumen/audio/JackOutput.java");
        C.ok("JackOutput: never removes the jack (no HEADSET_PLUG 0, players do not pause)",
                Check.countCode(jack, "setWiredDeviceConnectionState(jack(), 1)") == 1 && !jack.contains(", 0)"));
        C.ok("JackOutput: the role before the first connection",
                jack.contains("if (listed(am)) return false;\n            aside(am, media, !chosen, why);\n            List<String> active")
                && jack.indexOf("aside(am, media, !chosen, why);") < jack.indexOf("setWiredDeviceConnectionState(jack(), 1)"));
        C.ok("JackOutput: later the role after the preferred device (settle)",
                jack.contains("            if (listed(am)) aside(am, media, !chosen, why);"));
        String auto = read(g, "apps/core/lumen-audio/src/org/lumen/audio/AutoOutput.java");
        C.ok("AutoOutput: the wired output only where the jack is sensed",
                auto.contains("return p == MediaPath.WIRED && !jackSensed ? MediaPath.NONE : p;"));
        C.ok("format again on a path switch (PathSwitch)",
                read(g, "apps/core/lumen-audio/src/org/lumen/audio/PathSwitch.java").contains("if (MediaPath.formatAgain(prev, p)) sVendor.formatAgain("));
        C.ok("Z9X adapter: the stored format again", read(g, "apps/ProjectorBase/src/org/lumen/projector/audio/AudioPathReporter.java")
                .contains("AudioOutputChoice.resendFormat(why);"));
        C.ok("HB adapter: the stored format again", read(g, "apps/HbProjector/src/org/lumen/projector/audio/AudioPathReporter.java")
                .contains("AudioOutputChoice.resendFormat(why);"));
        C.ok("X50 adapter: the stored format again", read(g, "apps/X50Projector/src/org/lumen/x50/projector/AudioPath.java")
                .contains("AudioOutput.resendFormat(why);"));
        String[] recipes = {
                "devices/horse_popular/app/Z9xProjector.layers",
                "devices/goat_elegant/app/HbProjector.layers",
                "devices/dragon_healthy/app/Play6Projector.layers",
                "devices/g0111/app/X50Projector.layers",
                "devices/g0100/app/X50Projector.layers",
                "devices/g0155/app/X50Projector.layers",
        };
        for (String r : recipes) {
            boolean named = false;
            for (String line : read(g, r).split("\n")) named |= line.trim().matches("module\\s+lumen-audio");
            C.ok("recipe names lumen-audio: " + r, named);
        }
    }
}
