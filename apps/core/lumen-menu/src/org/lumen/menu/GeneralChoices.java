// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/**
 * The option tokens of the two General choices the shared settings rows write on the Z9X, Z9X HB and Play 6
 * (apps/common/projector panel/SettingsRows): the source at power-on and the speech language of the voice assistant.
 * Plain Java in the module, so each adapter suite checks its profile against the tokens the app really writes.
 */
public final class GeneralChoices {
    private GeneralChoices() {}

    /** power.boot_source with two HDMI inputs (Z9X, Z9X HB): the index is the stored BootSource value, HOME 0, HDMI1 1, HDMI2 2. */
    public static final String[] BOOT_TWO_HDMI = {"home", "hdmi1", "hdmi2"};

    /** power.boot_source with the one HDMI input of the Play 6: the index is the stored BootSource value, HOME 0, HDMI 1. */
    public static final String[] BOOT_ONE_HDMI = {"home", "hdmi"};

    /** voice.language tokens, the first one is the system language. */
    public static final String[] VOICE = {"system", "ru", "en", "uk", "kk", "de", "fr", "es", "it", "pt", "pl", "tr",
            "zh", "ja", "ko", "ar"};

    /** The speech language of each {@link #VOICE} token (BCP 47, "" = the system language). The recogniser decides what it supports. */
    private static final String[] VOICE_TAGS = {"", "ru-RU", "en-US", "uk-UA", "kk-KZ", "de-DE", "fr-FR", "es-ES",
            "it-IT", "pt-BR", "pl-PL", "tr-TR", "zh-CN", "ja-JP", "ko-KR", "ar-SA"};

    /** The speech language of a token, "" (the system language) for "system" and for a token that is not one. */
    public static String voiceTag(String token) {
        for (int i = 0; i < VOICE.length; i++) if (VOICE[i].equals(token)) return VOICE_TAGS[i];
        return "";
    }

    /** The token of a stored speech language, "system" when it is unset or not one of the list. */
    public static String voiceToken(String tag) {
        for (int i = 0; i < VOICE_TAGS.length; i++) if (VOICE_TAGS[i].equals(tag)) return VOICE[i];
        return VOICE[0];
    }
}
