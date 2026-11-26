// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.cec;

import org.lumen.hdmi.HdmiLink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays an HDMI-CEC wake of the Z9X HB with a soundbar on HDMI 1 (eARC) and a source on HDMI 2 (evleo790's Yandex
 * module, 2026-11-26) against WakeRules, EarcRules, HdmiLink and the CEC defaults table, and compares it with stock.
 *
 * Evidence the model is built from:
 * <ul>
 *   <li>Stock timing: X50 stock sniffer power-on (~/lumen-build-tmp/x50-cec-live-1126/ref): TV Report Physical Address
 *       11:46:57.060, Give System Audio Mode Status 11:47:09.552 (discovery done, 12.49 s), one Request Short Audio
 *       Descriptor <mac> at 11:47:10.370; no Request ARC Termination at the wake.</li>
 *   <li>AOSP (Lumen services.jar HdmiCecLocalDeviceTv.changeSystemAudioMode:386-392): an app's setSystemAudioMode while
 *       a DeviceDiscoveryAction runs turns System Audio off (and so ARC: Request ARC Termination).</li>
 *   <li>RequestSadAction: the enabled query_sad codecs, four per message (MAX_SAD_PER_REQUEST = 4).</li>
 *   <li>HB vendor (logs 2026-11-04 15:40:08-15 and 20:10:54-55): "updateEdidInfo: bEnableArc:0|1" on every ARC end and
 *       start, and "Update EDID by Audio System SAD Info" for every Report Short Audio Descriptor with codecs while
 *       ARC is up, each with that answer's codecs only. The LG soundbar there answered LPCM 1, AC-3 2, AAC 6, DTS 7,
 *       E-AC-3 10, DTS-HD 11, MAT 12.</li>
 * </ul>
 * Each EDID rewrite of the inputs is a chance for the source on HDMI 2 to see a hot plug (the picture drop); stock has
 * two at a wake (ARC on, one SAD answer). Arg: the CEC defaults table (devices/_image/cec_defaults.tsv).
 */
public final class WakeReplayTest {
    private static int pass, fail;

    static final long DISCOVERY_DONE_MS = 12_490;          // stock X50 trace
    static final int[] LG_CODECS = {1, 2, 6, 7, 10, 11, 12};
    static final String[] CODEC_KEYS = {"lpcm", "dd", "mpeg1", "mp3", "mpeg2", "aac", "dts", "atrac", "onebitaudio",
            "ddp", "dtshd", "truehd", "dst", "wmapro", "max"};

    public static void main(String[] a) throws IOException {
        Map<String, String> table = defaults(a[0]);
        wakeReplay(table);
        stormReplay();
        noRelinkNoEarcRestart();
        System.out.println("cec wake replay: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    /** One wake: what reaches the CEC bus and how often the HB vendor rewrites the inputs' EDID. */
    static void wakeReplay(Map<String, String> table) {
        for (boolean androidWoke : new boolean[] {true}) {
            List<String> bus = new ArrayList<>();
            int edidRewrites = 0;
            boolean arcUp = false;
            long appRequestAt = WakeRules.appSystemAudioRequest(androidWoke) ? WakeRules.AVR_ON_DELAY_MS : -1;
            if (appRequestAt >= 0 && appRequestAt < DISCOVERY_DONE_MS) {
                // AOSP: System Audio off during the discovery; nothing was up yet, but the soundbar is told to end ARC
                bus.add(appRequestAt + " 05:C4");
            }
            // discovery done: the framework's own System Audio and ARC start
            bus.add(DISCOVERY_DONE_MS + " 05:7D");
            bus.add(DISCOVERY_DONE_MS + " 05:C3");
            arcUp = true;
            edidRewrites++;                                     // updateEdidInfo bEnableArc:1
            List<int[]> batches = sadBatches(table);
            for (int[] b : batches) {
                StringBuilder s = new StringBuilder(DISCOVERY_DONE_MS + " 05:A4");
                for (int c : b) s.append(String.format(":%02X", c));
                bus.add(s.toString());
                if (arcUp && answers(b)) edidRewrites++;        // Update EDID by Audio System SAD Info
            }
            String w = androidWoke ? "STR wake: " : "standby exit: ";
            eq(w + "no app System Audio Mode Request while the framework's discovery runs (stock: none)", false,
                    appRequestAt >= 0 && appRequestAt < DISCOVERY_DONE_MS);
            eq(w + "no <Request ARC Termination> at the wake (stock trace)", false, contains(bus, "05:C4"));
            eq(w + "ARC is started by the framework when the discovery is done, stock time 12.49 s", true,
                    bus.contains(DISCOVERY_DONE_MS + " 05:C3"));
            eq(w + "one <Request Short Audio Descriptor>, the stock codecs AC-3 DTS E-AC-3 TrueHD (<mac>)",
                    Arrays.asList(DISCOVERY_DONE_MS + " <mac>"), sads(bus));
            eq(w + "the HB vendor rewrites the inputs' EDID twice, as on stock (ARC on, one SAD answer)", 2, edidRewrites);
        }
        eq("lamp-only standby exit: the app may still ask the soundbar (no framework restart)", true,
                WakeRules.appSystemAudioRequest(false));
    }

    /** evleo790: "Alice, turn on" / "Alice, turn off" a few times; then a CEC wake must still be allowed. */
    static void stormReplay() {
        WakeRules.Storm s = new WakeRules.Storm();
        long t = 1_000_000;
        for (int i = 0; i < 6; i++) {
            long wake = t;
            t += 60_000;                                        // watched a minute, then the module's <Standby>
            s.onSleep(t, wake, true);
            t += 120_000;
        }
        eq("six on/off cycles by the HDMI device in 18 min: CEC wake not paused", false, s.paused());
        eq("those cycles count as no unwanted wake", 0, s.count());

        WakeRules.Storm u = new WakeRules.Storm();
        t = 1_000_000;
        for (int i = 0; i < 4; i++) {
            long wake = t;
            t += 30_000;                                        // the user turns it off with the remote
            u.onSleep(t, wake, false);
            t += 60_000;
        }
        eq("four CEC wakes the user turned off within 2 min: paused (storm limit kept)", true, u.paused());
        eq("a remote key ends the pause", true, u.onUserKey());
        eq("after the key: not paused", false, u.paused());

        WakeRules.Storm late = new WakeRules.Storm();
        eq("a sleep 2 min or later after the CEC wake does not count", null,
                late.onSleep(1_000_000 + WakeRules.QUICK_SLEEP_MS, 1_000_000, false));
        eq("a sleep with no CEC wake does not count", null, late.onSleep(5_000_000, -1, false));
        WakeRules.Storm three = new WakeRules.Storm();
        for (int i = 0; i < 3; i++) three.onNobodyThere(1_000_000 + i * 1_000L);
        eq("three unwanted wakes: not yet paused", false, three.paused());
        three.onNobodyThere(1_004_000);
        eq("the fourth within 30 min: paused", true, three.paused());
        WakeRules.Storm spread = new WakeRules.Storm();
        for (int i = 0; i < 4; i++) spread.onNobodyThere(1_000_000 + i * (WakeRules.STORM_WINDOW_MS / 2 + 1));
        eq("four unwanted wakes spread over more than 30 min: not paused", false, spread.paused());
    }

    /** The wake itself must not re-open the HDMI port or restart ARC on the app's side. */
    static void noRelinkNoEarcRestart() {
        // picture stable 3 s after the wake, then a short drop (the EDID rewrite) of under 2 s
        eq("a drop shorter than the relink time: no EDID flip (HdmiLink.relinkNow)", false,
                HdmiLink.relinkNow(2_000, 15_000, true, true, 0, 0));
        eq("eARC not linked at the wake: EarcLink adds no ARC output", EarcStep.NONE,
                EarcRules.step(0, false, false, true));
        eq("no link of 3 seen since the wake, no panel press: no ARC restart from the app", false,
                EarcRules.restartAllowed(5_000, false, false, 30_000));
    }

    // ------------------------------------------------------------------ model helpers

    static List<int[]> sadBatches(Map<String, String> table) {
        List<Integer> codecs = new ArrayList<>();
        for (int i = 0; i < CODEC_KEYS.length; i++) {
            // LineageOS 21 default (no table row): every codec on
            if (!"0".equals(table.getOrDefault("query_sad_" + CODEC_KEYS[i], "1"))) codecs.add(i + 1);
        }
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < codecs.size(); i += 4) {
            int[] b = new int[Math.min(4, codecs.size() - i)];
            for (int j = 0; j < b.length; j++) b[j] = codecs.get(i + j);
            out.add(b);
        }
        return out;
    }

    static boolean answers(int[] batch) {
        for (int c : batch) for (int l : LG_CODECS) if (c == l) return true;
        return false;
    }

    static List<String> sads(List<String> bus) {
        List<String> out = new ArrayList<>();
        for (String s : bus) if (s.contains(" 05:A4")) out.add(s);
        return out;
    }

    static boolean contains(List<String> bus, String frame) {
        for (String s : bus) if (s.endsWith(" " + frame)) return true;
        return false;
    }

    static Map<String, String> defaults(String path) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            String[] f = line.split("#", 2)[0].trim().split("\\s+");
            if (f.length == 3 && "default".equals(f[0])) m.put(f[1], f[2]);
        }
        return m;
    }

    static void eq(String what, Object want, Object got) {
        boolean ok = want == null ? got == null : want.equals(got);
        if (ok) pass++;
        else {
            fail++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }
}
