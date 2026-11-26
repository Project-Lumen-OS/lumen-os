// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The decisions of the stock Wireless Hotspot (newsettings wifiap NetWorkApManager, NetWorkApRepository) as plain Java:
 * what a name and a password may be, the first configuration kept as the reset target, which Android hotspot states
 * show the switch on, and whether the vendor AP interface is one Android tethers. Hotspot does the Android calls.
 */
public final class HotspotRules {
    /** Wi-Fi limit of a network name, in UTF-8 bytes. */
    public static final int NAME_MAX_BYTES = 32;
    /** WPA2 pass phrase limits (stock asks for at least 8). */
    public static final int PASSWORD_MIN = 8, PASSWORD_MAX = 63;
    /** Length of a new password: the stock minimum. */
    public static final int NEW_PASSWORD = 8;
    /** Letters and digits of a new password, without the ones a person mixes up on a wall (l 1 i o 0). */
    static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    /** Android's own made-up hotspot name (WifiApConfigStore: wifi_tether_configure_ssid_default + "_" + 4 digits). */
    private static final Pattern ANDROID_DEFAULT = Pattern.compile("AndroidAP(_\\d{1,6})?");
    /** WifiManager hotspot states. */
    static final int AP_DISABLING = 10, AP_DISABLED = 11, AP_ENABLING = 12, AP_ENABLED = 13, AP_FAILED = 14;
    /** WifiInfo's name while not connected. */
    private static final String UNKNOWN_SSID = "<unknown ssid>";

    private HotspotRules() {}

    /** The problem of a network name, null when it is fine. */
    public static HotspotProblem name(String name) {
        if (name == null || name.trim().isEmpty()) return HotspotProblem.NAME_EMPTY;
        if (name.getBytes(StandardCharsets.UTF_8).length > NAME_MAX_BYTES) return HotspotProblem.NAME_LONG;
        return null;
    }

    /** The problem of a password, null when it is fine. */
    public static HotspotProblem password(String password) {
        if (password == null || password.length() < PASSWORD_MIN) return HotspotProblem.PASSWORD_SHORT;
        if (password.length() > PASSWORD_MAX) return HotspotProblem.PASSWORD_LONG;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (ch < 0x20 || ch > 0x7E) return HotspotProblem.PASSWORD_CHARS;
        }
        return null;
    }

    /** A new password: {@link #NEW_PASSWORD} characters of {@link #ALPHABET}, at least one letter and one digit. */
    public static String newPassword(Random r) {
        while (true) {
            StringBuilder b = new StringBuilder(NEW_PASSWORD);
            boolean letter = false, digit = false;
            for (int i = 0; i < NEW_PASSWORD; i++) {
                char ch = ALPHABET.charAt(r.nextInt(ALPHABET.length()));
                if (Character.isDigit(ch)) digit = true;
                else letter = true;
                b.append(ch);
            }
            if (letter && digit) return b.toString();
        }
    }

    /** True for Android's own made-up name (or none): a projector then gets the model's name instead. */
    public static boolean androidDefault(String ssid) {
        return ssid == null || ssid.trim().isEmpty() || ANDROID_DEFAULT.matcher(ssid).matches();
    }

    /**
     * The first configuration, kept as the reset target (stock keeps the one its page read first): the stored one,
     * except Android's made-up name, which becomes {@code modelName} with a new password; a stored password that
     * could not be saved again gets a new one too.
     */
    public static String[] first(String ssid, String password, String modelName, Random r) {
        if (androidDefault(ssid) && name(modelName) == null) return new String[]{modelName, newPassword(r)};
        if (name(ssid) != null) return new String[]{modelName, newPassword(r)};
        return new String[]{ssid, password(password) == null ? password : newPassword(r)};
    }

    /** The switch shows on while the hotspot runs or starts. */
    public static boolean on(int apState) { return apState == AP_ENABLED || apState == AP_ENABLING; }

    /** The hotspot has stopped (or failed to start): the moment a restart may start it again. */
    public static boolean stopped(int apState) { return apState == AP_DISABLED || apState == AP_FAILED; }

    /**
     * True when Android tethers {@code iface}: one of the Wi-Fi tether patterns (config_tether_wifi_regexs after every
     * overlay) matches it. An image whose overlay leaves the vendor AP interface out starts the hotspot but gives the
     * phones no address, so the rows stay hidden there.
     */
    public static boolean tetherable(String[] regexs, String iface) {
        if (regexs == null || iface == null || iface.isEmpty()) return false;
        for (String re : regexs) {
            try {
                if (re != null && Pattern.matches(re, iface)) return true;
            } catch (PatternSyntaxException e) {
                // a broken pattern tethers nothing
            }
        }
        return false;
    }

    /** The connected Wi-Fi network's name from WifiInfo, without quotes; null when there is none. */
    public static String ssid(String raw) {
        if (raw == null || raw.isEmpty() || UNKNOWN_SSID.equals(raw)) return null;
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) raw = raw.substring(1, raw.length() - 1);
        return raw.isEmpty() ? null : raw;
    }
}
