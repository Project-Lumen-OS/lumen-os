#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Resource and wiring checks of Lumen Keyboard (test/run.sh). Exit 1 on the first list of problems."""
import os
import re
import subprocess
import sys

APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(APP, "res")
SRC = os.path.join(APP, "src")
LANGS = "ar be de es fr it ja kk ko pl pt pt-rBR ru tr uk zh-rCN zh-rTW".split()
BOTTOM = ["-2", "-1", "-9", "32", "-3", "-4"]
problems = []


def bad(msg):
    problems.append(msg)


def read(*p):
    with open(os.path.join(APP, *p), encoding="utf-8") as f:
        return f.read()


def enum_rows():
    s = read("src", "org", "lumen", "keyboard", "KeyboardLanguage.java")
    return re.findall(r'^\s+([A-Z]+)\("([a-z]+)", "([A-Za-z_]+)", (0x[0-9a-f]+), (true|false)\)[,;]', s, re.M)


def subtypes():
    s = read("res", "xml", "method.xml")
    out = []
    for blk in re.findall(r"<subtype(.*?)/>", s, re.S):
        d = dict(re.findall(r'android:(\w+)="([^"]*)"', blk))
        out.append(d)
    return s, out


def check_subtypes():
    rows = enum_rows()
    if len(rows) != 6:
        bad("KeyboardLanguage: %d rows parsed, expected 6" % len(rows))
    text, subs = subtypes()
    if len(subs) != len(rows):
        bad("method.xml has %d subtypes, KeyboardLanguage %d" % (len(subs), len(rows)))
    for (name, tag, loc, sid, latin), st in zip(rows, subs):
        if st.get("imeSubtypeLocale") != loc:
            bad("%s: method.xml locale %s, enum %s" % (name, st.get("imeSubtypeLocale"), loc))
        if int(st.get("subtypeId", "0"), 16) != int(sid, 16):
            bad("%s: method.xml subtypeId %s, enum %s" % (name, st.get("subtypeId"), sid))
        if st.get("isAsciiCapable") != latin:
            bad("%s: isAsciiCapable %s, enum latin %s" % (name, st.get("isAsciiCapable"), latin))
        if st.get("imeSubtypeMode") != "keyboard":
            bad("%s: mode %s" % (name, st.get("imeSubtypeMode")))
    if "EnabledWhenDefaultIsNotAsciiCapable" not in (subs[0].get("imeSubtypeExtraValue", "") if subs else ""):
        bad("the Latin subtype must carry EnabledWhenDefaultIsNotAsciiCapable")
    if 'android:settingsActivity="org.lumen.keyboard.LanguagesActivity"' not in text:
        bad("method.xml: settingsActivity is not the languages screen")


def check_pages():
    c = read("src", "com", "android", "inputmethod", "leanback", "LeanbackKeyboardContainer.java")
    m = re.search(r"LANGUAGE_PAGES = \{\s*([^}]*)\}", c)
    pages = [p.strip() for p in m.group(1).split(",") if p.strip()] if m else []
    want = ["0"] + ["R.xml.lumen_%s" % tag for _, tag, _, _, _ in enum_rows()[1:]]
    if pages != want:
        bad("LANGUAGE_PAGES %s, want %s (KeyboardLanguage order)" % (pages, want))
    files = [f for f in os.listdir(os.path.join(RES, "xml"))
             if re.match(r"(lumen_[a-z]{2}|qwerty[a-z_]*|qwertz[a-z_]*|azerty|sym[a-z_]*)\.xml$", f)]
    for f in sorted(files):
        s = read("res", "xml", f)
        rows = re.findall(r"<Row([^>]*)>(.*?)</Row>", s, re.S)
        if len(rows) != 5:
            bad("%s: %d rows, the grid has 5" % (f, len(rows)))
            continue
        for i, (attrs, r) in enumerate(rows[:4]):
            n = len(re.findall(r"<Key\b", r))
            wide = 'android:horizontalGap="@dimen/keyboard_horizontal_gap_12"' in attrs
            if n != (12 if wide else 11) or (wide and i == 0):
                bad("%s row %d: %d keys%s, a row has 11 (a letter row 12 with the narrow gap)"
                    % (f, i, n, " with the narrow gap" if wide else ""))
        rows = [r for _, r in rows]
        codes = re.findall(r'android:codes="(-?\d+)"', rows[4])
        if codes != BOTTOM:
            bad("%s bottom row %s, want %s" % (f, codes, BOTTOM))
        lang = re.search(r'<Key\s+android:codes="-9"(.*?)/>', rows[4], re.S)
        if not lang or "@dimen/language_key_width" not in lang.group(1) or "@drawable/ic_ime_language" not in lang.group(1):
            bad("%s: language key without its width or globe" % f)
        space = re.search(r'<Key\s+android:codes="32"(.*?)/>', rows[4], re.S)
        if not space or "@dimen/space_key_width" not in space.group(1):
            bad("%s: space key without space_key_width" % f)
    d = read("res", "values", "dimens.xml")
    if '<dimen name="space_key_width">188dp</dimen>' not in d or '<dimen name="language_key_width">68dp</dimen>' not in d:
        bad("dimens: space 188dp (5 columns) and language 68dp (2 columns) keep the row 11 columns wide")
    if '<dimen name="keyboard_horizontal_gap_12">8.67dp</dimen>' not in d:
        bad("dimens: a row of 12 keys needs the 8.67dp gap to be as wide as 11 (12 x 36.67 = 440)")
    for tag in ("ru", "uk", "be", "kk", "ar"):
        if not os.path.exists(os.path.join(RES, "xml", "lumen_%s.xml" % tag)):
            bad("no page lumen_%s.xml" % tag)
    r = subprocess.run([sys.executable, os.path.join(APP, "tools", "gen_layouts.py"), "check"],
                       capture_output=True, text=True)
    if r.returncode != 0:
        bad("gen_layouts check: " + r.stdout.strip())


def check_strings():
    names = ["ime_name", "ime_service_name", "keyboardview_keycode_language", "lumen_languages_title",
             "lumen_languages_hint", "lumen_languages_last"]
    for lang in [""] + LANGS:
        d = "values" + ("-" + lang if lang else "")
        p = os.path.join(RES, d, "strings_lumen.xml")
        if not os.path.exists(p):
            bad("no %s/strings_lumen.xml" % d)
            continue
        s = read("res", d, "strings_lumen.xml")
        for n in names:
            m = re.search(r'<string name="%s">([^<]+)</string>' % n, s)
            if not m:
                bad("%s: no %s" % (d, n))
            elif re.search("[\u2013\u2014;]", m.group(1)):
                bad("%s: %s has a dash or a semicolon" % (d, n))
    for d in os.listdir(RES):
        p = os.path.join(RES, d, "strings.xml")
        if os.path.exists(p) and re.search(r'name="ime_(service_)?name"', read("res", d, "strings.xml")):
            bad("%s/strings.xml still names the keyboard (Leanback): the name lives in strings_lumen.xml" % d)


def check_wiring():
    svc = read("src", "com", "android", "inputmethod", "leanback", "service", "LeanbackImeService.java")
    ctl = read("src", "com", "android", "inputmethod", "leanback", "LeanbackKeyboardController.java")
    cnt = read("src", "com", "android", "inputmethod", "leanback", "LeanbackKeyboardContainer.java")
    view = read("src", "com", "android", "inputmethod", "leanback", "LeanbackKeyboardView.java")
    man = read("AndroidManifest.xml")
    scr = read("src", "org", "lumen", "keyboard", "LanguagesActivity.java")
    if "\\u2713" in scr or "\u2713" in scr:
        bad("languages screen: a text check mark, the rows show the switch icon")
    for icon in ("lumen_ic_toggle_on", "lumen_ic_toggle_off"):
        x = read("res", "drawable", icon + ".xml")
        if 'Material Symbols Rounded' not in x or 'android:viewportWidth="960"' not in x or 'android:width="24dp"' not in x:
            bad("%s: not the 24 dp Material Symbols icon of apps/core/lumen-icons" % icon)
    need = [
        (svc, r"super\.onCreate\(\);\s*ImeLanguages\.sync\(this\);", "service onCreate syncs the languages"),
        (svc, r"new IntentFilter\(Intent\.ACTION_LOCALE_CHANGED\)", "service listens to system language changes"),
        (svc, r"ImeLanguages\.sync\(LeanbackImeService\.this\);\s*applyLanguage\(\);", "locale change syncs and redraws"),
        (svc, r"onCurrentInputMethodSubtypeChanged\(newSubtype\);\s*applyLanguage\(\);", "subtype change redraws"),
        (svc, r"mKeyboardController\.onStartInputView\(\);\s*applyLanguage\(\);", "every keyboard start redraws"),
        (svc, r"if \(on\.size\(\) < 2\) \{\s*openLanguages\(\);\s*return;", "one language on: key opens the screen"),
        (svc, r"switchInputMethod\(me\.getId\(\), st\)", "language key switches the subtype"),
        (ctl, r"case LeanbackKeyboardView\.KEYCODE_LANGUAGE:\s*mContainer\.onLanguageClick\(\);", "controller: language key"),
        (cnt, r"KEYCODE_LANGUAGE\) \{\s*setTouchState\(TOUCH_STATE_NO_TOUCH\);\s*mContext\.openLanguages\(\);", "long OK opens the screen"),
        (cnt, r"initKeyboards\(mContext\.currentLanguage\(\)\);", "first pages follow the current subtype"),
        (cnt, r"if \(initialAbc\) mInitialMainKeyboard = mAbcKeyboard;", "a new language keeps the field's start page"),
        (view, r"public static final int KEYCODE_LANGUAGE = -9;", "language key code -9"),
        (view, r"KeyGrid\.nearest\(", "key under a point by the key rectangles"),
        (view, r"int baseIndex = KeyGrid\.popupStart\(tops, mFocusIndex, totalAccentKeys\);",
         "long press popup stays in the key's row (rows of 11 or 12 keys)"),
        (view, r"key\.popupCharacters\.length\(\) > 0\) \{\s*drawHint\(canvas, key, paint\);",
         "a key shows its long press letter in its corner"),
        (cnt, r"float\[\] p = KeyGrid\.aim\(r\.left, r\.right, r\.centerY\(\), r\.height\(\), fromX, dx, dy\);",
         "D-pad steps aim by KeyGrid"),
        (cnt, r"validNextFocus = getBestFocus\(p\[0\], p\[1\], nextFocus\);", "D-pad step goes to the aimed point"),
        (cnt, r"setKeyboard\(sym \? mSymKeyboard : mAbcKeyboard\);\s*refocus\(\);", "a new language keeps the focus at its place"),
        (cnt, r"mMainKeyboardView\.setKeyboard\(mSymKeyboard\);\s*\}\s*refocus\(\);", "letters and symbols keep the focus at its place"),
        (cnt, r"getBestFocus\(\(float\) mCurrKeyInfo\.rect\.centerX\(\), \(float\) mCurrKeyInfo\.rect\.centerY\(\), mTempKeyInfo\);\s*setKbFocus\(mTempKeyInfo, true, false\);",
         "refocus takes the key under the old focus"),
        (view, r"key\.codes\[0\] == ASCII_SPACE && mLangMany", "space bar names the language only when several are on"),
        (man, r'android:name="org\.lumen\.keyboard\.LanguagesActivity"\s+android:label="@string/lumen_languages_title"\s+android:exported="true"', "screen exported for TvSettings"),
        (scr, r"R\.drawable\.lumen_ic_toggle_on : R\.drawable\.lumen_ic_toggle_off", "screen: a switch icon per row, on and off"),
        (scr, r"mark\.setDuplicateParentStateEnabled\(true\);", "screen: the switch follows the row's focus"),
        (scr, r"icon\.setTintList\(focusedOr\(SWITCH_FOCUSED, on \? ACCENT : SWITCH_OFF\)\);", "screen: switch colours of the Lumen menu"),
        (man, r'<action android:name="android\.intent\.action\.LOCALE_CHANGED"/>', "receiver: LOCALE_CHANGED"),
        (man, r'<action android:name="android\.intent\.action\.BOOT_COMPLETED"/>', "receiver: BOOT_COMPLETED"),
        (man, r'package="com\.android\.inputmethod\.leanback"', "same package as LeanbackIME (replaces it in place)"),
    ]
    for text, rx, what in need:
        if not re.search(rx, text):
            bad("wiring: " + what)


check_subtypes()
check_pages()
check_strings()
check_wiring()
for p in problems:
    print("FAIL", p)
print("check_res: %d problems" % len(problems))
sys.exit(1 if problems else 0)
