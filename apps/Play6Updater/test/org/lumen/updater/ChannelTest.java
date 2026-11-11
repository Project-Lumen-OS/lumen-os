// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

/**
 * Host test of the Play 6 Ota (sh test/run.sh, on the assembled tree of devices/dragon_healthy/app/Play6Updater.layers):
 * the manifest file of each edition (docs/ota.md "Variants"), which is the USB file the updater looks for and the
 * channel a manifest must name; the Play 6's own channel tag and device name; no beta channel on the Play 6.
 */
public final class ChannelTest {
    private static int failed, passed;

    private static void eq(String what, Object want, Object got) {
        if (want == null ? got == null : want.equals(got)) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    public static void main(String[] a) {
        String b = "https://github.com/Project-Lumen-OS/lumen-os/releases/latest/download/";
        eq("private", "update-stable.json", Ota.channelFile(b + "update-stable.json"));
        eq("public", "update-public.json", Ota.channelFile(b + "update-public.json"));
        eq("no-Google", "update-public-nogms.json", Ota.channelFile(b + "update-public-nogms.json"));
        eq("query string", "update-public-nogms.json", Ota.channelFile(b + "update-public-nogms.json?x=1"));
        eq("not a manifest file", "update-play6.json", Ota.channelFile(b + "latest.json"));
        eq("empty", "update-play6.json", Ota.channelFile(""));
        eq("null", "update-play6.json", Ota.channelFile(null));
        // Preflight's guard: "update-" + channel + ".json" must be this file
        eq("guard nogms", true, ("update-" + "public-nogms" + ".json").equals(Ota.channelFile(b + "update-public-nogms.json")));
        eq("guard crossed", false, ("update-" + "public" + ".json").equals(Ota.channelFile(b + "update-public-nogms.json")));
        // the Play 6's own channel: the fixed tag play6, never the Z9X tag z9x or releases/latest
        String p6 = "https://github.com/Project-Lumen-OS/lumen-os/releases/download/play6/update-play6.json";
        eq("fallback on the tag play6", p6, Ota.DEFAULT_MANIFEST_URL);
        eq("fallback file", "update-play6.json", Ota.channelFile(Ota.DEFAULT_MANIFEST_URL));
        eq("device", "play6", Ota.DEVICE);
        // no beta channel on any Play 6 edition: no switch, the image's own manifest only
        for (String img : new String[] {p6, b + "update-public-nogms.json", b + "update-stable.json"}) {
            eq("no channels " + img, null, Ota.channels(img));
            eq("beta off " + img, false, Ota.betaOn(img, Boolean.TRUE));
            eq("manifest " + img, img, Ota.manifestUrl(img, Boolean.TRUE));
            eq("one channel read " + img, 1, Channels.read(img, true).length);
        }
        // the guide links stay the ones the Play 6 updater had
        String g = "https://github.com/Project-Lumen-OS/lumen-os/blob/main/docs/install/";
        eq("guide vbmeta en", g + "en.md#vbmeta", Ota.GUIDE_VBMETA_EN);
        eq("guide rescue ru", g + "ru.md#rescue", Ota.GUIDE_RESCUE_RU);
        System.out.println("channel tests passed=" + passed + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
