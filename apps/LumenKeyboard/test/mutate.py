#!/usr/bin/env python3
"""Targeted mutants of Lumen Keyboard: each must make test/run.sh fail (python3 test/mutate.py [NAME...]).

REGEN mutants edit the layout table and write the pages again, so the pages agree with the table and only the
alphabet and D-pad tests can see the change."""
import os
import shutil
import subprocess
import sys
import tempfile

APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K = "src/org/lumen/keyboard/"
L = "src/com/android/inputmethod/leanback/"
G = "tools/gen_layouts.py"

# name: (file, old, new): old must occur exactly once
MUTANTS = {
    "first_start_no_russian": (K + "LanguagePolicy.java", "{KeyboardLanguage.LATIN, KeyboardLanguage.RUSSIAN}", "{KeyboardLanguage.LATIN}"),
    "first_start_no_latin": (K + "LanguagePolicy.java", "{KeyboardLanguage.LATIN, KeyboardLanguage.RUSSIAN}", "{KeyboardLanguage.RUSSIAN}"),
    "first_start_no_system": (K + "LanguagePolicy.java", "        addSystem(out, system, null);\n", "\n"),
    "first_start_drops_on": (K + "LanguagePolicy.java", "    public static Set<KeyboardLanguage> firstStart(List<String> system, Collection<KeyboardLanguage> on) {\n        Set<KeyboardLanguage> out = copy(on);",
                             "    public static Set<KeyboardLanguage> firstStart(List<String> system, Collection<KeyboardLanguage> on) {\n        Set<KeyboardLanguage> out = copy(null);"),
    "change_readds_old": (K + "LanguagePolicy.java", "addSystem(out, now, before);", "addSystem(out, now, null);"),
    "change_no_latin_floor": (K + "LanguagePolicy.java", "        if (out.isEmpty()) out.add(KeyboardLanguage.LATIN);\n", "\n"),
    "change_removes": (K + "LanguagePolicy.java", "        if (out.isEmpty()) out.add(KeyboardLanguage.LATIN);\n        addSystem(out, now, before);\n        return out;",
                       "        if (out.isEmpty()) out.add(KeyboardLanguage.LATIN);\n        addSystem(out, now, before);\n        out.retainAll(copy(on));\n        if (out.isEmpty()) out.add(KeyboardLanguage.LATIN);\n        return out;"),
    "next_not_round": (K + "LanguagePolicy.java", "return l.get((i + 1) % l.size());", "return l.get(Math.min(i + 1, l.size() - 1));"),
    "next_backwards": (K + "LanguagePolicy.java", "return l.get((i + 1) % l.size());", "return l.get((i + l.size() - 1) % l.size());"),
    "next_none_null": (K + "LanguagePolicy.java", "        if (l.isEmpty()) return current;\n", "        if (l.isEmpty()) return null;\n"),
    "last_can_go": (K + "LanguagePolicy.java", "return on.contains(l) && on.size() > 1;", "return on.contains(l) && on.size() > 0;"),
    "off_can_go": (K + "LanguagePolicy.java", "return on.contains(l) && on.size() > 1;", "return on.size() > 1;"),
    "latin_always_system": (K + "LanguagePolicy.java", "return writtenInLatin(system) ? system : Locale.US;", "return system;"),
    "latin_always_us": (K + "LanguagePolicy.java", "return writtenInLatin(system) ? system : Locale.US;", "return Locale.US;"),
    "latin_script_check": (K + "LanguagePolicy.java", "Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN", "Character.UnicodeScript.of(c) != Character.UnicodeScript.CYRILLIC"),
    "label_lowercase": (K + "LanguagePolicy.java", "return tag.toUpperCase(Locale.ROOT);", "return tag;"),
    "name_no_capital": (K + "LanguagePolicy.java", "return n.substring(0, first).toUpperCase(loc) + n.substring(first);", "return n;"),
    "of_no_region_cut": (K + "KeyboardLanguage.java", "        if (cut >= 0) t = t.substring(0, cut);\n", "\n"),
    "of_case": (K + "KeyboardLanguage.java", "String t = tag.toLowerCase(Locale.ROOT);", "String t = tag;"),
    "enum_id_swap": (K + "KeyboardLanguage.java", 'RUSSIAN("ru", "ru_RU", 0x4c4b0002, false)', 'RUSSIAN("ru", "ru_RU", 0x4c4b0003, false)'),
    "enum_order": (K + "KeyboardLanguage.java", '    RUSSIAN("ru", "ru_RU", 0x4c4b0002, false),\n    UKRAINIAN("uk", "uk_UA", 0x4c4b0003, false),\n',
                   '    UKRAINIAN("uk", "uk_UA", 0x4c4b0003, false),\n    RUSSIAN("ru", "ru_RU", 0x4c4b0002, false),\n'),
    "grid_inside_wrong": (K + "KeyGrid.java", "x - (left[i] + width[i])", "x - (left[i] + width[i] / 2)"),
    "grid_ties_last": (K + "KeyGrid.java", "if (d < bestD) {", "if (d <= bestD) {"),
    "grid_no_y": (K + "KeyGrid.java", "double d = dx * dx + dy * dy;", "double d = dx * dx;"),
    "method_locale": ("res/xml/method.xml", 'android:imeSubtypeLocale="uk_UA"', 'android:imeSubtypeLocale="ru_UA"'),
    "method_no_settings": ("res/xml/method.xml", '    android:settingsActivity="org.lumen.keyboard.LanguagesActivity"\n', ""),
    "method_no_implicit_latin": ("res/xml/method.xml", 'android:imeSubtypeExtraValue="AsciiCapable,EnabledWhenDefaultIsNotAsciiCapable"', 'android:imeSubtypeExtraValue="AsciiCapable"'),
    "pages_order": (L + "LeanbackKeyboardContainer.java", "0, R.xml.lumen_ru, R.xml.lumen_uk,", "0, R.xml.lumen_uk, R.xml.lumen_ru,"),
    "space_wide": ("res/values/dimens.xml", '<dimen name="space_key_width">188dp</dimen>', '<dimen name="space_key_width">268dp</dimen>'),
    "page_row_short": ("res/xml/lumen_ru.xml", '            android:keyLabel="э" />\n', '            android:keyLabel="э" />\n        <Key\n            android:codes="1101"\n            android:keyLabel="э" />\n'),
    "aosp_page_no_lang": ("res/xml/qwerty_us.xml", '            android:codes="-9"\n', '            android:codes="-10"\n'),
    "svc_no_sync": (L + "service/LeanbackImeService.java", "        super.onCreate();\n        ImeLanguages.sync(this);\n", "        super.onCreate();\n"),
    "svc_no_locale": (L + "service/LeanbackImeService.java", "new IntentFilter(Intent.ACTION_LOCALE_CHANGED)", "new IntentFilter(Intent.ACTION_SCREEN_ON)"),
    "svc_single_switches": (L + "service/LeanbackImeService.java", "if (on.size() < 2) {", "if (on.size() < 1) {"),
    "svc_subtype_no_redraw": (L + "service/LeanbackImeService.java", "        super.onCurrentInputMethodSubtypeChanged(newSubtype);\n        applyLanguage();\n", "        super.onCurrentInputMethodSubtypeChanged(newSubtype);\n"),
    "svc_start_no_redraw": (L + "service/LeanbackImeService.java", "        mKeyboardController.onStartInputView();\n        applyLanguage();\n", "        mKeyboardController.onStartInputView();\n"),
    "ctl_key_ignored": (L + "LeanbackKeyboardController.java", "                mContainer.onLanguageClick();\n", "\n"),
    "cnt_longpress_off": (L + "LeanbackKeyboardContainer.java", "            mContext.openLanguages();\n            return true;\n", "            return false;\n"),
    "cnt_initial_kept": (L + "LeanbackKeyboardContainer.java", "        if (initialAbc) mInitialMainKeyboard = mAbcKeyboard;\n", "\n"),
    "view_space_always": (L + "LeanbackKeyboardView.java", "key.codes[0] == ASCII_SPACE && mLangMany", "key.codes[0] == ASCII_SPACE"),
    "view_old_grid": (L + "LeanbackKeyboardView.java", "int index = KeyGrid.nearest(l, t, w, h, x - getPaddingLeft(), y - getPaddingTop());", "int index = 0;"),
    "strings_ru_missing": ("res/values-ru/strings_lumen.xml", '    <string name="lumen_languages_hint">', '    <string name="lumen_languages_hintx">'),
    "strings_dash": ("res/values-de/strings_lumen.xml", "Tastatursprachen", "Tastatur \u2014 Sprachen"),
    "manifest_no_locale": ("AndroidManifest.xml", '<action android:name="android.intent.action.LOCALE_CHANGED"/>', ""),
    "manifest_screen_closed": ("AndroidManifest.xml", '             android:label="@string/lumen_languages_title"\n             android:exported="true"', '             android:label="@string/lumen_languages_title"\n             android:exported="false"'),
    "gen_table_edit": (G, '"й ц у к е:ё н г ш щ з х ъ"', '"й ц у к е:ё н г ш щ з х"'),
    "view_popup_old_grid": (L + "LeanbackKeyboardView.java", "int baseIndex = KeyGrid.popupStart(tops, mFocusIndex, totalAccentKeys);", "int baseIndex = mFocusIndex;"),
    "view_no_hint": (L + "LeanbackKeyboardView.java", "                drawHint(canvas, key, paint);\n", "\n"),
    "cnt_old_step": (L + "LeanbackKeyboardContainer.java", "float[] p = KeyGrid.aim(r.left, r.right, r.centerY(), r.height(), fromX, dx, dy);", "float[] p = {fromX, r.centerY()};"),
    "cnt_language_keeps_index": (L + "LeanbackKeyboardContainer.java", "setKeyboard(sym ? mSymKeyboard : mAbcKeyboard);\n            refocus();\n", "setKeyboard(sym ? mSymKeyboard : mAbcKeyboard);\n"),
    "cnt_symbols_keep_index": (L + "LeanbackKeyboardContainer.java", "            mMainKeyboardView.setKeyboard(mSymKeyboard);\n        }\n        refocus();\n", "            mMainKeyboardView.setKeyboard(mSymKeyboard);\n        }\n"),
    "aim_no_slide": (K + "KeyGrid.java", "float slide = height / 2f;", "float slide = 0f;"),
    "aim_one_row_step": (K + "KeyGrid.java", "centerY + dy * height * ROW_STEP", "centerY + dy * height"),
    "popup_no_shift": (K + "KeyGrid.java", "return Math.max(first, Math.min(focus, last + 1 - count));", "return focus;"),
    "popup_past_row_start": (K + "KeyGrid.java", "return Math.max(first, Math.min(focus, last + 1 - count));", "return Math.min(focus, last + 1 - count);"),
    "gap12_wide": ("res/values/dimens.xml", '<dimen name="keyboard_horizontal_gap_12">8.67dp</dimen>', '<dimen name="keyboard_horizontal_gap_12">12dp</dimen>'),
    "readme_no_ru_yo": ("README.md", "- Russian, Belarusian: е gives ё\n", "- Belarusian: е gives ё\n"),
    "screen_one_icon": (K + "LanguagesActivity.java", "R.drawable.lumen_ic_toggle_on : R.drawable.lumen_ic_toggle_off", "R.drawable.lumen_ic_toggle_on : R.drawable.lumen_ic_toggle_on"),
    "screen_no_focus_state": (K + "LanguagesActivity.java", "mark.setDuplicateParentStateEnabled(true);", "mark.setDuplicateParentStateEnabled(false);"),
    "screen_text_mark": (K + "LanguagesActivity.java", "        m.setImageDrawable(toggle(on));\n", "        m.setContentDescription(on ? \"\\u2713\" : \"\");\n"),
}

# name: (table edit in tools/gen_layouts.py), then the pages are written again, so only the page tests can kill it
REGEN = {
    "ru_no_hard_sign": ('"й ц у к е:ё н г ш щ з х ъ"', '"й ц у к е:ё н г ш щ з х"'),
    "ru_hard_sign_long_press": ('"й ц у к е:ё н г ш щ з х ъ",\n           "ф ы в а п р о л д ж э",\n           "я ч с м и т ь б ю , ."],\n    "uk"',
                                '"й ц у к е:ё н г ш щ з х",\n           "ф ы в а п р о л д ж э",\n           "я ч с м и т ь:ъ б ю , ."],\n    "uk"'),
    "ru_no_yo": ('"й ц у к е:ё н г ш щ з х ъ"', '"й ц у к е н г ш щ з х ъ"'),
    "uk_no_yi": ('"й ц у к е н г:ґ ш щ з х ї"', '"й ц у к е н г:ґ ш щ з х"'),
    "uk_no_ghe": ('"й ц у к е н г:ґ ш щ з х ї"', '"й ц у к е н г ш щ з х ї"'),
    "uk_no_apostrophe": ("\"я ч с м и т ь б ю ' .:,?!\"", '"я ч с м и т ь б ю , .:,?!"'),
    "be_no_short_u": ("\"й ц у к е:ё н г ш ў з х '\"", "\"й ц у к е:ё н г ш щ з х '\""),
    "be_latin_i": ('"я ч с м і т ь б ю , ."', '"я ч с м i т ь б ю , ."'),
    "kk_no_schwa": ('"ә:1 і:2', '"ъ:1 і:2'),
    "ar_no_alef_hamza": ('ا:أإآ', 'ا:إآ'),
    "gen_no_hint": ("keys.append(key(ord(ch), esc(ch), pop, edge, hint=more))", "keys.append(key(ord(ch), esc(ch), pop, edge))"),
    "gen_no_narrow_gap": ("        if n == WIDE:\n", "        if n == 0:\n"),
}


def run_tests(root):
    env = dict(os.environ, BUILD_DIR=os.path.join(root, "_b"))
    r = subprocess.run(["sh", os.path.join(root, "test", "run.sh")], capture_output=True, text=True, env=env, timeout=300)
    return r.returncode


def main(names):
    tmp = tempfile.mkdtemp(prefix="lumenkb-mut.")
    base = os.path.join(tmp, "base")
    shutil.copytree(APP, base, ignore=shutil.ignore_patterns("build", "_b"))
    if run_tests(base) != 0:
        print("FAIL unmutated tree does not pass")
        return 2
    killed = alive = broken = 0
    for name in names or sorted(list(MUTANTS) + list(REGEN)):
        f, old, new = MUTANTS[name] if name in MUTANTS else (G,) + REGEN[name]
        d = os.path.join(tmp, name)
        shutil.copytree(base, d)
        p = os.path.join(d, f)
        text = open(p, encoding="utf-8").read()
        if text.count(old) != 1:
            print("BROKEN %s: anchor found %d times in %s" % (name, text.count(old), f))
            broken += 1
            continue
        open(p, "w", encoding="utf-8").write(text.replace(old, new))
        if name in REGEN and subprocess.run([sys.executable, os.path.join(d, G), "write"], capture_output=True).returncode:
            print("BROKEN %s: the table does not write" % name)
            broken += 1
            continue
        rc = run_tests(d)
        if rc != 0:
            killed += 1
        else:
            alive += 1
            print("ALIVE", name)
        shutil.rmtree(d, ignore_errors=True)
    shutil.rmtree(tmp, ignore_errors=True)
    print("mutate: %d killed, %d alive, %d broken of %d" % (killed, alive, broken, killed + alive + broken))
    return 0 if alive == 0 and broken == 0 else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
