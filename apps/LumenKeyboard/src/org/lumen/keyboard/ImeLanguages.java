package org.lumen.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.LocaleList;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The keyboard's languages in the system (enabled_input_methods): read them, turn them on and off, and apply
 * {@link LanguagePolicy} at the first start and after a system language change. The keyboard may set its own
 * subtypes (InputMethodManager.setExplicitlyEnabledInputMethodSubtypes, API 34), no permission needed.
 */
public final class ImeLanguages {
    static final String TAG = "LumenKeyboard";
    private static final String PREFS = "languages";
    private static final String K_SEEDED = "seeded";
    private static final String K_SYSTEM = "system";

    private ImeLanguages() {
    }

    /** This keyboard as the system lists it, null when the system does not know it (yet). */
    public static InputMethodInfo self(Context c) {
        InputMethodManager imm = c.getSystemService(InputMethodManager.class);
        if (imm == null) return null;
        for (InputMethodInfo imi : imm.getInputMethodList()) {
            if (imi.getPackageName().equals(c.getPackageName())) return imi;
        }
        return null;
    }

    /** The languages that are on: the user's list, else the ones the system picks by itself (system language). */
    public static Set<KeyboardLanguage> on(Context c, InputMethodInfo me) {
        InputMethodManager imm = c.getSystemService(InputMethodManager.class);
        Set<KeyboardLanguage> s = languages(imm.getEnabledInputMethodSubtypeList(me, false));
        return s.isEmpty() ? languages(imm.getEnabledInputMethodSubtypeList(me, true)) : s;
    }

    /** Writes the list of languages that are on. */
    public static void set(Context c, InputMethodInfo me, Set<KeyboardLanguage> on) {
        int[] ids = new int[on.size()];
        int i = 0;
        for (KeyboardLanguage l : on) ids[i++] = l.subtypeId;
        c.getSystemService(InputMethodManager.class).setExplicitlyEnabledInputMethodSubtypes(me.getId(), ids);
        Log.i(TAG, "languages " + LanguagePolicy.ordered(on));
    }

    /** The subtype of a language in this keyboard, null when the keyboard has none (old method.xml). */
    public static InputMethodSubtype subtype(InputMethodInfo me, KeyboardLanguage l) {
        for (int i = 0; i < me.getSubtypeCount(); i++) {
            InputMethodSubtype st = me.getSubtypeAt(i);
            if (st.hashCode() == l.subtypeId) return st;
        }
        return null;
    }

    /** The keyboard language of a subtype (the Latin page for none or an unknown one). */
    public static KeyboardLanguage of(InputMethodSubtype st) {
        KeyboardLanguage l = st == null ? null : KeyboardLanguage.ofSubtypeId(st.hashCode());
        if (l == null && st != null) l = KeyboardLanguage.of(st.getLanguageTag());
        return l == null ? KeyboardLanguage.LATIN : l;
    }

    /**
     * First start and system language changes (the keyboard's start, boot, LOCALE_CHANGED). Runs only while the
     * keyboard is enabled, so a user who chose another keyboard is left alone.
     */
    public static void sync(Context c) {
        try {
            InputMethodInfo me = self(c);
            InputMethodManager imm = c.getSystemService(InputMethodManager.class);
            if (me == null || imm == null || !imm.getEnabledInputMethodList().contains(me)) return;
            SharedPreferences p = c.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, 0);
            List<String> system = systemLanguages();
            String sys = String.join(",", system);
            Set<KeyboardLanguage> on = on(c, me);
            Set<KeyboardLanguage> want;
            if (!p.getBoolean(K_SEEDED, false)) {
                want = LanguagePolicy.firstStart(system, on);
            } else if (!sys.equals(p.getString(K_SYSTEM, ""))) {
                want = LanguagePolicy.systemChanged(split(p.getString(K_SYSTEM, "")), system, on);
            } else {
                return;
            }
            if (!want.equals(languages(imm.getEnabledInputMethodSubtypeList(me, false)))) set(c, me, want);
            p.edit().putBoolean(K_SEEDED, true).putString(K_SYSTEM, sys).apply();
        } catch (RuntimeException e) {
            Log.w(TAG, "languages: " + e);
        }
    }

    static List<String> systemLanguages() {
        LocaleList ll = LocaleList.getDefault();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < ll.size(); i++) out.add(ll.get(i).getLanguage());
        return out;
    }

    private static List<String> split(String s) {
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    private static Set<KeyboardLanguage> languages(List<InputMethodSubtype> subs) {
        Set<KeyboardLanguage> s = EnumSet.noneOf(KeyboardLanguage.class);
        if (subs != null) for (InputMethodSubtype st : subs) s.add(of(st));
        return s;
    }
}
