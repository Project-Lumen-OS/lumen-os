// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

import org.lumen.testkit.Check;

import java.io.File;
import java.nio.file.Files;

/**
 * Host checks of the power-on sound: BootSoundRules (switch, first value, boot animation gate, wake, the WAV as the AOSP
 * player reads it) on the shipped asset, then the wiring: the menu row power.on_music is a lumen-menu module setting
 * of every model's profile written through BootSound, the Z9X / HB / Play 6 adapters only adopt and turn off the stock
 * switch, every app installs BootSound with PathSwitch, and every image recipe ships lumen_boot_sound.rc. Argument:
 * the gsi folder.
 */
public final class BootSoundTest {
    private static final Check C = new Check("lumen-audio boot sound");
    private static final String[] PROFILES = {"horse_popular", "goat_elegant", "dragon_healthy", "g0111", "g0100",
            "g0155", "h0061", "h0062", "h0063"};

    public static void main(String[] args) throws Exception {
        File g = new File(args[0]);
        rules();
        wav(g);
        wiring(g);
        C.done();
    }

    private static void rules() {
        C.ok("unset = on (stock power_on_music DEFAULT=1)", BootSoundRules.on(""));
        C.ok("1 = on", BootSoundRules.on("1"));
        C.ok("0 = off", !BootSoundRules.on("0"));
        C.eq("first value: the unit's stock switch on", "1", BootSoundRules.adopt("", Boolean.TRUE));
        C.eq("first value: the unit's stock switch off", "0", BootSoundRules.adopt(null, Boolean.FALSE));
        C.eq("first value: no answer keeps the default", null, BootSoundRules.adopt("", null));
        C.eq("a stored choice is never replaced", null, BootSoundRules.adopt("0", Boolean.TRUE));
        C.eq("gate: on and audible", "1", BootSoundRules.gate(true, false, 7));
        C.eq("gate: switched off", "0", BootSoundRules.gate(false, false, 7));
        C.eq("gate: muted", "0", BootSoundRules.gate(true, true, 7));
        C.eq("gate: volume 0", "0", BootSoundRules.gate(true, false, 0));
        long boot = BootSoundRules.COLD_BOOT_MS, gap = BootSoundRules.REPEAT_MS;
        C.ok("wake plays", BootSoundRules.playOnWake(true, true, boot, gap, false, 5, false));
        C.ok("wake: switched off", !BootSoundRules.playOnWake(false, true, boot, gap, false, 5, false));
        C.ok("wake: not booted", !BootSoundRules.playOnWake(true, false, boot, gap, false, 5, false));
        C.ok("wake: cold boot (the boot animation played it)", !BootSoundRules.playOnWake(true, true, boot - 1, gap, false, 5, false));
        C.ok("wake: second signal of one wake", !BootSoundRules.playOnWake(true, true, boot, gap - 1, false, 5, false));
        C.ok("wake: muted", !BootSoundRules.playOnWake(true, true, boot, gap, true, 5, false));
        C.ok("wake: volume 0", !BootSoundRules.playOnWake(true, true, boot, gap, false, 0, false));
        C.ok("wake: an app plays", !BootSoundRules.playOnWake(true, true, boot, gap, false, 5, true));
    }

    private static void wav(File g) throws Exception {
        byte[] w = Files.readAllBytes(new File(g, "apps/core/lumen-audio/assets/lumen_audio/poweron.wav").toPath());
        int[] p = BootSoundRules.pcm(w);
        C.eq("asset: 48 kHz", 48000, p[2]);
        C.eq("asset: stereo", 2, p[3]);
        C.eq("asset: 3.0 s of PCM 16 bit", 48000 * 4 * 3, p[1]);
        int peak = 0;
        for (int i = p[0]; i + 1 < p[0] + p[1]; i += 2) peak = Math.max(peak, Math.abs((short) (w[i] & 0xff | w[i + 1] << 8)));
        C.ok("asset: peak below -6 dBFS (-18 LUFS master, " + peak + ")", peak < 32768 / 2);
        C.ok("asset: first and last sample zero", w[p[0]] == 0 && w[p[0] + 1] == 0 && w[p[0] + p[1] - 1] == 0
                && w[p[0] + p[1] - 2] == 0);
        C.ok("refuses a mono file", refuses(patch(w, 22, 1)));
        C.ok("refuses 44.1 kHz", refuses(patch(w, 24, 44100)));
        C.ok("refuses 8 bit", refuses(patch(w, 34, 8)));
        C.ok("refuses no RIFF", refuses(patch(w, 0, 0)));
    }

    private static byte[] patch(byte[] w, int at, int v) {
        byte[] c = w.clone();
        c[at] = (byte) v;
        c[at + 1] = (byte) (v >> 8);
        return c;
    }

    private static boolean refuses(byte[] w) {
        try {
            BootSoundRules.pcm(w);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static void wiring(File g) throws Exception {
        String spec = Check.read(new File(g, "apps/core/lumen-menu/src/org/lumen/menu/MenuSpec.java"));
        int mod = spec.indexOf("MODULE_SETTINGS =");
        C.ok("power.on_music is a lumen-menu module setting",
                mod > 0 && spec.indexOf("FeatureId.POWER_ON_MUSIC", mod) > 0 && spec.indexOf("FeatureId.POWER_ON_MUSIC", mod) < spec.indexOf(";", mod));
        String screens = Check.read(new File(g, "apps/core/lumen-menu/src/org/lumen/projector/panel/ModuleScreens.java"));
        C.ok("the row reads and writes BootSound", screens.contains("BootSound.on()") && screens.contains("BootSound.set(c, ON.equals(value))"));
        for (String code : PROFILES) {
            C.ok(code + ": the profile has power.on_music", Check.read(new File(g, "devices/" + code + "/menu.json")).contains("\"power.on_music\""));
            String conf = Check.read(new File(g, "devices/" + code + "/build.conf"));
            C.ok(code + ": the image stages lumen_boot_sound.rc", conf.contains("apps/core/lumen-audio/image/lumen_boot_sound.rc"));
            String steps = conf.replaceAll("(?s).*\\nsteps=(\\S+).*", "$1");
            C.ok(code + ": its steps install /system/etc/init/lumen_boot_sound.rc",
                    Check.read(new File(g, steps)).contains("/etc/init/lumen_boot_sound.rc"));
        }
        String rows = Check.read(new File(g, "apps/common/projector/src/org/lumen/projector/panel/SettingsRows.java"));
        C.ok("Z9X / HB / Play 6: the stock switch gives the first value and goes off",
                rows.contains("BootSound.adopt(app, v)") && rows.contains("dev.setMusic(false"));
        C.ok("Z9X / HB / Play 6: the adapters no longer own the row", !rows.contains("case POWER_ON_MUSIC")
                && !rows.contains("EnumSet.of(FeatureId.POWER_ON_MUSIC"));
        for (String a : new String[] {"apps/ProjectorBase/src/org/lumen/projector/panel/Z9xFeatures.java",
                "apps/HbProjector/src/org/lumen/projector/panel/HbMenuGates.java",
                "apps/Play6Projector/src/org/lumen/projector/panel/Play6MenuGates.java"}) {
            C.ok(a + " does not name the module row", !Check.read(new File(g, a)).contains("POWER_ON_MUSIC"));
        }
        C.ok("every app: PathSwitch.install installs BootSound",
                Check.read(new File(g, "apps/core/lumen-audio/src/org/lumen/audio/PathSwitch.java")).contains("BootSound.install(sApp);"));
        String rc = Check.read(new File(g, "apps/core/lumen-audio/image/lumen_boot_sound.rc"));
        for (String v : new String[] {"0", "1"}) {
            C.ok("rc: gate " + v + " -> persist.sys.bootanim.play_sound " + v,
                    rc.contains("on property:persist.lumen.boot_sound.play=" + v + "\n    setprop persist.sys.bootanim.play_sound " + v + "\n"));
        }
        String bs = Check.read(new File(g, "apps/core/lumen-audio/src/org/lumen/audio/BootSound.java"));
        C.ok("BootSound: props as the rc", bs.contains("\"persist.lumen.boot_sound.play\"") && bs.contains("\"persist.lumen.boot_sound\""));
        C.ok("BootSound: the music stream (user's level), static track", bs.contains("USAGE_MEDIA") && bs.contains("MODE_STATIC"));
        C.ok("gen_bootanim.py stores the same asset as part0/audio.wav",
                Check.read(new File(g, "tools/brand/gen_bootanim.py")).contains("\"lumen-audio\", \"assets\", \"lumen_audio\", \"poweron.wav\""));
    }
}
