#!/bin/sh
# x50_power_keys_test.sh: host tests of the X50 power key (devices/g0111/README.md, "Power key"):
# devices/g0111/image/x50_power_keys.py on copies of the base's key layouts (overlay/v1/keylayout = the Z9X 1.0.9
# image's /system/product/usr/keylayout), its check against mutants, and the RRO apps/X50PowerOverlay (values,
# priority, global keys). Optional: BUILD=1 also builds the RRO with the test key (apps/build_apk.sh).
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
fresh() { rm -rf "$T/base" "$T/new"; cp -R "$G/overlay/v1/keylayout" "$T/base"; cp -R "$G/overlay/v1/keylayout" "$T/new"; }

# --- the edit on the real base key layouts
fresh
t "apply on the base key layouts" pk apply "$T/new"
t "check: the base with exactly the edit" pk check "$T/base" "$T/new"
kl() { grep -E "^key $2[[:space:]]" "$T/new/$1" | head -n 1; }
# stock X50 (v1.2.64 vendor/usr/keylayout): these scancodes are POWER / KPPOWER, so system POWER WAKE here
for k in Vendor_3697_Product_0001.kl:116 Vendor_3697_Product_0001.kl:190 Vendor_3697_Product_0001.kl:75 \
         Vendor_3697_Product_0002.kl:30 Vendor_3697_Product_0002.kl:116 Vendor_3697_Product_0002.kl:260 \
         Vendor_000d_Product_3840.kl:113 Vendor_000d_Product_3838.kl:113 Vendor_1d5a_Product_c081.kl:116 \
         Generic.kl:142; do
  t "${k%%:*} ${k#*:} is POWER WAKE" sh -c "printf '%s\n' \"\$1\" | grep -Eq '^key ${k#*:}[[:space:]]+POWER[[:space:]]+WAKE([[:space:]]|\$)'" _ "$(kl "${k%%:*}" "${k#*:}")"
done
t "no key layout maps STB_POWER any more" sh -c "! sed 's/#.*//' '$T/new'/*.kl | grep -Eq '^key[[:space:]]+[0-9]+[[:space:]]+STB_POWER'"
t "factory remotes untouched" cmp "$T/base/Vendor_9999_Product_0666.kl" "$T/new/Vendor_9999_Product_0666.kl"
t "BT 3839 untouched" cmp "$T/base/Vendor_000d_Product_3839.kl" "$T/new/Vendor_000d_Product_3839.kl"
n=$(for x in "$T"/base/*.kl; do cmp -s "$x" "$T/new/$(basename "$x")" || echo x; done | wc -l | tr -d ' ')
t "exactly 11 files changed ($n)" test "$n" = 11
d=$(diff "$T/base/Vendor_3697_Product_0002.kl" "$T/new/Vendor_3697_Product_0002.kl" | grep -c '^>')
t "keypad: exactly 3 lines changed ($d)" test "$d" = 3
f "apply twice refuses (no STB_POWER left to edit)" pk apply "$T/new"

# --- check refuses every mutant
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 116        POWER WAKE/key 116        STB_POWER WAKE/' "$T/new/Vendor_3697_Product_0001.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_3697_Product_0001.kl"
f "check refuses an IR power key left as STB_POWER" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 113        POWER WAKE/key 113        POWER/' "$T/new/Vendor_000d_Product_3840.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_000d_Product_3840.kl"
f "check refuses a BT power key without WAKE" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
echo "key 999 HOME" >> "$T/new/Vendor_000d_Product_3839.kl"
f "check refuses a change to a file outside the table" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
sed 's/^key 30         POWER WAKE/key 30         SLEEP WAKE/' "$T/new/Vendor_3697_Product_0002.kl" > "$T/m" && cp "$T/m" "$T/new/Vendor_3697_Product_0002.kl"
f "check refuses SLEEP in place of POWER" pk check "$T/base" "$T/new"
fresh; pk apply "$T/new" >/dev/null
rm "$T/new/Generic.kl"
f "check refuses a missing key layout" pk check "$T/base" "$T/new"
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
t "RRO: no STB_POWER / SETTINGS route; Source to tvinput; HDMI keys kept" python3 - "$O/res/xml/global_keys.xml" "$G/apps/Z9xFrameworkKeysOverlay/res/xml/global_keys.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
def keys(p):
    return {k.get("keyCode"): k.get("component") for k in ET.parse(p).getroot().iter("key")}
x, z = keys(sys.argv[1]), keys(sys.argv[2])
assert "KEYCODE_STB_POWER" not in x and "KEYCODE_SETTINGS" not in x and "KEYCODE_POWER" not in x, x
assert x["KEYCODE_TV_INPUT"] == "org.lumen.tvinput/.GlobalKeyReceiver"
for k in ("KEYCODE_TV_INPUT_HDMI_1", "KEYCODE_TV_INPUT_HDMI_2"):
    assert x[k] == z[k] == "org.lumen.tvinput/.GlobalKeyReceiver"
# every other Z9X entry is kept as it was (projector keys swallowed, not handed to the app in front)
for k, c in z.items():
    if k not in ("KEYCODE_STB_POWER", "KEYCODE_SETTINGS", "KEYCODE_TV_INPUT", "KEYCODE_SEARCH"):
        assert x.get(k) == c, (k, x.get(k), c)
assert x["KEYCODE_SEARCH"] == "org.lumen.projector/.KeyReceiver"
assert set(x) == set(z) - {"KEYCODE_STB_POWER", "KEYCODE_SETTINGS"}, sorted(set(x) ^ set(z))
PY
t "registry: g0111 builds X50PowerOverlay; image.conf stages x50_power_keys.py" sh -c \
  "grep -qx 'apps=X50EyeGuard X50PowerOverlay' '$G/devices/g0111/device.conf' && grep -q 'devices/g0111/image/x50_power_keys.py' '$G/devices/g0111/image.conf'"
t "compose and verify name the key edit and the RRO" sh -c \
  "grep -q 'x50_power_keys.py\" apply \"\$R/system/product/usr/keylayout\"' '$G/devices/g0111/image/compose_x50.sh' && grep -q 'system/product/overlay/X50PowerOverlay.apk' '$G/devices/g0111/image/compose_x50.sh' && grep -q '\"system/product/overlay/X50PowerOverlay.apk\": (0, 0, 0o644, SYSF, \"stage/X50PowerOverlay.apk\")' '$G/devices/g0111/image/verify_tree_x50.py'"
t "nothing outside the X50 names the RRO or the key tool" sh -c \
  "! grep -rlE 'X50PowerOverlay|x50_power_keys' '$G/tools/ota' '$G/tools/play6' '$G/installer' '$G/devices/horse_popular' '$G/devices/goat_elegant' 2>/dev/null | grep -q ."

if [ "${BUILD:-0}" = 1 ]; then
  t "RRO builds with the test key" env BUILD_DIR="$T/b" sh "$G/apps/build_apk.sh" "$O" "$T/X50PowerOverlay.apk"
  t "RRO APK has no code" sh -c "! unzip -l '$T/X50PowerOverlay.apk' | grep -qE '\\.dex|\\.so'"
fi
echo "x50_power_keys_test: $pass passed, $fail failed"
[ "$fail" = 0 ]
