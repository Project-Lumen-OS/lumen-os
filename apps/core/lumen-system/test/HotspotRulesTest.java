// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

import java.util.Random;

/** HotspotRules: the stock name and password rules, the first configuration, the switch states, the tether check. */
public final class HotspotRulesTest {
    private static int fails;

    private static void check(boolean ok, String what) {
        if (ok) System.out.println("ok   hotspot: " + what);
        else { System.out.println("FAIL hotspot: " + what); fails++; }
    }

    public static void main(String[] a) {
        // names: not empty, at most 32 UTF-8 bytes
        check(HotspotRules.name("Lumen Z9X") == null, "a plain name is fine");
        check(HotspotRules.name("") == HotspotProblem.NAME_EMPTY, "an empty name is refused");
        check(HotspotRules.name("   ") == HotspotProblem.NAME_EMPTY, "a name of spaces is refused");
        check(HotspotRules.name(null) == HotspotProblem.NAME_EMPTY, "no name is refused");
        check(HotspotRules.name("x".repeat(32)) == null, "32 bytes are fine");
        check(HotspotRules.name("x".repeat(33)) == HotspotProblem.NAME_LONG, "33 bytes are refused");
        check(HotspotRules.name("ж".repeat(16)) == null, "16 Cyrillic letters (32 bytes) are fine");
        check(HotspotRules.name("ж".repeat(17)) == HotspotProblem.NAME_LONG, "17 Cyrillic letters (34 bytes) are refused");
        // passwords: 8..63 printable ASCII
        check(HotspotRules.password("p4x7k2m9") == null, "8 characters are fine");
        check(HotspotRules.password("p4x7k2m") == HotspotProblem.PASSWORD_SHORT, "7 characters are refused (stock)");
        check(HotspotRules.password(null) == HotspotProblem.PASSWORD_SHORT, "no password is refused");
        check(HotspotRules.password("a".repeat(63)) == null, "63 characters are fine");
        check(HotspotRules.password("a".repeat(64)) == HotspotProblem.PASSWORD_LONG, "64 characters are refused");
        check(HotspotRules.password("пароль12") == HotspotProblem.PASSWORD_CHARS, "Cyrillic is refused");
        check(HotspotRules.password("abc def ~!") == null, "space and ~ are printable ASCII");
        check(HotspotRules.password("abcdefg\t") == HotspotProblem.PASSWORD_CHARS, "a tab is refused");
        check(HotspotRules.password("abcdefg\u007f") == HotspotProblem.PASSWORD_CHARS, "DEL is refused");
        // new passwords
        Random r = new Random(7);
        boolean shape = true;
        for (int i = 0; i < 500; i++) {
            String p = HotspotRules.newPassword(r);
            boolean letter = false, digit = false;
            for (char ch : p.toCharArray()) {
                if (HotspotRules.ALPHABET.indexOf(ch) < 0) shape = false;
                if (Character.isDigit(ch)) digit = true;
                else letter = true;
            }
            if (p.length() != 8 || !letter || !digit || HotspotRules.password(p) != null) shape = false;
        }
        check(shape, "a new password: 8 characters of the alphabet, a letter and a digit, accepted by the rules");
        check(!new Random(1).equals(null) && !HotspotRules.newPassword(new Random(1)).equals(HotspotRules.newPassword(new Random(2))),
                "new passwords differ between seeds");
        check(HotspotRules.ALPHABET.matches("[a-z2-9]+") && !HotspotRules.ALPHABET.matches(".*[l1io0].*"),
                "the alphabet has no l, 1, i, o, 0");
        // the first configuration
        check(HotspotRules.androidDefault("AndroidAP_1234") && HotspotRules.androidDefault("AndroidAP")
                && HotspotRules.androidDefault(null) && HotspotRules.androidDefault(""), "Android's made-up names");
        check(!HotspotRules.androidDefault("Home") && !HotspotRules.androidDefault("AndroidAP_home"), "other names are kept");
        String[] f = HotspotRules.first("AndroidAP_4821", "zzzzzzzz", "Lumen Z9X", new Random(3));
        check("Lumen Z9X".equals(f[0]) && !"zzzzzzzz".equals(f[1]) && HotspotRules.password(f[1]) == null,
                "Android's name becomes the model name with a new password");
        f = HotspotRules.first("Kino", "secret123", "Lumen Z9X", new Random(3));
        check("Kino".equals(f[0]) && "secret123".equals(f[1]), "a set name and password stay the reset target");
        f = HotspotRules.first("Kino", "short", "Lumen Z9X", new Random(3));
        check("Kino".equals(f[0]) && HotspotRules.password(f[1]) == null, "a password the rules refuse is replaced");
        f = HotspotRules.first("x".repeat(40), "secret123", "Lumen Z9X", new Random(3));
        check("Lumen Z9X".equals(f[0]), "a name the rules refuse becomes the model name");
        // switch states (WifiManager WIFI_AP_STATE_*)
        check(HotspotRules.on(13) && HotspotRules.on(12), "enabled and enabling show on");
        check(!HotspotRules.on(11) && !HotspotRules.on(10) && !HotspotRules.on(14), "disabled, disabling and failed show off");
        check(HotspotRules.stopped(11) && HotspotRules.stopped(14) && !HotspotRules.stopped(10) && !HotspotRules.stopped(13),
                "a restart starts again after disabled or failed, not while disabling");
        // tethering of the vendor AP interface
        String[] vendor = {"wlan\\d", "softap\\d", "ap\\d"}, emulator = {"wlan\\d"};
        check(HotspotRules.tetherable(vendor, "ap0"), "the vendor overlay tethers ap0");
        check(!HotspotRules.tetherable(emulator, "ap0"), "the emulator overlay (wlan only) does not tether ap0");
        check(!HotspotRules.tetherable(vendor, null) && !HotspotRules.tetherable(null, "ap0") && !HotspotRules.tetherable(vendor, ""),
                "no patterns or no interface tether nothing");
        check(!HotspotRules.tetherable(new String[]{"[", "wlan\\d"}, "ap0") && HotspotRules.tetherable(new String[]{"[", "ap\\d"}, "ap0"),
                "a broken pattern is skipped");
        check(!HotspotRules.tetherable(new String[]{"ap\\d"}, "xap0"), "a pattern matches the whole name");
        // the connected network's name
        check("Home 5G".equals(HotspotRules.ssid("\"Home 5G\"")), "quotes go");
        check(HotspotRules.ssid("<unknown ssid>") == null && HotspotRules.ssid(null) == null && HotspotRules.ssid("\"\"") == null,
                "no network gives no name");
        check("Home".equals(HotspotRules.ssid("Home")), "a name without quotes stays");
        if (fails > 0) System.exit(1);
    }
}
