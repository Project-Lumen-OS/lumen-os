#!/bin/sh
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# x50_power_keys_test.sh: host tests of the X50 power key (devices/g0111/README.md, "Power key"):
# devices/g0111/image/x50_power_keys.py on copies of the base's key layouts (devices/g0111/test/base109_keylayout = the
# published Z9X 1.0.9 image's /system/product/usr/keylayout, the pinned base; overlay/v1/keylayout = a later base with
# the shared gear-hold fix), its check against mutants, and the RRO apps/X50PowerOverlay (values, priority, global
# keys). Optional: BUILD=1 also builds the RRO with the test key (apps/build_apk.sh).
#   sh devices/g0111/test/x50_power_keys_test.sh
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
T=$(mktemp -d "${TMPDIR:-/tmp}/x50pk.XXXXXX")
trap 'rm -rf "$T"' EXIT
PKT=$G/devices/g0111/image/x50_power_keys.py
export PKT
pk() { python3 -B "$PKT" "$@"; }
O=$G/apps/X50PowerOverlay
pass=0 fail=0
ok() { pass=$((pass + 1)); echo "ok   $1"; }
no() { fail=$((fail + 1)); echo "FAIL $1"; }
t() { name=$1; shift; if "$@" >"$T/out" 2>&1; then ok "$name"; else no "$name"; sed 's/^/     /' "$T/out"; fi; }
f() { name=$1; shift; if "$@" >"$T/out" 2>&1; then no "$name (expected a refusal)"; sed 's/^/     /' "$T/out"; else ok "$name"; fi; }
K109=$G/devices/g0111/test/base109_keylayout
fresh_from() { rm -rf "$T/base" "$T/new"; mkdir "$T/base" "$T/new"; cp "$1"/*.kl "$T/base/"; cp "$1"/*.kl "$T/new/"; }
fresh() { fresh_from "$K109"; }
t "fixture: the 1.0.9 base layouts are the files read from the pinned base image" sh -c "cd '$K109' && shasum -a 256 -c SHA256SUMS >/dev/null && [ \$(ls *.kl | wc -l) = \$(wc -l < SHA256SUMS) ]"

# --- the edit on the real base key layouts
fresh
t "apply on the base key layouts" pk apply "$T/new"
t "check: the base with exactly the edit" pk check "$T/base" "$T/new"
kl() { grep -E "^key $2[[:space:]]" "$T/new/$1" | head -n 1; }
# stock X50 (v1.2.64 vendor/usr/keylayout): these scancodes are POWER / KPPOWER, so system POWER WAKE here
for k in Vendor_3697_Product_0001.kl:116 Vendor_3697_Product_0001.kl:190 Vendor_3697_Product_0001.kl:75 \
         Vendor_3697_Product_0002.kl:30 Vendor_3697_Product_0002.kl:116 Vendor_3697_Product_0002.kl:260 \
         Vendor_1d5a_Product_c081.kl:116 Generic.kl:142; do
  t "${k%%:*} ${k#*:} is POWER WAKE" sh -c "printf '%s\n' \"\$1\" | grep -Eq '^key ${k#*:}[[:space:]]+POWER[[:space:]]+WAKE([[:space:]]|\$)'" _ "$(kl "${k%%:*}" "${k#*:}")"
done
# rs30-power: the XGIMI BT remotes' own power keys (stock KPPOWER 113, SECONDARY_POWER 185; services.jar kPPowerPress)
# are the remote power key STB_POWER WAKE, routed to org.lumen.x50.projector (lumen-menu PowerPress)
for k in Vendor_000d_Product_3838.kl:113 Vendor_000d_Product_3840.kl:113 Vendor_000d_Product_3841.kl:113 \
         Vendor_000d_Product_3842.kl:113 Vendor_000d_Product_3843.kl:113; do
  t "${k%%:*} ${k#*:} stays STB_POWER WAKE, noted as the remote power key" sh -c "printf '%s\n' \"\$1\" | grep -Eq '^key ${k#*:}[[:space:]]+STB_POWER[[:space:]]+WAKE  # XGIMI: KPPOWER \\(BT remote\\); the remote power key -> org.lumen.x50.projector'" _ "$(kl "${k%%:*}" "${k#*:}")"
done
for f_ in Vendor_000d_Product_3838.kl Vendor_000d_Product_3840.kl Vendor_000d_Product_3841.kl Vendor_000d_Product_3842.kl; do
  t "$f_ adds stock 185 SECONDARY_POWER as STB_POWER WAKE" sh -c "grep -Eq '^key 185 STB_POWER WAKE  # XGIMI: SECONDARY_POWER' '$T/new/$f_' && ! grep -Eq '^key 185[[:space:]]' '$T/base/$f_'"
done
# review P3-4: the base's note above an unmapped 185 ("not mapped yet ... map it only if it sends 185 alone", 5 comment
# lines) goes with the mapping, only there
for f_ in Vendor_000d_Product_3838.kl Vendor_000d_Product_3840.kl Vendor_000d_Product_3841.kl Vendor_000d_Product_3842.kl; do
  t "$f_ drops the base's stale 185 note (5 comment lines, nothing else)" sh -c "grep -q 'not mapped yet' '$T/base/$f_' && ! grep -Eq 'not mapped yet|sends 185 alone' '$T/new/$f_' && [ \$(diff '$T/base/$f_' '$T/new/$f_' | grep -c '^< #') = 5 ]"
done
t "1d5a:c081 keeps 185 unmapped and its note" sh -c "cmp -s '$T/base/Vendor_1d5a_Product_c081.kl' '$T/new/Vendor_1d5a_Product_c081.kl' || [ \$(grep -c 'not mapped yet' '$T/new/Vendor_1d5a_Product_c081.kl') = 1 ]"
t "1d5a:c081 185 stays unmapped" sh -c "! grep -Eq '^key 185[[:space:]]' '$T/new/Vendor_1d5a_Product_c081.kl'"
fresh
printf '# key 999      NOTHING  (a later note\n#   that continues)\n' >> "$T/new/Vendor_000d_Product_3840.kl"
t "a later '#   ' comment after the 185 note stays (the note ends at its first other line)" sh -c "python3 -B \"\$PKT\" apply '$T/new' >/dev/null && grep -q '^#   that continues)' '$T/new/Vendor_000d_Product_3840.kl'"
fresh
pk apply "$T/new" >/dev/null
t "only the 9 remote power keys map STB_POWER (5 x 113, 4 x 185), all on 000d remotes" sh -c "[ \$(cd '$T/new' && sed 's/#.*//' *.kl | grep -Ec '^key[[:space:]]+[0-9]+[[:space:]]+STB_POWER') = 9 ] && [ \$(cd '$T/new' && sed 's/#.*//' Vendor_000d_Product_38*.kl | grep -Ec '^key[[:space:]]+(113|185)[[:space:]]+STB_POWER[[:space:]]+WAKE') = 9 ]"
t "factory remotes untouched" cmp "$T/base/Vendor_9999_Product_0666.kl" "$T/new/Vendor_9999_Product_0666.kl"
t "BT 3839 untouched" cmp "$T/base/Vendor_000d_Product_3839.kl" "$T/new/Vendor_000d_Product_3839.kl"
n=$(for x in "$T"/base/*.kl; do cmp -s "$x" "$T/new/$(basename "$x")" || echo x; done | wc -l | tr -d ' ')
t "exactly 11 files changed ($n)" test "$n" = 11
for k in Vendor_000d_Product_3840.kl:64:TV_AUDIO_DESCRIPTION Vendor_000d_Product_3840.kl:88:TV_DATA_SERVICE \
         Vendor_3697_Product_0001.kl:81:TV_DATA_SERVICE Vendor_000d_Product_3843.kl:87:FOCUS Vendor_000d_Product_3843.kl:62:TV_CONTENTS_MENU; do
  f_=${k%%:*}; r_=${k#*:}; sc=${r_%%:*}; kc=${r_#*:}
  t "t12: $f_ adds stock scancode $sc as $kc" sh -c "grep -Eq '^key $sc $kc  # ' '$T/new/$f_' && ! grep -Eq '^key $sc[[:space:]]' '$T/base/$f_'"
done
# the gear held: stock X50 kl 000d:3840 / 3838 / 3843 "key 127 MENU", IR / keypad 3697:0001 / 0002 "key 139 MENU" (the
# remote sends it once on a hold; stock xhplayer: MENU UP = game menu). The 1.0.9 base maps them to ALL_APPS (consumed
# by the framework); the X50 edit turns exactly those lines into the gear hold key.
GH="Vendor_000d_Product_3840.kl:127 Vendor_000d_Product_3838.kl:127 Vendor_000d_Product_3843.kl:127 \
         Vendor_3697_Product_0001.kl:139 Vendor_3697_Product_0002.kl:139"
for k in $GH; do
  f_=${k%%:*}; sc=${k#*:}
  t "gear hold: $f_ $sc (stock MENU) is ALL_APPS in the 1.0.9 base, TV_MEDIA_CONTEXT_MENU after the edit" sh -c \
    "grep -Eq '^key $sc[[:space:]]+ALL_APPS([[:space:]]|\$)' '$T/base/$f_' && [ \$(grep -Ec '^key $sc[[:space:]]' '$T/new/$f_') = 1 ] && grep -Eq '^key $sc[[:space:]]+TV_MEDIA_CONTEXT_MENU  # XGIMI: MENU \\(gear held\\)' '$T/new/$f_'"
done
d=$(diff "$T/base/Vendor_000d_Product_3840.kl" "$T/new/Vendor_000d_Product_3840.kl" | grep -c '^>')
t "3840: exactly 8 lines changed or added: 113, 64, 88, 127, 185, 184, 135, 136 ($d)" test "$d" = 8
t "gear hold: no X50 stock remote layout maps a stock MENU scancode to ALL_APPS" sh -c "cd '$T/new' && ! sed 's/#.*//' Vendor_000d_Product_3840.kl Vendor_000d_Product_3838.kl Vendor_000d_Product_3843.kl Vendor_3697_Product_0001.kl Vendor_3697_Product_0002.kl | grep -Eq '^key[[:space:]]+(127|139)[[:space:]]+ALL_APPS'"
for f_ in Vendor_000d_Product_3838.kl Vendor_000d_Product_3840.kl Vendor_000d_Product_3841.kl Vendor_000d_Product_3842.kl; do
  t "Remote buttons: $f_ 184 (stock custom shortcut key) is ALL_APPS in the 1.0.9 base, TV_RADIO_SERVICE after the edit" sh -c \
    "grep -Eq '^key 184[[:space:]]+ALL_APPS' '$T/base/$f_' && [ \$(grep -Ec '^key 184[[:space:]]' '$T/new/$f_') = 1 ] && grep -Eq '^key 184[[:space:]]+TV_RADIO_SERVICE  # XGIMI: SHORTCUT' '$T/new/$f_'"
done
for k in Vendor_000d_Product_3843.kl:135:MACRO_1 Vendor_000d_Product_3843.kl:136:MACRO_2 Vendor_000d_Product_3843.kl:217:MACRO_3 \
         Vendor_000d_Product_3843.kl:354:MACRO_4 Vendor_000d_Product_3840.kl:135:MACRO_1 Vendor_000d_Product_3840.kl:136:MACRO_2; do
  f_=${k%%:*}; r_=${k#*:}; sc=${r_%%:*}; kc=${r_#*:}
  t "Remote buttons: $f_ adds the stock custom key $sc as $kc" sh -c "grep -Eq '^key $sc $kc  # ' '$T/new/$f_' && ! grep -Eq '^key $sc[[:space:]]' '$T/base/$f_'"
done
d=$(diff "$T/base/Vendor_3697_Product_0002.kl" "$T/new/Vendor_3697_Product_0002.kl" | grep -c '^>')
t "keypad: exactly 4 lines changed: 30, 116, 260, 139 ($d)" test "$d" = 4
f "apply twice refuses (no STB_POWER left to edit)" pk apply "$T/new"

# --- a later base (overlay/v1/keylayout, the shared gear-hold fix): the gear hold lines are kept byte for byte
fresh_from "$G/overlay/v1/keylayout"
t "later base: apply" pk apply "$T/new"
t "later base: check" pk check "$T/base" "$T/new"
for k in $GH; do
  f_=${k%%:*}; sc=${k#*:}
  t "later base: gear hold $f_ $sc kept as the base has it" sh -c \
    "grep -Eq '^key $sc[[:space:]]+TV_MEDIA_CONTEXT_MENU([[:space:]]|\$)' '$T/base/$f_' && [ \"\$(grep -E '^key $sc[[:space:]]' '$T/new/$f_')\" = \"\$(grep -E '^key $sc[[:space:]]' '$T/base/$f_')\" ]"
done
d=$(diff "$T/base/Vendor_000d_Product_3840.kl" "$T/new/Vendor_000d_Product_3840.kl" | grep -c '^>')
t "later base: 3840 exactly 4 lines changed or added: 113, 64, 88, 185 ($d)" test "$d" = 4

# --- check refuses every mutant
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 116        POWER WAKE/key 116        STB_POWER WAKE/' "$T/new/Vendor_3697_Product_0001.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_3697_Product_0001.kl"
f "check refuses an IR power key left as STB_POWER" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 113        STB_POWER WAKE/key 113        STB_POWER/' "$T/new/Vendor_000d_Product_3840.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3840.kl"
f "check refuses a BT power key without WAKE" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 113        STB_POWER WAKE/key 113        POWER WAKE/' "$T/new/Vendor_000d_Product_3841.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3841.kl"
f "check refuses the remote power key turned into the system one" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed '/^key 185 /d' "$T/new/Vendor_000d_Product_3842.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3842.kl"
f "check refuses a dropped 185 SECONDARY_POWER" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
{ grep -A4 '^# key 185' "$T/base/Vendor_000d_Product_3840.kl"; cat "$T/new/Vendor_000d_Product_3840.kl"; } > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3840.kl"
f "check refuses the stale 185 note kept next to the mapping" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 116        POWER WAKE\(.*\)/key 116        STB_POWER WAKE/' "$T/new/Vendor_1d5a_Product_c081.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_1d5a_Product_c081.kl"
f "check refuses STB_POWER on a remote outside REMOTE" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
echo "key 999 HOME" >> "$T/new/Vendor_000d_Product_3839.kl"
f "check refuses a change to a file outside the table" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 30         POWER WAKE/key 30         SLEEP WAKE/' "$T/new/Vendor_3697_Product_0002.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_3697_Product_0002.kl"
f "check refuses SLEEP in place of POWER" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
rm "$T/new/Generic.kl"
f "check refuses a missing key layout" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed '/^key 64 TV_AUDIO_DESCRIPTION/d' "$T/new/Vendor_000d_Product_3840.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3840.kl"
f "check refuses a dropped t12 stock key" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 127        TV_MEDIA_CONTEXT_MENU.*/key 127        ALL_APPS/' "$T/new/Vendor_000d_Product_3840.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3840.kl"
f "check refuses the gear hold 127 turned into ALL_APPS" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 184        TV_RADIO_SERVICE.*/key 184        ALL_APPS/' "$T/new/Vendor_000d_Product_3841.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3841.kl"
f "check refuses the shortcut key 184 left as ALL_APPS" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed '/^key 354 MACRO_4/d' "$T/new/Vendor_000d_Product_3843.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3843.kl"
f "check refuses a dropped custom key" pk check "$T/base" "$T/new"
fresh
sed 's/^key 184        ALL_APPS.*/key 184        HOME/' "$T/new/Vendor_000d_Product_3838.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3838.kl"
f "apply refuses a base where 184 is neither ALL_APPS nor the shortcut key" pk apply "$T/new"
fresh
echo "key 217        HOME" >> "$T/new/Vendor_000d_Product_3843.kl"
f "apply refuses a base that maps a custom key scancode to another key" pk apply "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 139        TV_MEDIA_CONTEXT_MENU.*/key 139        ALL_APPS  # XGIMI: MENU (four-diamond key) -> Google TV "Your apps"/' "$T/new/Vendor_3697_Product_0002.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_3697_Product_0002.kl"
f "check refuses the keypad gear hold 139 left as the 1.0.9 base line" pk check "$T/base" "$T/new"
fresh
sed 's/^key 127        ALL_APPS.*/key 127        HOME/' "$T/new/Vendor_000d_Product_3838.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3838.kl"
f "apply refuses a base where 127 is neither ALL_APPS nor the gear hold" pk apply "$T/new"
fresh
sed 's/^key 139        ALL_APPS.*/key 139        MENU/' "$T/new/Vendor_3697_Product_0001.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_3697_Product_0001.kl"
f "apply refuses a base where the IR 139 is MENU" pk apply "$T/new"
fresh
echo "key 127        ALL_APPS" >> "$T/new/Vendor_000d_Product_3843.kl"
f "apply refuses a base with the gear hold scancode twice" pk apply "$T/new"
fresh_from "$G/overlay/v1/keylayout"
sed 's/^key 139        TV_MEDIA_CONTEXT_MENU.*/key 139        MENU/' "$T/new/Vendor_3697_Product_0001.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_3697_Product_0001.kl"
f "later base: apply refuses the IR 139 as MENU" pk apply "$T/new"
fresh
sed '/^key 127 /d' "$T/new/Vendor_000d_Product_3840.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3840.kl"
f "apply refuses a base without scancode 127" pk apply "$T/new"
fresh
echo "key 88 HOME" >> "$T/new/Vendor_000d_Product_3840.kl"
f "apply refuses a base that already maps an added scancode" pk apply "$T/new"
fresh
echo "key 185 HOME" >> "$T/new/Vendor_000d_Product_3841.kl"
f "apply refuses a base that already maps 185" pk apply "$T/new"
fresh
sed '/^key 113 /d' "$T/new/Vendor_000d_Product_3843.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3843.kl"
f "apply refuses a base without the remote power key 113" pk apply "$T/new"
fresh
echo "key 77 STB_POWER WAKE" >> "$T/new/Vendor_9999_Product_0777.kl"
f "apply refuses another STB_POWER key outside the table" pk apply "$T/new"
fresh
sed '/^key 75 /d' "$T/new/Vendor_3697_Product_0001.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_3697_Product_0001.kl"
f "apply refuses a base without an expected line" pk apply "$T/new"

# --- the RRO
t "RRO: static on android, priority 2100 over the Z9X 2000" python3 - "$O/AndroidManifest.xml" "$G/apps/Z9xFrameworkKeysOverlay/AndroidManifest.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
A = "{http://schemas.android.com/apk/res/android}"
def ov(p):
    m = ET.parse(p).getroot()
    o = m.find("overlay")
    return m.get("package"), o.get(A + "targetPackage"), o.get(A + "isStatic"), int(o.get(A + "priority"))
x, z = ov(sys.argv[1]), ov(sys.argv[2])
assert x[0] == "org.lumen.x50.overlay.power" and x[1] == "android" and x[2] == "true", x
assert x[3] > z[3], (x, z)
app = ET.parse(sys.argv[1]).getroot().find("application")
assert app.get(A + "hasCode") == "false"
PY
t "RRO: short press sleeps (1), long press power menu (1)" python3 - "$O/res/values/config.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
v = {i.get("name"): i.text.strip() for i in ET.parse(sys.argv[1]).getroot().iter("integer")}
assert v == {"config_shortPressOnPowerBehavior": "1", "config_longPressOnPowerBehavior": "1"}, v
PY
t "RRO: global_keys.xml passes check_global_keys.py" python3 "$G/apps/check_global_keys.py" "$O/res/xml/global_keys.xml"
t "RRO: the remote power key to the X50 app (no dispatchWhenNonInteractive: asleep it only wakes, as the Z9X); gear, projector and Source keys to the X50 app; HDMI keys kept" python3 - "$O/res/xml/global_keys.xml" "$G/apps/Z9xFrameworkKeysOverlay/res/xml/global_keys.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
def keys(p):
    return {k.get("keyCode"): k.get("component") for k in ET.parse(p).getroot().iter("key")}
x, z = keys(sys.argv[1]), keys(sys.argv[2])
assert "KEYCODE_POWER" not in x, x
# rs30-power: STB_POWER (the remote power key, x50_power_keys.py REMOTE) to the X50 app; asleep the WAKE press only
# wakes the projector and the framework swallows it (stock kPPowerPress kpPowerForWakeup), never also a menu or standby
stb = [k for k in ET.parse(sys.argv[1]).getroot().iter("key") if k.get("keyCode") == "KEYCODE_STB_POWER"]
assert len(stb) == 1 and stb[0].get("component") == "org.lumen.x50.projector/.KeyReceiver" \
    and stb[0].get("dispatchWhenNonInteractive") is None, [s.attrib for s in stb]
assert x["KEYCODE_SETTINGS"] == "org.lumen.x50.projector/.KeyReceiver", x
assert x["KEYCODE_TV_INPUT"] == "org.lumen.x50.projector/.KeyReceiver", x   # lumen-source, as every model
for k in ("KEYCODE_TV_INPUT_HDMI_1", "KEYCODE_TV_INPUT_HDMI_2"):
    assert x[k] == z[k] == "org.lumen.tvinput/.GlobalKeyReceiver"
# t12: every projector key of the Z9X file goes to the X50 projector app; none to the absent org.lumen.projector
P = "org.lumen.x50.projector/.KeyReceiver"
for k, c in z.items():
    if k == "KEYCODE_TV_INPUT":
        continue
    if c == "org.lumen.projector/.KeyReceiver":
        assert x.get(k) == P, (k, x.get(k))
    else:
        assert x.get(k) == c, (k, x.get(k), c)
assert "org.lumen.projector/" not in "".join(x.values()), x
assert x["KEYCODE_TV_AUDIO_DESCRIPTION"] == x["KEYCODE_TV_DATA_SERVICE"] == x["KEYCODE_TV_MEDIA_CONTEXT_MENU"] == P
# rs30-menu: the RS30 remote's picture keys (devices/g0155/image/rs30_menu_keys.py), keycodes no X50 layout sends
RS30 = {"KEYCODE_TV_ZOOM_MODE", "KEYCODE_TV_TIMER_PROGRAMMING", "KEYCODE_TV_NUMBER_ENTRY"}
assert all(x[k] == P for k in RS30), x
assert set(x) == set(z) | {"KEYCODE_TV_AUDIO_DESCRIPTION", "KEYCODE_TV_DATA_SERVICE",
                          "KEYCODE_TV_MEDIA_CONTEXT_MENU"} | RS30, sorted(set(x) ^ set(z))
PY
t "registry: g0111 builds X50PowerOverlay; build.conf stages x50_power_keys.py" sh -c \
  "python3 '$G/tools/devices/layered.py' '$G/devices/g0111/device.conf' | grep -qE '^apps=(.* )?X50PowerOverlay( .*)?$' && python3 '$G/tools/devices/layered.py' '$G/devices/g0111/build.conf' | grep -q 'devices/g0111/image/x50_power_keys.py'"
t "compose and verify name the key edit and the RRO" sh -c \
  "grep -qE 'x50_power_keys.py.apply.[{]sys[}]/product/usr/keylayout' '$G/devices/g0111/image/steps.tsv' && grep -q '{sys}/product/overlay/X50PowerOverlay.apk' '$G/devices/g0111/image/steps.tsv' && grep -q '\"system/product/overlay/X50PowerOverlay.apk\": (0, 0, 0o644, SYSF, \"stage/X50PowerOverlay.apk\")' '$G/devices/g0111/image/verify_tree_x50.py'"
fresh
pk apply "$T/new" > /dev/null
t "every layout the edit changes is one of its files" sh -c \
  "cd '$T/new' && for n in *.kl; do cmp -s \"\$n\" '$T/base/'\"\$n\" || python3 -B \"\$PKT\" files | grep -qx \"\$n\" || exit 1; done"
t "verify_tree_x50 lets exactly the edited layouts differ (FILES, the remote power keys included)" sh -c \
  "grep -q 'for n in x50_power_keys.FILES}' '$G/devices/g0111/image/verify_tree_x50.py' && ! grep -q 'x50_power_keys.TABLE' '$G/devices/g0111/image/verify_tree_x50.py'"
t "nothing outside the X50 names the RRO or the key tool" sh -c \
  "! grep -rlE 'X50PowerOverlay|x50_power_keys' '$G/tools/ota' '$G/tools/play6' '$G/installer' '$G/devices/horse_popular' '$G/devices/goat_elegant' 2>/dev/null | grep -q ."

if [ "${BUILD:-0}" = 1 ]; then
  t "RRO builds with the test key" env BUILD_DIR="$T/b" sh "$G/apps/build_apk.sh" "$O" "$T/X50PowerOverlay.apk"
  t "RRO APK has no code" sh -c "! unzip -l '$T/X50PowerOverlay.apk' | grep -qE '\\.dex|\\.so'"
fi
echo "x50_power_keys_test: $pass passed, $fail failed"
[ "$fail" = 0 ]
