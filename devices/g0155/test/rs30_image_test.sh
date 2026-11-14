#!/bin/bash
# rs30_image_test.sh: host checks of the RS30 Ultra Max trial recipe (devices/g0155/build.conf and image/steps.tsv of
# the one image builder): every APEX uncompressed before the signed set and
# none left in the image, the debloat list removed and checked gone, USB host mode and the TV props, the remote keys
# (Lumen's layouts with the X50 power edit, the RS30-only scancodes unmapped, no STB_POWER, every global key on an app
# of the image) and the HDMI input app behind the input rows. Then each check must turn red on a broken copy.
#   bash devices/g0155/test/rs30_image_test.sh
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
T=$(mktemp -d "${TMPDIR:-/tmp}/rs30img.XXXXXX")
trap 'rm -rf "$T"' EXIT
pass=0 fail=0
ok() { pass=$((pass + 1)); echo "ok   $1"; }
no() { fail=$((fail + 1)); echo "FAIL $1"; }
# the five scancodes the RS30 adds to the X50's 000d:3840 layout (stock PICTURE_MODE, SHORTCUT_1 / 2, PRO_SETTING,
# BRIGHTNESS): no stock handler proven, so unmapped
RS30_ONLY="172 135 136 217 234"

checks() {  # ROOT: every rule on that tree, one line per broken rule, nothing on a good tree
  local R=$1 S=$1/devices/g0155/image/steps.tsv B=$1/devices/g0155/build.conf D=$1/devices/g0155/image/debloat.txt
  local P=$1/devices/g0155/image/rs30_prop.txt K u b f p a sc kv want apps n
  row() {  # OP ARG...: the line of the first step of steps.tsv with that op and those first arguments, or nothing
    awk -F'\t' -v want="$(printf '%s\t' "$@")" '$1 !~ /^#/ { s = ""; for (i = 2; i <= NF; i++) s = s $i "\t"
      if (index(s, want) == 1) { print NR; exit } }' "$S"
  }
  u=$(row unpack_capex '{sys}/apex'); b=$(row base_signed)
  [ -n "$u" ] && [ -n "$b" ] && [ "$u" -lt "$b" ] || echo "capex: unpack_capex must run before base_signed"
  [ -n "$(row nofind '{mn}/system' '*.capex')" ] || echo "capex: the image is not checked for a .capex"
  for p in system_ext/priv-app/LineageSetupWizard product/priv-app/TvSampleLeanbackLauncher system_ext/priv-app/Updater \
           system_ext/priv-app/EmulatorRadioConfig product/overlay/framework-res__emulator__auto_generated_characteristics_rro.apk \
           'product/overlay/LineageSetupWizard__*_auto_generated_rro_product.apk' product/overlay/EmulationPixel8; do
    grep -qxF "$p" "$D" || echo "debloat: $p is not removed"
  done
  grep -v '^#' "$D" | grep -qE 'TvProvision|TVLauncherNoGMS|vndk\.v34' && echo "debloat: a path that must stay is listed"
  k=$(row keep_rules debloat.txt '{sys}/framework/oat/arm/services.odex' product/priv-app/TvProvision \
        product/priv-app/TVLauncherNoGMS system_ext/apex/com.android.vndk.v34.apex)
  r=$(row remove debloat.txt '{sys}')
  [ -n "$k" ] && [ -n "$r" ] && [ "$k" -lt "$r" ] || echo "debloat: no keep rules before the removal"
  [ -n "$(row gone debloat.txt '{mn}/system')" ] || echo "debloat: the image is not checked for the list"
  for p in TvProvision TVLauncherNoGMS; do
    [ -n "$(row is_dir "{mn}/system/product/priv-app/$p")" ] || echo "debloat: the image is not checked for $p"
  done
  grep -qx '+persist.sys.usb.config=mtp' "$P" || echo "usb: persist.sys.usb.config is not mtp"
  grep -q 'usb.config=adb' "$P" && echo "usb: adb in the USB config"
  grep -qx 'ro.build.characteristics=tv' "$P" || echo "props: not a TV"
  for kv in persist.sys.usb.config=mtp ro.build.characteristics=tv; do
    [ -n "$(row line '{mn}/system/product/etc/build.prop' "$kv")" ] || echo "props: the image is not checked for $kv"
  done
  n=$(row run_root x50_power_keys.py apply '{sys}/product/usr/keylayout')
  for f in "$R"/overlay/v1/keylayout/*.kl "$R"/overlay/v1/idc/*.idc; do
    a=${f##*/}; case $a in *.kl) d=keylayout ;; *) d=idc ;; esac
    grep -qE "(^|[= ])overlay/v1/$d/$a( |$)" "$B" || echo "keys: $a is not staged"
    p=$(row place "$a" "{sys}/product/usr/$d/$a" 0644 0 0)
    [ -n "$p" ] && { [ -z "$n" ] || [ "$p" -lt "$n" ]; } || echo "keys: $a is not placed in /system/product/usr/$d before the power edit"
    [ -n "$(row exists "{mn}/system/product/usr/$d/$a")" ] || echo "keys: the image is not checked for $a"
  done
  [ -n "$n" ] || echo "keys: no power-key edit"
  # the regexes of the recipe's noline steps (python re, as tools/build/engine.py reads them) must catch the lines
  python3 -B - "$S" "$RS30_ONLY" <<'PY'
import re, sys
rows = [l.rstrip("\n").split("\t") for l in open(sys.argv[1], encoding="utf-8") if l.strip() and not l.startswith("#")]
def rx(path):
    return [re.compile(r[3]) for r in rows if len(r) > 3 and r[1] == "noline" and r[2] == path]
g = rx("{sys}/product/usr/keylayout/Vendor_000d_Product_3840.kl")
for sc in sys.argv[2].split():
    if not any(x.search("key %s   TV" % sc) for x in g):
        print("keys: the recipe does not guard the RS30-only scancode %s" % sc)
if not any(x.search("key 116   STB_POWER") for x in rx("{mn}/system/product/usr/keylayout/*.kl")):
    print("keys: the image is not checked for STB_POWER")
PY
  rm -rf "$T/kl"; cp -R "$R/overlay/v1/keylayout" "$T/kl"
  python3 -B "$R/devices/g0111/image/x50_power_keys.py" apply "$T/kl" > "$T/pk.out" 2>&1 || echo "keys: power edit fails"
  K=$T/kl/Vendor_000d_Product_3840.kl
  for kv in "113 POWER WAKE" "66 SETTINGS" "102 HOME" "127 TV_MEDIA_CONTEXT_MENU"; do
    set -- $kv
    want="^key[[:space:]]+$1[[:space:]]+$2"; [ -n "${3:-}" ] && want="$want[[:space:]]+$3"
    grep -qE "$want([[:space:]]|$)" "$K" || echo "keys: 3840 $1 is not $kv"
  done
  for sc in $RS30_ONLY; do grep -qE "^key[[:space:]]+$sc[[:space:]]" "$K" && echo "keys: 3840 $sc is mapped"; done
  apps=$(python3 -B "$R/tools/devices/devices.py" --root "$R" get g0155 apps)
  for a in X50PowerOverlay X50TvInput X50Projector; do
    echo " $apps " | grep -q " $a " || echo "apps: $a is not a g0155 app"
  done
  for a in "X50PowerOverlay.apk	{sys}/product/overlay/X50PowerOverlay.apk	keys: the overlay" \
           "X50TvInput.apk	{sys}/app/X50TvInput/X50TvInput.apk	hdmi: org.lumen.tvinput"; do
    IFS='	' read -r f p want <<EOF2
$a
EOF2
    grep -qxF "!unsigned	place	signed_stage/apps/$f	$p	0644	0	0	{LABEL}" "$S" || echo "$want is not installed"
    grep -qxF "!unsigned	same	{mn}/system${p#\{sys\}}	signed_stage/apps/$f" "$S" || echo "$want is not checked in the image"
    grep -qxF "!unsigned	need	signed_stage/SHA256SUMS	signed_stage/apps/X50EyeGuard.apk	signed_stage/apps/X50Projector.apk	signed_stage/apps/X50PowerOverlay.apk	signed_stage/apps/X50TvInput.apk	signed_stage/base_signed/base_signed.tsv" "$S" \
      || echo "$want is not a need of the signed stage"
  done
  b=$(sed -n 's/^apps=//p' "$B")
  for a in X50PowerOverlay X50TvInput X50Projector; do echo " $b " | grep -q " $a " || echo "apps: $a is not in build.conf apps"; done
  [ -f "$R/devices/g0155/app/X50TvInput.layers" ] || echo "hdmi: no g0155 recipe of X50TvInput"
  # every global key goes to a component of the image (the base TvFrameworkOverlay rows to com.google.android.tv stay as
  # the base has them: no such app, the key does nothing, as without the overlay)
  python3 -B - "$R" <<'PY'
import re, sys
r = sys.argv[1]
def pkg(manifest):
    return re.search(r'package="([^"]+)"', open(r + "/" + manifest, encoding="utf-8").read()).group(1)
def receivers(manifest):
    return set(re.findall(r'android:name="\.([A-Za-z]+Receiver)"', open(r + "/" + manifest, encoding="utf-8").read()))
image = {pkg("apps/X50Projector/AndroidManifest.xml"): receivers("apps/X50Projector/AndroidManifest.xml"),
         pkg("apps/Z9xTvInput/AndroidManifest.xml"): receivers("apps/Z9xTvInput/AndroidManifest.xml"),
         "com.android.tv.settings": {"GlobalKeyReceiver"}}
base_rows = {"com.google.android.tv"}
xml = re.sub(r"<!--.*?-->", "", open(r + "/apps/X50PowerOverlay/res/xml/global_keys.xml", encoding="utf-8").read(), flags=re.S)
for code, comp in re.findall(r'keyCode="(\w+)" component="([^"]+)"', xml):
    p, cls = comp.split("/")
    if p in base_rows:
        continue
    if p not in image or cls.lstrip(".") not in image[p]:
        print("keys: %s goes to %s, not a receiver of the image" % (code, comp))
for need in ("KEYCODE_SETTINGS", "KEYCODE_TV_MEDIA_CONTEXT_MENU", "KEYCODE_TV_INPUT"):
    if 'keyCode="%s"' % need not in xml:
        print("keys: %s has no global key" % need)
PY
}

out=$(checks "$G")
if [ -z "$out" ]; then ok "the recipe keeps every rule"; else no "the recipe"; echo "$out" | sed 's/^/     /'; fi

# each rule must turn red on a broken copy
copy() {
  rm -rf "$T/t"; mkdir -p "$T/t/devices" "$T/t/overlay/v1" "$T/t/apps" "$T/t/tools"
  cp -R "$G/devices/g0155" "$G/devices/g0111" "$T/t/devices/"
  cp -R "$G/overlay/v1/keylayout" "$G/overlay/v1/idc" "$T/t/overlay/v1/"
  for a in X50PowerOverlay X50Projector Z9xTvInput; do mkdir -p "$T/t/apps/$a"; cp -R "$G/apps/$a/AndroidManifest.xml" "$G/apps/$a/res" "$T/t/apps/$a/" 2>/dev/null; done
  cp -R "$G/tools/devices" "$T/t/tools/"
  for d in "$G"/devices/*/; do [ -d "$T/t/devices/$(basename "$d")" ] || cp -R "$d" "$T/t/devices/"; done
}
mut() {  # NAME WANT FILE OLD NEW: the edit must make checks report a line starting with WANT
  name=$1 want=$2; shift 2
  copy
  python3 -B - "$T/t/$1" "$2" "$3" <<'PY' || { no "$name (edit not applied)"; return; }
import sys
p, old, new = sys.argv[1:4]
s = open(p, encoding="utf-8").read()
if s.count(old) != 1:
    sys.exit(1)
open(p, "w", encoding="utf-8").write(s.replace(old, new))
PY
  checks "$T/t" > "$T/out"
  if grep -q "^$want" "$T/out"; then ok "red on $name ($want)"; else no "$name survived"; fi
}
S=devices/g0155/image/steps.tsv
mut capex_after_sign "capex:" "$S" $'*\tunpack_capex\t{sys}/apex\n' ''
mut no_capex_check "capex:" "$S" $'*\tnofind\t{mn}/system\t*.capex' $'*\tnofind\t{mn}/system\t*.none'
mut setup_wizard_kept "debloat:" devices/g0155/image/debloat.txt $'system_ext/priv-app/LineageSetupWizard\n' ''
mut provision_removed "debloat: a path" devices/g0155/image/debloat.txt $'system_ext/priv-app/Updater\n' $'system_ext/priv-app/Updater\nproduct/priv-app/TvProvision\n'
mut no_keep_rules "debloat: no keep" "$S" $'\tproduct/priv-app/TVLauncherNoGMS\tsystem_ext/apex' $'\tsystem_ext/apex'
mut debloat_unchecked "debloat: the image" "$S" $'*\tgone\tdebloat.txt\t{mn}/system\n' ''
mut usb_adb "usb:" devices/g0155/image/rs30_prop.txt '+persist.sys.usb.config=mtp' '+persist.sys.usb.config=adb'
mut emulator "props:" devices/g0155/image/rs30_prop.txt 'ro.build.characteristics=tv' 'ro.build.characteristics=emulator'
mut tv_unchecked "props: the image" "$S" $'*\tline\t{mn}/system/product/etc/build.prop\tro.build.characteristics=tv\n' ''
mut no_power_edit "keys: no power" "$S" $'*\trun_root\tx50_power_keys.py\tapply\t{sys}/product/usr/keylayout\n' ''
mut scancode_unguarded "keys: the recipe does not guard the RS30-only scancode 172" "$S" '(172|135|' '(135|'
mut stb_power_unchecked "keys: the image is not checked for STB_POWER" "$S" $'\t^key\\s+[0-9]+\\s+STB_POWER\n' $'\t^key\\s+[0-9]+\\s+STB_POWERX\n'
mut rs30_key_mapped "keys: 3840 172" overlay/v1/keylayout/Vendor_000d_Product_3840.kl 'key 102        HOME' $'key 102        HOME\nkey 172        TV'
mut gear_elsewhere "keys: KEYCODE_SETTINGS" apps/X50PowerOverlay/res/xml/global_keys.xml '<key keyCode="KEYCODE_SETTINGS" component="org.lumen.x50.projector/.KeyReceiver" />' '<key keyCode="KEYCODE_SETTINGS" component="org.lumen.projector/.KeyReceiver" />'
mut no_tvinput "apps: X50TvInput" devices/g0155/device.conf 'X50Projector X50TvInput' 'X50Projector'
mut idc_not_staged "keys: Vendor_000d_Product_3840.idc" devices/g0155/build.conf 'overlay/v1/idc/Vendor_000d_Product_3840.idc ' ''
mut kl_not_placed "keys: Vendor_000d_Product_3840.kl" "$S" $'*\tplace\tVendor_000d_Product_3840.kl\t' $'*\tplace\tVendor_000d_Product_3841.kl\t'
mut overlay_not_installed "keys: the overlay" "$S" $'signed_stage/apps/X50PowerOverlay.apk\t{sys}/product/overlay/X50PowerOverlay.apk' $'signed_stage/apps/X50PowerOverlay.apk\t{sys}/product/overlay/Other.apk'
mut tvinput_unchecked "hdmi: org.lumen.tvinput is not checked" "$S" $'!unsigned\tsame\t{mn}/system/app/X50TvInput/X50TvInput.apk' $'!unsigned\tsame\t{mn}/system/app/X50TvInput/Other.apk'
echo "rs30_image_test: $pass passed, $fail failed"
[ "$fail" = 0 ]
