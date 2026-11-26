// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.sound;

import org.lumen.menu.FeatureId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The sound output rows read again on their own (a tester: eARC on, the sound on the soundbar, the output page still
 * "Auto, speaker" until he left it): the re-read times after eARC and output changes, and the wiring of OutputWatch
 * in the shared menu (every projector). Argument: the gsi tree.
 */
public final class OutputRefreshTest {
    private static int bad;

    private static void ok(String what, boolean c) {
        System.out.println((c ? "ok   " : "FAIL ") + what);
        if (!c) bad++;
    }

    private static boolean rising(long[] t) {
        for (int i = 1; i < t.length; i++) if (t[i] <= t[i - 1]) return false;
        return t.length > 0;
    }

    private static String read(Path p) throws Exception { return new String(Files.readAllBytes(p), "UTF-8"); }

    public static void main(String[] a) throws Exception {
        long[] earc = OutputRefresh.after(FeatureId.SOUND_EARC);
        ok("eARC: re-reads in rising order", rising(earc));
        ok("eARC: the first within 2 s", earc[0] <= 2_000);
        ok("eARC: the last past the 22 s of three ARC renegotiations", earc[earc.length - 1] >= 22_000);
        ok("eARC: no gap over 10 s", gaps(earc) <= 10_000);
        long[] out = OutputRefresh.after(FeatureId.SOUND_OUTPUT);
        ok("output: re-reads in rising order, within 2 s first", rising(out) && out[0] <= 2_000);
        ok("other rows: no re-read", OutputRefresh.after(FeatureId.SOUND_FORMAT).length == 0
                && OutputRefresh.after(FeatureId.PICTURE_MODE).length == 0);
        earc[0] = 99;
        ok("the table cannot be changed by a caller", OutputRefresh.after(FeatureId.SOUND_EARC)[0] != 99);
        ok("watch: with the output row", OutputRefresh.watch(true, false));
        ok("watch: with the eARC row", OutputRefresh.watch(false, true));
        ok("watch: not without both", !OutputRefresh.watch(false, false));
        ok("calm: short", OutputRefresh.CALM_MS > 0 && OutputRefresh.CALM_MS <= 500);
        // the wiring: every model's menu is this one MenuController
        Path panel = Paths.get(a[0], "apps/core/lumen-menu/src/org/lumen/projector/panel");
        String mc = read(panel.resolve("MenuController.java")), ow = read(panel.resolve("OutputWatch.java"));
        ok("MenuController: the watch starts when a page shows", mc.contains("watchSignal();\n        watchOutputs();"));
        ok("MenuController: a change reads again later", mc.contains("outputWatch.after(f);"));
        ok("MenuController: the watch stops when the menu closes", mc.contains("outputWatch.stop();"));
        ok("OutputWatch: no device callback of its own (AudioPorts rule)", !ow.contains("registerAudioDeviceCallback("));
        for (String act : new String[]{"AudioManager.ACTION_HDMI_AUDIO_PLUG", "AudioManager.ACTION_HEADSET_PLUG",
                "STREAM_DEVICES_CHANGED", "A2DP_CONNECTION", "A2DP_ACTIVE"}) {
            ok("OutputWatch listens to " + act, ow.contains("f.addAction(" + act + ");"));
        }
        ok("OutputWatch: sticky plugs are no change", ow.contains("if (isInitialStickyBroadcast()) return;"));
        ok("OutputWatch: unregisters", ow.contains("app.unregisterReceiver(plugs)"));
        if (bad > 0) { System.out.println("FAIL OutputRefreshTest: " + bad); System.exit(1); }
        System.out.println("ok   OutputRefreshTest");
    }

    private static long gaps(long[] t) {
        long g = t[0];
        for (int i = 1; i < t.length; i++) g = Math.max(g, t[i] - t[i - 1]);
        return g;
    }
}
