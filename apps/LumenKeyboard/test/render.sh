#!/bin/bash
# render.sh: PNGs of Lumen Keyboard on an Android TV 14 emulator (1920x1080, 320 dpi) for review, D-pad only.
#   ADB="adb -s emulator-5554" bash render.sh KEYBOARD.apk PREVIEW.apk OUTDIR
# The emulator image's own keyboard is Gboard, so the APK installs next to it as a user app (same package as on the
# images). PREVIEW.apk = test/preview (one text field). Never run against a projector.
set -eu
KB=$1 PV=$2 OUT=$3
A=${ADB:-adb -s emulator-5554}
IME=com.android.inputmethod.leanback/.service.LeanbackImeService
mkdir -p "$OUT"
k() { for x in "$@"; do $A shell input keyevent "$x"; sleep 0.7; done; }
shot() { sleep 1; $A exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
field() { $A shell am start -S -W -n org.lumen.kbpreview/.Field --es hint "$1" > /dev/null; sleep 3; }
# from the start focus (t) to the language key: three rows down lands on space, one left is the language key
to_lang() { k DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_LEFT; }
lang_is() { $A shell settings get secure selected_input_method_subtype | tr -d '\r'; }
switch_to() { # subtype id (decimal); OK on the language key until it is selected
  for _ in 1 2 3 4 5 6 7; do [ "$(lang_is)" = "$1" ] && return 0; k DPAD_CENTER; sleep 0.5; done
  echo "render: subtype $1 never selected"; return 1
}

$A install -r "$KB" > /dev/null
$A install -r "$PV" > /dev/null
$A shell settings put system accelerometer_rotation 0
# a first start: no keyboard data, no languages of ours in enabled_input_methods
$A shell pm clear com.android.inputmethod.leanback > /dev/null
E=$($A shell settings get secure enabled_input_methods | tr -d '\r' | sed "s#$IME[;0-9]*##; s#::#:#; s#:\$##")
$A shell settings put secure enabled_input_methods "'$E'"
$A shell cmd locale set-app-locales com.android.inputmethod.leanback --locales en-US
$A shell ime enable $IME > /dev/null
$A shell ime set $IME > /dev/null
sleep 2
echo "first start: $($A shell settings get secure enabled_input_methods | tr ':' '\n' | grep leanback)"

# English UI: the first start rule gave English and Russian
field "City"
to_lang
switch_to 1279983617; shot 01_en_language_key_focused
k DPAD_CENTER; switch_to 1279983618; shot 02_ru_after_language_key
k DPAD_UP DPAD_CENTER DPAD_RIGHT DPAD_CENTER DPAD_LEFT DPAD_LEFT DPAD_CENTER; shot 03_ru_typed_in_field
k DPAD_DOWN DPAD_RIGHT
$A shell input keyevent --longpress DPAD_CENTER; shot 04_languages_screen_en
# turn on Ukrainian, Belarusian, Kazakh and Arabic for the page renders
k DPAD_DOWN DPAD_DOWN DPAD_CENTER DPAD_DOWN DPAD_CENTER DPAD_DOWN DPAD_CENTER DPAD_DOWN DPAD_CENTER; shot 05_languages_all_on
k BACK
field "City"
to_lang
for id in 1279983619 1279983620 1279983621 1279983622; do switch_to $id; done
switch_to 1279983619; shot 06_uk_page
switch_to 1279983620; shot 07_be_page
switch_to 1279983621; shot 08_kk_page
switch_to 1279983622; shot 09_ar_page
switch_to 1279983617
k DPAD_LEFT DPAD_LEFT DPAD_CENTER; shot 10_symbols_page_with_language_key
$A shell am force-stop org.lumen.kbpreview

# Russian UI of the keyboard (its texts and the screen), English and Russian only again
$A shell cmd locale set-app-locales com.android.inputmethod.leanback --locales ru-RU
$A shell am start -W -n com.android.inputmethod.leanback/org.lumen.keyboard.LanguagesActivity > /dev/null; sleep 2
k DPAD_DOWN DPAD_DOWN DPAD_CENTER DPAD_DOWN DPAD_CENTER DPAD_DOWN DPAD_CENTER DPAD_DOWN DPAD_CENTER; shot 11_languages_screen_ru
k BACK
field "Город"
to_lang
switch_to 1279983617; shot 12_ru_ui_en_page
k DPAD_CENTER; switch_to 1279983618; shot 13_ru_ui_ru_page
# a new field starts on н: one left is е, its long press offers ё (the key shows it small in its corner)
field "Город"
k DPAD_LEFT
$A shell input keyevent --longpress DPAD_CENTER; shot 14_ru_long_press_e
$A shell am force-stop org.lumen.kbpreview
echo RENDER_DONE
