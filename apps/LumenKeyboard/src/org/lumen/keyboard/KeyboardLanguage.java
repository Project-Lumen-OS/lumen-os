package org.lumen.keyboard;

import java.util.Locale;

/**
 * The languages Lumen Keyboard types, in the order the language key walks them and the languages screen lists
 * them. One subtype each in res/xml/method.xml (same locale and id, test/run.sh checks it) and one page in
 * res/xml (LATIN keeps the AOSP page of the system language, the others are lumen_[tag].xml of
 * tools/gen_layouts.py). A language without a page here is never offered.
 */
public enum KeyboardLanguage {
    LATIN("en", "en_US", 0x4c4b0001, true),
    RUSSIAN("ru", "ru_RU", 0x4c4b0002, false),
    UKRAINIAN("uk", "uk_UA", 0x4c4b0003, false),
    BELARUSIAN("be", "be_BY", 0x4c4b0004, false),
    KAZAKH("kk", "kk_KZ", 0x4c4b0005, false),
    ARABIC("ar", "ar", 0x4c4b0006, false);

    /** Language code (ISO 639-1) the system and the subtype use. */
    public final String tag;
    /** imeSubtypeLocale of the subtype. */
    public final String subtypeLocale;
    /** android:subtypeId: the stable hash the system stores in enabled_input_methods. */
    public final int subtypeId;
    /** The page types Latin letters (isAsciiCapable). */
    public final boolean latin;

    KeyboardLanguage(String tag, String subtypeLocale, int subtypeId, boolean latin) {
        this.tag = tag;
        this.subtypeLocale = subtypeLocale;
        this.subtypeId = subtypeId;
        this.latin = latin;
    }

    /** The keyboard language of a language code, null when Lumen Keyboard has no page for it. */
    public static KeyboardLanguage of(String tag) {
        if (tag == null) return null;
        String t = tag.toLowerCase(Locale.ROOT);
        int cut = t.indexOf('_') >= 0 ? t.indexOf('_') : t.indexOf('-');
        if (cut >= 0) t = t.substring(0, cut);
        for (KeyboardLanguage l : values()) if (l.tag.equals(t)) return l;
        return null;
    }

    /** The keyboard language of a subtype id, null for an unknown id. */
    public static KeyboardLanguage ofSubtypeId(int id) {
        for (KeyboardLanguage l : values()) if (l.subtypeId == id) return l;
        return null;
    }
}
