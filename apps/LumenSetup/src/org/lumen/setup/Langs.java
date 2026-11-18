package org.lumen.setup;

import java.util.Locale;

/** The 17 UI languages of the Lumen apps, with native names. Not translated on purpose. */
public final class Langs {
    private Langs() {}

    /** Language tags passed to LocalePicker.updateLocales, in the list's order after the current one. */
    public static final String[] TAGS = {
            "en-US", "ru-RU", "uk-UA", "kk-KZ", "be-BY", "de-DE", "fr-FR", "es-ES", "it-IT",
            "pt-BR", "pl-PL", "tr-TR", "zh-CN", "zh-TW", "ja-JP", "ko-KR", "ar-EG",
    };
    public static final String[] NAMES = {
            "English", "Русский", "Українська", "Қазақша", "Беларуская", "Deutsch", "Français",
            "Español", "Italiano", "Português (Brasil)", "Polski", "Türkçe", "简体中文", "繁體中文",
            "日本語", "한국어", "العربية",
    };

    /** Index of the row that matches the locale best (exact region, else language), or -1. */
    public static int indexOf(Locale l) {
        if (l == null) return -1;
        String lang = l.getLanguage();
        String region = l.getCountry();
        if ("zh".equals(lang)) {
            String script = l.getScript();
            boolean trad = "Hant".equals(script) || "TW".equals(region) || "HK".equals(region) || "MO".equals(region);
            return indexOfTag(trad ? "zh-TW" : "zh-CN");
        }
        int langOnly = -1;
        for (int i = 0; i < TAGS.length; i++) {
            Locale t = Locale.forLanguageTag(TAGS[i]);
            if (!t.getLanguage().equals(lang)) continue;
            if (t.getCountry().equals(region)) return i;
            if (langOnly < 0) langOnly = i;
        }
        return langOnly;
    }

    private static int indexOfTag(String tag) {
        for (int i = 0; i < TAGS.length; i++) if (TAGS[i].equals(tag)) return i;
        return -1;
    }

    /**
     * The rows of the language list: the system's current language first (the one a first start already switched
     * to), then every other in {@link #TAGS} order. {@code current} is an index of TAGS or -1 (then TAGS order).
     */
    public static int[] order(int current) {
        int[] out = new int[TAGS.length];
        int n = 0;
        if (current >= 0 && current < TAGS.length) out[n++] = current;
        for (int i = 0; i < TAGS.length; i++) if (i != current) out[n++] = i;
        return out;
    }

    /** Native display name of any locale (for the "More languages" list and the summary). */
    public static String nativeName(Locale l) {
        int i = indexOf(l);
        if (i >= 0 && Locale.forLanguageTag(TAGS[i]).getCountry().equals(l.getCountry())) return NAMES[i];
        String n = l.getDisplayName(l);
        if (n.isEmpty()) return l.toLanguageTag();
        return n.substring(0, 1).toUpperCase(l) + n.substring(1);
    }
}
