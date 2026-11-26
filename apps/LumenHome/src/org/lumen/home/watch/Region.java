// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.watch;

/**
 * The user's region for TMDB watch providers. The time zone wins when it names a country (Lumen's default language is
 * English, so en-US says nothing about where the projector is), then the locale's country, else Russia.
 */
public final class Region {
    public static final String DEFAULT = "RU";

    private static final String[][] ZONES = {
            { "RU", "Europe/Moscow", "Europe/Kaliningrad", "Europe/Samara", "Europe/Volgograd", "Europe/Saratov",
                    "Europe/Ulyanovsk", "Europe/Astrakhan", "Europe/Kirov", "Asia/Yekaterinburg", "Asia/Omsk",
                    "Asia/Novosibirsk", "Asia/Barnaul", "Asia/Tomsk", "Asia/Novokuznetsk", "Asia/Krasnoyarsk",
                    "Asia/Irkutsk", "Asia/Chita", "Asia/Yakutsk", "Asia/Khandyga", "Asia/Vladivostok", "Asia/Ust-Nera",
                    "Asia/Magadan", "Asia/Sakhalin", "Asia/Srednekolymsk", "Asia/Kamchatka", "Asia/Anadyr",
                    "W-SU" },
            { "UA", "Europe/Kiev", "Europe/Kyiv", "Europe/Uzhgorod", "Europe/Zaporozhye" },
            { "BY", "Europe/Minsk" },
            { "KZ", "Asia/Almaty", "Asia/Qostanay", "Asia/Aqtobe", "Asia/Aqtau", "Asia/Atyrau", "Asia/Oral", "Asia/Qyzylorda" },
            { "UZ", "Asia/Tashkent", "Asia/Samarkand" },
            { "AM", "Asia/Yerevan" },
            { "GE", "Asia/Tbilisi" },
            { "AZ", "Asia/Baku" },
            { "KG", "Asia/Bishkek" },
    };

    private Region() {}

    public static String of(String timeZone, String localeCountry) {
        if (timeZone != null) {
            for (String[] z : ZONES) for (int i = 1; i < z.length; i++) if (z[i].equals(timeZone)) return z[0];
        }
        if (localeCountry != null && localeCountry.matches("[A-Za-z]{2}")) return localeCountry.toUpperCase(java.util.Locale.ROOT);
        return DEFAULT;
    }
}
