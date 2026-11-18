package org.lumen.keyboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which keyboard languages are on (pure Java, test/run.sh). The same rule on every projector:
 * first start: what is on, plus English, Russian and every system language that has a page;
 * the system language changes: the new system languages with a page are added, nothing is taken away;
 * the user's own choice on the languages screen is kept until the system language changes again.
 */
public final class LanguagePolicy {
    private LanguagePolicy() {
    }

    /** Always on after the first start: the Latin page and Russian (most Lumen users write Russian). */
    static final KeyboardLanguage[] FIRST_START = {KeyboardLanguage.LATIN, KeyboardLanguage.RUSSIAN};

    /** First start of this keyboard on a unit: on + English + Russian + the system languages with a page. */
    public static Set<KeyboardLanguage> firstStart(List<String> system, Collection<KeyboardLanguage> on) {
        Set<KeyboardLanguage> out = copy(on);
        for (KeyboardLanguage l : FIRST_START) out.add(l);
        addSystem(out, system, null);
        return out;
    }

    /** The system languages went from before to now: their new members with a page are added to on. */
    public static Set<KeyboardLanguage> systemChanged(List<String> before, List<String> now,
            Collection<KeyboardLanguage> on) {
        Set<KeyboardLanguage> out = copy(on);
        if (out.isEmpty()) out.add(KeyboardLanguage.LATIN);
        addSystem(out, now, before);
        return out;
    }

    /** The language after current in table order, round; current itself when it is the only one. */
    public static KeyboardLanguage next(Collection<KeyboardLanguage> on, KeyboardLanguage current) {
        List<KeyboardLanguage> l = ordered(on);
        if (l.isEmpty()) return current;
        int i = l.indexOf(current);
        return l.get((i + 1) % l.size());
    }

    /** One language always stays on (the screen refuses to turn off the last). */
    public static boolean canTurnOff(Collection<KeyboardLanguage> on, KeyboardLanguage l) {
        return on.contains(l) && on.size() > 1;
    }

    /** on in table order (the order of the language key and the screen). */
    public static List<KeyboardLanguage> ordered(Collection<KeyboardLanguage> on) {
        List<KeyboardLanguage> out = new ArrayList<>();
        for (KeyboardLanguage l : KeyboardLanguage.values()) if (on.contains(l)) out.add(l);
        return out;
    }

    /**
     * The language the Latin page is shown as: the system language when it is itself written in Latin letters
     * (the AOSP page follows it: German QWERTZ, French AZERTY), else English.
     */
    public static Locale latinLocale(Locale system) {
        if (system == null || system.getLanguage().isEmpty()) return Locale.US;
        return writtenInLatin(system) ? system : Locale.US;
    }

    /** The language's own name starts with a Latin letter ("Deutsch" yes, "русский" no). */
    public static boolean writtenInLatin(Locale l) {
        String name = l.getDisplayLanguage(l);
        if (name.isEmpty()) return false;
        int c = name.codePointAt(0);
        return Character.isLetter(c) && Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN;
    }

    /** Two-letter code the language key shows (EN, RU, DE). */
    public static String keyLabel(KeyboardLanguage l, Locale system) {
        String tag = l == KeyboardLanguage.LATIN ? latinLocale(system).getLanguage() : l.tag;
        return tag.toUpperCase(Locale.ROOT);
    }

    /** The language's own name with a capital letter ("Русский", "English"), for the space bar and the screen. */
    public static String ownName(KeyboardLanguage l, Locale system) {
        Locale loc = l == KeyboardLanguage.LATIN ? latinLocale(system) : new Locale(l.tag);
        String n = loc.getDisplayLanguage(loc);
        if (n.isEmpty()) return keyLabel(l, system);
        int first = n.offsetByCodePoints(0, 1);
        return n.substring(0, first).toUpperCase(loc) + n.substring(first);
    }

    private static void addSystem(Set<KeyboardLanguage> out, List<String> now, List<String> before) {
        if (now == null) return;
        for (String tag : now) {
            KeyboardLanguage l = KeyboardLanguage.of(tag);
            if (l == null || (before != null && containsLanguage(before, l))) continue;
            out.add(l);
        }
    }

    private static boolean containsLanguage(List<String> tags, KeyboardLanguage l) {
        for (String t : tags) if (KeyboardLanguage.of(t) == l) return true;
        return false;
    }

    private static Set<KeyboardLanguage> copy(Collection<KeyboardLanguage> on) {
        Set<KeyboardLanguage> s = EnumSet.noneOf(KeyboardLanguage.class);
        if (on != null) s.addAll(on);
        return s;
    }
}
