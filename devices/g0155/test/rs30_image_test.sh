#!/bin/bash
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
# rs30_image_test.sh: host checks of the RS30 Ultra Max trial recipe (devices/g0155/build.conf and image/steps.tsv of
# the one image builder): every APEX uncompressed before the signed set and
# none left in the image, the debloat list removed and checked gone, USB host mode and the TV props, the remote keys
# (Lumen's layouts with the X50 power edit, the RS30-only scancodes unmapped, STB_POWER only on the remote power keys, every global key on an app
# of the image) and the HDMI input app behind the input rows. Since t3: Lumen Home as the one HOME, the screen
# mirroring receiver with its Wi-Fi Direct overlay, AirPlay (the pin re-signed) and the sysconfig that lets every Lumen
# package run from the first boot. Since beta3: Lumen Setup runs the first start in the signed variants and TvProvision
# goes there (its HOME filter tied with Lumen Home at 3: the beta2 trial showed Android's chooser), the unsigned build
# check keeps TvProvision. Then each check must turn red on a broken copy.
#   bash devices/g0155/test/rs30_image_test.sh
set -u
G=$(cd "$(dirname "$0")/../../.." && pwd)
T=$(mktemp -d "${TMPDIR:-/tmp}/rs30img.XXXXXX") && [ -d "$T" ] || { echo "FAIL cannot create a temp dir under ${TMPDIR:-/tmp}" >&2; exit 2; }
trap 'rm -rf "$T"' EXIT
pass=0 fail=0
ok() { pass=$((pass + 1)); echo "ok   $1"; }
no() { fail=$((fail + 1)); echo "FAIL $1"; }
# the RS30's own 000d:3840 scancodes: PICTURE_MODE, PRO_SETTING and BRIGHTNESS open the menu's picture rows
# (devices/g0155/image/rs30_menu_keys.py), SHORTCUT_1 / 2 are the custom keys 1 and 2 of "Remote buttons"
# (devices/_remotes/keys.tsv: MACRO_1 / 2, stock RS30 binds a function to them)
RS30_CUSTOM="135:MACRO_1 136:MACRO_2"
RS30_MENU="172 TV_ZOOM_MODE|217 TV_TIMER_PROGRAMMING|234 TV_NUMBER_ENTRY"

checks() {  # ROOT: every rule on that tree, one line per broken rule, nothing on a good tree
  local R=$1 S=$1/devices/g0155/image/steps.tsv B=$T/build.conf.$$ D=$1/devices/g0155/image/debloat.txt
  # the descriptor with its family files (devices/_family/rs30/rs30.build.conf, tools/devices/layered.py)
  python3 "$G/tools/devices/layered.py" "$1/devices/g0155/build.conf" > "$B" || echo "build.conf: does not resolve"
  local P=$1/devices/_family/rs30/image/rs30_prop.txt K u b f p a sc kv want apps n
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
        system_ext/apex/com.android.vndk.v34.apex)
  r=$(row remove debloat.txt '{sys}')
  [ -n "$k" ] && [ -n "$r" ] && [ "$k" -lt "$r" ] || echo "debloat: no keep rules before the removal"
  [ -n "$(row gone debloat.txt '{mn}/system')" ] || echo "debloat: the image is not checked for the list"
  grep -qxF $'unsigned\tis_dir\t{mn}/system/product/priv-app/TvProvision' "$S" || echo "debloat: the unsigned image is not checked for TvProvision"
  # Lumen Setup provisions the signed variants (as the X50, the HB and the Play 6): placed from the signed stage with
  # its privileged permissions, the setup role (HOME 10, nothing else at 10 or at Lumen Home's priority), TvProvision
  # removed after the launcher and checked gone there, kept in the unsigned build check
  apps=$(python3 -B "$R/tools/devices/devices.py" --root "$R" get g0155 apps)
  echo " $apps " | grep -q " Z9xSetup " || echo "setup: Z9xSetup is not a g0155 app"
  echo " $(sed -n 's/^apps=//p' "$B") " | grep -q " Z9xSetup " || echo "setup: Z9xSetup is not in build.conf apps"
  grep -qE "^overlays=.* overlay/v1/privapp-permissions-z9xsetup.xml( |$)" "$B" || echo "setup: its privileged permissions are not staged"
  grep -qxF $'!unsigned\tplace\tsigned_stage/apps/Z9xSetup.apk\t{sys}/priv-app/Z9xSetup/Z9xSetup.apk\t0644\t0\t0\t{LABEL}' "$S" || echo "setup: Lumen Setup is not installed"
  grep -qxF $'!unsigned\tsame\t{mn}/system/priv-app/Z9xSetup/Z9xSetup.apk\tsigned_stage/apps/Z9xSetup.apk' "$S" || echo "setup: Lumen Setup is not checked in the image"
  grep -qxF $'!unsigned\tplace\tprivapp-permissions-z9xsetup.xml\t{sys}/etc/permissions/privapp-permissions-z9xsetup.xml\t0644\t0\t0\t{LABEL}' "$S" || echo "setup: its privileged permissions are not installed"
  grep -qF $'\tpriv-app/Z9xSetup/Z9xSetup.apk:org.lumen.setup:setup\t' "$S" || echo "setup: no setup role check"
  k=$(row remove debloat_launcher.txt '{sys}'); r=$(row rm '{sys}/product/priv-app/TvProvision')
  grep -qxF $'!unsigned\trm\t{sys}/product/priv-app/TvProvision' "$S" && [ -n "$k" ] && [ "$r" -gt "$k" ] || echo "setup: TvProvision stays next to Lumen Setup"
  grep -qE $'^[*u][a-z]*\trm\t[{]sys[}]/product/priv-app/TvProvision' "$S" && echo "setup: the unsigned build check loses TvProvision"
  grep -qxF $'!unsigned\tabsent\t{mn}/system/product/priv-app/TvProvision' "$S" || echo "setup: the image is not checked for TvProvision gone"
  # Lumen Home is the one HOME: the base launcher goes (debloat_launcher.txt, after its keep rules), Home is placed from
  # the signed stage, has the home role and is checked in the image
  grep -qxF product/priv-app/TVLauncherNoGMS "$R/devices/_image/debloat_launcher.txt" || echo "home: the base launcher stays"
  k=$(row keep_rules debloat_launcher.txt '{sys}/framework/oat/arm/services.odex' product/priv-app/TvProvision)
  r=$(row remove debloat_launcher.txt '{sys}')
  [ -n "$k" ] && [ -n "$r" ] && [ "$k" -lt "$r" ] || echo "home: the base launcher is not removed after its keep rules"
  grep -qxF $'!unsigned\tgone\tdebloat_launcher.txt\t{mn}/system' "$S" || echo "home: the image is not checked for the base launcher"
  grep -qF $'\tpriv-app/Z9xHome/Z9xHome.apk:org.lumen.home:home\t' "$S" || echo "home: no HOME role check"
  apps=$(python3 -B "$R/tools/devices/devices.py" --root "$R" get g0155 apps)
  for a in Z9xHome LumenMiracast LumenWifiP2pOverlay; do
    echo " $apps " | grep -q " $a " || echo "cast/home: $a is not a g0155 app"
  done
  for a in "Z9xHome.apk	{sys}/priv-app/Z9xHome/Z9xHome.apk	home: Lumen Home" \
           "LumenMiracast.apk	{sys}/priv-app/LumenMiracast/LumenMiracast.apk	cast: the Miracast receiver" \
           "LumenWifiP2pOverlay.apk	{sys}/product/overlay/LumenWifiP2pOverlay.apk	cast: the Wi-Fi Direct overlay"; do
    IFS='	' read -r f p want <<EOF2
$a
EOF2
    grep -qxF "!unsigned	place	signed_stage/apps/$f	$p	0644	0	0	{LABEL}" "$S" || echo "$want is not installed"
    grep -qxF "!unsigned	same	{mn}/system${p#\{sys\}}	signed_stage/apps/$f" "$S" || echo "$want is not checked in the image"
  done
  grep -qxF "!unsigned	place	privapp-permissions-lumen-miracast.xml	{sys}/etc/permissions/privapp-permissions-lumen-miracast.xml	0644	0	0	{LABEL}" "$S" \
    || echo "cast: the receiver's privileged permissions are not installed"
  # AirPlay: the released pin re-signed on the g0155 key (sign.conf pinned), its v7a library checked
  grep -q '^apps=.* Z9xAirPlay ' "$B" || echo "airplay: not built for g0155"
  grep -qxF "!unsigned	place	signed_stage/apps/Z9xAirPlay.apk	{sys}/app/Z9xAirPlay/Z9xAirPlay.apk	0644	0	0	{LABEL}" "$S" || echo "airplay: not installed"
  grep -qxF "!unsigned	run	airplay_libs.py	{sys}/app/Z9xAirPlay/Z9xAirPlay.apk" "$S" || echo "airplay: its armeabi-v7a library is not checked"
  # sysconfig: no Lumen package starts stopped (the base sets config_stopSystemPackagesByDefault)
  for f in "z9x-sysconfig-nogms.xml	{sys}/etc/sysconfig/z9x.xml" "rs30-sysconfig.xml	{sys}/etc/sysconfig/rs30.xml"; do
    IFS='	' read -r a p <<EOF2
$f
EOF2
    grep -qxF "*	place	$a	$p	0644	0	0	{LABEL}" "$S" || echo "sysconfig: $a is not installed"
  done
  python3 -B - "$R" <<'PY'
import re, sys
r = sys.argv[1]
dirs = {}   # APK name -> source folder of a renamed app (apps/app_dirs.tsv)
for l in open(r + "/apps/app_dirs.tsv", encoding="utf-8"):
    p = l.rstrip("\n").split("\t")
    if not l.startswith("#") and len(p) > 1:
        dirs[p[0]] = p[1]
def pkg(app):
    m = r + "/" + dirs.get(app, "apps/" + app) + "/AndroidManifest.xml"
    return re.search(r'package="([^"]+)"', open(m, encoding="utf-8").read()).group(1)
def running(path):
    s = re.sub(r"<!--.*?-->", "", open(r + "/" + path, encoding="utf-8").read(), flags=re.S)
    return set(re.findall(r'<initial-package-state package="([^"]+)" stopped="false" />', s))
own = running("devices/g0155/image/rs30-sysconfig.xml") | running("overlay/v1/z9x-sysconfig-nogms.xml")
for app in ("X50Projector", "X50EyeGuard", "Z9xSetup", "Z9xHome", "LumenMiracast", "Z9xTvInput"):
    if pkg(app) not in own:
        print("sysconfig: %s (%s) starts stopped" % (pkg(app), app))
if "org.lumen.airplay" not in own:
    print("sysconfig: org.lumen.airplay starts stopped")
PY
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
  # the RS30 picture keys: after the power edit, its tool staged and needed, every added line checked in tree and image
  m=$(row run_root rs30_menu_keys.py apply '{sys}/product/usr/keylayout')
  [ -n "$m" ] && [ -n "$n" ] && [ "$m" -gt "$n" ] || echo "keys: no menu-key edit after the power edit"
  grep -qE "^tools=.* devices/g0155/image/rs30_menu_keys.py( |$)" "$B" || echo "keys: rs30_menu_keys.py is not a staged tool"
  grep -qE "^[*]	need	.*	rs30_menu_keys.py(	|$)" "$S" || echo "keys: rs30_menu_keys.py is not a need"
  python3 -B "$R/devices/g0155/image/rs30_menu_keys.py" lines > "$T/ml" 2>&1 || echo "keys: rs30_menu_keys.py lines fails"
  while IFS= read -r ml; do
    for w in '{sys}' '{mn}/system'; do
      grep -qxF "*	line	$w/product/usr/keylayout/Vendor_000d_Product_3840.kl	$ml" "$S" || echo "keys: menu key line unchecked in $w: ${ml%%#*}"
    done
  done < "$T/ml"
  # the regexes of the recipe's noline steps (python re, as tools/build/engine.py reads them) must catch the lines
  python3 -B - "$S" <<'PY'
import re, sys
rows = [l.rstrip("\n").split("\t") for l in open(sys.argv[1], encoding="utf-8") if l.strip() and not l.startswith("#")]
def rx(path):
    return [re.compile(r[3]) for r in rows if len(r) > 3 and r[1] == "noline" and r[2] == path]
kl = rx("{mn}/system/product/usr/keylayout/*.kl")
if not any(x.search("key 116   STB_POWER") for x in kl):
    print("keys: the image is not checked for STB_POWER")
# rs30-power: the remote power keys stay STB_POWER WAKE (x50_power_keys.py REMOTE), only them
if any(x.search("key 113   STB_POWER WAKE") or x.search("key 185 STB_POWER WAKE") for x in kl):
    print("keys: the image check refuses the remote power key")
r3840 = rx("{mn}/system/product/usr/keylayout/Vendor_000d_Product_3840.kl")
if (not any(x.search("key 113        POWER WAKE") for x in r3840) or not any(x.search("key 185 STB_POWER") for x in r3840)
        or any(x.search("key 113        STB_POWER WAKE  #") for x in r3840)):
    print("keys: the image is not checked for the remote power key 113")
PY
  rm -rf "$T/kl"; cp -R "$R/overlay/v1/keylayout" "$T/kl"
  python3 -B "$R/devices/g0111/image/x50_power_keys.py" apply "$T/kl" > "$T/pk.out" 2>&1 || echo "keys: power edit fails"
  K=$T/kl/Vendor_000d_Product_3840.kl
  for kv in "113 STB_POWER WAKE" "185 STB_POWER WAKE" "66 SETTINGS" "102 HOME" "127 TV_MEDIA_CONTEXT_MENU"; do
    set -- $kv
    want="^key[[:space:]]+$1[[:space:]]+$2"; [ -n "${3:-}" ] && want="$want[[:space:]]+$3"
    grep -qE "$want([[:space:]]|$)" "$K" || echo "keys: 3840 $1 is not $kv"
  done
  rm -rf "$T/base"; cp -R "$T/kl" "$T/base"
  python3 -B "$R/devices/g0155/image/rs30_menu_keys.py" apply "$T/kl" > "$T/mk.out" 2>&1 || echo "keys: menu-key edit fails"
  python3 -B "$R/devices/g0155/image/rs30_menu_keys.py" check "$T/base" "$T/kl" > "$T/mc.out" 2>&1 || echo "keys: menu-key edit is not exactly its lines"
  rm -rf "$T/kx"; cp -R "$T/kl" "$T/kx"; echo "key 135   TV_TELETEXT" >> "$T/kx/Vendor_000d_Product_3840.kl"
  python3 -B "$R/devices/g0155/image/rs30_menu_keys.py" check "$T/base" "$T/kx" > "$T/mx.out" 2>&1 && echo "keys: the menu-key check passes a changed layout"
  for kv in $RS30_CUSTOM; do
    grep -qE "^key[[:space:]]+${kv%%:*}[[:space:]]+${kv#*:}([[:space:]]|$)" "$K" || echo "keys: 3840 ${kv%%:*} is not ${kv#*:} (Remote buttons)"
  done
  echo "$RS30_MENU" | tr '|' '\n' | while read -r sc key; do
    grep -qE "^key[[:space:]]+$sc[[:space:]]+$key([[:space:]]|$)" "$K" || echo "keys: 3840 $sc is not $key"
    grep -q "keyCode=\"KEYCODE_$key\" component=\"org.lumen.x50.projector/.KeyReceiver\"" "$R/apps/X50PowerOverlay/res/xml/global_keys.xml" \
      || echo "keys: KEYCODE_$key is not a global key of the menu"
    grep -q "case KeyEvent.KEYCODE_$key:" "$R/apps/LumenProjector/host/activity/src/org/lumen/x50/projector/KeyReceiver.java" \
      || echo "keys: KeyReceiver does not handle KEYCODE_$key"
  done
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
    grep -qxF "!unsigned	need	signed_stage/SHA256SUMS	signed_stage/apps/X50EyeGuard.apk	signed_stage/apps/X50Projector.apk	signed_stage/apps/X50PowerOverlay.apk	signed_stage/apps/X50TvInput.apk	signed_stage/apps/LumenKeyboard.apk	signed_stage/base_signed/base_signed.tsv" "$S" \
      || echo "$want is not a need of the signed stage"
  done
  b=$(sed -n 's/^apps=//p' "$B")
  for a in X50PowerOverlay X50TvInput X50Projector; do echo " $b " | grep -q " $a " || echo "apps: $a is not in build.conf apps"; done
  python3 "$G/tools/devices/assemble_app.py" --root "$R" --recipes g0155 2>/dev/null | grep -q "^X50TvInput	" || echo "hdmi: no g0155 recipe of X50TvInput"
  # every global key goes to a component of the image (the base TvFrameworkOverlay rows to com.google.android.tv stay as
  # the base has them: no such app, the key does nothing, as without the overlay)
  python3 -B - "$R" <<'PY'
import re, sys
r = sys.argv[1]
def pkg(manifest):
    return re.search(r'package="([^"]+)"', open(r + "/" + manifest, encoding="utf-8").read()).group(1)
def receivers(manifest):
    return set(re.findall(r'android:name="\.([A-Za-z]+Receiver)"', open(r + "/" + manifest, encoding="utf-8").read()))
image = {pkg("apps/LumenProjector/host/activity/AndroidManifest.xml"): receivers("apps/LumenProjector/host/activity/AndroidManifest.xml"),
         pkg("apps/LumenTvInput/AndroidManifest.xml"): receivers("apps/LumenTvInput/AndroidManifest.xml"),
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
  cp -R "$G/devices/g0155" "$G/devices/g0111" "$G/devices/_family" "$T/t/devices/"
  cp -R "$G/overlay/v1/keylayout" "$G/overlay/v1/idc" "$T/t/overlay/v1/"
  cp "$G/apps/app_dirs.tsv" "$T/t/apps/"
  for a in X50PowerOverlay LumenProjector/host/activity X50EyeGuard LumenTvInput LumenSetup LumenHome LumenMiracast; do mkdir -p "$T/t/apps/$a"; cp -R "$G/apps/$a/AndroidManifest.xml" "$G/apps/$a/res" "$T/t/apps/$a/" 2>/dev/null; done
  mkdir -p "$T/t/apps/LumenProjector/host/activity/src/org/lumen/x50/projector" && cp "$G/apps/LumenProjector/host/activity/src/org/lumen/x50/projector/KeyReceiver.java" "$T/t/apps/LumenProjector/host/activity/src/org/lumen/x50/projector/"
  cp "$G/overlay/v1/z9x-sysconfig-nogms.xml" "$T/t/overlay/v1/"
  cp -R "$G/tools/devices" "$T/t/tools/"
  for d in "$G"/devices/*/; do d=${d%/}; [ -d "$T/t/devices/${d##*/}" ] || cp -R "$d" "$T/t/devices/"; done
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
mut no_keep_rules "debloat: no keep" "$S" $'\tproduct/priv-app/TvProvision\tsystem_ext/apex' $'\tsystem_ext/apex'
mut debloat_unchecked "debloat: the image" "$S" $'*\tgone\tdebloat.txt\t{mn}/system\n' ''
mut usb_adb "usb:" devices/_family/rs30/image/rs30_prop.txt '+persist.sys.usb.config=mtp' '+persist.sys.usb.config=adb'
mut emulator "props:" devices/_family/rs30/image/rs30_prop.txt 'ro.build.characteristics=tv' 'ro.build.characteristics=emulator'
mut tv_unchecked "props: the image" "$S" $'*\tline\t{mn}/system/product/etc/build.prop\tro.build.characteristics=tv\n' ''
mut no_power_edit "keys: no power" "$S" $'*\trun_root\tx50_power_keys.py\tapply\t{sys}/product/usr/keylayout\n' ''
mut stb_power_unchecked "keys: the image is not checked for STB_POWER" "$S" $'\t^key\\s+(?!(113|185)\\s)[0-9]+\\s+STB_POWER\n' $'\t^key\\s+(?!(113|185)\\s)[0-9]+\\s+STB_POWERX\n'
mut remote_power_refused "keys: the image check refuses the remote power key" "$S" $'\t^key\\s+(?!(113|185)\\s)[0-9]+\\s+STB_POWER\n' $'\t^key\\s+[0-9]+\\s+STB_POWER\n'
mut remote_power_unchecked "keys: the image is not checked for the remote power key 113" "$S" $'\t^key\\s+(113|185)\\s+(?=\\S)(?!STB_POWER\\s+WAKE\\b)\n' $'\t^key\\s+(113|185)\\s+(?=\\S)(?!STB_POWER)\n'
mut rs30_key_mapped "keys: menu-key edit fails" overlay/v1/keylayout/Vendor_000d_Product_3840.kl 'key 102        HOME' $'key 102        HOME\nkey 172        TV'
mut menu_edit_gone "keys: no menu-key edit" "$S" $'*\trun_root\trs30_menu_keys.py\tapply\t{sys}/product/usr/keylayout\n' ''
mut menu_edit_first "keys: no menu-key edit after" "$S" $'*\trun_root\tx50_power_keys.py\tapply\t{sys}/product/usr/keylayout\n*\trun_root\trs30_menu_keys.py\tapply\t{sys}/product/usr/keylayout\n' $'*\trun_root\trs30_menu_keys.py\tapply\t{sys}/product/usr/keylayout\n*\trun_root\tx50_power_keys.py\tapply\t{sys}/product/usr/keylayout\n'
mut menu_tool_unstaged "keys: rs30_menu_keys.py is not a staged tool" devices/_family/rs30/rs30.build.conf ' devices/g0155/image/rs30_menu_keys.py' ''
mut menu_tool_not_needed "keys: rs30_menu_keys.py is not a need" "$S" $'x50_power_keys.py\trs30_menu_keys.py\t' $'x50_power_keys.py\t'
mut menu_line_unchecked "keys: menu key line unchecked in {mn}/system" "$S" $'*\tline\t{mn}/system/product/usr/keylayout/Vendor_000d_Product_3840.kl\tkey 217' $'*\tline\t{mn}/system/product/usr/keylayout/Vendor_000d_Product_3840.kl\tkey 218'
mut menu_key_not_global "keys: KEYCODE_TV_NUMBER_ENTRY is not a global key" apps/X50PowerOverlay/res/xml/global_keys.xml '<key keyCode="KEYCODE_TV_NUMBER_ENTRY" component="org.lumen.x50.projector/.KeyReceiver" />' ''
mut menu_key_wrong_code "keys: 3840 234 is not TV_NUMBER_ENTRY" devices/g0155/image/rs30_menu_keys.py '(234, "TV_NUMBER_ENTRY"' '(234, "TV_CONTENTS_MENU"'
mut menu_key_unhandled "keys: KeyReceiver does not handle KEYCODE_TV_ZOOM_MODE" apps/LumenProjector/host/activity/src/org/lumen/x50/projector/KeyReceiver.java 'case KeyEvent.KEYCODE_TV_ZOOM_MODE:' 'case KeyEvent.KEYCODE_TV_ZOOM_OUT:'
mut menu_check_lax "keys: the menu-key check passes" devices/g0155/image/rs30_menu_keys.py '        if tn != want:' '        if False:'
mut rs30_custom_dropped "keys: 3840 135 is not MACRO_1" overlay/v1/keylayout/Vendor_000d_Product_3840.kl 'key 135        MACRO_1' 'key 135        TV'
mut gear_elsewhere "keys: KEYCODE_SETTINGS" apps/X50PowerOverlay/res/xml/global_keys.xml '<key keyCode="KEYCODE_SETTINGS" component="org.lumen.x50.projector/.KeyReceiver" />' '<key keyCode="KEYCODE_SETTINGS" component="org.lumen.projector/.KeyReceiver" />'
mut no_tvinput "apps: X50TvInput" devices/_family/rs30/rs30.device.conf 'X50Projector X50TvInput' 'X50Projector'
mut idc_not_staged "keys: Vendor_000d_Product_3840.idc" devices/_family/rs30/rs30.build.conf 'overlay/v1/idc/Vendor_000d_Product_3840.idc ' ''
mut kl_not_placed "keys: Vendor_000d_Product_3840.kl" "$S" $'*\tplace\tVendor_000d_Product_3840.kl\t' $'*\tplace\tVendor_000d_Product_3841.kl\t'
mut overlay_not_installed "keys: the overlay" "$S" $'signed_stage/apps/X50PowerOverlay.apk\t{sys}/product/overlay/X50PowerOverlay.apk' $'signed_stage/apps/X50PowerOverlay.apk\t{sys}/product/overlay/Other.apk'
mut tvinput_unchecked "hdmi: org.lumen.tvinput is not checked" "$S" $'!unsigned\tsame\t{mn}/system/app/X50TvInput/X50TvInput.apk' $'!unsigned\tsame\t{mn}/system/app/X50TvInput/Other.apk'
mut launcher_kept "home: the base launcher is not removed" "$S" $'!unsigned\tremove\tdebloat_launcher.txt\t{sys}\n' ''
mut launcher_unlisted "home: the base launcher stays" devices/_image/debloat_launcher.txt $'product/priv-app/TVLauncherNoGMS\n' ''
mut launcher_unchecked "home: the image is not checked for the base launcher" "$S" $'!unsigned\tgone\tdebloat_launcher.txt\t{mn}/system\n' ''
mut home_role "home: no HOME role" "$S" 'Z9xHome.apk:org.lumen.home:home' 'Z9xHome.apk:org.lumen.home:nohome'
mut home_not_app "cast/home: Z9xHome" devices/_family/rs30/rs30.device.conf ' Z9xHome LumenMiracast' ' LumenMiracast'
mut home_not_placed "home: Lumen Home is not installed" "$S" $'signed_stage/apps/Z9xHome.apk\t{sys}/priv-app/Z9xHome/Z9xHome.apk' $'signed_stage/apps/Z9xHome.apk\t{sys}/app/Z9xHome/Z9xHome.apk'
mut home_unchecked "home: Lumen Home is not checked" "$S" $'!unsigned\tsame\t{mn}/system/priv-app/Z9xHome/Z9xHome.apk' $'!unsigned\tsame\t{mn}/system/priv-app/Z9xHome/Other.apk'
mut miracast_not_app "cast/home: LumenMiracast" devices/_family/rs30/rs30.device.conf ' LumenMiracast LumenWifiP2pOverlay' ' LumenWifiP2pOverlay'
mut p2p_not_placed "cast: the Wi-Fi Direct overlay is not installed" "$S" $'signed_stage/apps/LumenWifiP2pOverlay.apk\t{sys}/product/overlay/LumenWifiP2pOverlay.apk' $'signed_stage/apps/LumenWifiP2pOverlay.apk\t{sys}/app/LumenWifiP2pOverlay.apk'
mut miracast_privapp "cast: the receiver's privileged" "$S" $'privapp-permissions-lumen-miracast.xml\t{sys}/etc/permissions/' $'privapp-permissions-lumen-miracast.xml\t{sys}/etc/sysconfig/'
mut airplay_unbuilt "airplay: not built" devices/_family/rs30/rs30.build.conf ' Z9xAirPlay ' ' '
mut airplay_libs "airplay: its armeabi-v7a" "$S" $'!unsigned\trun\tairplay_libs.py\t{sys}/app/Z9xAirPlay/Z9xAirPlay.apk\n' ''
mut sysconfig_unplaced "sysconfig: rs30-sysconfig.xml" "$S" $'*\tplace\trs30-sysconfig.xml\t' $'*\tplace\tother.xml\t'
mut projector_stopped "sysconfig: org.lumen.x50.projector" devices/g0155/image/rs30-sysconfig.xml '<initial-package-state package="org.lumen.x50.projector" stopped="false" />' ''
mut setup_not_app "setup: Z9xSetup is not a g0155 app" devices/_family/rs30/rs30.device.conf ' Z9xSetup Z9xHome' ' Z9xHome'
mut setup_not_built "setup: Z9xSetup is not in build.conf" devices/_family/rs30/rs30.build.conf ' Z9xSetup Z9xHome' ' Z9xHome'
mut setup_privapp_unstaged "setup: its privileged permissions are not staged" devices/_family/rs30/rs30.build.conf ' overlay/v1/privapp-permissions-z9xsetup.xml' ''
mut setup_not_placed "setup: Lumen Setup is not installed" "$S" $'signed_stage/apps/Z9xSetup.apk\t{sys}/priv-app/Z9xSetup/Z9xSetup.apk' $'signed_stage/apps/Z9xSetup.apk\t{sys}/app/Z9xSetup/Z9xSetup.apk'
mut setup_unchecked "setup: Lumen Setup is not checked" "$S" $'!unsigned\tsame\t{mn}/system/priv-app/Z9xSetup/Z9xSetup.apk' $'!unsigned\tsame\t{mn}/system/priv-app/Z9xSetup/Other.apk'
mut setup_privapp_unplaced "setup: its privileged permissions are not installed" "$S" $'privapp-permissions-z9xsetup.xml\t{sys}/etc/permissions/' $'privapp-permissions-z9xsetup.xml\t{sys}/etc/sysconfig/'
mut setup_role "setup: no setup role" "$S" 'Z9xSetup.apk:org.lumen.setup:setup' 'Z9xSetup.apk:org.lumen.setup:plain'
mut provision_kept "setup: TvProvision stays" "$S" $'!unsigned\trm\t{sys}/product/priv-app/TvProvision\n' ''
mut provision_rm_early "setup: TvProvision stays" "$S" $'!unsigned\tremove\tdebloat_launcher.txt\t{sys}\n!unsigned\trm\t{sys}/product/priv-app/TvProvision\n' $'!unsigned\trm\t{sys}/product/priv-app/TvProvision\n!unsigned\tremove\tdebloat_launcher.txt\t{sys}\n'
mut provision_gone_unsigned "setup: the unsigned build check loses" "$S" $'!unsigned\trm\t{sys}/product/priv-app/TvProvision' $'*\trm\t{sys}/product/priv-app/TvProvision'
mut provision_gone_unchecked "setup: the image is not checked for TvProvision gone" "$S" $'!unsigned\tabsent\t{mn}/system/product/priv-app/TvProvision\n' ''
mut provision_unsigned_unchecked "debloat: the unsigned image" "$S" $'unsigned\tis_dir\t{mn}/system/product/priv-app/TvProvision\n' ''
mut setup_stopped "sysconfig: org.lumen.setup" overlay/v1/z9x-sysconfig-nogms.xml '<initial-package-state package="org.lumen.setup" stopped="false" />' ''
mut eyeguard_stopped "sysconfig: org.lumen.x50.eyeguard" devices/g0155/image/rs30-sysconfig.xml '<initial-package-state package="org.lumen.x50.eyeguard" stopped="false" />' ''
echo "rs30_image_test: $pass passed, $fail failed"
[ "$fail" = 0 ]
