# Lumen Keyboard (apps/LumenKeyboard)

The on-screen keyboard of every Lumen OS image. It is AOSP LeanbackIME (LineageOS 21
`packages/inputmethods/LeanbackIME` at e149bd75, Apache-2.0, `LICENSE`) with keyboard languages added. It keeps the
package `com.android.inputmethod.leanback` and replaces the base's APK in place
(`/system/product/app/LeanbackIME/LeanbackIME.apk`, old odex removed or rebuilt), so units updating from an older
build keep it as their selected keyboard.

## What it adds

- Languages: Latin (the AOSP page of the system language: QWERTY, QWERTZ, AZERTY), Russian, Ukrainian,
  Belarusian, Kazakh, Arabic. One subtype each (`res/xml/method.xml`, stable `subtypeId`), one row each in
  `org.lumen.keyboard.KeyboardLanguage`. A language without a page here is not offered (Chinese, Japanese, Korean
  need a conversion engine; Lumen Home's search offers a keyboard app for them).
- Language key on every page (globe and the code, EN / RU): OK switches to the next language that is on, in the
  table order. With one language on, OK opens the languages screen. A long OK on it always opens the screen. With
  two or more languages on, the space bar shows the language's own name.
- First start and system language changes (`LanguagePolicy`, applied by `ImeLanguages.sync` from the service start,
  boot, LOCALE_CHANGED): first start turns on English, Russian and every system language with a page; a later system
  language change adds the new language, nothing is taken away. The keyboard writes its own subtypes with
  `InputMethodManager.setExplicitlyEnabledInputMethodSubtypes` (API 34, no permission). Before that ran, the system
  itself pairs a non-Latin system language with the Latin page (`EnabledWhenDefaultIsNotAsciiCapable`).
- Languages screen (`LanguagesActivity`, the IME's `settingsActivity`): Settings, Device Preferences, Keyboard,
  "Lumen Keyboard settings". One row per language, OK turns it on or off, the last one stays on. Each row shows a
  switch on the right as the Lumen menu does (accent track when on, white track in the focused row): the Material
  Symbols `toggle_on` / `toggle_off` icons, byte copies of `apps/core/lumen-icons` (Apache-2.0) until the keyboard is
  built with that module. TvSettings
  (Lineage 21) has a subtype screen too (`android.settings.INPUT_METHOD_SUBTYPE_SETTINGS`) but nothing links to it.
- Key navigation and long press popups by the keys' own rectangles (`KeyGrid`): AOSP assumed a fixed 11 x 5 grid
  with a seven column space.

## Pages

`tools/gen_layouts.py` holds the Cyrillic and Arabic pages as data and puts the language key into every AOSP page:
four rows of 11 keys, then mode, shift, language (2 columns), space (5 columns), left, right. The Cyrillic pages
follow the standard ЙЦУКЕН layouts, so a letter row can have 12 keys (ъ, ї or the apostrophe after х): that row gets
a narrower gap and ends where the others end, and the D-pad goes by the keys' rectangles (`KeyGrid`). Edit the
table, run `python3 tools/gen_layouts.py write`, commit the XML.

Long presses (the key shows the first one small in its corner, as Gboard for Android TV puts ё on е):

- Russian, Belarusian: е gives ё
- Ukrainian: г gives ґ
- Arabic: ا gives أ إ آ
- Kazakh: the letters of the top row give the digits 1 to 0

`test/alphabet_check.py` types every letter of every language, lower and upper case, from the shipped pages: a key,
or a long press from this list.

## Tests (builder, not the Mac)

```
sh apps/LumenKeyboard/test/run.sh             # KeyboardTest (rules, D-pad on every page), alphabet_check.py, check_res.py
python3 apps/LumenKeyboard/test/mutate.py     # targeted mutants, every one must be killed
ADB=... bash apps/LumenKeyboard/test/render.sh KEYBOARD.apk PREVIEW.apk OUTDIR   # emulator PNGs (test/preview app)
```

## Images

Every recipe names it (`devices/<c>/build.conf apps=`, the registry `device.conf apps=` where the model signs its
own apps, `tools/play6/build_play6.sh` for the Play 6) and its steps put the staged APK at LeanbackIME's path.
